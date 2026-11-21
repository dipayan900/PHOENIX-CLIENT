package dev.brutalxd.distantterrain;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Captures a compact surface representation of chunks the client has already
 * received. It deliberately never requests or force-loads distant chunks.
 *
 * This is the persistent data foundation for the LOD renderer; it is not itself
 * the final distant-terrain renderer.
 */
public final class TerrainCache {
    private static final int SAMPLE_STEP = 4;
    private static final Set<String> CAPTURED = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger CAPTURED_CHUNKS = new AtomicInteger();
    private static Path cacheRoot;

    private TerrainCache() {}

    public static void initialize() {
        cacheRoot = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("brutal_xd_distant_terrain")
                .resolve("cache");
        try {
            Files.createDirectories(cacheRoot);
        } catch (IOException exception) {
            System.err.println("[Brutal_XD Distant Terrain] Cannot create cache directory: "
                    + exception.getMessage());
        }
    }

    public static void capture(Minecraft client, ClientLevel world, LevelChunk chunk) {
        if (cacheRoot == null) initialize();

        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        String worldKey = worldKey(client);
        String dimension = world.dimension().location().toString().replace(':', '_');
        String cacheKey = worldKey + "/" + dimension + "/" + chunkX + "_" + chunkZ;
        if (!CAPTURED.add(cacheKey)) return;

        StringBuilder data = new StringBuilder(1024);
        data.append("# Brutal_XD Distant Terrain surface samples\n");
        data.append("# chunkX=").append(chunkX)
                .append(",chunkZ=").append(chunkZ)
                .append(",dimension=").append(world.dimension().location())
                .append('\n');

        for (int localZ = 0; localZ < 16; localZ += SAMPLE_STEP) {
            for (int localX = 0; localX < 16; localX += SAMPLE_STEP) {
                int surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ);
                if (surfaceY <= world.getMinY()) continue;

                BlockPos pos = new BlockPos(chunkX * 16 + localX, surfaceY - 1,
                        chunkZ * 16 + localZ);
                String blockId = BuiltInRegistries.BLOCK.getKey(chunk.getBlockState(pos).getBlock())
                        .toString();

                data.append(localX).append(',')
                        .append(localZ).append(',')
                        .append(surfaceY).append(',')
                        .append(blockId).append('\n');
            }
        }

        Path dimensionFolder = cacheRoot.resolve(worldKey).resolve(dimension);
        Path output = dimensionFolder.resolve(chunkX + "_" + chunkZ + ".csv");

        try {
            Files.createDirectories(dimensionFolder);
            Files.writeString(output, data, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            CAPTURED_CHUNKS.incrementAndGet();
        } catch (IOException exception) {
            CAPTURED.remove(cacheKey);
            System.err.println("[Brutal_XD Distant Terrain] Cache write failed: "
                    + exception.getMessage());
        }
    }

    private static String worldKey(Minecraft client) {
        String identity = client.getCurrentServer() == null
                ? "singleplayer"
                : client.getCurrentServer().ip;
        return Integer.toHexString(identity.hashCode());
    }

    public static int getCapturedChunkCount() {
        return CAPTURED_CHUNKS.get();
    }
}
