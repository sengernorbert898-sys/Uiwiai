package dev.fishingcatchtimer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Shared HUD drawing and normalized free-position calculations. */
public final class HudRenderer {
    public static final int WIDTH = 244;
    public static final int HEIGHT = 66;
    public static final int MARGIN = 8;

    private HudRenderer() {}

    public static int positionX(double screenWidthAtBaseScale, double normalizedX) {
        int max = Math.max(0, (int) Math.floor(screenWidthAtBaseScale) - WIDTH - MARGIN * 2);
        return MARGIN + (int) Math.round(clamp(normalizedX, 0.0, 1.0, 0.72) * max);
    }

    public static int positionY(double screenHeightAtBaseScale, double normalizedY) {
        int max = Math.max(0, (int) Math.floor(screenHeightAtBaseScale) - HEIGHT - MARGIN * 2);
        return MARGIN + (int) Math.round(clamp(normalizedY, 0.0, 1.0, 0.06) * max);
    }

    public static double normalizedX(double screenWidthAtBaseScale, double x) {
        int max = Math.max(1, (int) Math.floor(screenWidthAtBaseScale) - WIDTH - MARGIN * 2);
        return clamp((x - MARGIN) / max, 0.0, 1.0, 0.72);
    }

    public static double normalizedY(double screenHeightAtBaseScale, double y) {
        int max = Math.max(1, (int) Math.floor(screenHeightAtBaseScale) - HEIGHT - MARGIN * 2);
        return clamp((y - MARGIN) / max, 0.0, 1.0, 0.06);
    }

    public static int screenX(double baseX, double scale) { return (int) Math.round(baseX * scale); }
    public static int screenY(double baseY, double scale) { return (int) Math.round(baseY * scale); }

    public static void render(GuiGraphicsExtractor graphics, Minecraft client, CatchTimerConfig config, boolean preview) {
        double scale = config.hudScale;
        double baseWidth = graphics.guiWidth() / scale;
        double baseHeight = graphics.guiHeight() / scale;
        int x = positionX(baseWidth, config.hudX);
        int y = positionY(baseHeight, config.hudY);
        graphics.pose().pushMatrix();
        graphics.pose().scale((float) scale, (float) scale);
        drawAt(graphics, client, x, y, preview);
        graphics.pose().popMatrix();
    }

    public static void drawAt(GuiGraphicsExtractor graphics, Minecraft client, int x, int y, boolean preview) {
        String status = preview ? "DRAG TO POSITION" : CatchTimerController.statusLabel();
        String timer = preview ? "2.5s" : CatchTimerController.timerLabel();
        int accent = preview ? 0xFF40D6B0 : CatchTimerController.accentColor();
        double progress = preview ? 0.28 : CatchTimerController.progress();

        graphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xE9111925);
        graphics.fill(x, y, x + 3, y + HEIGHT, accent);
        graphics.fill(x + 11, y + 43, x + WIDTH - 11, y + 46, 0xFF344354);
        if (progress > 0.0) {
            int railWidth = (int) ((WIDTH - 22) * Math.max(0.0, Math.min(1.0, progress)));
            graphics.fill(x + 11, y + 43, x + 11 + railWidth, y + 46, accent);
        }

        graphics.text(client.font, "FISHING CATCH TIMER", x + 11, y + 7, 0xFF9DAFC2, false);
        String meta = preview ? "FREE POSITION PREVIEW" : CatchTimerController.hudProfileLabel();
        graphics.text(client.font, meta, x + 11, y + 19, 0xFF8DA4B8, false);
        graphics.text(client.font, status, x + 11, y + 31, 0xFFFFFFFF, true);
        graphics.text(client.font, timer, x + WIDTH - client.font.width(timer) - 12, y + 29, accent, true);
        if (!preview) {
            String trail = CatchTimerController.trajectoryLabel();
            graphics.text(client.font, trail, x + 11, y + 52, 0xFF8DA4B8, false);
        } else {
            graphics.text(client.font, "LEFT-CLICK + DRAG ANYWHERE", x + 11, y + 52, 0xFF8DA4B8, false);
        }
    }

    private static double clamp(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) return fallback;
        return Math.max(min, Math.min(max, value));
    }
}
