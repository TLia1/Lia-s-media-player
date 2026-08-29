package com.lia.mediaplayer.media;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The mod's single point of contact with the game camera — who is listening, and which
 * way they are facing.
 *
 * <p>A seam of the same kind as the ones in {@code gui}, and for the same reason:
 * {@link Camera}'s two accessors have been renamed twice across the versions this mod
 * builds for, and {@code GameRenderer}'s getter once more. Three guards in one small file
 * beats three guards at every call site — and there will be more call sites as soon as
 * anything else in the mod needs to know where the player is.</p>
 *
 * <p>The <b>camera</b> rather than the player entity, because that is what the game's own
 * sound engine listens from: in third person a sound should get louder as the camera
 * approaches it, and in spectator there is no player entity to ask.</p>
 *
 * <p>Client thread only. Everything answers a neutral value when there is no world —
 * a main-menu tick must not throw its way into the mixer.</p>
 */
public final class AudioListener {

    private AudioListener() {
    }

    /**
     * Where the camera is, or {@code null} when there is no world to be in (the main
     * menu, or the moment between worlds). A positional sound with no listener is left
     * at whatever gain it had rather than being silenced: the alternative is a stutter
     * every time the player crosses a loading screen.
     */
    @Nullable
    public static Vec3 position() {
        Camera camera = camera();
        if (camera == null) {
            return null;
        }
        //? if <1.21.11 {
        return camera.getPosition();
        //?} else {
        /*return camera.position();
        *///?}
    }

    /**
     * Which way the camera faces, in degrees, {@code 0} looking towards {@code +Z} and
     * growing clockwise — the convention {@link PositionalAudio#pan} expects.
     */
    public static float yawDegrees() {
        Camera camera = camera();
        if (camera == null) {
            return 0.0f;
        }
        //? if <1.21.11 {
        return camera.getYRot();
        //?} else {
        /*return camera.yRot();
        *///?}
    }

    @Nullable
    private static Camera camera() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        //? if <26.2 {
        return minecraft.gameRenderer.getMainCamera();
        //?} else {
        /*return minecraft.gameRenderer.mainCamera();
        *///?}
    }
}
