package com.lia.mediaplayer.media;

import com.lia.mediaplayer.api.PlaybackState;

/**
 * What a window can ask of whichever engine is playing behind it.
 *
 * <p>{@code video.VideoPlayer} and {@code audio.AudioPlayer} are deliberately separate
 * engines that know nothing about each other, but they grew the same transport: the two
 * had identical signatures for every one of the calls below long before this interface
 * existed. Naming that overlap is what lets one window class own the queue, the transport
 * and the seek/volume dragging for both of them, instead of the two keeping a copy each.
 * It lives in {@code media} for the reason everything shared does — a dependency from
 * {@code video} to {@code audio} (or back) is the thing the package layout exists to
 * prevent.</p>
 *
 * <p>Only the <em>common</em> surface is here. Everything that is genuinely one engine's
 * — a frame to blit, a picture size, an audio track that may not exist, the {@code State}
 * enums, which are separate types — stays on the concrete player, which is why the window
 * holds its player as its own type and not as this interface.</p>
 *
 * <p>Threading is each implementation's business: these are called from the render
 * thread while decode and playback threads run behind them.</p>
 */
public interface MediaPlayback {

    /** The media this player was created for; it never plays anything else. */
    String url();

    /** Begins decoding. Called once, by the window that owns the player. */
    void start();

    /** Stops everything and releases the native resources. The player is dead after this. */
    void dispose();

    boolean isPlaying();

    void togglePause();

    /** Pauses, if playing. A player that is already paused is left alone. */
    void pause();

    /** Resumes, if paused. A player that is already running is left alone. */
    void resume();

    /**
     * Where playback stands, in the API's terms.
     *
     * <p>Both engines have a {@code state()} of their own, and the two enums are
     * deliberately separate types — they are each engine's business. This is the one
     * common answer, and it exists because everything outside the engines that has to
     * report a state (the API's handles, the playback events) needs one vocabulary
     * rather than a {@code switch} per caller.</p>
     */
    PlaybackState playbackState();

    /** Total length in microseconds, or {@code <= 0} for a live stream or an unknown one. */
    long durationMicros();

    /** Where playback has reached, in microseconds. */
    long positionMicros();

    /** {@link #positionMicros()} over {@link #durationMicros()}, or {@code 0} when unknown. */
    double progress();

    void seekTo(long targetMicros);

    /** Seeks to a point given as {@code 0..1} of the duration — what a seek bar produces. */
    void seekToFraction(double fraction);

    /** Whether a seek is still in flight (the picture or the sound is about to jump). */
    boolean isSeeking();

    /**
     * What {@link #driftCorrect} uses when the caller passes no tolerance: a frame or two
     * at 30fps. Here rather than in either engine because both answer to it, and
     * {@code video.PlaybackClock} reads it too.
     */
    long DEFAULT_DRIFT_TOLERANCE_MICROS = 60_000L;

    /**
     * Converges on {@code targetMicros} rather than jumping to it — the engine half of
     * {@code api.sync.SyncControl.driftCorrect}.
     *
     * <p>The two engines answer this differently, and honestly. A video has a
     * {@code PlaybackClock} whose offset can be slid a little at a time, so a small
     * correction is invisible; an audio track's line <em>is</em> its position and cannot
     * be skewed at all, so anything past the tolerance is a seek. Both are bounded the
     * same way: past about two seconds the correction is a seek either way, because
     * beyond that a slide is no longer a correction.</p>
     *
     * @param toleranceMicros how close is close enough; {@code <= 0} means the default
     * @return whether anything was done — {@code false} means it was already close enough
     */
    boolean driftCorrect(long targetMicros, long toleranceMicros);

    /**
     * Why playback failed, as ffmpeg reported it, or {@code null} if it has not.
     * {@link PlaybackError} turns it into something a player can show.
     */
    String errorMessage();

    // The single shared volume level (see Volume); these are here because the seek/volume
    // dragging that reads them is shared code.

    float volume();

    void setVolume(float value);

    void changeVolume(float delta);

    boolean isMuted();

    void toggleMute();

    // ------------------------------------------------------------------
    // This player's own share of the mix
    // ------------------------------------------------------------------

    /**
     * The channel, per-sound gain and placement this player's sound is multiplied by —
     * see {@link AudioGain}.
     *
     * <p>Here rather than on each engine for the same reason the transport is: a window
     * hands one gain to every player it swaps in as its queue advances, an off-screen
     * surface hands one to its video, and the API's {@code AudioControls} reads whichever
     * it is given, none of which should care which engine is behind it.</p>
     *
     * <p>Never {@code null}: a player nobody attached to a mixer starts with
     * {@link AudioGain#detached()} and behaves as it always did.</p>
     */
    AudioGain audioGain();

    /**
     * Hands this player the gain it should apply. Called by whatever owns the player,
     * before {@link #start()}; a player already running picks the new one up on its next
     * buffer.
     */
    void setAudioGain(AudioGain gain);
}
