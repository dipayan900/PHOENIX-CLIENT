package dev.brutalxd.phantomprivacy;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class PhantomPrivacyClient implements ClientModInitializer {
    private static boolean f9WasDown;

    @Override
    public void onInitializeClient() {
        PhantomPrivacyConfig.load();

        // Poll the physical F9 key directly. This avoids keybinding registration,
        // category conflicts, and keybinding-consumption issues in launcher clients.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean f9Down = InputUtil.isKeyPressed(
                    client.getWindow().getHandle(),
                    GLFW.GLFW_KEY_F9
            );

            if (f9Down && !f9WasDown && !(client.currentScreen instanceof PhantomPrivacyScreen)) {
                client.setScreen(new PhantomPrivacyScreen(client.currentScreen));
            }
            f9WasDown = f9Down;
        });
    }
}
