/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.audio;

/**
 * The one place every sound the mod plays is scaled — the master level and the five
 * channel gains.
 *
 * <p>Reached through {@link MediaAudio#mixer()}.</p>
 *
 * <h2>The chain</h2>
 *
 * <pre>{@code
 * effective = masterVolume x channelGain(channel) x handleGain x distanceAttenuation
 * }</pre>
 *
 * <p>Only the first two live here; the last two are one sound's own — see
 * {@link AudioControls}. The mod deliberately keeps a <b>single master level</b> shared by
 * every player, video and audio alike, and this does not change that: a mixer is a set of
 * factors multiplied into it, not a second volume.</p>
 *
 * <h2>Which knob to turn</h2>
 *
 * <p>{@link #setMasterVolume(float)} is <b>the user's</b> volume — the one they set with
 * the slider on a window, the one their music is playing at. An addon that turns it down
 * has turned the user's music down, and nothing puts it back. Reach for it only when the
 * addon <em>is</em> the volume control (a settings screen, a keybind that means "quieter").
 * To make room for a sound of your own, turn a channel down and put it back afterwards;
 * to make one sound quieter, use its own {@link AudioControls#setGain(float)}.</p>
 *
 * <p>Master volume is persisted, as the user's setting always was. <b>Channel gains are
 * not</b> — every channel is back at {@code 1.0} on the next game start, so an addon that
 * dies mid-fade cannot leave someone permanently quiet with no way to find out why.</p>
 *
 * <p><b>Thread-safe</b>, unlike most of the API: the levels are plain volatile values and
 * the audio threads read them. The effect of a change lands within a client tick.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 3.1.0
 */
public interface MediaMixer {

    /**
     * The user's level, {@code 0..1} — the same value
     * {@link com.lia.mediaplayer.api.IMediaPlayerAPI#getVolume()} answers.
     */
    float masterVolume();

    /**
     * Sets the user's level, clamped to {@code 0..1}, and saves it. Read the note above
     * before calling this.
     */
    void setMasterVolume(float volume);

    /** Whether the master level is at (or as good as at) zero. */
    boolean isMuted();

    /** The gain applied to every sound on {@code channel}, {@code 0..1}. Unity by default. */
    float channelGain(AudioChannel channel);

    /**
     * Sets a channel's gain, clamped to {@code 0..1}. A {@code null} channel is ignored.
     *
     * <p>The ducking call. Remember that channels are shared: whatever an addon sets here
     * applies to every other addon's sounds on that channel too, so put it back.</p>
     */
    void setChannelGain(AudioChannel channel, float gain);

    /** Puts every channel back to {@code 1.0}. What to call if a fade was interrupted. */
    void resetChannelGains();
}
