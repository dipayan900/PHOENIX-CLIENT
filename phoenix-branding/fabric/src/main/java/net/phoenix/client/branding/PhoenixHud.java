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

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Configurable, lightweight client-side Phoenix HUD modules. */
public final class PhoenixHud {
    private static boolean visible = true;
    private static boolean fpsEnabled = true;
    private static boolean coordinatesEnabled = true;
    private static boolean directionEnabled = true;
    private static boolean armorEnabled = true;
    private static boolean clockEnabled = false;
    private static KeyBinding toggleKey;
    private static KeyBinding menuKey;

    private PhoenixHud() {}

    public static boolean isVisible() { return visible; }
    public static boolean isFpsEnabled() { return fpsEnabled; }
    public static boolean isCoordinatesEnabled() { return coordinatesEnabled; }
    public static boolean isDirectionEnabled() { return directionEnabled; }
    public static boolean isArmorEnabled() { return armorEnabled; }
    public static boolean isClockEnabled() { return clockEnabled; }

    public static void toggle() { visible = !visible; }
    public static void toggleFps() { fpsEnabled = !fpsEnabled; }
    public static void toggleCoordinates() { coordinatesEnabled = !coordinatesEnabled; }
    public static void toggleDirection() { directionEnabled = !directionEnabled; }
    public static void toggleArmor() { armorEnabled = !armorEnabled; }
    public static void toggleClock() { clockEnabled = !clockEnabled; }

    public static void register() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.phoenix.toggle_hud", InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT, "category.phoenix"));
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.phoenix.open_menu", InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_CONTROL, "category.phoenix"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                visible = !visible;
                if (client.player != null) {
                    client.player.sendMessage(Text.literal(
                            "§6Phoenix HUD " + (visible ? "enabled" : "disabled")), true);
                }
            }
            while (menuKey.wasPressed()) {
                client.setScreen(new PhoenixClientScreen(client.currentScreen));
            }
        });

        HudRenderCallback.EVENT.register((draw, tickCounter) -> {
            if (!visible) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.world == null) return;

            int lines = (fpsEnabled ? 1 : 0) + (coordinatesEnabled ? 1 : 0)
                    + (directionEnabled ? 1 : 0) + (armorEnabled ? 1 : 0)
                    + (clockEnabled ? 1 : 0);
            if (lines == 0) return;

            int x = 10, y = 10, width = 178, height = 19 + lines * 12;
            draw.fill(x, y, x + width, y + height, 0xB5100D14);
            draw.fill(x, y, x + 2, y + height, 0xFFFF702E);
            draw.drawTextWithShadow(client.textRenderer,
                    Text.literal("PHOENIX  /  MODULES"), x + 8, y + 5, 0xFFFF8A4C);

            int row = y + 19;
            if (fpsEnabled) {
                line(draw, client, "FPS", String.valueOf(client.getCurrentFps()), row);
                row += 12;
            }
            if (coordinatesEnabled) {
                BlockPos pos = client.player.getBlockPos();
                line(draw, client, "XYZ",
                        pos.getX() + "  " + pos.getY() + "  " + pos.getZ(), row);
                row += 12;
            }
            if (directionEnabled) {
                line(draw, client, "FACING",
                        client.player.getHorizontalFacing().getName().toUpperCase(), row);
                row += 12;
            }
            if (armorEnabled) {
                int pieces = 0;
                int remaining = 0;
                for (var stack : client.player.getArmorItems()) {
                    if (!stack.isEmpty()) {
                        pieces++;
                        if (stack.isDamageable()) {
                            remaining += Math.max(0, stack.getMaxDamage() - stack.getDamage());
                        }
                    }
                }
                line(draw, client, "ARMOR", pieces + " pieces  ·  " + remaining + " durability", row);
                row += 12;
            }
            if (clockEnabled) {
                line(draw, client, "LOCAL", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")), row);
            }
        });
    }

    private static void line(net.minecraft.client.gui.DrawContext draw, MinecraftClient client,
                             String label, String value, int y) {
        draw.drawTextWithShadow(client.textRenderer, Text.literal(label), 18, y, 0xFFFFA06C);
        draw.drawTextWithShadow(client.textRenderer, Text.literal(value), 72, y, 0xFFF4EDF4);
    }
}
