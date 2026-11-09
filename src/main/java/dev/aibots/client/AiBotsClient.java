package dev.aibots.client;

import dev.aibots.core.BotManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class AiBotsClient implements ClientModInitializer {
    public static KeyBinding OPEN_MENU;

    @Override
    public void onInitializeClient() {
        // Domyślnie G - zmienisz w Ustawienia > Sterowanie > "Boty AI"
        OPEN_MENU = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.aibots.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.aibots"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_MENU.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new BotScreen());
                }
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> BotManager.stopAll());
    }
}
