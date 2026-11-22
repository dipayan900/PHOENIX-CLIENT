package dev.brutalxd.distantterrain;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
    private static final RenderPipeline TERRAIN_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(
                            BrutalDistantTerrainClient.MOD_ID, "pipeline/distant_terrain"))
                    .build());
    private static final ByteBufferBuilder ALLOCATOR =
            new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1, 1, 1, 1);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final int MAX_TILES_PER_FRAME = 8192;
    private static MappableRingBuffer vertexBuffer;
    private record Candidate(TerrainCache.SurfaceSample sample, double distance) {}

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
        BufferBuilder builder = new BufferBuilder(
                ALLOCATOR, TERRAIN_PIPELINE.getVertexFormatMode(),
                TERRAIN_PIPELINE.getVertexFormat());
        Matrix4fc matrix = context.matrices().last().pose();
        int tiles = 0;

        int radiusBlocks = TerrainSettings.distanceChunks * 16;
        // Select the nearest visible tiles first. The previous queue-order cutoff
        // could spend the entire per-frame budget on old terrain at the edge of
        // the cache, leaving holes around the player.
        List<Candidate> candidates = new ArrayList<>();
        for (TerrainCache.SurfaceSample sample : TerrainCache.getSurfaceSamplesNear(
                (int) camera.x, (int) camera.z, radiusBlocks)) {
            double dx = sample.x() - camera.x;
            double dz = sample.z() - camera.z;
            double chunkDistance = Math.sqrt(dx * dx + dz * dz) / 16.0;
            if (chunkDistance > TerrainSettings.distanceChunks) continue;
            int stride = chunkDistance < 48 ? 1 : chunkDistance < 128 ? 2
                    : chunkDistance < 256 ? 4 : 8;
            if (Math.floorMod(sample.x(), stride * 4) != 0
                    || Math.floorMod(sample.z(), stride * 4) != 0) continue;
            candidates.add(new Candidate(sample, chunkDistance));
        }
        candidates.sort(Comparator.comparingDouble(Candidate::distance));

        for (Candidate candidate : candidates) {
            TerrainCache.SurfaceSample sample = candidate.sample();
            double chunkDistance = candidate.distance();
            int stride = chunkDistance < 48 ? 1 : chunkDistance < 128 ? 2
                    : chunkDistance < 256 ? 4 : 8;
            float size = 4.0f * stride;
            float y00 = sample.y();
            TerrainCache.SurfaceSample east = TerrainCache.getSurfaceSample(sample.x() + (int) size, sample.z());
            TerrainCache.SurfaceSample south = TerrainCache.getSurfaceSample(sample.x(), sample.z() + (int) size);
            TerrainCache.SurfaceSample diagonal = TerrainCache.getSurfaceSample(
                    sample.x() + (int) size, sample.z() + (int) size);
            float y10 = east == null ? y00 : east.y();
            float y01 = south == null ? y00 : south.y();
            float y11 = diagonal == null ? (y10 + y01) * 0.5f : diagonal.y();
            float[] color = colorFor(sample.blockId(), chunkDistance);
            addQuad(builder, matrix, sample.x(), sample.z(), sample.x() + size,
                    sample.z() + size, y00 + 0.02f, y10 + 0.02f,
                    y11 + 0.02f, y01 + 0.02f, color);
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
            pass.drawIndexed(0, 0, drawState.indexCount(), 1);
        }
        mesh.close();
        vertexBuffer.rotate();
    }

    private static void addQuad(BufferBuilder out, Matrix4fc matrix,
                                float x0, float z0, float x1, float z1,
                                float y00, float y10, float y11, float y01,
                                float[] color) {
        out.addVertex(matrix, x0, y00, z0).setColor(color[0], color[1], color[2], 1);
        out.addVertex(matrix, x0, y01, z1).setColor(color[0], color[1], color[2], 1);
        out.addVertex(matrix, x1, y11, z1).setColor(color[0], color[1], color[2], 1);
        out.addVertex(matrix, x1, y10, z0).setColor(color[0], color[1], color[2], 1);
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
