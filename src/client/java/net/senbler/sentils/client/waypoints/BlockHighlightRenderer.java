package net.senbler.sentils.client.waypoints;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

/**
 * Covers blocks in the world with a see-through color. Unlike the waypoints this is not drawn
 * through walls: a block is only colored where it can be seen.
 *
 * Usage:
 *   1. Call BlockHighlightRenderer.init() once from your ClientModInitializer.
 *   2. Call show with the blocks to color, and clear to stop.
 */
public final class BlockHighlightRenderer {

    private BlockHighlightRenderer() {}

    // How far outside the block its color is drawn, so the two do not flicker against each other.
    private static final float OUTSET = 0.005f;

    private static RenderPipeline pipeline;
    private static StagedVertexBuffer stagedBuffer;
    private static boolean initialized = false;

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

    /** A block to color, and the sides of it to draw: the bits from faceBit for each. */
    public record Block(int x, int y, int z, int faces) {}

    private record Highlight(List<Block> blocks, float r, float g, float b, float a) {}

    private static volatile Highlight requested;
    private static Highlight frameHighlight;

    /** The bit that stands for a side of a block in Block.faces. */
    public static int faceBit(Direction side) {
        return 1 << side.ordinal();
    }

    /** Call exactly once, from your ClientModInitializer#onInitializeClient. */
    public static void init() {
        if (initialized) return;
        initialized = true;

        // Tested against the world's depth the way the game's own drawing is, but leaving it as it was.
        pipeline = RenderPipelines.register(
                RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                        .withLocation(Identifier.fromNamespaceAndPath("sentils", "pipeline/block_highlight"))
                        .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false))
                        .build());

        stagedBuffer = new StagedVertexBuffer(
                () -> "sentils block highlight buffer", RenderType.SMALL_BUFFER_SIZE);

        LevelExtractionEvents.END_EXTRACTION.register(BlockHighlightRenderer::extract);
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(BlockHighlightRenderer::render);
    }

    /** Colors the given blocks until clear or the next show. */
    public static void show(List<Block> blocks, float r, float g, float b, float a) {
        requested = blocks.isEmpty() ? null : new Highlight(List.copyOf(blocks), r, g, b, a);
    }

    public static void clear() {
        requested = null;
    }

    private static void extract(LevelExtractionContext context) {
        frameHighlight = requested;
    }

    private static void render(LevelRenderContext context) {
        Highlight highlight = frameHighlight;
        if (highlight == null || !initialized) return;

        VertexFormat format = pipeline.getVertexFormatBinding(0);
        assert format != null;

        PrimitiveTopology topology = pipeline.getPrimitiveTopology();
        StagedVertexBuffer.Draw draw = stagedBuffer.appendDraw(
                format,
                topology,
                topology == PrimitiveTopology.QUADS ? RenderSystem.getProjectionType().vertexSorting() : null);

        PoseStack poseStack = context.poseStack();
        Vec3 cam = context.levelState().cameraRenderState.pos;

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);

        Matrix4fc pose = poseStack.last().pose();
        VertexConsumer builder = stagedBuffer.getVertexBuilder(draw);
        for (Block block : highlight.blocks()) {
            addFaces(pose, builder, block, highlight.r(), highlight.g(), highlight.b(), highlight.a());
        }

        poseStack.popPose();

        stagedBuffer.upload();

        StagedVertexBuffer.ExecuteInfo info = stagedBuffer.getExecuteInfo(draw);
        if (info != null) {
            submit(Minecraft.getInstance(), info);
        }

        stagedBuffer.endFrame();
    }

    private static void submit(Minecraft client, StagedVertexBuffer.ExecuteInfo info) {
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrixCopy(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        RenderTarget target = client.gameRenderer.mainRenderTarget();
        GpuTextureView color = target.getColorTextureView();
        assert color != null;

        try (RenderPass pass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "sentils block highlight rendering",
                        color, Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, info.vertexBuffer().slice());
            pass.setIndexBuffer(info.indexBuffer(), info.indexType());
            pass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
        }
    }

    /** The sides of the block that are to be drawn, each facing outwards. */
    private static void addFaces(Matrix4fc m, VertexConsumer b, Block block, float r, float g, float bl, float a) {
        float x0 = block.x() - OUTSET, y0 = block.y() - OUTSET, z0 = block.z() - OUTSET;
        float x1 = block.x() + 1 + OUTSET, y1 = block.y() + 1 + OUTSET, z1 = block.z() + 1 + OUTSET;
        int faces = block.faces();

        if ((faces & faceBit(Direction.SOUTH)) != 0) {
            v(m, b, x0, y0, z1, r, g, bl, a); v(m, b, x1, y0, z1, r, g, bl, a);
            v(m, b, x1, y1, z1, r, g, bl, a); v(m, b, x0, y1, z1, r, g, bl, a);
        }
        if ((faces & faceBit(Direction.NORTH)) != 0) {
            v(m, b, x1, y0, z0, r, g, bl, a); v(m, b, x0, y0, z0, r, g, bl, a);
            v(m, b, x0, y1, z0, r, g, bl, a); v(m, b, x1, y1, z0, r, g, bl, a);
        }
        if ((faces & faceBit(Direction.WEST)) != 0) {
            v(m, b, x0, y0, z0, r, g, bl, a); v(m, b, x0, y0, z1, r, g, bl, a);
            v(m, b, x0, y1, z1, r, g, bl, a); v(m, b, x0, y1, z0, r, g, bl, a);
        }
        if ((faces & faceBit(Direction.EAST)) != 0) {
            v(m, b, x1, y0, z1, r, g, bl, a); v(m, b, x1, y0, z0, r, g, bl, a);
            v(m, b, x1, y1, z0, r, g, bl, a); v(m, b, x1, y1, z1, r, g, bl, a);
        }
        if ((faces & faceBit(Direction.UP)) != 0) {
            v(m, b, x0, y1, z1, r, g, bl, a); v(m, b, x1, y1, z1, r, g, bl, a);
            v(m, b, x1, y1, z0, r, g, bl, a); v(m, b, x0, y1, z0, r, g, bl, a);
        }
        if ((faces & faceBit(Direction.DOWN)) != 0) {
            v(m, b, x0, y0, z0, r, g, bl, a); v(m, b, x1, y0, z0, r, g, bl, a);
            v(m, b, x1, y0, z1, r, g, bl, a); v(m, b, x0, y0, z1, r, g, bl, a);
        }
    }

    private static void v(Matrix4fc m, VertexConsumer b, float x, float y, float z,
                          float r, float g, float bl, float a) {
        b.addVertex(m, x, y, z).setColor(r, g, bl, a);
    }
}
