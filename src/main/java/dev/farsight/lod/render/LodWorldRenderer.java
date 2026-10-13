package dev.farsight.lod.render;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import dev.farsight.lod.FarSightConfig;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Experimental Minecraft 26.3 render-pipeline integration.
 *
 * LOD terrain is drawn after opaque terrain/entities but before translucent
 * terrain, so water and other translucent surfaces can correctly appear over it.
 * The exact API still needs a full build and in-game validation.
 */
public final class LodWorldRenderer {
    private static final SurfaceTileCache CACHE = new SurfaceTileCache();
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("farsight_lod", "pipeline/lod_terrain"))
                    .withCull(false)
                    .build()
    );
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1, 1, 1, 1);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final StagedVertexBuffer BUFFER =
            new StagedVertexBuffer(() -> "FarSight LOD terrain", RenderType.SMALL_BUFFER_SIZE);

    private LodWorldRenderer() {}

    public static SurfaceTileCache cache() {
        return CACHE;
    }

    public static void register() {
        LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(LodWorldRenderer::render);
    }

    private static void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || !FarSightConfig.get().enabled) return;

        int configuredDistance = Math.min(FarSightConfig.get().chunkDistance, 2048);
        int vanillaDistance = Math.max(2, client.options.getEffectiveRenderDistance());
        Vec3 camera = context.levelState().cameraRenderState.pos;
        double vanillaBoundary = (vanillaDistance + 1) * 16.0;
        double maxDistance = configuredDistance * 16.0;

        // Filter once per frame: avoid traversing the whole cache twice and keep
        // the staged upload limited to meshes that can actually be drawn.
        var visibleMeshes = new java.util.ArrayList<LodMesh>();
        for (LodMesh mesh : CACHE.meshesSnapshot()) {
            if (isVisible(mesh, camera.x, camera.z, vanillaBoundary, maxDistance)) {
                visibleMeshes.add(mesh);
            }
        }
        if (visibleMeshes.isEmpty()) return;

        VertexFormat format = PIPELINE.getVertexFormatBinding(0);
        if (format == null) return;
        PrimitiveTopology topology = PIPELINE.getPrimitiveTopology();
        StagedVertexBuffer.Draw draw = BUFFER.appendDraw(
                format, topology,
                topology == PrimitiveTopology.QUADS
                        ? RenderSystem.getProjectionType().vertexSorting()
                        : null
        );

        PoseStack poses = context.poseStack();
        poses.pushPose();
        poses.translate(-camera.x, -camera.y, -camera.z);
        Matrix4fc matrix = poses.last().pose();
        VertexConsumer out = BUFFER.getVertexBuilder(draw);

        for (LodMesh mesh : visibleMeshes) {
            // DEBUG_FILLED_SNIPPET uses quads. Emit each triangle as a
            // degenerate quad (last vertex duplicated) for compatibility.
            var vertices = mesh.vertices();
            for (int i = 0; i + 2 < vertices.size(); i += 3) {
                emit(out, matrix, vertices.get(i));
                emit(out, matrix, vertices.get(i + 1));
                emit(out, matrix, vertices.get(i + 2));
                emit(out, matrix, vertices.get(i + 2));
            }
        }
        poses.popPose();

        BUFFER.upload();
        StagedVertexBuffer.ExecuteInfo info = BUFFER.getExecuteInfo(draw);
        if (info != null) draw(info);
        BUFFER.endFrame();
    }


    private static boolean isVisible(LodMesh mesh, double cameraX, double cameraZ,
                                     double vanillaBoundary, double maxDistance) {
        if (mesh.isEmpty()) return false;
        double cx = mesh.chunkX() * 16.0 + 8.0;
        double cz = mesh.chunkZ() * 16.0 + 8.0;
        double dx = cx - cameraX;
        double dz = cz - cameraZ;
        double distanceSquared = dx * dx + dz * dz;
        return distanceSquared > vanillaBoundary * vanillaBoundary
                && distanceSquared <= maxDistance * maxDistance;
    }

    private static void emit(VertexConsumer out, Matrix4fc matrix, LodVertex v) {
        int rgb = v.rgb();
        out.addVertex(matrix, v.x(), v.y(), v.z()).setColor(
                ((rgb >> 16) & 255) / 255.0f,
                ((rgb >> 8) & 255) / 255.0f,
                (rgb & 255) / 255.0f,
                1.0f
        );
    }

    private static void draw(StagedVertexBuffer.ExecuteInfo info) {
        Minecraft client = Minecraft.getInstance();
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrixCopy(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);
        RenderTarget target = client.gameRenderer.mainRenderTarget();
        GpuTextureView color = target.getColorTextureView();
        if (color == null) return;

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "FarSight LOD terrain", color, Optional.empty(),
                target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINE));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynamicTransforms);
            pass.setVertexBuffer(0, info.vertexBuffer().slice());
            pass.setIndexBuffer(info.indexBuffer(), info.indexType());
            pass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
        }
    }
}
