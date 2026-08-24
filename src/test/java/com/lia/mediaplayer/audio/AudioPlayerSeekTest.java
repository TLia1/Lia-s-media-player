package com.lia.mediaplayer.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The audio bar's half of the seek-position contract — see
 * {@code VideoPlayerSeekTest} for what it is protecting against. The two engines are
 * deliberately independent, so the behaviour is pinned on each rather than on one and
 * assumed of the other.
 *
 * <p>Nothing here starts the control thread, so no process, no audio line and no game.</p>
 */
class AudioPlayerSeekTest {

    private static final String URL = "https://example.invalid/track.mp3";

    @Test
    void aFreshPlayerIsNotSeeking() {
        AudioPlayer player = new AudioPlayer(URL);
        assertFalse(player.isSeeking());
        assertEquals(0, player.positionMicros());
    }

    @Test
    void aRequestedSeekIsReportedImmediately() {
        AudioPlayer player = new AudioPlayer(URL);
        player.seekTo(42_000_000L);

        assertTrue(player.isSeeking(), "the seek should be in flight");
        assertEquals(42_000_000L, player.positionMicros(),
                "the position must be the target, not the line's stale reading");
    }

    @Test
    void theLatestSeekWins() {
        AudioPlayer player = new AudioPlayer(URL);
        player.seekTo(10_000_000L);
        player.seekTo(90_000_000L);

        assertEquals(90_000_000L, player.positionMicros());
    }

    @Test
    void aNegativeTargetIsClampedToTheStart() {
        AudioPlayer player = new AudioPlayer(URL);
        player.seekTo(-5_000_000L);

        assertTrue(player.isSeeking());
        assertEquals(0, player.positionMicros());
    }
}
