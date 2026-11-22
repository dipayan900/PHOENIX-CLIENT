package dev.brutalxd.distantterrain;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.textures.FilterMode;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3fc;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Near-field voxel mesh renderer. It consumes the persisted 3D chunk snapshots,
 * emits exposed textured block faces, and keeps GPU meshes per chunk so geometry
 * is not rebuilt every frame. The surface LOD renderer remains responsible for
 * the far horizon.
 */
public final class VoxelTerrainRenderer {
    private static final RenderPipeline PIPELINE = RenderPipelines.SOLID_BLOCK;
    private static final ByteBufferBuilder ALLOCATOR =
            new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private static final org.joml.Vector4f WHITE = new org.joml.Vector4f(1, 1, 1, 1);
    private static final org.joml.Vector3f ZERO = new org.joml.Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final int DETAIL_RADIUS = 8;
    private static final int MAX_GPU_CHUNKS = 32;
    private static final Map<Long, ChunkMesh> MESHES = new ConcurrentHashMap<>();
    private static final Map<BlockState, List<BlockModelPart>> MODEL_PARTS = new ConcurrentHashMap<>();

    private record ChunkMesh(VoxelChunkCache.VoxelChunk source, GpuBuffer vertices,
                             GpuBuffer indices, VertexFormat.IndexType indexType,
                             int indexCount) {
        void close() {
            if (vertices != null) vertices.close();
            if (indices != null) indices.close();
        }
    }

    private VoxelTerrainRenderer() {}

    public static void initialize() {
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(VoxelTerrainRenderer::render);
    }

    public static void clear() {
        for (ChunkMesh mesh : MESHES.values()) mesh.close();
        MESHES.clear();
        MODEL_PARTS.clear();
    }

    public static void invalidateAround(int chunkX, int chunkZ) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                ChunkMesh removed = MESHES.remove(key(chunkX + dx, chunkZ + dz));
                if (removed != null) removed.close();
            }
        }
    }

    private static void render(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (!TerrainSettings.enabled || client.level == null || client.player == null) return;

        Vec3 camera = context.worldState().cameraRenderState.pos;
        int centerX = Math.floorDiv((int) Math.floor(camera.x), 16);
        int centerZ = Math.floorDiv((int) Math.floor(camera.z), 16);

        // Disk snapshots are requested asynchronously; render-thread disk I/O is forbidden.
        for (int dz = -DETAIL_RADIUS; dz <= DETAIL_RADIUS; dz++) {
            for (int dx = -DETAIL_RADIUS; dx <= DETAIL_RADIUS; dx++) {
                if (dx * dx + dz * dz <= DETAIL_RADIUS * DETAIL_RADIUS) {
                    VoxelChunkCache.requestLoad(centerX + dx, centerZ + dz);
                }
            }
        }

        List<VoxelChunkCache.VoxelChunk> chunks =
                VoxelChunkCache.getResidentChunksNear(centerX, centerZ, DETAIL_RADIUS);
        chunks.sort(Comparator.comparingInt(chunk ->
                (chunk.chunkX() - centerX) * (chunk.chunkX() - centerX)
                        + (chunk.chunkZ() - centerZ) * (chunk.chunkZ() - centerZ)));

        // Build at most one chunk mesh per frame. Existing GPU meshes are reused.
        for (VoxelChunkCache.VoxelChunk chunk : chunks) {
            long key = key(chunk.chunkX(), chunk.chunkZ());
            ChunkMesh current = MESHES.get(key);
            if (current == null || current.source() != chunk) {
                ChunkMesh built = buildMesh(chunk);
                if (built != null) {
                    ChunkMesh old = MESHES.put(key, built);
                    if (old != null) old.close();
                }
                break;
            }
        }

        // Bound GPU memory and discard meshes outside the active near-field radius.
        Iterator<Map.Entry<Long, ChunkMesh>> iterator = MESHES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, ChunkMesh> entry = iterator.next();
            ChunkMesh mesh = entry.getValue();
            int dx = mesh.source().chunkX() - centerX;
            int dz = mesh.source().chunkZ() - centerZ;
            if (dx * dx + dz * dz > DETAIL_RADIUS * DETAIL_RADIUS) {
                if (MESHES.remove(entry.getKey(), mesh)) mesh.close();
            }
        }
        if (MESHES.size() > MAX_GPU_CHUNKS) {
            List<Map.Entry<Long, ChunkMesh>> farthest = new ArrayList<>(MESHES.entrySet());
            farthest.sort(Comparator.comparingInt((Map.Entry<Long, ChunkMesh> entry) -> {
                int dx = entry.getValue().source().chunkX() - centerX;
                int dz = entry.getValue().source().chunkZ() - centerZ;
                return dx * dx + dz * dz;
            }).reversed());
            for (int i = 0; i < farthest.size() - MAX_GPU_CHUNKS; i++) {
                Map.Entry<Long, ChunkMesh> entry = farthest.get(i);
                if (MESHES.remove(entry.getKey(), entry.getValue())) entry.getValue().close();
            }
        }

        for (VoxelChunkCache.VoxelChunk chunk : chunks) {
            ChunkMesh mesh = MESHES.get(key(chunk.chunkX(), chunk.chunkZ()));
            if (mesh == null || mesh.vertices() == null || mesh.indexCount() <= 0) continue;
            context.matrices().pushPose();
            context.matrices().translate(chunk.chunkX() * 16.0 - camera.x,
                    -camera.y, chunk.chunkZ() * 16.0 - camera.z);
            GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(
                    RenderSystem.getModelViewMatrix(), WHITE, ZERO, TEXTURE_MATRIX);
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "Brutal_XD voxel terrain",
                    client.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(),
                    client.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
                pass.setPipeline(PIPELINE);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transforms);
                pass.setVertexBuffer(0, mesh.vertices());
                pass.setIndexBuffer(mesh.indices(), mesh.indexType());
                var atlas = client.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
                pass.bindTexture("Sampler0", atlas.getTextureView(),
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST, true));
                pass.bindTexture("Sampler2", client.gameRenderer.lightTexture().getTextureView(),
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                pass.drawIndexed(0, 0, mesh.indexCount(), 1);
            }
            context.matrices().popPose();
        }
    }

    private static ChunkMesh buildMesh(VoxelChunkCache.VoxelChunk chunk) {
        BufferBuilder builder = new BufferBuilder(ALLOCATOR,
                VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        Matrix4fc identity = new Matrix4f();
        int minY = chunk.minY();
        int maxY = minY + chunk.height();

        for (int y = minY; y < maxY; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (chunk.blockAt(x, y, z) == 0) continue;
                    BlockState blockState = chunk.stateAt(x, y, z);
                    if (blockState.isAir()) continue;
                    List<BlockModelPart> parts = MODEL_PARTS.computeIfAbsent(blockState, state -> {
                        BlockStateModel model = Minecraft.getInstance().getBlockRenderer()
                                .getBlockModelShaper().getBlockModel(state);
                        List<BlockModelPart> collected = new ArrayList<>();
                        model.collectParts(RandomSource.create(42L), collected);
                        return List.copyOf(collected);
                    });

                    for (Direction face : Direction.values()) {
                        if (shouldSkipFace(blockState, chunk, x + face.getStepX(), y + face.getStepY(),
                                z + face.getStepZ(), face)) continue;
                        for (BlockModelPart part : parts) {
                            for (BakedQuad quad : part.getQuads(face)) {
                                emitBakedQuad(builder, identity, x, y, z, quad);
                            }
                        }
                    }
                    for (BlockModelPart part : parts) {
                        for (BakedQuad quad : part.getQuads(null)) {
                            emitBakedQuad(builder, identity, x, y, z, quad);
                        }
                    }
                }
            }
        }

        MeshData mesh = builder.build();
        if (mesh == null) return new ChunkMesh(chunk, null, null,
                VertexFormat.IndexType.SHORT, 0);
        try {
            MeshData.DrawState state = mesh.drawState();
            GpuBuffer vertices = RenderSystem.getDevice().createBuffer(
                    () -> "Brutal_XD voxel chunk vertices",
                    GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
            GpuBuffer indices = PIPELINE.getVertexFormat()
                    .uploadImmediateIndexBuffer(mesh.indexBuffer());
            return new ChunkMesh(chunk, vertices, indices, state.indexType(), state.indexCount());
        } finally {
            mesh.close();
        }
    }

    private static boolean shouldSkipFace(BlockState current,
                                           VoxelChunkCache.VoxelChunk chunk,
                                           int x, int y, int z, Direction face) {
        if (y < chunk.minY() || y >= chunk.minY() + chunk.height()) return false;
        VoxelChunkCache.VoxelChunk source = chunk;
        int localX = x, localZ = z;
        if (x < 0 || x >= 16 || z < 0 || z >= 16) {
            int neighborX = chunk.chunkX() + Math.floorDiv(x, 16);
            int neighborZ = chunk.chunkZ() + Math.floorDiv(z, 16);
            source = VoxelChunkCache.get(neighborX, neighborZ);
            if (source == null) return false;
            localX = Math.floorMod(x, 16);
            localZ = Math.floorMod(z, 16);
        }
        BlockState neighbor = source.stateAt(localX, y, localZ);
        return current.skipRendering(neighbor, face);
    }

    private static void emitBakedQuad(BufferBuilder out, Matrix4fc matrix,
                                      int blockX, int blockY, int blockZ, BakedQuad quad) {
        Direction face = quad.direction();
        float nx = face.getStepX(), ny = face.getStepY(), nz = face.getStepZ();
        TextureAtlasSprite sprite = quad.sprite();
        for (int i = 0; i < 4; i++) {
            Vector3fc position = quad.position(i);
            long packedUv = quad.packedUV(i);
            float u = Float.intBitsToFloat((int) (packedUv >>> 32));
            float v = Float.intBitsToFloat((int) packedUv);
            out.addVertex(matrix, blockX + position.x(), blockY + position.y(),
                            blockZ + position.z())
                    .setColor(1, 1, 1, 1)
                    .setUv(u, v)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LightTexture.FULL_BRIGHT)
                    .setNormal(nx, ny, nz);
        }
    }

    private static void emitFace(BufferBuilder out, Matrix4fc matrix, float x, float y,
                                 float z, Direction face, TextureAtlasSprite sprite) {
        float x1 = x + 1, y1 = y + 1, z1 = z + 1;
        float[][] points = switch (face) {
            case DOWN -> new float[][]{{x,y,z1},{x,y,z},{x1,y,z},{x1,y,z1}};
            case UP -> new float[][]{{x,y1,z},{x,y1,z1},{x1,y1,z1},{x1,y1,z}};
            case NORTH -> new float[][]{{x,y,z},{x,y1,z},{x1,y1,z},{x1,y,z}};
            case SOUTH -> new float[][]{{x1,y,z1},{x1,y1,z1},{x,y1,z1},{x,y,z1}};
            case WEST -> new float[][]{{x,y,z1},{x,y1,z1},{x,y1,z},{x,y,z}};
            case EAST -> new float[][]{{x1,y,z},{x1,y1,z},{x1,y1,z1},{x1,y,z1}};
        };
        float u0 = sprite.getU0(), u1 = sprite.getU1();
        float v0 = sprite.getV0(), v1 = sprite.getV1();
        float[][] uv = {{u0,v1},{u0,v0},{u1,v0},{u1,v1}};
        float nx = face.getStepX(), ny = face.getStepY(), nz = face.getStepZ();
        for (int i = 0; i < 4; i++) {
            out.addVertex(matrix, points[i][0], points[i][1], points[i][2])
                    .setColor(1, 1, 1, 1)
                    .setUv(uv[i][0], uv[i][1])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LightTexture.FULL_BRIGHT)
                    .setNormal(nx, ny, nz);
        }
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
