package net.phoenix.client.branding;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Individually draggable Phoenix HUD elements with persistent per-element positions. */
public final class PhoenixHudEditorScreen extends Screen {
    private final Screen parent;
    private String draggingKey;
    private double dragX, dragY;

    public PhoenixHudEditorScreen(Screen parent) {
        super(Text.literal("Phoenix HUD Editor"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        PhoenixScreenStyle.beginTransition();
        int y = height - 28;
        addDrawableChild(ButtonWidget.builder(Text.literal("RESET ALL"), b -> PhoenixHud.resetElementPositions())
                .dimensions(width / 2 - 154, y, 96, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("SAVE & RETURN"), b -> {
            PhoenixHud.saveConfig();
            close();
        }).dimensions(width / 2 - 53, y, 106, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("CANCEL"), b -> close())
                .dimensions(width / 2 + 62, y, 92, 20).build());
    }

    @Override
    public void render(DrawContext d, int mx, int my, float delta) {
        PhoenixScreenStyle.background(d, width, height);
        d.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, PhoenixScreenStyle.accent());
        d.drawCenteredTextWithShadow(textRenderer,
                Text.literal("DRAG EACH HUD WIDGET SEPARATELY • POSITIONS SAVE AUTOMATICALLY"),
                width / 2, 27, PhoenixScreenStyle.mutedText());
        for (String key : PhoenixHud.getElementKeys()) {
            int x = PhoenixHud.getElementX(key), y = PhoenixHud.getElementY(key);
            int w = Math.max(92, textRenderer.getWidth(key) + 22);
            d.fill(x, y, x + w, y + 18, PhoenixScreenStyle.panel());
            d.fill(x, y, x + 2, y + 18, PhoenixScreenStyle.accent());
            d.drawTextWithShadow(textRenderer, Text.literal(key), x + 7, y + 5, PhoenixScreenStyle.mainText());
            d.drawTextWithShadow(textRenderer, Text.literal("≡"), x + w - 12, y + 5, 0xFFB8AEBB);
        }
        d.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Move widgets away from screen edges if they overlap."),
                width / 2, height - 42, 0xFFB8AEBB);
        PhoenixScreenStyle.renderControlFrames(this, d);
        super.render(d, mx, my, delta);
        PhoenixScreenStyle.renderChrome(this, d);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            String[] keys = PhoenixHud.getElementKeys();
            for (int i = keys.length - 1; i >= 0; i--) {
                String key = keys[i];
                int x = PhoenixHud.getElementX(key), y = PhoenixHud.getElementY(key);
                int w = Math.max(92, textRenderer.getWidth(key) + 22);
                if (click.x() >= x && click.x() <= x + w && click.y() >= y && click.y() <= y + 18) {
                    draggingKey = key;
                    dragX = click.x() - x;
                    dragY = click.y() - y;
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (click.button() == 0 && draggingKey != null) {
            PhoenixHud.setElementPosition(draggingKey, (int) (click.x() - dragX), (int) (click.y() - dragY));
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0 && draggingKey != null) {
            draggingKey = null;
            PhoenixHud.saveConfig();
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
