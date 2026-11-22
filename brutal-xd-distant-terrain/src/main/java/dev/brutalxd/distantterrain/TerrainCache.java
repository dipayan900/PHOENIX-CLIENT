package dev.brutalxd.distantterrain;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Persistent record of terrain that the vanilla client has already received.
 * Disk I/O is performed away from the render/client thread. This cache never
 * requests chunks from a server and never force-loads terrain.
 */
public final class TerrainCache {
    private static final int SAMPLE_STEP = 1;
    private static final int MAX_IN_MEMORY_SAMPLES = 750_000;
    private static final Set<String> CAPTURED = ConcurrentHashMap.newKeySet();
    private static final Set<String> SAMPLE_KEYS = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<Long, SurfaceSample> SAMPLE_BY_POSITION = new ConcurrentHashMap<>();
    private static final AtomicInteger CAPTURED_CHUNKS = new AtomicInteger();
    private static final AtomicInteger SAMPLE_COUNT = new AtomicInteger();
    private static final Collection<SurfaceSample> SURFACE_SAMPLES = new ConcurrentLinkedQueue<>();
    private static final ConcurrentHashMap<Long, ConcurrentLinkedQueue<SurfaceSample>> SPATIAL_BUCKETS = new ConcurrentHashMap<>();
    private static final int BUCKET_BLOCKS = 512;

    public record SurfaceSample(int x, int y, int z, String blockId) {}

    private static Path cacheRoot;
    private static volatile String activeCacheKey = "";
    private static volatile long worldGeneration;

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

    /** Activate a server/dimension namespace and restore its data asynchronously. */
    public static synchronized void activateWorld(Minecraft client, ClientLevel world) {
        if (cacheRoot == null) initialize();
        String key = worldKey(client) + "/" +
                world.dimension().identifier().toString().replace(':', '_');
        if (key.equals(activeCacheKey)) return;

        activeCacheKey = key;
        long generation = ++worldGeneration;
        SURFACE_SAMPLES.clear();
        SPATIAL_BUCKETS.clear();
        SAMPLE_KEYS.clear();
        SAMPLE_BY_POSITION.clear();
        CAPTURED.clear();
        SAMPLE_COUNT.set(0);
        CAPTURED_CHUNKS.set(0);

        Path folder = cacheRoot.resolve("surface-v3").resolve(key);
        CompletableFuture.runAsync(() -> restore(folder, key, generation));
    }

    private static void restore(Path folder, String key, long generation) {
        if (!Files.isDirectory(folder)) return;
        int restoredChunks = 0;
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".csv")).toList()) {
                if (!isCurrent(key, generation)) return;
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
                    if (!isCurrent(key, generation)) return;
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] values = line.split(",", 4);
                    if (values.length != 4) continue;
                    try {
                        int localX = Integer.parseInt(values[0]);
                        int localZ = Integer.parseInt(values[1]);
                        int y = Integer.parseInt(values[2]);
                        if (localX < 0 || localX >= 16 || localZ < 0 || localZ >= 16) continue;
                        addSample(key, generation, new SurfaceSample(
                                chunkX * 16 + localX, y, chunkZ * 16 + localZ, values[3]));
                        validChunk = true;
                    } catch (NumberFormatException ignored) {
                        // Ignore a damaged row, preserving other valid samples.
                    }
                }
                if (validChunk) {
                    CAPTURED.add(chunkKey(key, chunkX, chunkZ));
                    restoredChunks++;
                }
            }
        } catch (IOException exception) {
            System.err.println("[Brutal_XD Distant Terrain] Cache restore failed: "
                    + exception.getMessage());
        }
        if (isCurrent(key, generation)) CAPTURED_CHUNKS.set(CAPTURED.size());
    }

    public static void capture(Minecraft client, ClientLevel world, LevelChunk chunk) {
        activateWorld(client, world);
        String key = activeCacheKey;
        long generation = worldGeneration;
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        String id = chunkKey(key, chunkX, chunkZ);
        if (!CAPTURED.add(id)) return;

        // Snapshot block data immediately while the chunk is valid; only file I/O
        // is moved to the worker thread.
        StringBuilder data = new StringBuilder(2048);
        data.append("# Brutal_XD Distant Terrain surface samples\n");
        data.append("# format=3,step=1,chunkX=").append(chunkX).append(",chunkZ=")
                .append(chunkZ).append('\n');

        for (int localZ = 0; localZ < 16; localZ += SAMPLE_STEP) {
            for (int localX = 0; localX < 16; localX += SAMPLE_STEP) {
                int surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ);
                if (surfaceY <= world.getMinY()) continue;
                BlockPos pos = new BlockPos(chunkX * 16 + localX, surfaceY - 1,
                        chunkZ * 16 + localZ);
                String blockId = BuiltInRegistries.BLOCK.getKey(
                        chunk.getBlockState(pos).getBlock()).toString();
                addSample(key, generation, new SurfaceSample(
                        chunkX * 16 + localX, surfaceY, chunkZ * 16 + localZ, blockId));
                data.append(localX).append(',').append(localZ).append(',')
                        .append(surfaceY).append(',').append(blockId).append('\n');
            }
        }

        Path folder = cacheRoot.resolve("surface-v2").resolve(key);
        Path output = folder.resolve(chunkX + "_" + chunkZ + ".csv");
        String snapshot = data.toString();
        CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(folder);
                Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
                Files.writeString(temporary, snapshot, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                try {
                    Files.move(temporary, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException atomicMoveUnsupported) {
                    Files.move(temporary, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                if (isCurrent(key, generation)) CAPTURED_CHUNKS.incrementAndGet();
            } catch (IOException exception) {
                CAPTURED.remove(id);
                System.err.println("[Brutal_XD Distant Terrain] Cache write failed: "
                        + exception.getMessage());
            }
        });
    }

    private static void addSample(String key, long generation, SurfaceSample sample) {
        if (!isCurrent(key, generation) || SAMPLE_COUNT.get() >= MAX_IN_MEMORY_SAMPLES) return;
        String sampleKey = sample.x() + ":" + sample.z();
        if (SAMPLE_KEYS.add(sampleKey)) {
            int count = SAMPLE_COUNT.incrementAndGet();
            if (count <= MAX_IN_MEMORY_SAMPLES) {
                SURFACE_SAMPLES.add(sample);
                long bucket = bucketKey(sample.x(), sample.z());
                SPATIAL_BUCKETS.computeIfAbsent(bucket, ignored -> new ConcurrentLinkedQueue<>()).add(sample);
                SAMPLE_BY_POSITION.putIfAbsent(positionKey(sample.x(), sample.z()), sample);
            }
            else {
                SAMPLE_KEYS.remove(sampleKey);
                SAMPLE_COUNT.decrementAndGet();
            }
        }
    }

    private static boolean isCurrent(String key, long generation) {
        return generation == worldGeneration && key.equals(activeCacheKey);
    }

    private static String chunkKey(String key, int x, int z) {
        return key + "/" + x + "_" + z;
    }

    private static String worldKey(Minecraft client) {
        // Include the integrated world's save identity where available; the old
        // shared "singleplayer" namespace caused unrelated saves to overlap.
        if (client.getSingleplayerServer() != null) {
            String savePath = client.getSingleplayerServer().getWorldPath(LevelResource.ROOT)
                    .toAbsolutePath().normalize().toString();
            return "singleplayer_" + Integer.toUnsignedString(savePath.hashCode(), 16);
        }
        String identity = client.getCurrentServer() == null
                ? "unknown_server" : client.getCurrentServer().ip;
        return "server_" + Integer.toUnsignedString(identity.hashCode(), 16);
    }


    /** Returns only spatial buckets intersecting the requested square radius. */
    public static Collection<SurfaceSample> getSurfaceSamplesNear(int centerX, int centerZ, int radiusBlocks) {
        int minX = Math.floorDiv(centerX - radiusBlocks, BUCKET_BLOCKS);
        int maxX = Math.floorDiv(centerX + radiusBlocks, BUCKET_BLOCKS);
        int minZ = Math.floorDiv(centerZ - radiusBlocks, BUCKET_BLOCKS);
        int maxZ = Math.floorDiv(centerZ + radiusBlocks, BUCKET_BLOCKS);
        List<SurfaceSample> visible = new ArrayList<>();
        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                Collection<SurfaceSample> bucket = SPATIAL_BUCKETS.get(bucketKey(bx * BUCKET_BLOCKS, bz * BUCKET_BLOCKS));
                if (bucket != null) visible.addAll(bucket);
            }
        }
        return visible;
    }


    public static SurfaceSample getSurfaceSample(int x, int z) {
        return SAMPLE_BY_POSITION.get(positionKey(x, z));
    }

    private static long positionKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static long bucketKey(int x, int z) {
        long bx = Math.floorDiv(x, BUCKET_BLOCKS);
        long bz = Math.floorDiv(z, BUCKET_BLOCKS);
        return (bx << 32) ^ (bz & 0xffffffffL);
    }

    public static Collection<SurfaceSample> getSurfaceSamples() {
        // ConcurrentLinkedQueue provides a weakly-consistent iterator. Avoid a
        // full 100k+ element copy on every render frame.
        return SURFACE_SAMPLES;
    }

    public static int getCapturedChunkCount() {
        return CAPTURED_CHUNKS.get();
    }

    public static int getSampleCount() {
        return SAMPLE_COUNT.get();
    }
}
