package dev.brutalxd.phantomprivacy;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class PhantomPrivacyClient implements ClientModInitializer {
    private static KeyBinding openConfig;

    @Override
    public void onInitializeClient() {
        PhantomPrivacyConfig.load();
        openConfig = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.phantomprivacy.open_config",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_EQUAL,
                KeyBinding.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfig.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new PhantomPrivacyScreen(null));
                }
            }
        });
    }
}
