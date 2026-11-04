package net.phoenix.client.branding;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Persistent visual-module controls backed by Minecraft's own client options. */
public final class PhoenixVisualSettingsScreen extends Screen {
    private final Screen parent;
    public PhoenixVisualSettingsScreen(Screen parent) {
        super(Text.literal("Phoenix Visual Settings"));
        this.parent = parent;
    }

    @Override protected void init() {
        PhoenixScreenStyle.beginTransition();
        int w = Math.min(270, width - 24), x = (width - w) / 2;
        int y = Math.max(52, height / 2 - 104);
        addDrawableChild(ButtonWidget.builder(Text.literal("FULLBRIGHT: " + state(PhoenixHud.isFullbrightEnabled())),
                b -> { PhoenixHud.toggleFullbright(); b.setMessage(Text.literal("FULLBRIGHT: " + state(PhoenixHud.isFullbrightEnabled()))); })
                .dimensions(x, y, w, 21).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("CHAT TRANSPARENCY: " + state(PhoenixHud.isChatTransparencyEnabled())),
                b -> { PhoenixHud.toggleChatTransparency(); b.setMessage(Text.literal("CHAT TRANSPARENCY: " + state(PhoenixHud.isChatTransparencyEnabled()))); })
                .dimensions(x, y + 25, w, 21).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("CHAT OPACITY  −"),
                b -> adjustChatOpacity(-0.1)).dimensions(x, y + 50, w / 2 - 2, 21).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("CHAT OPACITY  +"),
                b -> adjustChatOpacity(0.1)).dimensions(x + w / 2 + 2, y + 50, w / 2 - 2, 21).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("RESET CHAT OPACITY"),
                b -> { client.options.getChatOpacity().setValue(1.0); client.options.write(); })
                .dimensions(x, y + 75, w, 21).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("OPEN CROSSHAIR STUDIO"),
                b -> client.setScreen(new PhoenixCrosshairScreen(this)))
                .dimensions(x, y + 100, w, 21).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("HUD LAYOUT EDITOR"),
                b -> client.setScreen(new PhoenixHudEditorScreen(this)))
                .dimensions(x, y + 125, w, 21).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("SAVE & RETURN"),
                b -> { client.options.write(); PhoenixHud.saveConfig(); close(); })
                .dimensions(x, y + 153, w, 21).build());
    }

    private static String state(boolean enabled) { return enabled ? "ON" : "OFF"; }
    private void adjustChatOpacity(double delta) {
        double current = client.options.getChatOpacity().getValue();
        client.options.getChatOpacity().setValue(Math.max(0.0, Math.min(1.0, current + delta)));
        client.options.write();
    }

    @Override public void renderBackground(DrawContext d, int mx, int my, float delta) {
        PhoenixScreenStyle.background(d, width, height);
    }
    @Override public void render(DrawContext d, int mx, int my, float delta) {
        renderBackground(d, mx, my, delta);
        int accent = PhoenixScreenStyle.accent();
        d.drawCenteredTextWithShadow(textRenderer, title, width / 2, 25, accent);
        d.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Controls are saved to your local Minecraft profile."),
                width / 2, height - 20, PhoenixScreenStyle.mutedText());
        PhoenixScreenStyle.renderControlFrames(this, d);
        super.render(d, mx, my, delta);
        PhoenixScreenStyle.renderChrome(this, d);
    }
    @Override public void close() { if (client != null) client.setScreen(parent); }
}