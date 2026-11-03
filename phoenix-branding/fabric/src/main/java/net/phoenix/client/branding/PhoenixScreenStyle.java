package net.phoenix.client.branding;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;

/** Shared appearance, focus and lightweight screen-transition treatment. */
public final class PhoenixScreenStyle {
    private static long transitionStarted;
    private PhoenixScreenStyle() {}

    public static boolean light() { return PhoenixHud.isLightUiTheme(); }
    public static int accent() { return light() ? 0xFFB94D1B : 0xFFFF8547; }
    public static int mainText() { return light() ? 0xFF25232A : 0xFFF7EFF8; }
    public static int mutedText() { return light() ? 0xFF66636D : 0xFFB8AEBB; }
    public static int panel() { return light() ? 0xEFFFFFFF : 0xD5100D16; }

    public static void background(DrawContext draw, int width, int height) {
        draw.fillGradient(0, 0, width, height,
                light() ? 0xFFF3F4F7 : 0xFF09070D,
                light() ? 0xFFE4E6EC : 0xFF25111B);
        draw.fill(8, 8, width - 8, height - 8, panel());
        draw.fill(8, 8, width - 8, 10, accent());
    }

    public static void beginTransition() {
        if (!Boolean.parseBoolean(System.getProperty("phoenix.ui.reduced_motion", "false"))) {
            transitionStarted = System.nanoTime();
        } else {
            transitionStarted = 0L;
        }
    }

    /** Draw after Screen.render so keyboard/controller focus is visible above widgets. */
    public static void renderChrome(Screen screen, DrawContext draw) {
        for (var child : screen.children()) {
            if (child instanceof ClickableWidget widget && widget.isFocused() && widget.visible && widget.active) {
                int x = widget.getX(), y = widget.getY(), right = x + widget.getWidth(), bottom = y + widget.getHeight();
                int c = accent();
                draw.fill(x - 2, y - 2, right + 2, y - 1, c);
                draw.fill(x - 2, bottom + 1, right + 2, bottom + 2, c);
                draw.fill(x - 2, y - 1, x - 1, bottom + 1, c);
                draw.fill(right + 1, y - 1, right + 2, bottom + 1, c);
            }
        }
        long started = transitionStarted;
        if (started != 0L) {
            float progress = Math.min(1f, (System.nanoTime() - started) / 150_000_000f);
            int alpha = Math.max(0, Math.round((1f - progress) * 105f));
            if (alpha > 0) draw.fill(0, 0, screen.width, screen.height, (alpha << 24));
            else transitionStarted = 0L;
        }
    }
}
