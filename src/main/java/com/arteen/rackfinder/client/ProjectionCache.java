package com.arteen.rackfinder.client;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Keeps the projection matrix and the camera basis of the world pass so the highlight overlay can
 * project world positions to screen coordinates (the GUI pass uses a different matrix).
 */
public final class ProjectionCache {
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Vector3f EYE = new Vector3f();
    private static final Vector3f LOOK = new Vector3f(0.0F, 0.0F, -1.0F);
    private static final Vector3f UP = new Vector3f(0.0F, 1.0F, 0.0F);
    private static boolean valid;

    private ProjectionCache() {
    }

    public static void capture(final RenderLevelStageEvent event) {
        PROJECTION.set(event.getProjectionMatrix());
        final Camera camera = event.getCamera();
        final Vec3 position = camera.getPosition();
        EYE.set((float) position.x, (float) position.y, (float) position.z);
        LOOK.set(camera.getLookVector());
        UP.set(camera.getUpVector());
        valid = true;
    }

    public static Matrix4f projection() {
        return new Matrix4f(PROJECTION);
    }

    public static Matrix4f view() {
        return new Matrix4f().lookAt(EYE, new Vector3f(EYE).add(LOOK), UP);
    }

    public static Matrix4f modelViewProjection() {
        return projection().mul(view());
    }

    public static boolean valid() {
        return valid;
    }
}
