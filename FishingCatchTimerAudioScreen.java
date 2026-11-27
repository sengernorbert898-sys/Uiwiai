package dev.fishingcatchtimer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Per-event sound identifiers, independent volumes and local test playback. */
public final class FishingCatchTimerAudioScreen extends Screen {
    private static final int ROW_GAP = 78;
    private static final String[] LABELS = {"START / PARTICLE CUE", "WARNING / COUNTDOWN", "BITE DETECTED"};

    private final Screen parent;
    private final EditBox[] soundFields = new EditBox[3];
    private String status = "Sound IDs are event IDs, not file paths. Resource-pack sounds.json events are supported.";
    private boolean statusError;
    private int contentLeft;
    private int contentWidth;
    private int firstRowY;

    public FishingCatchTimerAudioScreen(Screen parent) {
        super(Component.literal("Fishing Catch Timer — Audio"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        CatchTimerConfig config = FishingCatchTimerClient.config();
        contentWidth = Math.min(540, this.width - 24);
        contentLeft = (this.width - contentWidth) / 2;
        firstRowY = 78;
        int editWidth = Math.max(120, contentWidth - 104);

        addSoundRow(0, config.soundStartId, config.soundStartVolume, editWidth,
                CatchTimerController.SoundChannel.START);
        addSoundRow(1, config.soundWarningId, config.soundWarningVolume, editWidth,
                CatchTimerController.SoundChannel.WARNING);
        addSoundRow(2, config.soundBiteId, config.soundBiteVolume, editWidth,
                CatchTimerController.SoundChannel.BITE);

        int bottomY = this.height - 34;
        int gap = 6;
        int buttonWidth = (contentWidth - gap * 2) / 3;
        addRenderableWidget(Button.builder(Component.literal("Reset audio"), button -> resetAudio())
                .bounds(contentLeft, bottomY, buttonWidth, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Save"), button -> {
            if (saveSoundIds()) this.minecraft.gui.setScreen(parent);
        }).bounds(contentLeft + buttonWidth + gap, bottomY, buttonWidth, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> {
            if (saveSoundIds()) this.minecraft.gui.setScreen(parent);
        }).bounds(contentLeft + (buttonWidth + gap) * 2, bottomY, buttonWidth, 22).build());
    }

    private void addSoundRow(int index, String initialId, double initialVolume, int editWidth,
                             CatchTimerController.SoundChannel channel) {
        int y = firstRowY + index * ROW_GAP;
        EditBox field = new EditBox(this.font, contentLeft, y + 14, editWidth, 20,
                Component.literal("Sound event identifier"));
        field.setMaxLength(256);
        field.setValue(initialId);
        field.setHint(Component.literal("minecraft:..."));
        soundFields[index] = addRenderableWidget(field);

        int testX = contentLeft + editWidth + 8;
        addRenderableWidget(Button.builder(Component.literal("Test"), button -> {
            if (saveSoundIds()) CatchTimerController.testSound(channel);
        }).bounds(testX, y + 14, contentWidth - editWidth - 8, 20).build());

        addRenderableWidget(new AbstractSliderButton(contentLeft, y + 40, contentWidth, 20,
                Component.empty(), clamp(initialVolume, 0.0, 1.0)) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal(LABELS[index].split(" /")[0] + " volume: "
                        + Math.round(this.value * 100.0) + "%"));
            }

            @Override
            protected void applyValue() {
                CatchTimerConfig config = configForScreen();
                setVolume(config, index, this.value);
                config.soundVolume = (config.soundStartVolume + config.soundWarningVolume + config.soundBiteVolume) / 3.0;
                config.save();
            }
        });
    }

    private CatchTimerConfig configForScreen() {
        return FishingCatchTimerClient.config();
    }

    private static void setVolume(CatchTimerConfig config, int index, double volume) {
        switch (index) {
            case 0 -> config.soundStartVolume = volume;
            case 1 -> config.soundWarningVolume = volume;
            case 2 -> config.soundBiteVolume = volume;
            default -> throw new IllegalArgumentException("Unknown sound channel " + index);
        }
    }

    private boolean saveSoundIds() {
        String[] values = new String[soundFields.length];
        for (int i = 0; i < soundFields.length; i++) {
            values[i] = soundFields[i].getValue().trim();
            if (!CatchTimerConfig.isValidSoundId(values[i])) {
                status = "Invalid sound ID in " + LABELS[i] + ". Use namespace:path, e.g. minecraft:entity.experience_orb.pickup.";
                statusError = true;
                return false;
            }
        }

        CatchTimerConfig config = FishingCatchTimerClient.config();
        config.soundStartId = CatchTimerConfig.normalizeSoundId(values[0], "minecraft:entity.experience_orb.pickup");
        config.soundWarningId = CatchTimerConfig.normalizeSoundId(values[1], "minecraft:block.bell.resonate");
        config.soundBiteId = CatchTimerConfig.normalizeSoundId(values[2], "minecraft:ui.toast.challenge_complete");
        config.save();
        status = "Audio settings saved. If a custom event is unknown, check the resource pack's sounds.json event ID.";
        statusError = false;
        return true;
    }

    private void resetAudio() {
        CatchTimerConfig config = FishingCatchTimerClient.config();
        config.soundStartId = "minecraft:entity.experience_orb.pickup";
        config.soundWarningId = "minecraft:block.bell.resonate";
        config.soundBiteId = "minecraft:ui.toast.challenge_complete";
        config.soundStartVolume = 0.75;
        config.soundWarningVolume = 0.75;
        config.soundBiteVolume = 0.75;
        config.soundVolume = 0.75;
        config.save();
        this.minecraft.gui.setScreen(new FishingCatchTimerAudioScreen(parent));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        String title = "AUDIO CONFIGURATION";
        graphics.text(this.font, title, (this.width - this.font.width(title)) / 2, 18, 0xFFECF4FC, true);
        String subtitle = "Choose an event ID, adjust its own volume, then test it";
        graphics.text(this.font, subtitle, (this.width - this.font.width(subtitle)) / 2, 36, 0xFF91A7BC, false);

        for (int i = 0; i < LABELS.length; i++) {
            graphics.text(this.font, LABELS[i], contentLeft, firstRowY + i * ROW_GAP, 0xFF75D9BD, true);
        }

        int statusColor = statusError ? 0xFFFF8D83 : 0xFF91A7BC;
        String fitted = status;
        while (!fitted.isEmpty() && this.font.width(fitted) > this.width - 24) {
            fitted = fitted.substring(0, Math.max(0, fitted.length() - 2)) + "…";
        }
        graphics.text(this.font, fitted, (this.width - this.font.width(fitted)) / 2,
                this.height - 50, statusColor, false);
    }

    @Override
    public void onClose() {
        // Escape should always let the player leave; malformed unsaved edits are discarded.
        saveSoundIds();
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) return 0.75;
        return Math.max(min, Math.min(max, value));
    }
}
