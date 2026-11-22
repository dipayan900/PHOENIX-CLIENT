package dev.brutalxd.distantterrain;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Compact persistent 3D block snapshot cache. Unlike the surface cache this
 * stores every block position, including air, caves and overhangs. Snapshots
 * are taken on the client thread; compression and disk I/O run asynchronously.
 *
 * The in-memory LRU is deliberately bounded for mobile devices. The disk cache
 * is versioned and keyed by world/dimension and chunk coordinates.
 */
public final class VoxelChunkCache {
    private static final int MAGIC = 0x42585631; // BXV1
    private static final int FORMAT = 2;
    private static final int MAX_RESIDENT_CHUNKS = 96;
    private static final int MAX_PALETTE = 65535;
    private static final Map<Long, VoxelChunk> RESIDENT =
            new LinkedHashMap<>(32, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<Long, VoxelChunk> eldest) {
                    return size() > MAX_RESIDENT_CHUNKS;
                }
            };

    public record VoxelChunk(int chunkX, int chunkZ, int minY, int height,
                             String[] palette, short[] blocks) {
        public int blockAt(int localX, int y, int localZ) {
            if (localX < 0 || localX > 15 || localZ < 0 || localZ > 15
                    || y < minY || y >= minY + height) return 0;
            return Short.toUnsignedInt(blocks[((y - minY) * 16 + localZ) * 16 + localX]);
        }
        public String blockIdAt(int localX, int y, int localZ) {
            int index = blockAt(localX, y, localZ);
            return index >= palette.length ? "minecraft:air" : blockIdFromStateKey(palette[index]);
        }
        public BlockState stateAt(int localX, int y, int localZ) {
            int index = blockAt(localX, y, localZ);
            if (index >= palette.length) return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            return resolveState(palette[index]);
        }
    }

    private static Path root;
    private static volatile String worldKey = "unknown";
    private static volatile int minY;
    private static volatile int height;

    private VoxelChunkCache() {}

    public static void initialize() {
        root = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("brutal_xd_distant_terrain").resolve("voxels");
    }

    public static synchronized void activate(Minecraft client, net.minecraft.client.multiplayer.ClientLevel level) {
        if (root == null) initialize();
        String server = client.getSingleplayerServer() != null ? "singleplayer_" +
                Integer.toUnsignedString(client.getSingleplayerServer().getWorldPath(
                        net.minecraft.world.level.storage.LevelResource.ROOT)
                        .toAbsolutePath().normalize().toString().hashCode(), 16)
                : "server_" + Integer.toUnsignedString((client.getCurrentServer() == null
                        ? "unknown" : client.getCurrentServer().ip).hashCode(), 16);
        String next = server + "/" + level.dimension().identifier().toString().replace(':', '_');
        if (!next.equals(worldKey)) {
            worldKey = next;
            minY = level.getMinY();
            height = level.getMaxY() - level.getMinY();
            synchronized (RESIDENT) { RESIDENT.clear(); }
        }
    }

    public static void capture(net.minecraft.client.multiplayer.ClientLevel level, LevelChunk chunk) {
        Minecraft client = Minecraft.getInstance();
        activate(client, level);
        int y0 = level.getMinY();
        int h = level.getMaxY() - y0;
        if (h <= 0 || h > 4096) return;

        // Palette index zero is always air. Capture is intentionally performed
        // before leaving the client event; no live Chunk access occurs in I/O tasks.
        Map<BlockState, Integer> indices = new java.util.HashMap<>();
        java.util.ArrayList<String> palette = new java.util.ArrayList<>();
        palette.add("minecraft:air");
        indices.put(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 0);
        short[] blocks = new short[16 * 16 * h];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int cx = chunk.getPos().x;
        int cz = chunk.getPos().z;
        for (int y = y0; y < y0 + h; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    pos.set(cx * 16 + x, y, cz * 16 + z);
                    var state = chunk.getBlockState(pos);
                    if (state.isAir()) continue;
                    String id = stateKey(state);
                    Integer index = indices.get(state);
                    if (index == null) {
                        if (palette.size() >= MAX_PALETTE) continue;
                        index = palette.size();
                        palette.add(id);
                        indices.put(state, index);
                    }
                    blocks[((y - y0) * 16 + z) * 16 + x] = (short) (int) index;
                }
            }
        }

        VoxelChunk snapshot = new VoxelChunk(cx, cz, y0, h,
                palette.toArray(String[]::new), blocks);
        synchronized (RESIDENT) { RESIDENT.put(key(cx, cz), snapshot); }
        Path folder = root.resolve(worldKey);
        Path file = folder.resolve(cx + "_" + cz + ".bxv");
        String snapshotWorld = worldKey;
        CompletableFuture.runAsync(() -> write(file, snapshot, snapshotWorld));
    }

    private static void write(Path file, VoxelChunk chunk, String expectedWorld) {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    new GZIPOutputStream(Files.newOutputStream(temp))))) {
                out.writeInt(MAGIC);
                out.writeInt(FORMAT);
                out.writeInt(chunk.chunkX());
                out.writeInt(chunk.chunkZ());
                out.writeInt(chunk.minY());
                out.writeInt(chunk.height());
                out.writeInt(chunk.palette().length);
                for (String id : chunk.palette()) out.writeUTF(id);
                for (short block : chunk.blocks()) out.writeShort(Short.toUnsignedInt(block));
            }
            if (expectedWorld.equals(worldKey)) {
                try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (IOException ignored) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
            } else Files.deleteIfExists(temp);
        } catch (IOException e) {
            System.err.println("[Brutal_XD Distant Terrain] Voxel cache write failed: " + e.getMessage());
        }
    }

    public static VoxelChunk get(int chunkX, int chunkZ) {
        synchronized (RESIDENT) {
            VoxelChunk loaded = RESIDENT.get(key(chunkX, chunkZ));
            if (loaded != null) return loaded;
        }
        if (root == null) return null;
        Path file = root.resolve(worldKey).resolve(chunkX + "_" + chunkZ + ".bxv");
        if (!Files.isRegularFile(file)) return null;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(Files.newInputStream(file))))) {
            if (in.readInt() != MAGIC || in.readInt() != FORMAT) return null;
            int cx = in.readInt(), cz = in.readInt(), y = in.readInt(), h = in.readInt();
            int paletteSize = in.readInt();
            if (cx != chunkX || cz != chunkZ || h <= 0 || h > 4096
                    || paletteSize <= 0 || paletteSize > MAX_PALETTE) return null;
            String[] palette = new String[paletteSize];
            for (int i = 0; i < paletteSize; i++) palette[i] = in.readUTF();
            short[] blocks = new short[16 * 16 * h];
            for (int i = 0; i < blocks.length; i++) blocks[i] = (short) in.readUnsignedShort();
            VoxelChunk loaded = new VoxelChunk(cx, cz, y, h, palette, blocks);
            synchronized (RESIDENT) { RESIDENT.put(key(cx, cz), loaded); }
            return loaded;
        } catch (IOException | OutOfMemoryError e) {
            System.err.println("[Brutal_XD Distant Terrain] Voxel cache read failed: " + e.getMessage());
            return null;
        }
    }


    private static String stateKey(BlockState state) {
        StringBuilder key = new StringBuilder(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        if (!state.getValues().isEmpty()) {
            key.append('[');
            boolean first = true;
            for (Map.Entry<Property<?>, Comparable<?>> entry : state.getValues().entrySet()) {
                if (!first) key.append(',');
                first = false;
                key.append(entry.getKey().getName()).append('=');
                key.append(propertyName(entry.getKey(), entry.getValue()));
            }
            key.append(']');
        }
        return key.toString();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propertyName(Property property, Comparable value) {
        return property.getName(value);
    }

    private static String blockIdFromStateKey(String key) {
        int bracket = key.indexOf('[');
        return bracket < 0 ? key : key.substring(0, bracket);
    }

    private static BlockState resolveState(String key) {
        int bracket = key.indexOf('[');
        String id = bracket < 0 ? key : key.substring(0, bracket);
        Identifier identifier = Identifier.tryParse(id);
        Block block = identifier == null ? net.minecraft.world.level.block.Blocks.AIR
                : BuiltInRegistries.BLOCK.get(identifier).map(reference -> reference.value())
                    .orElse(net.minecraft.world.level.block.Blocks.AIR);
        BlockState state = block.defaultBlockState();
        if (bracket < 0 || !key.endsWith("]")) return state;
        String properties = key.substring(bracket + 1, key.length() - 1);
        for (String pair : properties.split(",")) {
            int equals = pair.indexOf('=');
            if (equals <= 0) continue;
            String propertyName = pair.substring(0, equals);
            String valueName = pair.substring(equals + 1);
            Property<?> property = block.getStateDefinition().getProperty(propertyName);
            if (property != null) state = applyProperty(state, property, valueName);
        }
        return state;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState applyProperty(BlockState state, Property property, String valueName) {
        java.util.Optional parsed = property.getValue(valueName);
        return parsed.isPresent() ? state.setValue(property, (Comparable) parsed.get()) : state;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
