package dev.brutalxd.distantterrain;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
    private static final Map<String, TextureAtlasSprite> TOP_SPRITES = new ConcurrentHashMap<>();
    private static final ByteBufferBuilder ALLOCATOR =
            new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1, 1, 1, 1);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final int MAX_TILES_PER_FRAME = 8192;
    private static MappableRingBuffer vertexBuffer;
    private record Candidate(TerrainCache.SurfaceSample sample, double distance) {}
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
            List<Candidate> candidates = new ArrayList<>();
            for (TerrainCache.SurfaceSample sample : TerrainCache.getSurfaceSamplesNear(
                    centerX, centerZ, radiusBlocks)) {
                double dx = sample.x() - centerX;
                double dz = sample.z() - centerZ;
                double chunkDistance = Math.sqrt(dx * dx + dz * dz) / 16.0;
                if (chunkDistance > TerrainSettings.distanceChunks) continue;
                int stride = chunkDistance < 48 ? 1 : chunkDistance < 128 ? 2
                        : chunkDistance < 256 ? 4 : 8;
                if (Math.floorMod(sample.x(), stride * 2) != 0
                        || Math.floorMod(sample.z(), stride * 2) != 0) continue;
                candidates.add(new Candidate(sample, chunkDistance));
            }
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
            int stride = chunkDistance < 48 ? 1 : chunkDistance < 128 ? 2
                    : chunkDistance < 256 ? 4 : 8;
            float size = 2.0f * stride;
            float y00 = sample.y();
            TerrainCache.SurfaceSample east = TerrainCache.getSurfaceSample(sample.x() + (int) size, sample.z());
            TerrainCache.SurfaceSample south = TerrainCache.getSurfaceSample(sample.x(), sample.z() + (int) size);
            TerrainCache.SurfaceSample diagonal = TerrainCache.getSurfaceSample(
                    sample.x() + (int) size, sample.z() + (int) size);
            float y10 = east == null ? y00 : east.y();
            float y01 = south == null ? y00 : south.y();
            float y11 = diagonal == null ? (y10 + y01) * 0.5f : diagonal.y();
            float[] color = colorFor(sample.blockId(), chunkDistance);
            TextureAtlasSprite sprite = topSprite(sample.blockId());
            addTerrainCell(builder, matrix, sample.x(), sample.z(), sample.x() + size,
                    sample.z() + size, y00 + 0.02f, y10 + 0.02f,
                    y11 + 0.02f, y01 + 0.02f, color, sprite);
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
            pass.bindTexture("Sampler2", client.gameRenderer.lightmap(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.drawIndexed(0, 0, drawState.indexCount(), 1);
        }
        mesh.close();
        vertexBuffer.rotate();
    }

    private static TextureAtlasSprite topSprite(String blockId) {
        return TOP_SPRITES.computeIfAbsent(blockId, id -> {
            try {
                Identifier identifier = Identifier.tryParse(id);
                if (identifier == null) return Minecraft.getInstance().getBlockRenderer().getBlockModelShaper()
                        .getBlockModel(Blocks.STONE.defaultBlockState()).particleIcon();
                Block block = BuiltInRegistries.BLOCK.get(identifier);
                BlockState state = block.defaultBlockState();
                BlockStateModel model = Minecraft.getInstance().getBlockRenderer()
                        .getBlockModelShaper().getBlockModel(state);
                List<BlockModelPart> parts = new ArrayList<>();
                model.collectParts(RandomSource.create(42L), parts);
                for (BlockModelPart part : parts) {
                    List<BakedQuad> quads = part.getQuads(Direction.UP);
                    if (!quads.isEmpty()) return quads.get(0).sprite();
                }
                return model.particleIcon();
            } catch (Throwable ignored) {
                return Minecraft.getInstance().getBlockRenderer().getBlockModelShaper()
                        .getBlockModel(Blocks.STONE.defaultBlockState()).particleIcon();
            }
        });
    }

    /**
     * Builds a height-following 3D cell with an atlas-textured top and edge
     * skirts. Each vertex uses Minecraft's block vertex format, block atlas,
     * full-bright distant lighting, and the selected block model's top sprite.
     */
    private static void addTerrainCell(BufferBuilder out, Matrix4fc matrix,
                                       float x0, float z0, float x1, float z1,
                                       float y00, float y10, float y11, float y01,
                                       float[] color, TextureAtlasSprite sprite) {
        float floor = Math.min(Math.min(y00, y10), Math.min(y11, y01)) - 8.0f;
        addTexturedQuad(out, matrix, x0, z0, x1, z1,
                y00, y10, y11, y01, color, sprite, 0, 1, 0);
        addTexturedQuad(out, matrix, x0, z0, x1, z0,
                y00, y10, floor, floor, color, sprite, 0, 0, -1);
        addTexturedQuad(out, matrix, x1, z0, x1, z1,
                y10, y11, floor, floor, color, sprite, 1, 0, 0);
        addTexturedQuad(out, matrix, x1, z1, x0, z1,
                y11, y01, floor, floor, color, sprite, 0, 0, 1);
        addTexturedQuad(out, matrix, x0, z1, x0, z0,
                y01, y00, floor, floor, color, sprite, -1, 0, 0);
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
        float r = 0.34f, g = 0.58f, b = 0.27f;
        if (block.contains("water") || block.contains("ice")) {
            r = 0.22f; g = 0.43f; b = 0.72f;
        } else if (block.contains("sand") || block.contains("sandstone")) {
            r = 0.72f; g = 0.65f; b = 0.42f;
        } else if (block.contains("snow") || block.contains("quartz")) {
            r = 0.78f; g = 0.80f; b = 0.79f;
        } else if (block.contains("stone") || block.contains("deepslate")) {
            r = 0.43f; g = 0.43f; b = 0.42f;
        } else if (block.contains("leaves") || block.contains("moss")) {
            r = 0.20f; g = 0.43f; b = 0.19f;
        }
        float fade = (float) Math.min(1.0, distance / 512.0);
        float fog = 0.48f + fade * 0.42f;
        return new float[]{r * fog + 0.48f * (1 - fog),
                g * fog + 0.55f * (1 - fog), b * fog + 0.62f * (1 - fog)};
    }
}
