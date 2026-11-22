package dev.brutalxd.distantterrain;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

/**
 * CPU-built, camera-relative terrain LOD surface renderer. It draws only
 * terrain samples already received and cached by the normal client.
 */
public final class TerrainLodRenderer {
    private static final RenderPipeline TERRAIN_PIPELINE = RenderPipelines.SOLID_BLOCK;
    private static final Map<String, BlockSprites> BLOCK_SPRITES = new ConcurrentHashMap<>();
    private record BlockSprites(TextureAtlasSprite top, TextureAtlasSprite bottom, TextureAtlasSprite north,
                                TextureAtlasSprite east, TextureAtlasSprite south,
                                TextureAtlasSprite west) {
        TextureAtlasSprite side(Direction direction) {
            return switch (direction) {
                case NORTH -> north;
                case EAST -> east;
                case SOUTH -> south;
                case WEST -> west;
                case DOWN -> bottom;
                default -> top;
            };
        }
    }
    private static final ByteBufferBuilder ALLOCATOR =
            new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1, 1, 1, 1);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final int MAX_TILES_PER_FRAME = 8192;
    private static final int MAX_CACHED_CANDIDATES = 24000;
    private static MappableRingBuffer vertexBuffer;
    private record Candidate(TerrainCache.SurfaceSample sample, double distance) {}
    private record MeshPoint(float x, float y, float z, float u, float v) {}
    private static List<Candidate> cachedCandidates = List.of();
    private static int cachedCenterX = Integer.MIN_VALUE;
    private static int cachedCenterZ = Integer.MIN_VALUE;
    private static int cachedSampleCount = -1;
    private static int cachedDistanceChunks = -1;

    private TerrainLodRenderer() {}

    public static void initialize() {
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(TerrainLodRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (!TerrainSettings.enabled || client.level == null || client.player == null) return;

        Vec3 camera = context.worldState().cameraRenderState.pos;
        context.matrices().pushPose();
        context.matrices().translate(-camera.x, -camera.y, -camera.z);
        BufferBuilder builder = new BufferBuilder(ALLOCATOR, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        Matrix4fc matrix = context.matrices().last().pose();
        int tiles = 0;

        int centerX = Math.floorDiv((int) camera.x, 16) * 16 + 8;
        int centerZ = Math.floorDiv((int) camera.z, 16) * 16 + 8;
        int sampleCount = TerrainCache.getSampleCount();
        // Rebuild the distance-sorted candidate list only when the player changes
        // chunk, the explored cache grows, or the configured radius changes.
        // This avoids allocating and sorting tens of thousands of samples every frame.
        if (centerX != cachedCenterX || centerZ != cachedCenterZ
                || sampleCount != cachedSampleCount
                || TerrainSettings.distanceChunks != cachedDistanceChunks) {
            int radiusBlocks = TerrainSettings.distanceChunks * 16;
            // Keep only the nearest candidates. Sorting every cached column can
            // allocate hundreds of thousands of objects on mobile when the user
            // selects the 512-chunk target. A bounded max-heap keeps both memory
            // use and sort cost predictable while preserving nearest terrain first.
            PriorityQueue<Candidate> nearest = new PriorityQueue<>(
                    Comparator.comparingDouble(Candidate::distance).reversed());
            for (TerrainCache.SurfaceSample sample : TerrainCache.getSurfaceSamplesNear(
                    centerX, centerZ, radiusBlocks)) {
                double dx = sample.x() - centerX;
                double dz = sample.z() - centerZ;
                double chunkDistance = Math.sqrt(dx * dx + dz * dz) / 16.0;
                if (chunkDistance > TerrainSettings.distanceChunks) continue;
                int stride = strideForDistance(chunkDistance);
                if (Math.floorMod(sample.x(), stride) != 0
                        || Math.floorMod(sample.z(), stride) != 0) continue;
                Candidate candidate = new Candidate(sample, chunkDistance);
                if (nearest.size() < MAX_CACHED_CANDIDATES) {
                    nearest.offer(candidate);
                } else if (chunkDistance < nearest.peek().distance()) {
                    nearest.poll();
                    nearest.offer(candidate);
                }
            }
            List<Candidate> candidates = new ArrayList<>(nearest);
            candidates.sort(Comparator.comparingDouble(Candidate::distance));
            cachedCandidates = candidates;
            cachedCenterX = centerX;
            cachedCenterZ = centerZ;
            cachedSampleCount = sampleCount;
            cachedDistanceChunks = TerrainSettings.distanceChunks;
        }

        for (Candidate candidate : cachedCandidates) {
            TerrainCache.SurfaceSample sample = candidate.sample();
            double chunkDistance = candidate.distance();
            int stride = strideForDistance(chunkDistance);
            float size = 1.0f * stride;
            float y00 = sample.y();
            TerrainCache.SurfaceSample east = TerrainCache.getSurfaceSample(sample.x() + (int) size, sample.z());
            TerrainCache.SurfaceSample south = TerrainCache.getSurfaceSample(sample.x(), sample.z() + (int) size);
            TerrainCache.SurfaceSample diagonal = TerrainCache.getSurfaceSample(
                    sample.x() + (int) size, sample.z() + (int) size);
            float y10 = east == null ? y00 : east.y();
            float y01 = south == null ? y00 : south.y();
            float y11 = diagonal == null ? (y10 + y01) * 0.5f : diagonal.y();
            float[] color = colorFor(sample.blockId(), chunkDistance);
            BlockSprites sprites = blockSprites(sample.blockId());
            TextureAtlasSprite sprite = sprites.top();
            addTerrainCell(builder, matrix, sample.x(), sample.z(), sample.x() + size,
                    sample.z() + size, y00 + 0.02f, y10 + 0.02f,
                    y11 + 0.02f, y01 + 0.02f, color, sprites, centerX, centerZ);
            if (++tiles >= MAX_TILES_PER_FRAME) break;
        }

        context.matrices().popPose();
        if (tiles == 0) {
            // No vertices were emitted; skip this frame without building a mesh.
            return;
        }

        MeshData mesh = builder.build();
        if (mesh == null) return;
        MeshData.DrawState drawState = mesh.drawState();
        VertexFormat format = drawState.format();
        int bytes = drawState.vertexCount() * format.getVertexSize();
        if (vertexBuffer == null || vertexBuffer.size() < bytes) {
            if (vertexBuffer != null) vertexBuffer.close();
            vertexBuffer = new MappableRingBuffer(
                    () -> "Brutal_XD distant terrain vertices",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, bytes);
        }
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuBuffer.MappedView mapped = encoder.mapBuffer(
                vertexBuffer.currentBuffer().slice(0, mesh.vertexBuffer().remaining()),
                false, true)) {
            MemoryUtil.memCopy(mesh.vertexBuffer(), mapped.data());
        }

        GpuBuffer indices = TERRAIN_PIPELINE.getVertexFormat()
                .uploadImmediateIndexBuffer(mesh.indexBuffer());
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(
                RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Brutal_XD distant terrain",
                client.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(),
                client.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(TERRAIN_PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, vertexBuffer.currentBuffer());
            pass.setIndexBuffer(indices, drawState.indexType());
            var atlas = client.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
            pass.bindTexture("Sampler0", atlas.getTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST, true));
            pass.bindTexture("Sampler2", client.gameRenderer.lightTexture().getTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.drawIndexed(0, 0, drawState.indexCount(), 1);
        }
        mesh.close();
        vertexBuffer.rotate();
    }

    public static TextureAtlasSprite faceSpriteFor(String blockId, Direction direction) {
        BlockSprites sprites = blockSprites(blockId);
        return direction == Direction.UP ? sprites.top() : sprites.side(direction);
    }

    private static BlockSprites blockSprites(String blockId) {
        return BLOCK_SPRITES.computeIfAbsent(blockId, id -> {
            try {
                Identifier identifier = Identifier.tryParse(id);
                Block fallback = Blocks.STONE;
                Block block = identifier == null ? fallback : BuiltInRegistries.BLOCK
                        .get(identifier).map(reference -> reference.value()).orElse(fallback);
                BlockStateModel model = Minecraft.getInstance().getBlockRenderer()
                        .getBlockModelShaper().getBlockModel(block.defaultBlockState());
                List<BlockModelPart> parts = new ArrayList<>();
                model.collectParts(RandomSource.create(42L), parts);
                TextureAtlasSprite particle = model.particleIcon();
                return new BlockSprites(faceSprite(parts, Direction.UP, particle),
                        faceSprite(parts, Direction.DOWN, particle),
                        faceSprite(parts, Direction.NORTH, particle),
                        faceSprite(parts, Direction.EAST, particle),
                        faceSprite(parts, Direction.SOUTH, particle),
                        faceSprite(parts, Direction.WEST, particle));
            } catch (Throwable ignored) {
                TextureAtlasSprite fallback = Minecraft.getInstance().getBlockRenderer()
                        .getBlockModelShaper().getBlockModel(Blocks.STONE.defaultBlockState()).particleIcon();
                return new BlockSprites(fallback, fallback, fallback, fallback, fallback, fallback);
            }
        });
    }

    private static TextureAtlasSprite faceSprite(List<BlockModelPart> parts, Direction face,
                                                  TextureAtlasSprite fallback) {
        for (BlockModelPart part : parts) {
            List<BakedQuad> quads = part.getQuads(face);
            if (!quads.isEmpty()) return quads.get(0).sprite();
        }
        return fallback;
    }

    /**
     * Builds a terrain cell as a triangle fan whose boundary is subdivided
     * wherever the adjacent tile uses a finer LOD. Shared boundary vertices
     * are sampled from the same cache coordinates on both sides, avoiding
     * T-junctions between coarse and fine meshes. Skirts remain as a fallback
     * for unexplored/missing edge samples.
     */
    private static void addTerrainCell(BufferBuilder out, Matrix4fc matrix,
                                       float x0, float z0, float x1, float z1,
                                       float y00, float y10, float y11, float y01,
                                       float[] color, BlockSprites sprites,
                                       int cameraX, int cameraZ) {
        TextureAtlasSprite sprite = sprites.top();
        float size = x1 - x0;
        int stride = Math.max(1, Math.round(size));
        float midX = (x0 + x1) * 0.5f;
        float midZ = (z0 + z1) * 0.5f;
        float midY = (y00 + y10 + y11 + y01) * 0.25f;
        TerrainCache.SurfaceSample centerSample = TerrainCache.getSurfaceSample(
                Math.round(midX), Math.round(midZ));
        if (centerSample != null) midY = centerSample.y() + 0.02f;

        List<MeshPoint> perimeter = new ArrayList<>(stride * 4);
        int west = edgeSegments(stride, cameraX, cameraZ, x0 - 1, midZ);
        int south = edgeSegments(stride, cameraX, cameraZ, midX, z1 + 1);
        int east = edgeSegments(stride, cameraX, cameraZ, x1 + 1, midZ);
        int north = edgeSegments(stride, cameraX, cameraZ, midX, z0 - 1);

        appendEdge(perimeter, x0, z0, y00, x0, z1, y01, west, x0, z0, size, sprite);
        appendEdge(perimeter, x0, z1, y01, x1, z1, y11, south, x0, z0, size, sprite);
        appendEdge(perimeter, x1, z1, y11, x1, z0, y10, east, x0, z0, size, sprite);
        appendEdge(perimeter, x1, z0, y10, x0, z0, y00, north, x0, z0, size, sprite);

        MeshPoint center = new MeshPoint(midX, midY, midZ,
                (sprite.getU0() + sprite.getU1()) * 0.5f,
                (sprite.getV0() + sprite.getV1()) * 0.5f);
        for (int i = 0; i < perimeter.size(); i++) {
            MeshPoint a = perimeter.get(i);
            MeshPoint b = perimeter.get((i + 1) % perimeter.size());
            // QUADS pipeline: the repeated centre vertex turns this into one
            // valid triangle plus one degenerate triangle.
            addTexturedQuadPoints(out, matrix, center, a, b, center, color);
        }

        float floor = Math.min(Math.min(y00, y10), Math.min(y11, y01)) - 8.0f;
        addTexturedQuad(out, matrix, x0, z0, x1, z0,
                y00, y10, floor, floor, color, sprites.side(Direction.NORTH), 0, 0, -1);
        addTexturedQuad(out, matrix, x1, z0, x1, z1,
                y10, y11, floor, floor, color, sprites.side(Direction.EAST), 1, 0, 0);
        addTexturedQuad(out, matrix, x1, z1, x0, z1,
                y11, y01, floor, floor, color, sprites.side(Direction.SOUTH), 0, 0, 1);
        addTexturedQuad(out, matrix, x0, z1, x0, z0,
                y01, y00, floor, floor, color, sprites.side(Direction.WEST), -1, 0, 0);
    }

    private static int strideForDistance(double distance) {
        return distance < 48 ? 1 : distance < 128 ? 2 : distance < 256 ? 4 : 8;
    }

    private static int edgeSegments(int stride, int cameraX, int cameraZ,
                                    float edgeX, float edgeZ) {
        double distance = Math.hypot(edgeX - cameraX, edgeZ - cameraZ) / 16.0;
        int neighbourStride = strideForDistance(distance);
        return Math.max(1, stride / Math.min(stride, neighbourStride));
    }

    private static void appendEdge(List<MeshPoint> points,
                                   float sx, float sz, float sy,
                                   float ex, float ez, float ey, int segments,
                                   float cellX, float cellZ, float cellSize,
                                   TextureAtlasSprite sprite) {
        for (int i = 0; i < segments; i++) {
            float t = i / (float) segments;
            float x = sx + (ex - sx) * t;
            float z = sz + (ez - sz) * t;
            float y = sy + (ey - sy) * t;
            TerrainCache.SurfaceSample sample = TerrainCache.getSurfaceSample(
                    Math.round(x), Math.round(z));
            if (sample != null) y = sample.y() + 0.02f;
            float u = sprite.getU0() + (x - cellX) / cellSize * (sprite.getU1() - sprite.getU0());
            float v = sprite.getV0() + (z - cellZ) / cellSize * (sprite.getV1() - sprite.getV0());
            points.add(new MeshPoint(x, y, z, u, v));
        }
    }

    private static void addTexturedQuadPoints(BufferBuilder out, Matrix4fc matrix,
                                              MeshPoint a, MeshPoint b, MeshPoint c,
                                              MeshPoint d, float[] color) {
        addTexturedVertex(out, matrix, a.x(), a.y(), a.z(), a.u(), a.v(), color, 0, 1, 0);
        addTexturedVertex(out, matrix, b.x(), b.y(), b.z(), b.u(), b.v(), color, 0, 1, 0);
        addTexturedVertex(out, matrix, c.x(), c.y(), c.z(), c.u(), c.v(), color, 0, 1, 0);
        addTexturedVertex(out, matrix, d.x(), d.y(), d.z(), d.u(), d.v(), color, 0, 1, 0);
    }

    private static void addTexturedQuad(BufferBuilder out, Matrix4fc matrix,
                                        float x0, float z0, float x1, float z1,
                                        float y00, float y10, float y11, float y01,
                                        float[] color, TextureAtlasSprite sprite,
                                        float nx, float ny, float nz) {
        float u0 = sprite.getU0(), u1 = sprite.getU1();
        float v0 = sprite.getV0(), v1 = sprite.getV1();
        addTexturedVertex(out, matrix, x0, y00, z0, u0, v0, color, nx, ny, nz);
        addTexturedVertex(out, matrix, x0, y01, z1, u0, v1, color, nx, ny, nz);
        addTexturedVertex(out, matrix, x1, y11, z1, u1, v1, color, nx, ny, nz);
        addTexturedVertex(out, matrix, x1, y10, z0, u1, v0, color, nx, ny, nz);
    }

    private static void addTexturedVertex(BufferBuilder out, Matrix4fc matrix,
                                         float x, float y, float z, float u, float v,
                                         float[] color, float nx, float ny, float nz) {
        out.addVertex(matrix, x, y, z)
                .setColor(color[0], color[1], color[2], 1.0f)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(nx, ny, nz);
    }

    private static float[] colorFor(String block, double distance) {
        // The atlas already contains the block's real colour. Apply only a
        // neutral distance fade so grass, stone and sand are not tinted twice.
        float fog = 0.96f - (float) Math.min(0.38, distance / 512.0 * 0.38);
        return new float[]{fog, fog, fog};
    }
}
