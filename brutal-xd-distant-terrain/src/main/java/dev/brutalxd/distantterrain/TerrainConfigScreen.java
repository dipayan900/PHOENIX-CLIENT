package dev.brutalxd.distantterrain;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class TerrainConfigScreen extends Screen {
    private final Screen parent;
    private Button enabledButton;
    private Button distanceButton;

    public TerrainConfigScreen(Screen parent) {
        super(Component.literal("Brutal_XD Distant Terrain"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        enabledButton = this.addRenderableWidget(Button.builder(enabledLabel(), button -> {
            TerrainSettings.enabled = !TerrainSettings.enabled;
            button.setMessage(enabledLabel());
            TerrainSettings.save();
        }).bounds(center - 100, this.height / 2 - 34, 200, 20).build());

        distanceButton = this.addRenderableWidget(Button.builder(distanceLabel(), button -> {
            TerrainSettings.distanceChunks = switch (TerrainSettings.distanceChunks) {
                case 128 -> 256;
                case 256 -> 512;
                default -> 128;
            };
            button.setMessage(distanceLabel());
            TerrainSettings.save();
        }).bounds(center - 100, this.height / 2 - 6, 200, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(center - 100, this.height / 2 + 28, 200, 20).build());
    }

    private Component enabledLabel() {
        return Component.literal("Distant terrain: " + (TerrainSettings.enabled ? "ON" : "OFF"));
    }

    private Component distanceLabel() {
        return Component.literal("Render distance: " + TerrainSettings.distanceChunks + " chunks");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 72, 0xFFE8A34A);
        graphics.drawCenteredString(this.font, "Balanced profile · Android-friendly surface LOD",
                this.width / 2, this.height / 2 - 54, 0xFFCCCCCC);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        TerrainSettings.save();
        this.minecraft.setScreen(parent);
    }
}
