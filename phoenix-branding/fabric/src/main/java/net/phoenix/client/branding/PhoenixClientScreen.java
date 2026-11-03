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
    public void renderBackground(net.minecraft.client.gui.DrawContext draw, int mouseX, int mouseY, float delta) {
        // Original Phoenix background: ember gradient and restrained technical accents.
        draw.fillGradient(0, 0, this.width, this.height, 0xFF09070D, 0xFF25111B);
        draw.fill(0, this.height / 3, this.width, this.height / 3 + 1, 0x44FF702E);
        draw.fill(0, this.height / 3 + 2, this.width, this.height / 3 + 3, 0x223F2B35);
        draw.fill(this.width / 2 - 115, 22, this.width / 2 + 115, 23, 0x66FF702E);
        draw.drawTextWithShadow(this.textRenderer, Text.literal("PHOENIX"), this.width / 2 - 37, 30, 0xFFFF8547);
        draw.drawTextWithShadow(this.textRenderer, Text.literal("CLIENT CONTROL"), this.width / 2 - 45, 43, 0xFFB8AEBB);
        draw.fill(this.width / 2 - 115, 58, this.width / 2 + 115, 59, 0x443F2B35);
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
