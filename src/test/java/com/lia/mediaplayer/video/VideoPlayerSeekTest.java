package com.lia.mediaplayer.video;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The position a player reports while a seek is still being served.
 *
 * <p>A seek is not instant — it relaunches ffmpeg at the new offset, which takes the
 * better part of a second — and for the whole of that gap the playback clock still
 * holds the old position. A seek bar reading the clock therefore jumped to where it was
 * clicked, snapped back to where playback was, and only landed on the target once
 * ffmpeg came up. These pin the fix: from the moment a seek is requested the player
 * reports the target.</p>
 *
 * <p>No decode thread is started here ({@code start()} is never called), so the player
 * stays in its constructed state and nothing touches ffmpeg, the audio system or the
 * game.</p>
 */
class VideoPlayerSeekTest {

    private static final String URL = "https://example.invalid/clip.mp4";

    @Test
    void aFreshPlayerIsNotSeeking() {
        VideoPlayer player = new VideoPlayer(URL);
        assertFalse(player.isSeeking());
        assertEquals(0, player.positionMicros());
    }

    @Test
    void aRequestedSeekIsReportedImmediately() {
        VideoPlayer player = new VideoPlayer(URL);
        player.seekTo(42_000_000L);

        assertTrue(player.isSeeking(), "the seek should be in flight");
        assertEquals(42_000_000L, player.positionMicros(),
                "the position must be the target, not the clock's stale reading");
    }

    @Test
    void theLatestSeekWins() {
        VideoPlayer player = new VideoPlayer(URL);
        player.seekTo(10_000_000L);
        player.seekTo(90_000_000L);

        assertEquals(90_000_000L, player.positionMicros());
    }

    @Test
    void aNegativeTargetIsClampedToTheStart() {
        VideoPlayer player = new VideoPlayer(URL);
        player.seekTo(-5_000_000L);

        assertTrue(player.isSeeking());
        assertEquals(0, player.positionMicros());
    }

    @Test
    void progressFollowsTheRequestedTarget() {
        VideoPlayer player = new VideoPlayer(URL);
        // Duration is unknown before probing, so seekToFraction has nothing to scale
        // against and must not move the player at all.
        player.seekToFraction(0.5);
        assertFalse(player.isSeeking(), "a fraction of an unknown duration means nothing");
        assertEquals(0.0, player.progress(), 1e-9);
    }
}
