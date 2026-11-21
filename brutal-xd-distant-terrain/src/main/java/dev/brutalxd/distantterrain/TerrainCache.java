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
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Stores compact surface samples from chunks already received by the client.
 * Previously explored samples are restored when the player returns to the
 * same server and dimension. This never requests or force-loads remote chunks.
 */
public final class TerrainCache {
    private static final int SAMPLE_STEP = 4;
    private static final Set<String> CAPTURED = ConcurrentHashMap.newKeySet();
    private static final Set<String> SAMPLE_KEYS = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger CAPTURED_CHUNKS = new AtomicInteger();
    private static final Collection<SurfaceSample> SURFACE_SAMPLES = new ConcurrentLinkedQueue<>();

    public record SurfaceSample(int x, int y, int z, String blockId) {}

    private static Path cacheRoot;
    private static volatile String activeCacheKey = "";

    private TerrainCache() {}

    public static void initialize() {
        cacheRoot = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("brutal_xd_distant_terrain").resolve("cache");
        try {
            Files.createDirectories(cacheRoot);
        } catch (IOException exception) {
            System.err.println("[Brutal_XD Distant Terrain] Cannot create cache directory: "
                    + exception.getMessage());
        }
    }

    /** Switches the active in-memory terrain set and restores its disk cache off-thread. */
    public static void activateWorld(Minecraft client, ClientLevel world) {
        if (cacheRoot == null) initialize();
        String worldKey = worldKey(client);
        String dimension = world.dimension().identifier().toString().replace(':', '_');
        String key = worldKey + "/" + dimension;
        if (key.equals(activeCacheKey)) return;

        activeCacheKey = key;
        SURFACE_SAMPLES.clear();
        SAMPLE_KEYS.clear();
        CAPTURED.clear();
        CAPTURED_CHUNKS.set(0);

        Path folder = cacheRoot.resolve(worldKey).resolve(dimension);
        CompletableFuture.runAsync(() -> restore(folder, key));
    }

    private static void restore(Path folder, String key) {
        if (!Files.isDirectory(folder)) return;
        int restoredChunks = 0;
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".csv")).toList()) {
                if (!key.equals(activeCacheKey)) return;
                String name = file.getFileName().toString();
                String[] coords = name.substring(0, name.length() - 4).split("_");
                if (coords.length != 2) continue;

                final int chunkX;
                final int chunkZ;
                try {
                    chunkX = Integer.parseInt(coords[0]);
                    chunkZ = Integer.parseInt(coords[1]);
                } catch (NumberFormatException ignored) {
                    continue;
                }

                boolean validChunk = false;
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] values = line.split(",", 4);
                    if (values.length != 4) continue;
                    try {
                        int localX = Integer.parseInt(values[0]);
                        int localZ = Integer.parseInt(values[1]);
                        int y = Integer.parseInt(values[2]);
                        int x = chunkX * 16 + localX;
                        int z = chunkZ * 16 + localZ;
                        addSample(new SurfaceSample(x, y, z, values[3]));
                        validChunk = true;
                    } catch (NumberFormatException ignored) {
                        // Ignore malformed rows without discarding the rest of the chunk.
                    }
                }
                if (validChunk) {
                    CAPTURED.add(key + "/" + chunkX + "_" + chunkZ);
                    restoredChunks++;
                }
            }
        } catch (IOException exception) {
            System.err.println("[Brutal_XD Distant Terrain] Cache restore failed: "
                    + exception.getMessage());
        }
        if (key.equals(activeCacheKey)) CAPTURED_CHUNKS.set(restoredChunks);
    }

    public static void capture(Minecraft client, ClientLevel world, LevelChunk chunk) {
        activateWorld(client, world);

        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        String worldKey = worldKey(client);
        String dimension = world.dimension().identifier().toString().replace(':', '_');
        String cacheKey = worldKey + "/" + dimension + "/" + chunkX + "_" + chunkZ;
        if (!CAPTURED.add(cacheKey)) return;

        StringBuilder data = new StringBuilder(1024);
        data.append("# Brutal_XD Distant Terrain surface samples\n");
        data.append("# chunkX=").append(chunkX).append(",chunkZ=").append(chunkZ)
                .append(",dimension=").append(world.dimension().identifier()).append('\n');

        for (int localZ = 0; localZ < 16; localZ += SAMPLE_STEP) {
            for (int localX = 0; localX < 16; localX += SAMPLE_STEP) {
                int surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ);
                if (surfaceY <= world.getMinY()) continue;

                BlockPos pos = new BlockPos(chunkX * 16 + localX, surfaceY - 1,
                        chunkZ * 16 + localZ);
                String blockId = BuiltInRegistries.BLOCK.getKey(
                        chunk.getBlockState(pos).getBlock()).toString();
                addSample(new SurfaceSample(chunkX * 16 + localX, surfaceY,
                        chunkZ * 16 + localZ, blockId));

                data.append(localX).append(',').append(localZ).append(',')
                        .append(surfaceY).append(',').append(blockId).append('\n');
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

    private static void addSample(SurfaceSample sample) {
        String key = sample.x() + ":" + sample.z();
        if (SAMPLE_KEYS.add(key)) SURFACE_SAMPLES.add(sample);
    }

    private static String worldKey(Minecraft client) {
        String identity = client.getCurrentServer() == null
                ? "singleplayer"
                : client.getCurrentServer().ip;
        return Integer.toHexString(identity.hashCode());
    }

    public static Collection<SurfaceSample> getSurfaceSamples() {
        return List.copyOf(SURFACE_SAMPLES);
    }

    public static int getCapturedChunkCount() {
        return CAPTURED_CHUNKS.get();
    }
}
