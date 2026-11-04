package net.phoenix.client.branding;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Icon-only hit-particle selector; the choices are shown as previews, not particle names. */
public final class PhoenixParticleScreen extends Screen {
    private final Screen parent;
    private static final String[] ICONS = {"✦", "♥", "✧", "●"};
    private static final int[] COLORS = {0xFFFFD36A, 0xFFFF668B, 0xFF9BE7FF, 0xFFFF9854};

    public PhoenixParticleScreen(Screen parent) {
        super(Text.literal("Phoenix Particle Studio"));
        this.parent = parent;
    }

    @Override protected void init() {
        int w = Math.min(260, width - 24);
        int x = (width - w) / 2;
        int y = Math.max(62, height / 2 - 76);
        addDrawableChild(ButtonWidget.builder(
                Text.literal("EFFECTS: " + (PhoenixHud.isParticlesEnabled() ? "ON" : "OFF")),
                b -> {
                    PhoenixHud.toggleParticles();
                    b.setMessage(Text.literal("EFFECTS: " + (PhoenixHud.isParticlesEnabled() ? "ON" : "OFF")));
                }).dimensions(x, y - 34, w, 22).build());

        int gap = 8, cellW = (w - gap) / 2, cellH = 52;
        for (int i = 0; i < ICONS.length; i++) {
            final int style = i;
            int col = i % 2, row = i / 2;
            addDrawableChild(ButtonWidget.builder(Text.literal(ICONS[i]), b -> {
                PhoenixHud.setParticleStyle(style);
            }).dimensions(x + col * (cellW + gap), y + row * (cellH + gap), cellW, cellH).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("BACK"),
                b -> close()).dimensions(x, y + 2 * (cellH + gap) + 2, w, 22).build());
    }

    @Override public void renderBackground(DrawContext d, int mx, int my, float delta) {
        PhoenixScreenStyle.background(d, width, height);
    }

    @Override public void render(DrawContext d, int mx, int my, float delta) {
        renderBackground(d, mx, my, delta);
        d.drawCenteredTextWithShadow(textRenderer, Text.literal("PARTICLE VISUALS"), width / 2, 24,
                PhoenixScreenStyle.accent());
        d.drawCenteredTextWithShadow(textRenderer, Text.literal("ICON PREVIEWS · NO PARTICLE NAMES"),
                width / 2, 42, PhoenixScreenStyle.mutedText());
        super.render(d, mx, my, delta);
        int w = Math.min(260, width - 24), x = (width - w) / 2;
        int y = Math.max(62, height / 2 - 76);
        for (int i = 0; i < ICONS.length; i++) {
            int col = i % 2, row = i / 2;
            int cellW = (w - 8) / 2, cellH = 52;
            int left = x + col * (cellW + 8), top = y + row * (cellH + 8);
            if (PhoenixHud.getParticleStyle() == i) {
                d.fill(left - 2, top - 2, left + cellW + 2, top + cellH + 2, 0x66FF702E);
                d.fill(left - 2, top - 2, left + cellW + 2, top, PhoenixScreenStyle.accent());
            }
            d.drawCenteredTextWithShadow(textRenderer, Text.literal(ICONS[i]),
                    left + cellW / 2, top + 17, COLORS[i]);
        }
        PhoenixScreenStyle.renderChrome(this, d);
    }

    @Override public void close() {
        if (client != null) client.setScreen(parent);
    }
}
