/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.audio;

import com.lia.mediaplayer.api.MediaHandle;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * One playing thing's own share of the mix — reached through
 * {@link MediaHandle#audio()}.
 *
 * <p>These are the two factors of the gain chain that belong to a single sound, on top of
 * the two the user and the mixer own:</p>
 *
 * <pre>{@code
 * effective = masterVolume x channelGain(channel) x gain x distanceAttenuation(placement)
 * }</pre>
 *
 * <p>Note what is <em>not</em> here: the master level. That is
 * {@link com.lia.mediaplayer.api.IMediaPlayerAPI#setVolume(float)}, it is the user's, it
 * is shared by every player in the mod, and an addon that turns it down turns down the
 * music the user was listening to. {@link #setGain(float)} is the one an addon should
 * reach for — it scales this sound and nothing else.</p>
 *
 * <p>The whole chain is recomputed once per client tick and published to the audio thread
 * as a plain value, so setting any of this is cheap and takes effect within a tick. It is
 * never sample-accurate: {@link #fadeTo(float, int)} is a ramp in tick steps, not a
 * per-sample envelope.</p>
 *
 * <p><b>Render thread only</b>, like the rest of {@link MediaHandle}. A dead handle's
 * controls are inert: every setter is a no-op and every getter answers neutrally.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 3.1.0
 */
public interface AudioControls {

    // ------------------------------------------------------------------
    // Gain
    // ------------------------------------------------------------------

    /**
     * This sound's own level, {@code 0..1}. {@code 1} — untouched — is the default, and
     * means "whatever the user's volume says".
     */
    float gain();

    /**
     * Sets this sound's own level, clamped to {@code 0..1}. Cancels a
     * {@link #fadeTo(float, int)} in flight.
     */
    void setGain(float gain);

    /**
     * Ramps {@link #gain()} to {@code target} over {@code millis}, linearly, in client
     * ticks. A {@code millis} of {@code 0} or less is an immediate {@link #setGain}, and
     * a second call replaces whatever ramp was running — from wherever it had got to, so
     * two fades in quick succession do not jump.
     *
     * <p>This does not stop anything at the end. A fade-out that should also close the
     * player is {@link AudioOptions#fadeOutMillis()}, which {@link MediaHandle#close()}
     * honours.</p>
     */
    void fadeTo(float target, int millis);

    /** Whether a {@link #fadeTo} is still running. */
    boolean isFading();

    // ------------------------------------------------------------------
    // Channel
    // ------------------------------------------------------------------

    /** Which channel's gain this sound is multiplied by. Never {@code null}. */
    AudioChannel channel();

    /** Moves this sound to another channel. A {@code null} channel is ignored. */
    void setChannel(AudioChannel channel);

    // ------------------------------------------------------------------
    // Placement
    // ------------------------------------------------------------------

    /** Where this sound is, or empty for 2D — see {@link AudioPlacement}. */
    Optional<AudioPlacement> placement();

    /**
     * Puts this sound somewhere in the world, or takes it back out ({@code null}, which
     * is the same as {@link AudioPlacement#screen()}).
     *
     * <p>Safe to call every tick with a fresh {@link AudioPlacement#world(net.minecraft.world.phys.Vec3, double)}
     * for a source that moves and is not an entity; nothing is allocated behind it beyond
     * the placement itself.</p>
     */
    void setPlacement(@Nullable AudioPlacement placement);

    // ------------------------------------------------------------------
    // Read-back
    // ------------------------------------------------------------------

    /**
     * The whole chain as it was last computed, {@code 0..1} — what is actually on the
     * line right now. For a HUD, a debug overlay, or working out why a sound cannot be
     * heard.
     */
    float effectiveGain();

    /**
     * Where the sound is being panned, {@code -1} full left to {@code +1} full right,
     * {@code 0} centred. Always {@code 0} for a 2D sound, and also {@code 0} when the
     * audio device exposes no pan control.
     */
    float pan();
}
