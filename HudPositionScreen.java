package dev.fishingcatchtimer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Full-screen drag editor. The HUD can be dropped anywhere, not just at an anchor. */
public final class HudPositionScreen extends Screen {
    private final Screen parent;
    private boolean dragging;
    private double dragOffsetX;
    private double dragOffsetY;

    public HudPositionScreen(Screen parent) {
        super(Component.literal("Position Fishing Catch Timer HUD"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int buttonY = this.height - 42;
        int buttonWidth = 118;
        int gap = 8;
        int left = (this.width - (buttonWidth * 2 + gap)) / 2;
        addRenderableWidget(Button.builder(Component.literal("Center HUD"), button -> {
            FishingCatchTimerClient.config().hudX = 0.5;
            FishingCatchTimerClient.config().hudY = 0.5;
            FishingCatchTimerClient.config().save();
        }).bounds(left, buttonY, buttonWidth, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(left + buttonWidth + gap, buttonY, buttonWidth, 22).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        graphics.fill(18, 18, this.width - 18, this.height - 54, 0x331B3541);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        CatchTimerConfig config = FishingCatchTimerClient.config();
        double scale = config.hudScale;
        double baseWidth = this.width / scale;
        double baseHeight = this.height / scale;
        int baseX = HudRenderer.positionX(baseWidth, config.hudX);
        int baseY = HudRenderer.positionY(baseHeight, config.hudY);

        String title = "POSITION YOUR HUD";
        String subtitle = "Left-click and drag the timer to any point on the screen. Your position is saved automatically.";
        graphics.text(this.font, title, (this.width - this.font.width(title)) / 2, 24, 0xFFECF4FC, true);
        graphics.text(this.font, subtitle, (this.width - this.font.width(subtitle)) / 2, 42, 0xFF9DB1C3, false);

        graphics.pose().pushMatrix();
        graphics.pose().scale((float) scale, (float) scale);
        HudRenderer.drawAt(graphics, this.minecraft, baseX, baseY, true);
        graphics.pose().popMatrix();

        String coordinates = String.format(java.util.Locale.ROOT, "X %.0f%%   Y %.0f%%   Scale %.2f×",
                config.hudX * 100.0, config.hudY * 100.0, config.hudScale);
        graphics.text(this.font, coordinates, (this.width - this.font.width(coordinates)) / 2, this.height - 62, 0xFF8DA4B8, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1) {
            CatchTimerConfig config = FishingCatchTimerClient.config();
            double scale = config.hudScale;
            double baseWidth = this.width / scale;
            double baseHeight = this.height / scale;
            int baseX = HudRenderer.positionX(baseWidth, config.hudX);
            int baseY = HudRenderer.positionY(baseHeight, config.hudY);
            int screenX = HudRenderer.screenX(baseX, scale);
            int screenY = HudRenderer.screenY(baseY, scale);
            int cardWidth = HudRenderer.screenX(HudRenderer.WIDTH, scale);
            int cardHeight = HudRenderer.screenY(HudRenderer.HEIGHT, scale);
            if (event.x() >= screenX && event.x() <= screenX + cardWidth
                    && event.y() >= screenY && event.y() <= screenY + cardHeight) {
                dragging = true;
                dragOffsetX = event.x() - screenX;
                dragOffsetY = event.y() - screenY;
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging && event.button() == 1) {
            CatchTimerConfig config = FishingCatchTimerClient.config();
            double scale = config.hudScale;
            double baseWidth = this.width / scale;
            double baseHeight = this.height / scale;
            double newBaseX = (event.x() - dragOffsetX) / scale;
            double newBaseY = (event.y() - dragOffsetY) / scale;
            config.hudX = HudRenderer.normalizedX(baseWidth, newBaseX);
            config.hudY = HudRenderer.normalizedY(baseHeight, newBaseY);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging) {
            dragging = false;
            FishingCatchTimerClient.config().save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        FishingCatchTimerClient.config().save();
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
