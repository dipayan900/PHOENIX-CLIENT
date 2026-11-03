package net.phoenix.client.branding;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Phoenix in-game module controls. */
public final class PhoenixClientScreen extends Screen {
    private final Screen parent;

    public PhoenixClientScreen(Screen parent) {
        super(Text.literal("Phoenix Client"));
        this.parent = parent;
    }

    @Override
    public void renderBackground(net.minecraft.client.gui.DrawContext draw, int mouseX, int mouseY, float delta) {
        draw.fillGradient(0, 0, this.width, this.height, 0xFF09070D, 0xFF25111B);
        draw.fill(0, this.height / 3, this.width, this.height / 3 + 1, 0x44FF702E);
        draw.fill(this.width / 2 - 125, 22, this.width / 2 + 125, 23, 0x66FF702E);
        draw.drawTextWithShadow(this.textRenderer, Text.literal("PHOENIX"), this.width / 2 - 37, 30, 0xFFFF8547);
        draw.drawTextWithShadow(this.textRenderer, Text.literal("CLIENT  /  MODULE CONTROL"), this.width / 2 - 82, 43, 0xFFB8AEBB);
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        int top = Math.max(72, this.height / 5);
        int width = 224;
        int left = center - width / 2;
        addToggle(left, top, "Master HUD", PhoenixHud.isVisible(), PhoenixHud::toggle);
        addToggle(left, top + 25, "FPS counter", PhoenixHud.isFpsEnabled(), PhoenixHud::toggleFps);
        addToggle(left, top + 50, "Coordinates", PhoenixHud.isCoordinatesEnabled(), PhoenixHud::toggleCoordinates);
        addToggle(left, top + 75, "Direction", PhoenixHud.isDirectionEnabled(), PhoenixHud::toggleDirection);
        addToggle(left, top + 100, "Armor status", PhoenixHud.isArmorEnabled(), PhoenixHud::toggleArmor);
        addToggle(left, top + 125, "Local clock", PhoenixHud.isClockEnabled(), PhoenixHud::toggleClock);

        addDrawableChild(ButtonWidget.builder(Text.literal("Friends & Social"), button -> {
            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(Text.literal(
                        "§6Phoenix Social is available in the launcher. In-game cloud chat is not connected yet."), false);
            }
        }).dimensions(left, top + 154, width, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> close())
                .dimensions(left, top + 181, width, 20).build());
    }

    private void addToggle(int x, int y, String name, boolean enabled, Runnable action) {
        addDrawableChild(ButtonWidget.builder(toggleLabel(name, enabled), button -> {
            action.run();
            button.setMessage(toggleLabel(name, currentState(name)));
        }).dimensions(x, y, 224, 20).build());
    }

    private static boolean currentState(String name) {
        return switch (name) {
            case "Master HUD" -> PhoenixHud.isVisible();
            case "FPS counter" -> PhoenixHud.isFpsEnabled();
            case "Coordinates" -> PhoenixHud.isCoordinatesEnabled();
            case "Direction" -> PhoenixHud.isDirectionEnabled();
            case "Armor status" -> PhoenixHud.isArmorEnabled();
            case "Local clock" -> PhoenixHud.isClockEnabled();
            default -> false;
        };
    }

    private static Text toggleLabel(String name, boolean enabled) {
        return Text.literal(name + "  [" + (enabled ? "ON" : "OFF") + "]");
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(parent);
    }
}
