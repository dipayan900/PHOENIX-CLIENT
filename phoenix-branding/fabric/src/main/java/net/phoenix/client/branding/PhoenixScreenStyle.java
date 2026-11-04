package net.phoenix.client.branding;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;

/** Shared, low-cost visual system for Phoenix screens. No textures or background threads required. */
public final class PhoenixScreenStyle {
    private static long transitionStarted;
    private PhoenixScreenStyle() {}

    public static boolean light() { return PhoenixHud.isLightUiTheme(); }
    public static int accent() { return light() ? 0xFFB94D1B : 0xFFFF8547; }
    public static int mainText() { return light() ? 0xFF25232A : 0xFFF7EFF8; }
    public static int mutedText() { return light() ? 0xFF66636D : 0xFFB8AEBB; }
    public static int panel() { return light() ? 0xEFFFFFFF : 0xD5100D16; }
    private static int grid() { return light() ? 0x167A6470 : 0x18F5A0C0; }
    private static int edge() { return light() ? 0x557D6674 : 0x665E4057; }

    /** Premium layered backdrop drawn with Minecraft's own immediate UI renderer. */
    public static void background(DrawContext draw, int width, int height) {
        int inset = Math.max(7, Math.min(12, width / 48));
        draw.fillGradient(0, 0, width, height,
                light() ? 0xFFF4F5F8 : 0xFF08070D,
                light() ? 0xFFE4E6ED : 0xFF21101D);
        // Restrained technical grid; intentionally static to avoid per-frame animation cost.
        for (int x = 18; x < width; x += 28) draw.fill(x, 0, x + 1, height, grid());
        for (int y = 18; y < height; y += 28) draw.fill(0, y, width, y + 1, grid());
        draw.fill(inset, inset, width - inset, height - inset, panel());
        draw.fill(inset, inset, width - inset, inset + 2, accent());
        draw.fill(inset, height - inset - 1, width - inset, height - inset, edge());
        draw.fill(inset, inset, inset + 1, height - inset, edge());
        draw.fill(width - inset - 1, inset, width - inset, height - inset, edge());
        // Corner markers give the panels a framed, hardware-like finish.
        int marker = Math.min(22, Math.max(12, width / 18));
        draw.fill(inset, inset + 5, inset + marker, inset + 6, accent());
        draw.fill(width - inset - marker, inset + 5, width - inset, inset + 6, accent());
        draw.fill(inset, height - inset - 6, inset + marker, height - inset - 5, accent());
        draw.fill(width - inset - marker, height - inset - 6, width - inset, height - inset - 5, accent());
    }

    public static void beginTransition() {
        if (!Boolean.parseBoolean(System.getProperty("phoenix.ui.reduced_motion", "false"))) {
            transitionStarted = System.nanoTime();
        } else {
            transitionStarted = 0L;
        }
    }

    /** Draw after Screen.render so keyboard/controller focus remains visible above widgets. */
    public static void renderChrome(Screen screen, DrawContext draw) {
        for (var child : screen.children()) {
            if (child instanceof ClickableWidget widget && widget.isFocused()) {
                int x = widget.getX(), y = widget.getY();
                int right = x + widget.getWidth(), bottom = y + widget.getHeight();
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
            if (alpha > 0) draw.fill(0, 0, screen.width, screen.height, alpha << 24);
            else transitionStarted = 0L;
        }
    }
}
