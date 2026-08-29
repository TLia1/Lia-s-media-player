/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.audio;

/**
 * What a sound is <em>for</em>, so several addons can turn each other down without
 * knowing about each other.
 *
 * <p>A channel is one factor of the gain chain and nothing else:</p>
 *
 * <pre>{@code
 * effective = masterVolume x channelGain(channel) x handleGain x distanceAttenuation
 * }</pre>
 *
 * <p>The point is the middle term. A cutscene addon that wants the jukebox quiet for
 * twelve seconds sets {@code MEDIA} to {@code 0.2f} and puts it back afterwards; it does
 * not have to find the jukebox addon's handles, and the jukebox addon does not have to
 * know it happened. Nothing in the mod reads the channel for any other purpose — it does
 * not route to a different output, it does not change how a sound is mixed, and it is
 * <b>not</b> one of Minecraft's own {@code SoundSource} categories (the mod writes PCM to
 * a {@code javax.sound} line, not to the game's sound engine, so the in-game sliders other
 * than <em>Master</em> do not apply to it).</p>
 *
 * <h2>Why a closed enum</h2>
 *
 * <p>Five names two addons can agree on without coordinating are worth more than an open
 * registry where everyone invents their own id and nothing ever ducks anything. If that
 * turns out to be wrong, a string-keyed channel can be added beside this without moving
 * anything — but a constant here is never removed, for the reason spelled out in
 * {@link com.lia.mediaplayer.api.Capability}.</p>
 *
 * <p>Every channel starts at gain {@code 1.0} and is <b>not persisted</b>: channel gains
 * are a runtime mix, restored to unity on every game start, so an addon that crashes
 * halfway through a fade cannot leave the user permanently quiet.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @see MediaMixer
 * @since API 3.1.0
 */
public enum AudioChannel {

    /** Music and video sound — what the mod's own windows play. The default. */
    MEDIA,

    /** Speech: a radio broadcast, a narrator, a recorded call. */
    VOICE,

    /** Background texture meant to sit under everything else — rain on a roof, a crowd. */
    AMBIENT,

    /** Short interface sounds: a click, a confirmation, a notification. */
    UI,

    /** Anything that is none of the above. The escape hatch, deliberately vague. */
    ADDON
}
