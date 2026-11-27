package dev.fishingcatchtimer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Settings screen for timing, learning, detection, audio and HUD placement. */
public final class FishingCatchTimerScreen extends Screen {
    private final Screen parent;

    public FishingCatchTimerScreen(Screen parent) {
        super(Component.literal("Fishing Catch Timer"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        CatchTimerConfig config = FishingCatchTimerClient.config();
        int contentWidth = Math.min(410, this.width - 24);
        int left = (this.width - contentWidth) / 2;
        int rowY = 66;
        int rowHeight = 20;
        int gap = 3;

        addOption(left, rowY, contentWidth, rowHeight, "Fallback bite window: " + format(config.biteWindowSeconds) + " s  · click to cycle", () -> {
            config.biteWindowSeconds = config.biteWindowSeconds >= 8.0 ? 0.5 : Math.min(8.0, config.biteWindowSeconds + 0.25);
            refresh();
        });
        rowY += rowHeight + gap;

        addOption(left, rowY, contentWidth, rowHeight, "Warning thresholds: " + warningLabel(config.warningSeconds), () -> {
            config.warningSeconds = nextWarningProfile(config.warningSeconds, config.biteWindowSeconds);
            refresh();
        });
        rowY += rowHeight + gap;

        addOption(left, rowY, contentWidth, rowHeight, "Adaptive per-server learning: " + onOff(config.adaptiveLearning), () -> {
            config.adaptiveLearning = !config.adaptiveLearning;
            refresh();
        });
        rowY += rowHeight + gap;

        addOption(left, rowY, contentWidth, rowHeight, "Particle trajectory tracking: " + onOff(config.trajectoryTracking), () -> {
            config.trajectoryTracking = !config.trajectoryTracking;
            refresh();
        });
        rowY += rowHeight + gap;

        addOption(left, rowY, contentWidth, rowHeight, "Strict particle signature filter: " + onOff(config.strictParticleFilter), () -> {
            config.strictParticleFilter = !config.strictParticleFilter;
            refresh();
        });
        rowY += rowHeight + gap;

        addOption(left, rowY, contentWidth, rowHeight, "Visual HUD: " + onOff(config.visualAlerts), () -> {
            config.visualAlerts = !config.visualAlerts;
            refresh();
        });
        rowY += rowHeight + gap;

        addOption(left, rowY, contentWidth, rowHeight, "Sound alerts: " + onOff(config.soundAlerts), () -> {
            config.soundAlerts = !config.soundAlerts;
            refresh();
        });
        rowY += rowHeight + gap;

        addOption(left, rowY, contentWidth, rowHeight, "Audio settings: sound IDs, volumes and test playback", () -> {
            config.save();
            this.minecraft.gui.setScreen(new FishingCatchTimerAudioScreen(this));
        });
        rowY += rowHeight + gap;

        addOption(left, rowY, contentWidth, rowHeight, "Particle detection radius: " + format(config.particleRadius) + " blocks", () -> {
            config.particleRadius = config.particleRadius >= 16.0 ? 2.0 : Math.min(16.0, config.particleRadius + 2.0);
            refresh();
        });
        rowY += rowHeight + gap;

        addOption(left, rowY, contentWidth, rowHeight, "HUD scale: " + format(config.hudScale) + "×", () -> {
            config.hudScale = config.hudScale >= 1.75 ? 0.65 : Math.min(1.75, config.hudScale + 0.1);
            refresh();
        });
        rowY += rowHeight + 8;

        int buttonGap = 5;
        int buttonWidth = (contentWidth - buttonGap * 3) / 4;
        addRenderableWidget(Button.builder(Component.literal("Reset"), button -> {
            config.resetPreferences();
            refresh();
        }).bounds(left, rowY, buttonWidth, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Clear data"), button -> {
            CatchTimerController.clearCurrentServerLearning();
            refresh();
        }).bounds(left + buttonWidth + buttonGap, rowY, buttonWidth, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Move HUD"), button -> {
            config.save();
            this.minecraft.gui.setScreen(new HudPositionScreen(this));
        }).bounds(left + (buttonWidth + buttonGap) * 2, rowY, buttonWidth, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(left + (buttonWidth + buttonGap) * 3, rowY, buttonWidth, 22).build());
    }

    private void addOption(int x, int y, int width, int height, String label, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label), button -> {
            action.run();
            FishingCatchTimerClient.config().save();
        }).bounds(x, y, width, height).build());
    }

    private void refresh() {
        FishingCatchTimerClient.config().save();
        this.minecraft.gui.setScreen(new FishingCatchTimerScreen(parent));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        String title = "FISHING CATCH TIMER · ADAPTIVE";
        String subtitle = "Learned timing is stored separately for each server and dimension";
        graphics.text(this.font, title, (this.width - this.font.width(title)) / 2, 16, 0xFFECF4FC, true);
        graphics.text(this.font, subtitle, (this.width - this.font.width(subtitle)) / 2, 32, 0xFF91A7BC, false);

        String stats = CatchTimerController.currentStatsSummary();
        String fitted = fit(stats, this.width - 30);
        graphics.text(this.font, fitted, (this.width - this.font.width(fitted)) / 2, 48, 0xFF75D9BD, false);

        String footer = "Move HUD opens a drag screen · settings and learned profiles save locally";
        graphics.text(this.font, footer, (this.width - this.font.width(footer)) / 2, this.height - 18, 0xFF7E93A8, false);
    }

    @Override
    public void onClose() {
        FishingCatchTimerClient.config().save();
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static String fit(String value, int maxWidth) {
        String text = value == null ? "" : value;
        while (!text.isEmpty() && MinecraftFontWidth.width(text) > maxWidth) text = text.substring(0, text.length() - 2) + "…";
        return text;
    }

    private static String onOff(boolean value) { return value ? "ON" : "OFF"; }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String warningLabel(List<Double> warningSeconds) {
        if (warningSeconds == null || warningSeconds.isEmpty()) return "Off";
        return String.join(" · ", warningSeconds.stream().map(FishingCatchTimerScreen::format).map(s -> s + "s").toList());
    }

    private static List<Double> nextWarningProfile(List<Double> current, double biteWindow) {
        double first = Math.max(0.25, Math.min(1.5, Math.floor((biteWindow - 0.25) * 4.0) / 4.0));
        double finalPoint = Math.min(0.5, first - 0.25);
        List<Double> twoStep = finalPoint >= 0.25 ? List.of(first, finalPoint) : List.of(first);
        double singlePoint = Math.max(0.25, Math.min(1.0, Math.floor((biteWindow - 0.25) * 4.0) / 4.0));
        List<List<Double>> profiles = new ArrayList<>();
        profiles.add(twoStep);
        List<Double> singleStep = List.of(singlePoint);
        if (!profiles.contains(singleStep)) profiles.add(singleStep);
        profiles.add(List.of());
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).equals(current)) return new ArrayList<>(profiles.get((i + 1) % profiles.size()));
        }
        return new ArrayList<>(profiles.get(0));
    }

    /** Keeps screen text fitting without requiring a font API outside this screen. */
    private static final class MinecraftFontWidth {
        private static int width(String text) {
            return net.minecraft.client.Minecraft.getInstance().font.width(text);
        }
    }
}
