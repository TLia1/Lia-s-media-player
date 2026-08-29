/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.audio;

import org.jetbrains.annotations.Nullable;

/**
 * How a headless sound should be played — everything {@link MediaAudio#play} needs
 * beyond the URL.
 *
 * <p>Built by chaining from {@link #defaults()}, the way {@code SurfaceOptions} and
 * {@code MediaRequest} are, so an addon names the two things it cares about and inherits
 * the rest:</p>
 *
 * <pre>{@code
 * MediaAudio.play(url, AudioOptions.defaults()
 *         .withPlacement(AudioPlacement.world(speaker, 24))
 *         .withLoop(true)
 *         .withFade(1500, 800));
 * }</pre>
 *
 * <p>Everything here is a <em>starting</em> value: {@link com.lia.mediaplayer.api.MediaHandle#audio()}
 * changes the gain, the channel and the placement afterwards.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @param gain          this sound's own level, {@code 0..1} — see {@link AudioControls#setGain}
 * @param loop          restart from the beginning when the track ends, instead of ending
 * @param startMicros   where to begin, in microseconds; {@code 0} is the start
 * @param channel       which channel gain this sound is multiplied by, never {@code null}
 * @param placement     where the sound is, or {@code null} for 2D
 * @param fadeInMillis  ramp the gain up from silence over this long when playback starts
 * @param fadeOutMillis ramp it back down over this long before the end of a non-looping
 *                      track, and when {@link com.lia.mediaplayer.api.MediaHandle#close()}
 *                      is called — a closing sound fades and <em>then</em> stops, so an
 *                      addon does not have to schedule that itself
 * @param pauseWithGame pause while the game is paused (the single-player pause menu).
 *                      {@code true} for anything diegetic — a speaker in the world should
 *                      not keep playing over a paused game — and {@code false} for music
 *                      that is meant to carry on
 * @since API 3.1.0
 */
public record AudioOptions(float gain, boolean loop, long startMicros, AudioChannel channel,
                           @Nullable AudioPlacement placement, int fadeInMillis, int fadeOutMillis,
                           boolean pauseWithGame) {

    private static final AudioOptions DEFAULTS =
            new AudioOptions(1.0f, false, 0L, AudioChannel.MEDIA, null, 0, 0, true);

    /**
     * Full volume, played once from the start, 2D, on {@link AudioChannel#MEDIA}, no
     * fades, paused with the game.
     */
    public static AudioOptions defaults() {
        return DEFAULTS;
    }

    /**
     * Canonicalises whatever was handed in: the gain is clamped to {@code 0..1}, a
     * negative {@code startMicros} or fade length becomes {@code 0}, and a {@code null}
     * channel becomes {@link AudioChannel#MEDIA}.
     *
     * <p>Compact rather than validating, because none of these is a mistake worth
     * throwing over: an addon computing a gain from a distance it got slightly wrong
     * wants a quiet sound, not a crash inside a block-entity tick.</p>
     */
    public AudioOptions {
        gain = Math.max(0.0f, Math.min(1.0f, gain));
        startMicros = Math.max(0L, startMicros);
        fadeInMillis = Math.max(0, fadeInMillis);
        fadeOutMillis = Math.max(0, fadeOutMillis);
        channel = channel == null ? AudioChannel.MEDIA : channel;
    }

    /** @see AudioControls#setGain(float) */
    public AudioOptions withGain(float value) {
        return new AudioOptions(value, loop, startMicros, channel, placement, fadeInMillis, fadeOutMillis, pauseWithGame);
    }

    public AudioOptions withLoop(boolean value) {
        return new AudioOptions(gain, value, startMicros, channel, placement, fadeInMillis, fadeOutMillis, pauseWithGame);
    }

    /** Begins {@code micros} into the track, the way resuming a saved position does. */
    public AudioOptions withStart(long micros) {
        return new AudioOptions(gain, loop, micros, channel, placement, fadeInMillis, fadeOutMillis, pauseWithGame);
    }

    public AudioOptions withChannel(AudioChannel value) {
        return new AudioOptions(gain, loop, startMicros, value, placement, fadeInMillis, fadeOutMillis, pauseWithGame);
    }

    /** @see AudioPlacement */
    public AudioOptions withPlacement(@Nullable AudioPlacement value) {
        return new AudioOptions(gain, loop, startMicros, channel, value, fadeInMillis, fadeOutMillis, pauseWithGame);
    }

    /** Both fades at once, since they are almost always set together. */
    public AudioOptions withFade(int inMillis, int outMillis) {
        return new AudioOptions(gain, loop, startMicros, channel, placement, inMillis, outMillis, pauseWithGame);
    }

    public AudioOptions withPauseWithGame(boolean value) {
        return new AudioOptions(gain, loop, startMicros, channel, placement, fadeInMillis, fadeOutMillis, value);
    }
}
