package dev.colin.aster;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import java.util.LinkedHashMap;

/** The exact world projection uploaded by Minecraft, including view bob and hurt/portal effects. */
public final class WorldCameraTransforms {
    private static final Matrix4f worldProjection = new Matrix4f();
    private static boolean captured;
    private static int captures;

    public static void capture(Matrix4fc projection) {
        worldProjection.set(projection);
        captured = true;
        captures++;
    }

    public static Matrix4fc projection(CameraRenderState camera) {
        // A hidden comparison can reproduce the previous depth reconstruction.
        boolean legacy = AutomatedVerification.HIDDEN
            && (AsterRenderer.INSTANCE.verificationLightingFlags() & 16777216) != 0;
        return captured && !legacy ? worldProjection : camera.projectionMatrix;
    }

    public static Matrix4f captureAndJitter(Matrix4f projection) {
        var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        var actual = AsterRenderer.INSTANCE.temporalAa.jitter(projection,target.width,target.height);
        capture(actual);
        return actual;
    }

    public static Object evidence(CameraRenderState camera) {
        var result = new LinkedHashMap<String,Object>();
        float[] actual = worldProjection.get(new float[16]);
        float[] base = camera.projectionMatrix.get(new float[16]);
        float difference = 0;
        for (int i=0;i<16;i++) difference = Math.max(difference,Math.abs(actual[i]-base[i]));
        result.put("captured",captured);
        result.put("captures",captures);
        result.put("nativeProjectionDelta",difference);
        result.put("bob",camera.entityRenderState.bob);
        result.put("walkDistance",camera.entityRenderState.backwardsInterpolatedWalkDistance);
        result.put("bobViewEnabled",Minecraft.getInstance().options.bobView().get());
        var player = Minecraft.getInstance().player;
        result.put("onGround",player != null && player.onGround());
        return result;
    }

    public static void clear() { captured = false; }
}
