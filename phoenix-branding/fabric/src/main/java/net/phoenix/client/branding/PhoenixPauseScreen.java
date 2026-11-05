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
     * Disconnect through Minecraft's normal screen-aware disconnect API.
     * The dedicated saving-screen helper can leave some mobile runtimes parked
     * on its progress screen; the regular disconnect API still shuts down an
     * integrated server safely and returns to the supplied title screen.
     */
    private void saveAndReturn() {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        TitleScreen titleScreen = new TitleScreen();

        // Prefer the current 1.21.x three-argument disconnect overload. It
        // closes multiplayer connections and cleanly stops a local integrated
        // server before showing the destination screen.
        if (tryInvokeClientMethod(minecraft, "method_18096",
                new Class<?>[]{Screen.class, boolean.class, boolean.class},
                titleScreen, false, true)) {
            return;
        }

        // Newer/older adapters may expose the two-argument overload instead.
        if (tryInvokeClientMethod(minecraft, "method_76795",
                new Class<?>[]{Screen.class, boolean.class},
                titleScreen, false)) {
            return;
        }
        if (tryInvokeClientMethod(minecraft, "method_18096",
                new Class<?>[]{Screen.class, boolean.class},
                titleScreen, false)) {
            return;
        }
        if (tryInvokeClientMethod(minecraft, "method_18096",
                new Class<?>[]{Screen.class}, titleScreen)) {
            return;
        }

        // Last-resort vanilla path for adapters that do not expose a compatible
        // disconnect overload. Keep this last to avoid the stuck progress flow
        // on runtimes where the screen-aware overload is available.
        if (tryInvokeClientMethod(minecraft, "method_72099", new Class<?>[0])) {
            return;
        }

        System.err.println("[Phoenix Client] No compatible Minecraft disconnect method was found; "
                + "kept the current world open instead of showing a false title screen.");
        minecraft.setScreen(this);
    }

    private static boolean tryInvokeClientMethod(MinecraftClient minecraft, String name,
                                                  Class<?>[] parameterTypes, Object... arguments) {
        try {
            java.lang.reflect.Method method =
                    MinecraftClient.class.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            method.invoke(minecraft, arguments);
            return true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            System.err.println("[Phoenix Client] Disconnect API " + name
                    + " was unavailable or failed for this Minecraft version: " + error);
            return false;
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
        PhoenixScreenStyle.renderControlFrames(this, draw);
        super.render(draw, mouseX, mouseY, delta);
        PhoenixScreenStyle.renderChrome(this, draw);
    }

    @Override
    public boolean shouldPause() { return true; }
}
