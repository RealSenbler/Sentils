package net.senbler.sentils.client.waypoints;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
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
import net.minecraft.client.renderer.state.level.CameraEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

/**
 * Draws a box plus a vertical beacon beam at fixed world coordinates,
 * visible through walls, for a set duration.
 *
 * Usage:
 *   1. Call WaypointRenderer.init() once from your existing ClientModInitializer.
 *   2. Call WaypointRenderer.showWaypoint(x, y, z, durationMillis) whenever you want one.
 *
 * Besides that one waypoint, a set of markers can be shown with showMarkers. They are drawn the
 * same way but never get a tracer, stay until clearMarkers, and ignore the waypoint settings.
 *
 * A feature can also show one target with showTarget. Like the markers it stays until clearTarget
 * and ignores the waypoint settings, and it can have a tracer of its own.
 */
public final class WaypointRenderer {

    private WaypointRenderer() {}

    // Beam settings
    private static final float BEAM_INNER_RADIUS = 0.2f;   // vanilla inner beam
    private static final float BEAM_OUTER_RADIUS = 0.25f;  // vanilla glow layer
    private static final float BEAM_BOTTOM = -64f;
    private static final float BEAM_TOP = 320f;

    // Half the tracer's width for each block it is from the camera, which keeps it a thin line of
    // about the same width on screen along its whole length.
    private static final double TRACER_HALF_WIDTH = 0.0024;

    private static RenderPipeline pipeline;
    private static StagedVertexBuffer stagedBuffer;
    private static boolean initialized = false;

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

    private static volatile Waypoint requested;
    private static Waypoint frameWaypoint;
    private static volatile List<Waypoint> markers = List.of();
    private static List<Waypoint> frameMarkers = List.of();
    private static volatile Waypoint target;
    private static volatile boolean targetTracer;
    private static Waypoint frameTarget;

    private record Waypoint(int x, int y, int z, long expiresAtMillis,
                            float r, float g, float b, float a, boolean beacon) {}

    /** Call exactly once, from your existing ClientModInitializer#onInitializeClient. */
    public static void init() {
        if (initialized) return;
        initialized = true;

        pipeline = RenderPipelines.register(
                RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                        .withLocation(Identifier.fromNamespaceAndPath("sentils", "pipeline/waypoint_through_walls"))
                        .withDepthStencilState(Optional.empty())
                        .build());

        stagedBuffer = new StagedVertexBuffer(
                () -> "sentils waypoint buffer", RenderType.SMALL_BUFFER_SIZE);

        LevelExtractionEvents.END_EXTRACTION.register(WaypointRenderer::extract);
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(WaypointRenderer::render);
    }

    /** Green box + beacon at the block (x, y, z) for durationMillis. Replaces any current one. */
    public static void showWaypoint(int x, int y, int z, long durationMillis) {
        showWaypoint(x, y, z, durationMillis, 0f, 1f, 0f, 0.5f, true);
    }

    public static void showWaypoint(int x, int y, int z, long durationMillis, boolean beacon) {
        showWaypoint(x, y, z, durationMillis, 0f, 1f, 0f, 0.5f, beacon);
    }

    public static void showWaypoint(int x, int y, int z, long durationMillis,
                                    float r, float g, float b, float a, boolean beacon) {
        requested = new Waypoint(x, y, z, System.currentTimeMillis() + durationMillis, r, g, b, a, beacon);
    }

    public static void clearWaypoint() {
        requested = null;
    }

    /** Box + beacon in the given color at each {x, y, z} block, until clearMarkers. Replaces any current ones. */
    public static void showMarkers(int[][] positions, float r, float g, float b) {
        List<Waypoint> list = new ArrayList<>();
        for (int[] position : positions) {
            list.add(new Waypoint(position[0], position[1], position[2], Long.MAX_VALUE, r, g, b, 0.5f, true));
        }
        markers = List.copyOf(list);
    }

    public static void clearMarkers() {
        markers = List.of();
    }

    /** Box in the given color at the block (x, y, z), with no beacon, until clearTarget. Replaces any current one. */
    public static void showTarget(int x, int y, int z, float r, float g, float b, boolean tracer) {
        targetTracer = tracer;
        target = new Waypoint(x, y, z, Long.MAX_VALUE, r, g, b, 0.5f, false);
    }

    public static void clearTarget() {
        target = null;
    }

    // --- Extraction ---

    private static void extract(LevelExtractionContext context) {
        frameMarkers = markers;
        frameTarget = target;
        if (!WaypointSettings.renderWaypoints) {
            frameWaypoint = null;
            return;
        }
        Waypoint wp = requested;
        if (wp != null && System.currentTimeMillis() >= wp.expiresAtMillis()) {
            requested = null;
            wp = null;
        }
        frameWaypoint = wp;
    }

    // --- Rendering ---

    private static void render(LevelRenderContext context) {
        Waypoint wp = frameWaypoint;
        List<Waypoint> marked = frameMarkers;
        Waypoint aim = frameTarget;
        if ((wp == null && aim == null && marked.isEmpty()) || !initialized) return;

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

        for (Waypoint marker : marked) {
            addWaypoint(pose, builder, marker);
        }
        if (wp != null) addWaypoint(pose, builder, wp);
        if (aim != null) addWaypoint(pose, builder, aim);

        CameraRenderState camera = context.levelState().cameraRenderState;
        if (wp != null && WaypointSettings.renderTracer) addTracerTo(pose, builder, camera, wp);
        if (aim != null && targetTracer) addTracerTo(pose, builder, camera, aim);

        poseStack.popPose();

        stagedBuffer.upload();

        StagedVertexBuffer.ExecuteInfo info = stagedBuffer.getExecuteInfo(draw);
        if (info != null) {
            submit(Minecraft.getInstance(), info);
        }

        stagedBuffer.endFrame();
    }

    /** A tracer from the crosshair, a block in front of the camera, to the middle of the waypoint's block. */
    private static void addTracerTo(Matrix4fc pose, VertexConsumer builder, CameraRenderState camera, Waypoint wp) {
        Vec3 cam = camera.pos;
        Vector3f ahead = underCrosshair(camera);
        Vec3 start = cam.add(ahead.x, ahead.y, ahead.z);
        Vec3 end = new Vec3(wp.x() + 0.5, wp.y() + 0.5, wp.z() + 0.5);
        addTracer(pose, builder, cam, start, end, TRACER_HALF_WIDTH, wp.r(), wp.g(), wp.b(), 0.8f);
    }

    /**
     * Where, from the camera, the spot a block ahead under the crosshair is. With view bobbing on,
     * the game sways everything in the world as the player walks, but not the crosshair, so the
     * spot straight ahead of the camera drifts off it. This undoes the sway, which is worked out
     * as GameRenderer.bobView does, so that the spot stays under the crosshair.
     */
    private static Vector3f underCrosshair(CameraRenderState camera) {
        Vector3f ahead = new Vector3f(0, 0, -1);
        CameraEntityRenderState entity = camera.entityRenderState;
        if (Minecraft.getInstance().options.bobView().get() && entity != null && entity.isPlayer) {
            float walk = entity.backwardsInterpolatedWalkDistance * Mth.PI;
            float bob = entity.bob;
            Matrix4f sway = new Matrix4f()
                    .translate(Mth.sin(walk) * bob * 0.5f, -Math.abs(Mth.cos(walk) * bob), 0)
                    .rotateZ(Mth.sin(walk) * bob * 3 * Mth.DEG_TO_RAD)
                    .rotateX(Math.abs(Mth.cos(walk - 0.2f) * bob) * 5 * Mth.DEG_TO_RAD);
            sway.invert().transformPosition(ahead);
        }
        // From the camera's own way of facing to the world's.
        return camera.orientation.transform(ahead);
    }

    /** The box, and the beacon if the waypoint has one. */
    private static void addWaypoint(Matrix4fc pose, VertexConsumer builder, Waypoint wp) {
        // The box
        float e = 0.02f;
        addBox(pose, builder,
                wp.x() - e, wp.y() - e, wp.z() - e,
                wp.x() + 1 + e, wp.y() + 1 + e, wp.z() + 1 + e,
                wp.r(), wp.g(), wp.b(), wp.a());

        // The beacon: a thin column centered on the block, rising from its top
        if (wp.beacon()) {
            float cx = wp.x() + 0.5f;
            float cz = wp.z() + 0.5f;
            long ms = System.currentTimeMillis();
            // Slow spin, like vanilla
            float angle = (ms % 36000L) / 1000f * 0.6f;
            // Gentle pulse
            float pulse = 0.85f + 0.15f * (float) Math.sin(ms / 400.0);

            // Outer glow (fainter, spins the opposite way)
            addColumn(pose, builder, cx, cz, BEAM_OUTER_RADIUS, -angle * 0.5f,
                    BEAM_BOTTOM, BEAM_TOP,
                    wp.r(), wp.g(), wp.b(), 0.25f * pulse);
            // Inner core
            addColumn(pose, builder, cx, cz, BEAM_INNER_RADIUS, angle,
                    BEAM_BOTTOM, BEAM_TOP,
                    wp.r(), wp.g(), wp.b(), 0.6f * pulse);
        }
    }

    private static void submit(Minecraft client, StagedVertexBuffer.ExecuteInfo info) {
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrixCopy(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        RenderTarget target = client.gameRenderer.mainRenderTarget();
        GpuTextureView color = target.getColorTextureView();
        assert color != null;

        try (RenderPass pass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "sentils waypoint rendering",
                        color, Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, info.vertexBuffer().slice());
            pass.setIndexBuffer(info.indexBuffer(), info.indexType());
            pass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
        }
    }

    private static void addBox(Matrix4fc m, VertexConsumer b,
                               float x0, float y0, float z0, float x1, float y1, float z1,
                               float r, float g, float bl, float a) {
        // South
        v(m, b, x0, y0, z1, r, g, bl, a); v(m, b, x1, y0, z1, r, g, bl, a);
        v(m, b, x1, y1, z1, r, g, bl, a); v(m, b, x0, y1, z1, r, g, bl, a);
        // North
        v(m, b, x1, y0, z0, r, g, bl, a); v(m, b, x0, y0, z0, r, g, bl, a);
        v(m, b, x0, y1, z0, r, g, bl, a); v(m, b, x1, y1, z0, r, g, bl, a);
        // West
        v(m, b, x0, y0, z0, r, g, bl, a); v(m, b, x0, y0, z1, r, g, bl, a);
        v(m, b, x0, y1, z1, r, g, bl, a); v(m, b, x0, y1, z0, r, g, bl, a);
        // East
        v(m, b, x1, y0, z1, r, g, bl, a); v(m, b, x1, y0, z0, r, g, bl, a);
        v(m, b, x1, y1, z0, r, g, bl, a); v(m, b, x1, y1, z1, r, g, bl, a);
        // Top
        v(m, b, x0, y1, z1, r, g, bl, a); v(m, b, x1, y1, z1, r, g, bl, a);
        v(m, b, x1, y1, z0, r, g, bl, a); v(m, b, x0, y1, z0, r, g, bl, a);
        // Bottom
        v(m, b, x0, y0, z0, r, g, bl, a); v(m, b, x1, y0, z0, r, g, bl, a);
        v(m, b, x1, y0, z1, r, g, bl, a); v(m, b, x0, y0, z1, r, g, bl, a);
    }

    /**
     * A flat ribbon from start to end that turns to face the camera. Emitted double-sided.
     * It widens with distance from the camera, by halfWidth each side per block.
     */
    private static void addTracer(Matrix4fc m, VertexConsumer b, Vec3 cam, Vec3 start, Vec3 end,
                                  double halfWidth, float r, float g, float bl, float a) {
        Vec3 dir = end.subtract(start);
        if (dir.lengthSqr() < 1.0e-4) return;

        Vec3 mid = start.add(end).scale(0.5);
        Vec3 side = dir.cross(mid.subtract(cam));
        if (side.lengthSqr() < 1.0e-8) return; // looking straight down the line
        side = side.normalize();
        Vec3 near = side.scale(halfWidth * start.distanceTo(cam));
        Vec3 far = side.scale(halfWidth * end.distanceTo(cam));

        float ax = (float) (start.x + near.x), ay = (float) (start.y + near.y), az = (float) (start.z + near.z);
        float bx = (float) (start.x - near.x), by = (float) (start.y - near.y), bz = (float) (start.z - near.z);
        float cx = (float) (end.x - far.x),    cy = (float) (end.y - far.y),    cz = (float) (end.z - far.z);
        float dx = (float) (end.x + far.x),    dy = (float) (end.y + far.y),    dz = (float) (end.z + far.z);

        // Front
        v(m, b, ax, ay, az, r, g, bl, a); v(m, b, bx, by, bz, r, g, bl, a);
        v(m, b, cx, cy, cz, r, g, bl, a); v(m, b, dx, dy, dz, r, g, bl, a);
        // Back (reversed winding)
        v(m, b, dx, dy, dz, r, g, bl, a); v(m, b, cx, cy, cz, r, g, bl, a);
        v(m, b, bx, by, bz, r, g, bl, a); v(m, b, ax, ay, az, r, g, bl, a);
    }

    /** A vertical square column rotated by `angle` radians. Each side is emitted twice so it shows from both sides. */
    private static void addColumn(Matrix4fc m, VertexConsumer b, float cx, float cz,
                                  float radius, float angle, float y0, float y1,
                                  float r, float g, float bl, float a) {
        float[] px = new float[4];
        float[] pz = new float[4];
        for (int i = 0; i < 4; i++) {
            double t = angle + Math.PI / 4.0 + i * (Math.PI / 2.0);
            px[i] = cx + (float) Math.cos(t) * radius * 1.4142f;
            pz[i] = cz + (float) Math.sin(t) * radius * 1.4142f;
        }
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            // Front
            v(m, b, px[i], y0, pz[i], r, g, bl, a); v(m, b, px[j], y0, pz[j], r, g, bl, a);
            v(m, b, px[j], y1, pz[j], r, g, bl, a); v(m, b, px[i], y1, pz[i], r, g, bl, a);
            // Back (reversed winding)
            v(m, b, px[i], y1, pz[i], r, g, bl, a); v(m, b, px[j], y1, pz[j], r, g, bl, a);
            v(m, b, px[j], y0, pz[j], r, g, bl, a); v(m, b, px[i], y0, pz[i], r, g, bl, a);
        }
    }

    private static void v(Matrix4fc m, VertexConsumer b, float x, float y, float z,
                          float r, float g, float bl, float a) {
        b.addVertex(m, x, y, z).setColor(r, g, bl, a);
    }

    /** Call from your GameRenderer#close mixin to free GPU resources. */
    public static void close() {
        if (stagedBuffer != null) stagedBuffer.close();
    }
}