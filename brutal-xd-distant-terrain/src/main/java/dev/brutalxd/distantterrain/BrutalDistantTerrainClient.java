package dev.brutalxd.distantterrain;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class BrutalDistantTerrainClient implements ClientModInitializer {
    public static final String MOD_ID = "brutal_xd_distant_terrain";
    public static final int DEFAULT_DISTANCE_CHUNKS = 512;

    @Override
    public void onInitializeClient() {
        TerrainCache.initialize();
        TerrainSettings.initialize();
        TerrainLodRenderer.initialize();
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) ->
                TerrainCache.capture(Minecraft.getInstance(), world, chunk));

        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(MOD_ID, "cache_status"),
                (graphics, tickCounter) -> {
                    Minecraft client = Minecraft.getInstance();
                    if (client.player == null || client.options.hideGui) return;
                    String status = "Brutal_XD Distant Terrain | "
                            + TerrainCache.getCapturedChunkCount() + " cached chunks"
                            + " | " + TerrainCache.getSampleCount() + " samples"
                            + " | " + TerrainSettings.distanceChunks + " chunk target";
                    graphics.drawString(client.font, status, 6, 6, 0xFFE8A34A, true);
                }
        );

        System.out.println("[Brutal_XD Distant Terrain] Client initialized. "
                + "Configured target distance: " + TerrainSettings.distanceChunks + " chunks.");
    }
}
