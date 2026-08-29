package com.lia.mediaplayer.api.sync;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one piece of arithmetic on the wire format: projecting a {@code PLAY} forward by
 * however long its packet spent in flight.
 *
 * <p>It matters because it is what stops a watch party drifting by the round-trip time on
 * every single resume, and because the floor at zero is what stops a receiver whose clock
 * runs behind the sender's from rewinding — two clocks that disagree should converge on
 * the offset, not fight over the truth.
 */
class SyncActionTest {

    @Test
    void projectsForwardByTheTimeSpentInFlight() {
        long sentAt = System.currentTimeMillis() - 200L;
        SyncAction action = new SyncAction(1L, "https://example.com/a.mp4",
                SyncAction.Type.PLAY, 10_000_000L, sentAt);
        long projected = action.projectedPositionMicros();
        assertTrue(projected >= 10_200_000L, "expected at least 200ms of travel, got " + projected);
        // A generous ceiling: the only thing between the two reads is this test.
        assertTrue(projected < 10_400_000L, "projected implausibly far: " + projected);
    }

    @Test
    void neverRewindsWhenTheSendersClockIsAhead() {
        SyncAction action = new SyncAction(1L, null, SyncAction.Type.PLAY,
                10_000_000L, System.currentTimeMillis() + 60_000L);
        assertEquals(10_000_000L, action.projectedPositionMicros(),
                "a clock skew must not push playback backwards");
    }

    @Test
    void nowStampsTheCurrentWallClock() {
        long before = System.currentTimeMillis();
        SyncAction action = SyncAction.now(7L, "https://example.com/a.mp4",
                SyncAction.Type.SEEK, 5_000_000L);
        long after = System.currentTimeMillis();
        assertTrue(action.wallClockMillis() >= before && action.wallClockMillis() <= after);
        assertEquals(7L, action.handleId());
        assertEquals(SyncAction.Type.SEEK, action.type());
        assertEquals(5_000_000L, action.positionMicros());
    }

    @Test
    void anActionWithNothingPlayingCarriesNoUrl() {
        assertNull(SyncAction.now(1L, null, SyncAction.Type.STOP, 0L).url());
    }
}
