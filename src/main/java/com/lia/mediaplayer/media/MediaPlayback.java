package com.lia.mediaplayer.media;

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
}
