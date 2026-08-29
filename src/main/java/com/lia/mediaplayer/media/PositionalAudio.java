package com.lia.mediaplayer.media;

/**
 * The arithmetic behind {@code AudioPlacement} — how far away turns into a gain, and
 * which way round turns into a pan.
 *
 * <p>Pure functions over plain numbers, and that is the point: the mod's positional audio
 * is two multiplications, and they are the part most likely to be wrong in a way nobody
 * notices until a speaker is loudest from behind. Kept free of {@code Vec3}, the camera
 * and everything else that needs a running game so they can be tested — see
 * {@code PositionalAudioTest}. {@link AudioGain} does the fetching and calls these.</p>
 *
 * <h2>What model this is</h2>
 *
 * <p>Deliberately the simple one: a linear ramp to silence at the radius, and a pan taken
 * from the horizontal angle between the camera's facing and the source. No inverse-square
 * law (it is unusable at close range and needs a reference distance nobody wants to
 * think about), no elevation, no occlusion. Vanilla attenuates its own sounds linearly
 * too, so a speaker built on this sits in the same world as the note block beside it.</p>
 */
public final class PositionalAudio {

    /**
     * How close a sound has to get before the panning starts collapsing back to the
     * centre, in blocks.
     *
     * <p>Standing on top of a source and hearing it hard left is the one thing a naive
     * pan gets obviously wrong: the direction is meaningless there — half a block of
     * movement swings it from one ear to the other — and it is unpleasant to listen to.
     * Inside this distance the pan is scaled down to nothing, which is what a real sound
     * source close to your head does anyway.</p>
     */
    private static final double NEAR_FIELD_BLOCKS = 1.5;

    private PositionalAudio() {
    }

    /**
     * The distance factor, {@code 0..1}: full volume at the source, silent at
     * {@code radius}, linear between.
     *
     * <p>A non-positive radius answers {@code 1} rather than dividing by zero — a
     * placement with no reach is treated as no attenuation at all, which is what
     * {@code AudioPlacement.screen()} means.</p>
     */
    public static float attenuation(double distance, double radius) {
        if (!(radius > 0.0)) {
            return 1.0f;
        }
        if (!(distance > 0.0)) {
            return 1.0f;
        }
        double factor = 1.0 - distance / radius;
        return (float) Math.max(0.0, Math.min(1.0, factor));
    }

    /**
     * Where to pan a sound that is {@code (dx, dz)} away horizontally while the camera
     * faces {@code yawDegrees}: {@code -1} full left, {@code +1} full right, {@code 0}
     * centred.
     *
     * <p>Minecraft's yaw is {@code 0} looking towards {@code +Z} and grows clockwise, so
     * the camera's forward vector is {@code (-sin y, cos y)} and the vector out of its
     * right ear is {@code (-cos y, -sin y)}. The pan is the offset, normalised, projected
     * onto that right vector — which is the sine of the angle between them, so a source
     * directly ahead <em>or directly behind</em> is centred. That front/back ambiguity is
     * real and is not a bug to fix here: stereo panning cannot express it, and resolving
     * it is what a proper HRTF renderer is for.</p>
     *
     * <p>Scaled down inside {@link #NEAR_FIELD_BLOCKS} — see that constant.</p>
     */
    public static float pan(double dx, double dz, float yawDegrees) {
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (!(distance > 0.0)) {
            return 0.0f;
        }
        double yaw = Math.toRadians(yawDegrees);
        double rightX = -Math.cos(yaw);
        double rightZ = -Math.sin(yaw);
        double projected = (dx * rightX + dz * rightZ) / distance;
        double nearField = Math.min(1.0, distance / NEAR_FIELD_BLOCKS);
        return (float) Math.max(-1.0, Math.min(1.0, projected * nearField));
    }
}
