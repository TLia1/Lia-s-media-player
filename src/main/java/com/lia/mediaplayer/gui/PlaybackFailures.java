package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.api.MediaKind;
import com.lia.mediaplayer.api.diag.MediaLogEntry;
import com.lia.mediaplayer.api.diag.MediaPlayerLog;
import com.lia.mediaplayer.media.PlaybackError;

import org.jetbrains.annotations.Nullable;

/**
 * The one place a failed playback becomes a {@link MediaLogEntry}.
 *
 * <p>{@code media.PlaybackError} already turns ffmpeg's and yt-dlp's stderr into a
 * readable line, a hint and a classified cause — that is what the mod's own error panel
 * shows. Exposing <em>that</em>, rather than the raw stderr, is the whole point of the
 * log an addon can subscribe to: a pack maintainer surfacing "ffmpeg failed on this
 * link" in their own UI should get the same words the mod would have used.</p>
 *
 * <p>It sits in {@code gui} because that is where a failure is noticed — a window polling
 * its player's state is the thing that knows one happened. Neither engine has any
 * business knowing a public log exists, for the same reason neither posts its own
 * playback events.</p>
 */
final class PlaybackFailures {

    private PlaybackFailures() {
    }

    /**
     * Posts one failure. Called from {@code MediaWindow.postPlaybackEvent} on the
     * {@code FAILED} transition, so it fires once per failure rather than once per tick
     * a player spends failed.
     */
    static void report(String url, MediaKind kind, @Nullable String raw) {
        PlaybackError.Cause cause = PlaybackError.classify(raw);
        MediaPlayerLog.post(new MediaLogEntry(
                url,
                kind,
                cause.id(),
                cause.suggestsToolUpdate(),
                PlaybackError.message(raw),
                PlaybackError.hint(raw),
                raw,
                System.currentTimeMillis()));
    }
}
