package net.phoenix.client.branding;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Phoenix-branded in-game quick settings screen. */
public final class PhoenixClientScreen extends Screen {
    private final Screen parent;

    public PhoenixClientScreen(Screen parent) {
        super(Text.literal("Phoenix Client"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        int top = this.height / 4 + 18;
        this.addDrawableChild(ButtonWidget.builder(hudLabel(), button -> {
            PhoenixHud.toggle();
            button.setMessage(hudLabel());
        }).dimensions(center - 100, top, 200, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Friends & Social"), button -> {
            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(Text.literal(
                        "§6Phoenix Social is managed from the launcher. In-game cloud presence is not enabled on this server yet."
                ), false);
            }
        }).dimensions(center - 100, top + 26, 200, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> close())
                .dimensions(center - 100, top + 78, 200, 20).build());
    }

    private static Text hudLabel() {
        return Text.literal("HUD: " + (PhoenixHud.isVisible() ? "ON" : "OFF") + "  ·  Toggle");
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(parent);
    }
}
