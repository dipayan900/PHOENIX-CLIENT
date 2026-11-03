package net.phoenix.client.branding;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

/**
 * Lightweight Phoenix in-game status HUD for the supported Fabric profile.
 * The HUD is client-side and does not require a server plugin.
 */
public final class PhoenixHud {
    private static boolean visible = true;
    private static KeyBinding toggleKey;

    private PhoenixHud() {}

    public static boolean isVisible() { return visible; }\n\n    public static void toggle() { visible = !visible; }\n\n    public static void register() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.phoenix.toggle_hud",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "category.phoenix"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                visible = !visible;
                if (client.player != null) {
                    client.player.sendMessage(Text.literal(
                            "§6Phoenix HUD " + (visible ? "enabled" : "disabled")
                    ), true);
                }
            }
        });

        HudRenderCallback.EVENT.register((draw, tickCounter) -> {
            if (!visible) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.world == null) return;

            int x = 10;
            int y = 10;
            int width = 154;
            int height = 57;
            draw.fill(x, y, x + width, y + height, 0xB5100D14);
            draw.fill(x, y, x + 2, y + height, 0xFFFF702E);
            draw.fill(x + 7, y + 7, x + width - 7, y + 8, 0x663F2B35);

            draw.drawTextWithShadow(client.textRenderer,
                    Text.literal("PHOENIX  /  LIVE"), x + 8, y + 4, 0xFFFF8A4C);
            draw.drawTextWithShadow(client.textRenderer,
                    Text.literal("FPS  " + client.getCurrentFps()), x + 8, y + 18, 0xFFF4EDF4);

            BlockPos pos = client.player.getBlockPos();
            String coordinates = pos.getX() + "  " + pos.getY() + "  " + pos.getZ();
            draw.drawTextWithShadow(client.textRenderer,
                    Text.literal("XYZ  " + coordinates), x + 8, y + 30, 0xFFD8CBD8);
            draw.drawTextWithShadow(client.textRenderer,
                    Text.literal("FACING  " + client.player.getHorizontalFacing().getName().toUpperCase()),
                    x + 8, y + 42, 0xFFB8AEBB);
        });
    }
}
