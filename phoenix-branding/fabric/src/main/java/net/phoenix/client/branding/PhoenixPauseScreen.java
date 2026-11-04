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
        PhoenixScreenStyle.beginTransition();
        int center = width / 2;
        int buttonWidth = Math.min(260, width - 32);
        int left = center - buttonWidth / 2;
        int top = Math.max(44, height / 2 - 104);
        addDrawableChild(ButtonWidget.builder(Text.literal("RESUME  /  RETURN TO GAME"),
                button -> close()).dimensions(left, top, buttonWidth, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("PHOENIX CLIENT MENU"),
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

    /**
     * Prefer Minecraft's dedicated saving-disconnect flow. On older supported
     * game versions, fall back to the version-appropriate disconnect overload.
     */
    private void saveAndReturn() {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        try {
            try {
                MinecraftClient.class.getMethod("disconnectWithSavingScreen").invoke(minecraft);
                return;
            } catch (NoSuchMethodException olderVersion) {
                // The dedicated saving screen was added after older supported versions.
            }

            TitleScreen titleScreen = new TitleScreen();
            try {
                MinecraftClient.class
                        .getMethod("disconnect", Screen.class, boolean.class, boolean.class)
                        .invoke(minecraft, titleScreen, false, true);
            } catch (NoSuchMethodException intermediateVersion) {
                try {
                    MinecraftClient.class
                            .getMethod("disconnect", Screen.class, boolean.class)
                            .invoke(minecraft, titleScreen, false);
                } catch (NoSuchMethodException legacyVersion) {
                    MinecraftClient.class
                            .getMethod("disconnect", Screen.class)
                            .invoke(minecraft, titleScreen);
                }
            }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Phoenix could not save and return to the title screen", error);
        }
    }

    @Override
    public void renderBackground(DrawContext draw, int mouseX, int mouseY, float delta) {
        super.renderBackground(draw, mouseX, mouseY, delta);
        PhoenixScreenStyle.background(draw, width, height);
        draw.fill(width / 2 - 132, 28, width / 2 + 132, 29, PhoenixScreenStyle.accent());
        draw.drawCenteredTextWithShadow(textRenderer, Text.literal("🔥"), width / 2, 20, PhoenixScreenStyle.accent());
        draw.drawCenteredTextWithShadow(textRenderer, Text.literal("PHOENIX"),
                width / 2 + 1, 42, PhoenixScreenStyle.mainText());
        draw.drawCenteredTextWithShadow(textRenderer, Text.literal("PHOENIX"),
                width / 2, 41, PhoenixScreenStyle.accent());
        draw.drawCenteredTextWithShadow(textRenderer, Text.literal("CLIENT  /  PAUSE CONTROL CENTER"),
                width / 2, 53, PhoenixScreenStyle.mutedText());
    }

    @Override
    public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        super.render(draw, mouseX, mouseY, delta);
        PhoenixScreenStyle.renderChrome(this, draw);
    }

    @Override
    public boolean shouldPause() { return true; }
}
