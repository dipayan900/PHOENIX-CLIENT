package net.phoenix.client.branding;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Dedicated per-module configuration page for every Phoenix HUD and visual toggle. */
public final class PhoenixModuleSettingsScreen extends Screen {
    private record Entry(String name, BooleanSupplier enabled, Runnable toggle) {}
    private final Screen parent;
    private final List<Entry> entries = List.of(
        new Entry("Master HUD", PhoenixHud::isVisible, PhoenixHud::toggle),
        new Entry("FPS Counter", PhoenixHud::isFpsEnabled, PhoenixHud::toggleFps),
        new Entry("Coordinates", PhoenixHud::isCoordinatesEnabled, PhoenixHud::toggleCoordinates),
        new Entry("Direction", PhoenixHud::isDirectionEnabled, PhoenixHud::toggleDirection),
        new Entry("Armor Status", PhoenixHud::isArmorEnabled, PhoenixHud::toggleArmor),
        new Entry("Ping Display", PhoenixHud::isPingEnabled, PhoenixHud::togglePing),
        new Entry("Movement Speed", PhoenixHud::isSpeedEnabled, PhoenixHud::toggleSpeed),
        new Entry("Biome Display", PhoenixHud::isBiomeEnabled, PhoenixHud::toggleBiome),
        new Entry("Dimension", PhoenixHud::isDimensionEnabled, PhoenixHud::toggleDimension),
        new Entry("World Time", PhoenixHud::isWorldTimeEnabled, PhoenixHud::toggleWorldTime),
        new Entry("Local Clock", PhoenixHud::isClockEnabled, PhoenixHud::toggleClock),
        new Entry("Attack Indicator", PhoenixHud::isAttackIndicatorEnabled, PhoenixHud::toggleAttackIndicator),
        new Entry("Potion Effects", PhoenixHud::isPotionEffectsEnabled, PhoenixHud::togglePotionEffects),
        new Entry("Held Item Counter", PhoenixHud::isItemCounterEnabled, PhoenixHud::toggleItemCounter),
        new Entry("Keystrokes", PhoenixHud::isKeystrokesEnabled, PhoenixHud::toggleKeystrokes),
        new Entry("Session Timer", PhoenixHud::isSessionTimeEnabled, PhoenixHud::toggleSessionTime),
        new Entry("Waypoint HUD", PhoenixHud::isWaypointEnabled, PhoenixHud::toggleWaypoint),
        new Entry("Inventory Overlay", PhoenixHud::isInventoryOverlayEnabled, PhoenixHud::toggleInventoryOverlay),
        new Entry("Light Level", PhoenixHud::isLightLevelEnabled, PhoenixHud::toggleLightLevel),
        new Entry("Nearby Entities", PhoenixHud::isEntityOverlayEnabled, PhoenixHud::toggleEntityOverlay),
        new Entry("Tool Information", PhoenixHud::isToolInfoEnabled, PhoenixHud::toggleToolInfo),
        new Entry("CPS Counter", PhoenixHud::isCpsEnabled, PhoenixHud::toggleCps),
        new Entry("Fullbright", PhoenixHud::isFullbrightEnabled, PhoenixHud::toggleFullbright),
        new Entry("Chat Transparency", PhoenixHud::isChatTransparencyEnabled, PhoenixHud::toggleChatTransparency),
        new Entry("Custom Crosshair", PhoenixHud::isCustomCrosshairEnabled, PhoenixHud::toggleCustomCrosshair),
        new Entry("Toggle Sprint", PhoenixHud::isSprintToggled, PhoenixHud::toggleSprint)
    );
    private int page;

    public PhoenixModuleSettingsScreen(Screen parent) {
        super(Text.literal("Phoenix Module Controls"));
        this.parent = parent;
    }

    @Override protected void init() {
        PhoenixScreenStyle.beginTransition();
        clearChildren();
        int perPage = 8;
        int pages = (entries.size() + perPage - 1) / perPage;
        page = Math.max(0, Math.min(page, pages - 1));
        int cardWidth = Math.max(86, (width - 30) / 2);
        int left = (width - (cardWidth * 2 + 8)) / 2;
        int top = Math.max(66, height / 2 - 94);
        for (int i = page * perPage; i < Math.min(entries.size(), (page + 1) * perPage); i++) {
            Entry entry = entries.get(i);
            int local = i - page * perPage;
            int x = left + (local % 2) * (cardWidth + 8);
            int y = top + (local / 2) * 30;
            addDrawableChild(ButtonWidget.builder(label(entry), button -> {
                entry.toggle().run();
                button.setMessage(label(entry));
            }).dimensions(x, y, cardWidth, 25).build());
        }
        int navY = height - 30;
        addDrawableChild(ButtonWidget.builder(Text.literal("‹ BACK"), b -> close())
                .dimensions(8, navY, 72, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("‹"), b -> { page = Math.max(0, page - 1); init(); })
                .dimensions(width / 2 - 62, navY, 24, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("PAGE " + (page + 1) + " / " + pages), b -> {})
                .dimensions(width / 2 - 34, navY, 68, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("›"), b -> { page = Math.min(pages - 1, page + 1); init(); })
                .dimensions(width / 2 + 38, navY, 24, 20).build());
    }

    private static Text label(Entry entry) {
        return Text.literal(entry.name() + "  ·  " + (entry.enabled().getAsBoolean() ? "ON" : "OFF"));
    }

    @Override public void renderBackground(DrawContext draw, int mouseX, int mouseY, float delta) {
        PhoenixScreenStyle.background(draw, width, height);
        draw.drawCenteredTextWithShadow(textRenderer, title, width / 2, 22, PhoenixScreenStyle.accent());
        draw.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Each module is independently saved to the local Phoenix config."),
                width / 2, 42, PhoenixScreenStyle.mutedText());
    }

    @Override public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        renderBackground(draw, mouseX, mouseY, delta);
        super.render(draw, mouseX, mouseY, delta);
        PhoenixScreenStyle.renderChrome(this, draw);
    }

    @Override public void close() { if (client != null) client.setScreen(parent); }
}
