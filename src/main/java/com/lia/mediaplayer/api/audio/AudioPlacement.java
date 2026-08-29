/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.audio;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Where a sound comes from. A {@code null} placement means "nowhere" — 2D at full
 * volume, which is what every sound the mod plays did before this existed.
 *
 * <h2>What "positional" means here, honestly</h2>
 *
 * <p>This is <b>2.5D</b>, and the mod says so rather than letting an addon author find
 * out. The engine writes PCM to a {@code javax.sound.sampled} line, so what it can do to
 * a sound is scale it and pan it:</p>
 *
 * <ul>
 *   <li><b>Distance attenuation</b> — a linear ramp from full volume at the source to
 *       silence at {@link #radius()}, recomputed every client tick.</li>
 *   <li><b>Stereo panning</b> — left/right from the angle between where you are looking
 *       and where the sound is, and only where the audio device exposes a pan control
 *       (most do; some exclusive-mode outputs do not, and there the sound is simply
 *       centred).</li>
 * </ul>
 *
 * <p>What it is <b>not</b>: there is no HRTF, no elevation, no occlusion by blocks, no
 * reverb, and no Doppler. A sound behind you and a sound in front of you are mixed the
 * same way, and a wall between you and a speaker changes nothing. Real 3D would mean
 * feeding Minecraft's own OpenAL engine, which does not accept arbitrary streamed PCM
 * without a custom sound instance and stream — a separate project, deliberately not
 * attempted here. Design for what is documented above and an addon will sound the same
 * on every version this mod ships for.</p>
 *
 * <p>The listener is the <b>camera</b>, not the player entity, so third person and
 * spectator behave the way the rest of the game's sound does.</p>
 *
 * <p>Placements are immutable values and safe to share; {@link #position()} reads live
 * world state and is <b>render/client thread only</b>.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 3.1.0
 */
public sealed interface AudioPlacement {

    /**
     * How far away the sound fades to silence, in blocks. {@code 0} for
     * {@link #screen()}, which does not fade at all.
     */
    double radius();

    /**
     * Where the sound is right now, or {@code null} when it has no place in the world.
     *
     * <p>Two different {@code null}s, told apart by {@link #isPositional()}: a
     * {@link #screen()} placement has no position because it is not in the world, while a
     * {@link #entity(Entity, double)} placement whose entity has been removed or unloaded
     * has lost its position — that one is treated as silent until the entity comes back,
     * because a sound whose source is not there should not be heard at full volume in the
     * middle of your head.</p>
     *
     * <p><b>Client thread only.</b></p>
     */
    @Nullable
    Vec3 position();

    /** Whether distance and direction apply at all. {@code false} only for {@link #screen()}. */
    default boolean isPositional() {
        return true;
    }

    // ------------------------------------------------------------------
    // Factories
    // ------------------------------------------------------------------

    /**
     * A fixed point in the world — a speaker block, a cinema screen, a campfire.
     *
     * <p>The position is not tied to a dimension: the mod cannot tell whether the
     * {@link Vec3} you handed it belongs to the world the player is standing in, so an
     * addon that plays into one dimension is responsible for muting or closing the sound
     * when the player leaves it. {@link #entity(Entity, double)} does not have this
     * problem, because an entity in another dimension is not in the client's world.</p>
     *
     * @param position the source, in world coordinates
     * @param radius   how far away it fades to silence, in blocks
     * @throws IllegalArgumentException if {@code position} is {@code null}, or
     *                                  {@code radius} is not a positive finite number
     */
    static AudioPlacement world(Vec3 position, double radius) {
        if (position == null) {
            throw new IllegalArgumentException("position must not be null");
        }
        return new Fixed(position, requireRadius(radius));
    }

    /**
     * A point that follows an entity — a boombox on a player's shoulder, a jukebox
     * minecart. Read fresh every tick, so it moves with whatever it is attached to.
     *
     * <p>The sound is measured from the entity's eyes, which is where the game puts an
     * entity's own sounds. It does <b>not</b> keep the entity alive in any sense: the mod
     * holds an ordinary reference and stops hearing it once it is removed, but an addon
     * that plays a two-hour track onto a mob it then forgets about has leaked a
     * reference, and should {@link com.lia.mediaplayer.api.MediaHandle#close() close} the
     * handle instead.</p>
     *
     * @throws IllegalArgumentException if {@code entity} is {@code null}, or
     *                                  {@code radius} is not a positive finite number
     */
    static AudioPlacement entity(Entity entity, double radius) {
        if (entity == null) {
            throw new IllegalArgumentException("entity must not be null");
        }
        return new Following(entity, requireRadius(radius));
    }

    /**
     * Not in the world at all: full volume, centred, wherever the player is standing.
     *
     * <p>The same thing a {@code null} placement does, as a value — for an addon that
     * holds an {@code AudioPlacement} field and wants to say "2D" without a null check,
     * and for {@link AudioControls#setPlacement(AudioPlacement)} to move a sound back out
     * of the world.</p>
     */
    static AudioPlacement screen() {
        return Screen.INSTANCE;
    }

    private static double requireRadius(double radius) {
        if (!(radius > 0.0) || Double.isInfinite(radius)) {
            throw new IllegalArgumentException("radius must be a positive, finite number, was " + radius);
        }
        return radius;
    }

    // ------------------------------------------------------------------
    // The cases
    // ------------------------------------------------------------------

    /** @see #world(Vec3, double) */
    record Fixed(Vec3 position, double radius) implements AudioPlacement {
    }

    /** @see #entity(Entity, double) */
    record Following(Entity entity, double radius) implements AudioPlacement {

        @Override
        @Nullable
        public Vec3 position() {
            return entity.isRemoved() ? null : entity.getEyePosition();
        }
    }

    /** @see #screen() */
    record Screen() implements AudioPlacement {

        private static final Screen INSTANCE = new Screen();

        @Override
        public double radius() {
            return 0.0;
        }

        @Override
        @Nullable
        public Vec3 position() {
            return null;
        }

        @Override
        public boolean isPositional() {
            return false;
        }
    }
}
