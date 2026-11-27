package dev.fishingcatchtimer;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FishingCatchTimerClient implements ClientModInitializer {
    public static final String MOD_ID = "fishingcatchtimer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static CatchTimerConfig config;
    private static KeyMapping openConfigKey;

    public static CatchTimerConfig config() { return config; }

    @Override
    public void onInitializeClient() {
        config = CatchTimerConfig.load();

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(MOD_ID, "controls")
        );
        openConfigKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.fishingcatchtimer.open_config",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_O,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfigKey.consumeClick()) {
                client.gui.setScreen(new FishingCatchTimerScreen(client.gui.screen()));
            }
            CatchTimerController.tick(client);
        });

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "catch_timer"),
                FishingCatchTimerClient::renderHud
        );

        LOGGER.info("Fishing Catch Timer initialized. Press O to configure; drag the HUD with the Position HUD screen.");
    }

    private static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (config == null || !config.visualAlerts || !CatchTimerController.shouldRender()) return;
        HudRenderer.render(graphics, Minecraft.getInstance(), config, false);
    }
}
