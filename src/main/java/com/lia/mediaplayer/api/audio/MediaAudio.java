/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.audio;

import com.lia.mediaplayer.api.IMediaPlayerAPI;
import com.lia.mediaplayer.api.LiasMediaPlayerApi;
import com.lia.mediaplayer.api.MediaHandle;

import org.jetbrains.annotations.Nullable;

/**
 * Sound with no window — the front door for an addon that wants the mod's playback
 * engine and none of its user interface.
 *
 * <p>Everything the mod plays otherwise belongs to a window the user can see, move and
 * close. A speaker block, an ambience loop, a radio in a vehicle and a cutscene's
 * soundtrack are none of those things: they belong to the addon, they are stopped by the
 * addon, and drawing a media bar for them would be wrong. This plays them.</p>
 *
 * <pre>{@code
 * MediaHandle radio = MediaAudio.play(url, AudioOptions.defaults()
 *         .withPlacement(AudioPlacement.world(speakerCentre, 24))
 *         .withChannel(AudioChannel.AMBIENT)
 *         .withLoop(true)
 *         .withFade(1000, 1000));
 * // ... later, when the block is broken:
 * radio.close();   // fades out over the second asked for above, then stops
 * }</pre>
 *
 * <h2>Rules worth knowing before the first call</h2>
 *
 * <ul>
 *   <li><b>The addon owns the lifetime.</b> Nothing on screen can stop a headless sound,
 *       because there is nothing on screen. A non-looping track retires itself when it
 *       ends and everything is dropped when the player leaves the world; anything else is
 *       {@link MediaHandle#close()}, and an addon that never calls it has left a process
 *       running.</li>
 *   <li><b>There is a cap</b>, and it is small — each headless sound is an ffmpeg process
 *       and an audio line, exactly like a window. Past it a request is refused (and
 *       logged) rather than queued, and this answers {@code null}. One sound per speaker
 *       block in a world of speaker blocks is not a design this can support; one sound
 *       for the nearest speaker is.</li>
 *   <li><b>It is 2.5D</b> — attenuation and panning, no occlusion and no HRTF. Read
 *       {@link AudioPlacement} before building anything around it.</li>
 * </ul>
 *
 * <p><b>Render thread only.</b> {@link #mixer()} is the exception and is safe anywhere.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 3.1.0
 */
public final class MediaAudio {

    private MediaAudio() {
    }

    /** Plays {@code url} once, at full volume, 2D — {@link AudioOptions#defaults()}. */
    @Nullable
    public static MediaHandle play(String url) {
        return play(url, AudioOptions.defaults());
    }

    /**
     * Plays {@code url} with no window at all.
     *
     * <p>The handle is a {@link MediaHandle} like any other, so an addon can pause it,
     * seek it and listen to it the same way; its {@code window()} and {@code queue()} are
     * empty, because it has neither, and its {@link MediaHandle#audio()} is where the
     * gain, the channel and the placement can be changed afterwards.</p>
     *
     * @return the handle, or {@code null} if the mod is not up, the link is not something
     *         it can play, or the headless-audio cap is already reached
     */
    @Nullable
    public static MediaHandle play(String url, AudioOptions options) {
        IMediaPlayerAPI api = LiasMediaPlayerApi.getInstanceOrNull();
        return api == null
                ? null
                : api.playHeadlessAudio(url, options == null ? AudioOptions.defaults() : options);
    }

    /**
     * The master level and the channel gains — see {@link MediaMixer}.
     *
     * <p>Never {@code null}: before the mod is up this answers a mixer that reads unity
     * and ignores every setter, so an addon's initialiser does not have to care which
     * mod loaded first.</p>
     *
     * <p>Thread-safe.</p>
     */
    public static MediaMixer mixer() {
        IMediaPlayerAPI api = LiasMediaPlayerApi.getInstanceOrNull();
        return api == null ? DeadMixer.INSTANCE : api.getMixer();
    }
}
