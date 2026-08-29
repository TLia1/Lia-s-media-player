package com.lia.mediaplayer.media;

import com.lia.mediaplayer.api.PlaybackState;
import com.lia.mediaplayer.api.event.PlaybackEvent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The transitions {@link PlaybackWatcher} derives from a player's polled state — the
 * whole of what makes the API's playback events fire, and pure enough to test.
 */
class PlaybackWatcherTest {

    private final PlaybackWatcher watcher = new PlaybackWatcher();

    private List<PlaybackEvent.Type> poll(PlaybackState state) {
        return watcher.poll(state, false);
    }

    @Test
    void firstPollEstablishesTheBaselineWithoutFiring() {
        assertTrue(poll(PlaybackState.LOADING).isEmpty());
        assertTrue(poll(PlaybackState.PLAYING).isEmpty(),
                "LOADING to PLAYING is the track opening, which STARTED already covered");
    }

    @Test
    void pauseAndResumeAreReported() {
        poll(PlaybackState.PLAYING);
        assertEquals(List.of(PlaybackEvent.Type.PAUSED), poll(PlaybackState.PAUSED));
        assertEquals(List.of(PlaybackEvent.Type.RESUMED), poll(PlaybackState.PLAYING));
    }

    @Test
    void anUnchangedStateFiresNothing() {
        poll(PlaybackState.PLAYING);
        assertTrue(poll(PlaybackState.PLAYING).isEmpty());
        assertTrue(poll(PlaybackState.PLAYING).isEmpty());
    }

    @Test
    void endedAndFailedFireOnce() {
        poll(PlaybackState.PLAYING);
        assertEquals(List.of(PlaybackEvent.Type.ENDED), poll(PlaybackState.ENDED));
        assertTrue(poll(PlaybackState.ENDED).isEmpty(), "a player sits in ENDED until it is retired");

        PlaybackWatcher other = new PlaybackWatcher();
        other.poll(PlaybackState.LOADING, false);
        assertEquals(List.of(PlaybackEvent.Type.FAILED), other.poll(PlaybackState.FAILED, false));
    }

    @Test
    void seekIsReportedOncePerSeekNotOncePerTick() {
        watcher.poll(PlaybackState.PLAYING, false);
        assertEquals(List.of(PlaybackEvent.Type.SEEKED), watcher.poll(PlaybackState.PLAYING, true));
        assertTrue(watcher.poll(PlaybackState.PLAYING, true).isEmpty(),
                "the flag stays up while the seek is in flight");
        assertTrue(watcher.poll(PlaybackState.PLAYING, false).isEmpty());
        assertEquals(List.of(PlaybackEvent.Type.SEEKED), watcher.poll(PlaybackState.PLAYING, true));
    }

    @Test
    void aStateChangeIsReportedBeforeTheSeekThatCausedIt() {
        watcher.poll(PlaybackState.PAUSED, false);
        assertEquals(List.of(PlaybackEvent.Type.RESUMED, PlaybackEvent.Type.SEEKED),
                watcher.poll(PlaybackState.PLAYING, true));
    }

    @Test
    void resetMakesTheNextPollABaselineAgain() {
        watcher.poll(PlaybackState.PLAYING, true);
        watcher.poll(PlaybackState.ENDED, false);
        watcher.reset();
        assertTrue(watcher.poll(PlaybackState.LOADING, false).isEmpty(),
                "a fresh player's LOADING must not read as a transition out of the old one's ENDED");
        assertTrue(watcher.poll(PlaybackState.PLAYING, false).isEmpty());
    }
}
