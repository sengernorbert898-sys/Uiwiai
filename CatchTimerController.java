package dev.fishingcatchtimer;

import dev.fishingcatchtimer.mixin.FishingHookAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Client-only state machine, cue classifier, trajectory estimator and per-server learner. */
public final class CatchTimerController {
    public enum State { WAITING, APPROACHING, READY, BITING, CANCELLED }

    private static final double VANILLA_WAKE_SPEED = 0.04;
    private static final int REQUIRED_SAMPLES_FOR_ADAPTIVE = 3;

    private static State state = State.WAITING;
    private static UUID trackedBobber;
    private static long approachStartedAt;
    private static long countdownEndsAt;
    private static long stateUntil;
    private static long lastTickAt;
    private static long lastParticleAt;
    private static long lastParticleSignatureAt;
    private static final Set<Double> firedWarnings = new HashSet<>();

    private static String activeProfileKey = "singleplayer|unknown";
    private static double previousParticleDistance;
    private static double closingSpeedBlocksPerSecond;
    private static int acceptedParticleCount;
    private static boolean learnedThisCast;
    private static double currentTrajectorySeconds = Double.NaN;

    private CatchTimerController() {}

    public static void tick(Minecraft client) {
        long now = Util.getMillis();
        lastTickAt = now;
        if (client.player == null || client.level == null) {
            clear();
            return;
        }

        String nextProfileKey = resolveProfileKey(client);
        if (!nextProfileKey.equals(activeProfileKey)) {
            // Never carry an in-progress estimate from one server/world into another profile.
            activeProfileKey = nextProfileKey;
            trackedBobber = null;
            state = State.WAITING;
            stateUntil = now;
            resetApproachTracking();
        }

        FishingHook hook = findLocalBobber(client);
        if (hook == null) {
            if (trackedBobber != null) {
                if (state == State.BITING) {
                    stateUntil = now + 1200;
                } else {
                    state = State.CANCELLED;
                    stateUntil = now + 900;
                }
                trackedBobber = null;
            } else if ((state == State.CANCELLED || state == State.BITING) && now >= stateUntil) {
                state = State.WAITING;
                resetApproachTracking();
            }
            return;
        }

        ensureTracked(hook, now);
        boolean biting = hook.getEntityData().get(FishingHookAccessor.fishingcatchtimer$getDataBiting());
        if (biting) {
            if (state != State.BITING) {
                recordConfirmedBite(now);
                state = State.BITING;
                stateUntil = now + 1600;
                playBite(client.player);
            } else {
                stateUntil = now + 1600;
            }
            return;
        }

        if (state == State.BITING) {
            if (now >= stateUntil) {
                state = State.WAITING;
                resetApproachTracking();
            }
            return;
        }

        if (state == State.APPROACHING) {
            double remaining = Math.max(0.0, (countdownEndsAt - now) / 1000.0);
            for (double warning : FishingCatchTimerClient.config().warningSeconds) {
                if (remaining <= warning && firedWarnings.add(warning)) playWarning(client.player, warning);
            }
            if (now >= countdownEndsAt) {
                state = State.READY;
                playReady(client.player);
            }
        } else if (state == State.CANCELLED && now >= stateUntil) {
            state = State.WAITING;
            resetApproachTracking();
        }
    }

    /** Called by the ClientLevel mixin with particle position and its directional offsets. */
    public static void onFishingParticle(ClientLevel level, double x, double y, double z,
                                         double offsetX, double offsetY, double offsetZ) {
        Minecraft client = Minecraft.getInstance();
        if (client.level != level || client.player == null) return;

        FishingHook hook = findLocalBobber(client);
        if (hook == null) return;

        long now = Util.getMillis();
        ensureTracked(hook, now);
        if (state == State.BITING || state == State.READY) return;

        CatchTimerConfig config = FishingCatchTimerClient.config();
        if (config.strictParticleFilter && Math.abs(offsetY - 0.01) >= 0.001) return;

        double dx = x - hook.getX();
        double dz = z - hook.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        double verticalDistance = Math.abs(y - hook.getY());
        if (horizontalDistance > config.particleRadius || verticalDistance > 3.0) return;
        if (!pointsTowardHook(x, z, hook.getX(), hook.getZ(), offsetX, offsetZ, horizontalDistance)) return;

        // Ignore duplicate spawn callbacks for the same wake location in a single frame.
        if (now - lastParticleSignatureAt < 35 && horizontalDistance > 0.18) return;
        lastParticleSignatureAt = now;

        if (state == State.WAITING || state == State.CANCELLED) {
            beginApproach(client.player, now, horizontalDistance);
        } else if (state == State.APPROACHING) {
            updateTrajectory(now, horizontalDistance);
        }
        lastParticleAt = now;
    }

    private static void beginApproach(Player player, long now, double distance) {
        CatchTimerConfig config = FishingCatchTimerClient.config();
        CatchTimerConfig.ServerProfile profile = config.profile(activeProfileKey);
        double baseline = config.biteWindowSeconds;
        if (config.adaptiveLearning && profile.recentSampleCount() >= REQUIRED_SAMPLES_FOR_ADAPTIVE) {
            baseline = clamp(profile.medianSeconds(), 0.5, 8.0, config.biteWindowSeconds);
        }

        state = State.APPROACHING;
        approachStartedAt = now;
        countdownEndsAt = now + Math.round(baseline * 1000.0);
        previousParticleDistance = distance;
        closingSpeedBlocksPerSecond = 0.0;
        acceptedParticleCount = 1;
        learnedThisCast = false;
        currentTrajectorySeconds = Double.NaN;
        firedWarnings.clear();
        playStart(player);
    }

    private static void updateTrajectory(long now, double distance) {
        long elapsedSinceParticle = now - lastParticleAt;
        if (elapsedSinceParticle >= 45 && elapsedSinceParticle <= 1200) {
            double closing = (previousParticleDistance - distance) / (elapsedSinceParticle / 1000.0);
            // Particle locations should form a wake approaching the bobber. Reject jitter and impossible jumps.
            if (closing > 0.03 && closing < 14.0) {
                closingSpeedBlocksPerSecond = closingSpeedBlocksPerSecond <= 0.0
                        ? closing : closingSpeedBlocksPerSecond * 0.65 + closing * 0.35;
            }
        }
        previousParticleDistance = distance;
        acceptedParticleCount++;

        CatchTimerConfig config = FishingCatchTimerClient.config();
        if (!config.trajectoryTracking || acceptedParticleCount < 3 || closingSpeedBlocksPerSecond < 0.08) return;

        double raw = distance / closingSpeedBlocksPerSecond;
        if (!Double.isFinite(raw) || raw < 0.15 || raw > 8.0) return;
        currentTrajectorySeconds = raw;
        double remaining = Math.max(0.0, (countdownEndsAt - now) / 1000.0);
        // Blend gently so individual particle-position noise cannot make the display jump wildly.
        double adjusted = remaining * 0.62 + raw * 0.38;
        adjusted = clamp(adjusted, 0.12, 8.0, remaining);
        countdownEndsAt = now + Math.round(adjusted * 1000.0);
    }

    private static void recordConfirmedBite(long now) {
        if (learnedThisCast || approachStartedAt <= 0L) return;
        double actual = (now - approachStartedAt) / 1000.0;
        if (actual < 0.25 || actual > 12.0) return;
        CatchTimerConfig config = FishingCatchTimerClient.config();
        if (config.adaptiveLearning) {
            double predicted = Math.max(0.25, (countdownEndsAt - approachStartedAt) / 1000.0);
            config.profile(activeProfileKey).record(actual, predicted);
            config.save();
        }
        learnedThisCast = true;
    }

    private static boolean pointsTowardHook(double particleX, double particleZ, double hookX, double hookZ,
                                            double offsetX, double offsetZ, double distance) {
        if (distance < 0.20) return true;
        double magnitude = Math.sqrt(offsetX * offsetX + offsetZ * offsetZ);
        if (magnitude < 0.012 || magnitude > 0.065) return false;

        double candidateOne = angleFromOffsets(offsetX, -offsetZ);
        double candidateTwo = angleFromOffsets(-offsetX, offsetZ);
        double dx = particleX - hookX;
        double dz = particleZ - hookZ;
        double targetAngle = normalizeDegrees(Math.toDegrees(Math.atan2(dx, dz)));
        double tolerance = Math.toDegrees(Math.atan2(0.03125, distance)) * 1.5;
        return anglesClose(candidateOne, targetAngle, tolerance) || anglesClose(candidateTwo, targetAngle, tolerance);
    }

    /** Converts the two horizontal wake offsets into one candidate travel angle. */
    private static double angleFromOffsets(double xOffset, double zOffset) {
        double xRatio = clamp(xOffset / VANILLA_WAKE_SPEED, -1.0, 1.0, 0.0);
        double zRatio = clamp(zOffset / VANILLA_WAKE_SPEED, -1.0, 1.0, 0.0);
        double angleX = Math.toDegrees(Math.acos(xRatio));
        double angleZ = Math.toDegrees(Math.asin(zRatio));
        if (xOffset < 0) angleZ = 180.0 - angleZ;
        if (zOffset < 0) angleX = 360.0 - angleX;
        angleX = normalizeDegrees(angleX);
        angleZ = normalizeDegrees(angleZ);
        double difference = angleX - angleZ;
        if (difference < -180.0) difference += 360.0;
        if (difference > 180.0) difference -= 360.0;
        return normalizeDegrees(angleZ + difference / 2.0);
    }

    private static boolean anglesClose(double first, double second, double tolerance) {
        double difference = Math.abs(normalizeDegrees(first) - normalizeDegrees(second));
        if (difference > 180.0) difference = 360.0 - difference;
        return difference <= tolerance;
    }

    private static double normalizeDegrees(double value) {
        value %= 360.0;
        return value < 0.0 ? value + 360.0 : value;
    }

    private static FishingHook findLocalBobber(Minecraft client) {
        if (client.level == null || client.player == null) return null;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity instanceof FishingHook hook && hook.getPlayerOwner() == client.player) return hook;
        }
        return null;
    }

    private static String resolveProfileKey(Minecraft client) {
        String server = client.getCurrentServer() != null
                ? client.getCurrentServer().ip.toLowerCase(Locale.ROOT)
                : "singleplayer";
        String dimension = client.level == null ? "unknown" : client.level.dimension().toString();
        return server + "|" + dimension;
    }

    private static void ensureTracked(FishingHook hook, long now) {
        UUID uuid = hook.getUUID();
        if (!uuid.equals(trackedBobber)) {
            trackedBobber = uuid;
            state = State.WAITING;
            stateUntil = now;
            resetApproachTracking();
            firedWarnings.clear();
        }
    }

    private static void resetApproachTracking() {
        approachStartedAt = 0L;
        countdownEndsAt = 0L;
        lastParticleAt = 0L;
        lastParticleSignatureAt = 0L;
        previousParticleDistance = 0.0;
        closingSpeedBlocksPerSecond = 0.0;
        acceptedParticleCount = 0;
        learnedThisCast = false;
        currentTrajectorySeconds = Double.NaN;
        firedWarnings.clear();
    }

    private static void clear() {
        state = State.WAITING;
        trackedBobber = null;
        stateUntil = 0L;
        resetApproachTracking();
    }

    public static boolean shouldRender() {
        if (state == State.CANCELLED || state == State.BITING) return lastTickAt <= stateUntil;
        // READY remains visible at zero until the authoritative bite flag or reel-in is seen.
        return trackedBobber != null;
    }

    public static State state() { return state; }

    public static String statusLabel() {
        return switch (state) {
            case WAITING -> "WAITING FOR FISH";
            case APPROACHING -> "FISH APPROACHING";
            case READY -> "BITE WINDOW";
            case BITING -> "BITE DETECTED";
            case CANCELLED -> "CAST RESET";
        };
    }

    public static String timerLabel() {
        if (state == State.APPROACHING) return String.format(Locale.ROOT, "%.1fs", remainingSeconds());
        return switch (state) {
            case WAITING -> "—";
            case READY -> "0.0s";
            case BITING -> "NOW!";
            case CANCELLED -> "—";
            case APPROACHING -> "";
        };
    }

    public static double remainingSeconds() {
        return Math.max(0.0, (countdownEndsAt - Util.getMillis()) / 1000.0);
    }

    public static double progress() {
        if (state != State.APPROACHING || approachStartedAt <= 0L) return 0.0;
        long total = Math.max(1L, countdownEndsAt - approachStartedAt);
        return Math.max(0.0, Math.min(1.0, 1.0 - (double) (countdownEndsAt - Util.getMillis()) / total));
    }

    public static int accentColor() {
        return switch (state) {
            case WAITING -> 0xFF6E8AA8;
            case APPROACHING -> 0xFF40D6B0;
            case READY -> 0xFFFFC15C;
            case BITING -> 0xFFFF6E70;
            case CANCELLED -> 0xFF8D9AA8;
        };
    }

    public static String profileLabel() {
        CatchTimerConfig.ServerProfile profile = FishingCatchTimerClient.config().profile(activeProfileKey);
        int count = profile.recentSampleCount();
        if (count == 0) return "CALIBRATING · first confirmed bites train this server";
        String median = String.format(Locale.ROOT, "%.2fs", profile.medianSeconds());
        if (count < REQUIRED_SAMPLES_FOR_ADAPTIVE) return "LEARNING " + count + "/" + REQUIRED_SAMPLES_FOR_ADAPTIVE + " · MED " + median;
        return "LEARNED " + count + " · MED " + median + " · ERR ±" + String.format(Locale.ROOT, "%.2fs", profile.averageAbsoluteErrorSeconds());
    }

    public static String currentServerLabel() {
        int divider = activeProfileKey.indexOf('|');
        return divider < 0 ? activeProfileKey : activeProfileKey.substring(0, divider);
    }

    public static String currentStatsSummary() {
        CatchTimerConfig.ServerProfile profile = FishingCatchTimerClient.config().profile(activeProfileKey);
        if (profile.recentSampleCount() == 0) return "No confirmed catches learned on this server yet.";
        return String.format(Locale.ROOT, "Server: %s   ·   Catches: %d   ·   Recent: %d/24   ·   Median: %.2fs   ·   Mean error: ±%.2fs",
                currentServerLabel(), profile.totalCatches, profile.recentSampleCount(), profile.medianSeconds(), profile.averageAbsoluteErrorSeconds());
    }

    public static String hudProfileLabel() {
        CatchTimerConfig.ServerProfile profile = FishingCatchTimerClient.config().profile(activeProfileKey);
        int count = profile.recentSampleCount();
        if (count == 0) return "CALIBRATING · no saved catches";
        if (count < REQUIRED_SAMPLES_FOR_ADAPTIVE) {
            return String.format(Locale.ROOT, "CALIBRATING %d/%d · MED %.2fs",
                    count, REQUIRED_SAMPLES_FOR_ADAPTIVE, profile.medianSeconds());
        }
        return String.format(Locale.ROOT, "LEARNED %d · MED %.2fs · ERR ±%.2fs",
                profile.totalCatches, profile.medianSeconds(), profile.averageAbsoluteErrorSeconds());
    }

    public static String trajectoryLabel() {
        if (acceptedParticleCount < 3) return "TRAIL: collecting";
        if (Double.isFinite(currentTrajectorySeconds)) return String.format(Locale.ROOT, "TRAIL: %.1fs", currentTrajectorySeconds);
        return "TRAIL: tracking";
    }

    public static void clearCurrentServerLearning() {
        FishingCatchTimerClient.config().clearProfile(activeProfileKey);
    }

    private static void playStart(Player player) {
        CatchTimerConfig config = FishingCatchTimerClient.config();
        playConfigured(config.soundStartId, config.soundStartVolume, 1.35f, true);
    }

    private static void playWarning(Player player, double threshold) {
        CatchTimerConfig config = FishingCatchTimerClient.config();
        playConfigured(config.soundWarningId, config.soundWarningVolume,
                threshold < 0.75 ? 1.45f : 1.15f, true);
    }

    private static void playReady(Player player) {
        CatchTimerConfig config = FishingCatchTimerClient.config();
        // Countdown-complete shares the warning channel but uses a higher pitch.
        playConfigured(config.soundWarningId, config.soundWarningVolume, 1.55f, true);
    }

    private static void playBite(Player player) {
        CatchTimerConfig config = FishingCatchTimerClient.config();
        playConfigured(config.soundBiteId, config.soundBiteVolume, 1.0f, true);
    }

    public enum SoundChannel { START, WARNING, BITE }

    /** Plays a selected channel even when global alerts are disabled; used by explicit Test buttons. */
    public static void testSound(SoundChannel channel) {
        CatchTimerConfig config = FishingCatchTimerClient.config();
        switch (channel) {
            case START -> playConfigured(config.soundStartId, config.soundStartVolume, 1.0f, false);
            case WARNING -> playConfigured(config.soundWarningId, config.soundWarningVolume, 1.0f, false);
            case BITE -> playConfigured(config.soundBiteId, config.soundBiteVolume, 1.0f, false);
        }
    }

    private static void playConfigured(String soundId, double volume, float pitch, boolean respectGlobalToggle) {
        CatchTimerConfig config = FishingCatchTimerClient.config();
        if (respectGlobalToggle && !config.soundAlerts) return;
        if (!Double.isFinite(volume) || volume <= 0.0) return;

        try {
            Identifier identifier = Identifier.parse(soundId);
            SimpleSoundInstance instance = new SimpleSoundInstance(
                    identifier,
                    SoundSource.MASTER,
                    (float) Math.min(1.0, volume),
                    pitch,
                    RandomSource.create(),
                    false,
                    0,
                    SoundInstance.Attenuation.NONE,
                    0.0,
                    0.0,
                    0.0,
                    true
            );
            Minecraft.getInstance().getSoundManager().play(instance);
        } catch (Exception exception) {
            FishingCatchTimerClient.LOGGER.warn("Could not play Fishing Catch Timer sound '{}'", soundId, exception);
        }
    }

    private static double clamp(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) return fallback;
        return Math.max(min, Math.min(max, value));
    }
}
