package net.phoenix.client.branding;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Phoenix replacement for the vanilla pause menu. */
public final class PhoenixPauseScreen extends Screen {
    public PhoenixPauseScreen() { super(Text.literal("Phoenix Pause")); }

    @Override
    protected void init() {
        int center = width / 2;
        int buttonWidth = Math.min(260, width - 32);
        int left = center - buttonWidth / 2;
        int top = Math.max(44, height / 2 - 104);
        addDrawableChild(ButtonWidget.builder(Text.literal("RESUME  /  RETURN TO GAME"),
                button -> close()).dimensions(left, top, buttonWidth, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("PHOENIX MODULES"),
                button -> client.setScreen(new PhoenixClientScreen(this)))
                .dimensions(left, top + 28, buttonWidth, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("HUD EDITOR"),
                button -> client.setScreen(new PhoenixHudEditorScreen(this)))
                .dimensions(left, top + 56, buttonWidth, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("OPTIONS"),
                button -> client.setScreen(new OptionsScreen(this, client.options)))
                .dimensions(left, top + 84, buttonWidth, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("SAVE & RETURN TO TITLE"),
                button -> saveAndReturn()).dimensions(left, top + 112, buttonWidth, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("QUIT GAME"),
                button -> client.scheduleStop()).dimensions(left, top + 140, buttonWidth, 22).build());
    }

    private void saveAndReturn() {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        // Integrated worlds are saved by the server as the client disconnects.
        minecraft.disconnect();
        minecraft.setScreen(new TitleScreen());
    }

    @Override
    public void renderBackground(DrawContext draw, int mouseX, int mouseY, float delta) {
        draw.fillGradient(0, 0, width, height, 0xF509070D, 0xF525111B);
        draw.fill(width / 2 - 132, 28, width / 2 + 132, 29, 0xAAFF702E);
        draw.drawCenteredTextWithShadow(textRenderer, Text.literal("PHOENIX"),
                width / 2, 39, 0xFFFF8547);
        draw.drawCenteredTextWithShadow(textRenderer, Text.literal("CLIENT  /  PAUSE CONTROL CENTER"),
                width / 2, 53, 0xFFB8AEBB);
        super.renderBackground(draw, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() { return true; }
}
