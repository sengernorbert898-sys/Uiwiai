package dev.fishingcatchtimer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Persistent client-side preferences and independent calibration profiles per server. */
public final class CatchTimerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("fishingcatchtimer.json");

    /** Used until a server profile has at least three confirmed catches. */
    public double biteWindowSeconds = 2.5;
    public List<Double> warningSeconds = new ArrayList<>(List.of(1.5, 0.5));
    public boolean visualAlerts = true;
    public boolean soundAlerts = true;
    /** Legacy master volume retained for importing older 1.0.x/1.1.x config files. */
    public double soundVolume = 0.75;

    /** Event identifiers are SoundEvent ids, including resource-pack sounds.json entries. */
    public String soundStartId = "minecraft:entity.experience_orb.pickup";
    public String soundWarningId = "minecraft:block.bell.resonate";
    public String soundBiteId = "minecraft:ui.toast.challenge_complete";

    /** -1 means migrate the previous global soundVolume value when loading an older config. */
    public double soundStartVolume = -1.0;
    public double soundWarningVolume = -1.0;
    public double soundBiteVolume = -1.0;

    /** Normalized HUD top-left position within the available screen area (0..1). */
    public double hudX = 0.72;
    public double hudY = 0.06;
    public double hudScale = 1.0;

    /** Radius and particle signature/direction filtering for the local bobber. */
    public double particleRadius = 7.0;
    public boolean strictParticleFilter = true;

    /** Learn confirmed cue-to-bite durations and refine the live estimate from wake movement. */
    public boolean adaptiveLearning = true;
    public boolean trajectoryTracking = true;

    /** Keyed by server address + dimension. Profiles stay local to this client. */
    public Map<String, ServerProfile> serverProfiles = new LinkedHashMap<>();

    public static final class ServerProfile {
        /** Rolling recent sample window, in seconds. */
        public List<Double> samplesSeconds = new ArrayList<>();
        public long totalCatches;
        public double totalAbsoluteErrorSeconds;
        public double lastActualSeconds;
        public double lastAbsoluteErrorSeconds;

        public void record(double actualSeconds, double predictedSeconds) {
            if (!Double.isFinite(actualSeconds) || actualSeconds < 0.25 || actualSeconds > 12.0) return;
            if (samplesSeconds == null) samplesSeconds = new ArrayList<>();
            samplesSeconds.add(actualSeconds);
            while (samplesSeconds.size() > 24) samplesSeconds.remove(0);
            totalCatches++;
            lastActualSeconds = actualSeconds;
            double safePrediction = Double.isFinite(predictedSeconds) ? predictedSeconds : actualSeconds;
            lastAbsoluteErrorSeconds = Math.abs(actualSeconds - safePrediction);
            totalAbsoluteErrorSeconds += lastAbsoluteErrorSeconds;
        }

        public int recentSampleCount() {
            return samplesSeconds == null ? 0 : samplesSeconds.size();
        }

        public double medianSeconds() {
            if (samplesSeconds == null || samplesSeconds.isEmpty()) return 0.0;
            List<Double> sorted = new ArrayList<>(samplesSeconds);
            sorted.sort(Comparator.naturalOrder());
            int middle = sorted.size() / 2;
            if (sorted.size() % 2 == 1) return sorted.get(middle);
            return (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
        }

        public double averageAbsoluteErrorSeconds() {
            return totalCatches <= 0 ? 0.0 : totalAbsoluteErrorSeconds / totalCatches;
        }
    }

    public static CatchTimerConfig load() {
        CatchTimerConfig config = new CatchTimerConfig();
        if (Files.isRegularFile(FILE)) {
            try (Reader reader = Files.newBufferedReader(FILE)) {
                CatchTimerConfig loaded = GSON.fromJson(reader, CatchTimerConfig.class);
                if (loaded != null) config = loaded;
            } catch (Exception exception) {
                FishingCatchTimerClient.LOGGER.error("Could not read fishingcatchtimer.json; using defaults", exception);
            }
        }
        config.sanitize();
        config.save();
        return config;
    }

    public void save() {
        sanitize();
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception exception) {
            FishingCatchTimerClient.LOGGER.error("Could not save fishingcatchtimer.json", exception);
        }
    }

    public ServerProfile profile(String key) {
        if (serverProfiles == null) serverProfiles = new LinkedHashMap<>();
        return serverProfiles.computeIfAbsent(key, ignored -> new ServerProfile());
    }

    public void clearProfile(String key) {
        if (serverProfiles != null) serverProfiles.remove(key);
        save();
    }

    public void resetPreferences() {
        CatchTimerConfig defaults = new CatchTimerConfig();
        biteWindowSeconds = defaults.biteWindowSeconds;
        warningSeconds = new ArrayList<>(defaults.warningSeconds);
        visualAlerts = defaults.visualAlerts;
        soundAlerts = defaults.soundAlerts;
        soundVolume = defaults.soundVolume;
        soundStartId = defaults.soundStartId;
        soundWarningId = defaults.soundWarningId;
        soundBiteId = defaults.soundBiteId;
        soundStartVolume = defaults.soundStartVolume;
        soundWarningVolume = defaults.soundWarningVolume;
        soundBiteVolume = defaults.soundBiteVolume;
        hudX = defaults.hudX;
        hudY = defaults.hudY;
        hudScale = defaults.hudScale;
        particleRadius = defaults.particleRadius;
        strictParticleFilter = defaults.strictParticleFilter;
        adaptiveLearning = defaults.adaptiveLearning;
        trajectoryTracking = defaults.trajectoryTracking;
        // Deliberately retain learned server profiles when preferences are reset.
        save();
    }

    private void sanitize() {
        biteWindowSeconds = clamp(biteWindowSeconds, 0.5, 8.0, 2.5);
        soundVolume = clamp(soundVolume, 0.0, 1.0, 0.75);
        soundStartId = normalizeSoundId(soundStartId, "minecraft:entity.experience_orb.pickup");
        soundWarningId = normalizeSoundId(soundWarningId, "minecraft:block.bell.resonate");
        soundBiteId = normalizeSoundId(soundBiteId, "minecraft:ui.toast.challenge_complete");
        soundStartVolume = clamp(soundStartVolume < 0.0 ? soundVolume : soundStartVolume, 0.0, 1.0, 0.75);
        soundWarningVolume = clamp(soundWarningVolume < 0.0 ? soundVolume : soundWarningVolume, 0.0, 1.0, 0.75);
        soundBiteVolume = clamp(soundBiteVolume < 0.0 ? soundVolume : soundBiteVolume, 0.0, 1.0, 0.75);
        hudX = clamp(hudX, 0.0, 1.0, 0.72);
        hudY = clamp(hudY, 0.0, 1.0, 0.06);
        hudScale = clamp(hudScale, 0.65, 1.75, 1.0);
        particleRadius = clamp(particleRadius, 2.0, 16.0, 7.0);

        List<Double> cleaned = new ArrayList<>();
        if (warningSeconds != null) {
            for (Double warning : warningSeconds) {
                if (warning != null && Double.isFinite(warning) && warning > 0.0 && warning < biteWindowSeconds) {
                    cleaned.add(Math.min(7.5, warning));
                }
            }
        }
        cleaned.sort(Comparator.reverseOrder());
        warningSeconds = new ArrayList<>(cleaned.stream().distinct().limit(2).toList());

        if (serverProfiles == null) serverProfiles = new LinkedHashMap<>();
        for (ServerProfile profile : serverProfiles.values()) {
            if (profile.samplesSeconds == null) profile.samplesSeconds = new ArrayList<>();
            profile.samplesSeconds.removeIf(value -> value == null || !Double.isFinite(value) || value < 0.25 || value > 12.0);
            while (profile.samplesSeconds.size() > 24) profile.samplesSeconds.remove(0);
            if (profile.totalCatches < profile.samplesSeconds.size()) profile.totalCatches = profile.samplesSeconds.size();
            if (!Double.isFinite(profile.totalAbsoluteErrorSeconds) || profile.totalAbsoluteErrorSeconds < 0) profile.totalAbsoluteErrorSeconds = 0;
        }
        // Keep the config bounded if the player visits many servers over time.
        while (serverProfiles.size() > 32) {
            String oldest = serverProfiles.keySet().iterator().next();
            serverProfiles.remove(oldest);
        }
    }

    /** Adds the default namespace and rejects malformed resource identifiers. */
    public static String normalizeSoundId(String value, String fallback) {
        String candidate = value == null ? "" : value.trim();
        if (candidate.isEmpty()) return fallback;
        if (!candidate.contains(":")) candidate = "minecraft:" + candidate;
        try {
            return net.minecraft.resources.Identifier.parse(candidate).toString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public static boolean isValidSoundId(String value) {
        if (value == null || value.isBlank()) return false;
        String candidate = value.trim();
        if (!candidate.contains(":")) candidate = "minecraft:" + candidate;
        try {
            net.minecraft.resources.Identifier.parse(candidate);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static double clamp(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) return fallback;
        return Math.max(min, Math.min(max, value));
    }
}
