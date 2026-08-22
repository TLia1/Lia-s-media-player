package com.lia.mediaplayer.gui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlayQueueTest {
    private PlayQueue queue;

    @BeforeEach
    void setUp() {
        queue = new PlayQueue();
    }

    @Test
    void isEmpty_WorksCorrectly() {
        assertTrue(queue.isEmpty());
        queue.add("url1");
        assertFalse(queue.isEmpty());
    }

    @Test
    void add_AddsToQueue() {
        queue.add("url1");
        queue.add("url2");
        assertEquals(2, queue.size());
        assertEquals("url1", queue.get(0));
        assertEquals("url2", queue.get(1));
    }

    @Test
    void addAll_AddsMultipleToQueue() {
        queue.addAll(List.of("url1", "url2"));
        assertEquals(2, queue.size());
        assertEquals("url1", queue.get(0));
        assertEquals("url2", queue.get(1));
    }

    @Test
    void addFirst_AddsToFrontOfQueue() {
        queue.add("url1");
        queue.addFirst("url0");
        assertEquals(2, queue.size());
        assertEquals("url0", queue.get(0));
        assertEquals("url1", queue.get(1));
    }

    @Test
    void remove_RemovesFromQueue() {
        queue.add("url1");
        queue.add("url2");
        String removed = queue.remove(0);
        assertEquals("url1", removed);
        assertEquals(1, queue.size());
        assertEquals("url2", queue.get(0));
    }

    @Test
    void removeFirst_RemovesFromFrontOfQueue() {
        queue.add("url1");
        queue.add("url2");
        String removed = queue.removeFirst();
        assertEquals("url1", removed);
        assertEquals(1, queue.size());
        assertEquals("url2", queue.get(0));
    }

    @Test
    void removeFirst_OnEmptyQueue_ThrowsException() {
        assertThrows(IndexOutOfBoundsException.class, () -> queue.removeFirst());
    }

    @Test
    void clear_EmptiesQueue() {
        queue.add("url1");
        queue.clear();
        assertEquals(0, queue.size());
        assertTrue(queue.isEmpty());
    }

    @Test
    void snapshot_ReturnsDefensiveCopy() {
        queue.add("url1");
        List<String> snapshot = queue.snapshot();
        assertEquals(1, snapshot.size());

        // Mutating queue shouldn't affect snapshot
        queue.add("url2");
        assertEquals(1, snapshot.size());
        assertEquals(2, queue.size());

        // Mutating snapshot shouldn't affect queue
        snapshot.add("url3");
        assertEquals(2, queue.size());
    }

    @Test
    void moveUp_MovesElementCorrectly() {
        queue.addAll(List.of("url1", "url2", "url3"));
        queue.moveUp(1); // Move url2 up

        assertEquals("url2", queue.get(0));
        assertEquals("url1", queue.get(1));
        assertEquals("url3", queue.get(2));
    }

    @Test
    void moveUp_OutOfBounds_DoesNothing() {
        queue.addAll(List.of("url1", "url2"));
        queue.moveUp(0); // Cannot move up first element
        queue.moveUp(-1);
        queue.moveUp(5);

        assertEquals("url1", queue.get(0));
        assertEquals("url2", queue.get(1));
    }

    @Test
    void moveDown_MovesElementCorrectly() {
        queue.addAll(List.of("url1", "url2", "url3"));
        queue.moveDown(1); // Move url2 down

        assertEquals("url1", queue.get(0));
        assertEquals("url3", queue.get(1));
        assertEquals("url2", queue.get(2));
    }

    @Test
    void moveDown_OutOfBounds_DoesNothing() {
        queue.addAll(List.of("url1", "url2"));
        queue.moveDown(1); // Cannot move down last element
        queue.moveDown(-1);
        queue.moveDown(5);

        assertEquals("url1", queue.get(0));
        assertEquals("url2", queue.get(1));
    }

    // ------------------------------------------------------------------
    // Repeat / shuffle
    // ------------------------------------------------------------------

    @Test
    void next_WithoutRepeat_WalksTheQueueThenStops() {
        queue.addAll(List.of("b", "c"));

        assertEquals("b", queue.next("a"));
        assertEquals("c", queue.next("b"));
        assertNull(queue.next("c"));
        assertTrue(queue.isEmpty());
    }

    @Test
    void next_RepeatOne_ReplaysTheCurrentTrackAndLeavesTheQueueAlone() {
        queue.setRepeat(RepeatMode.ONE);
        queue.add("b");

        assertEquals("a", queue.next("a"));
        assertEquals("a", queue.next("a"));
        assertEquals(1, queue.size());
        assertFalse(queue.hasPrevious());
    }

    @Test
    void next_RepeatOne_OnAnEmptyQueue_StillLoops() {
        queue.setRepeat(RepeatMode.ONE);
        assertEquals("a", queue.next("a"));
    }

    @Test
    void next_RepeatAll_ReplaysTheWholeRoundInOrder() {
        queue.setRepeat(RepeatMode.ALL);
        queue.addAll(List.of("b", "c"));

        assertEquals("b", queue.next("a"));
        assertEquals("c", queue.next("b"));
        // The round is over: it starts again from the top, current track included.
        assertEquals("a", queue.next("c"));
        assertEquals("b", queue.next("a"));
        assertEquals("c", queue.next("b"));
        assertEquals("a", queue.next("c"));
    }

    @Test
    void next_RepeatAll_DoesNotDuplicateTracksAcrossRounds() {
        queue.setRepeat(RepeatMode.ALL);
        queue.addAll(List.of("b", "c"));

        List<String> played = new ArrayList<>();
        String current = "a";
        for (int i = 0; i < 6; i++) {
            current = queue.next(current);
            played.add(current);
        }
        assertEquals(List.of("b", "c", "a", "b", "c", "a"), played);
    }

    @Test
    void next_RepeatAll_WithASingleTrack_LoopsThatTrack() {
        queue.setRepeat(RepeatMode.ALL);
        assertEquals("a", queue.next("a"));
        assertEquals("a", queue.next("a"));
    }

    @Test
    void next_RepeatAllShuffled_ReplaysTheSameTracksInSomeOrder() {
        queue.setRepeat(RepeatMode.ALL);
        queue.setShuffle(true);
        queue.addAll(List.of("b", "c", "d"));

        // Drain the first round, then collect the reshuffled second one.
        String current = "a";
        for (int i = 0; i < 3; i++) {
            current = queue.next(current);
        }
        List<String> round = new ArrayList<>();
        round.add(queue.next(current)); // the track that opens the new round
        round.addAll(queue.snapshot());

        assertEquals(4, round.size());
        assertEquals(List.of("a", "b", "c", "d"), round.stream().sorted().toList());
    }

    @Test
    void setShuffle_KeepsEveryQueuedTrack() {
        queue.addAll(List.of("a", "b", "c", "d", "e"));
        queue.setShuffle(true);

        assertTrue(queue.shuffle());
        assertEquals(List.of("a", "b", "c", "d", "e"), queue.snapshot().stream().sorted().toList());
    }

    @Test
    void toggleShuffle_FlipsTheFlag() {
        assertTrue(queue.toggleShuffle());
        assertFalse(queue.toggleShuffle());
    }

    @Test
    void cycleRepeat_StepsThroughEveryMode() {
        assertEquals(RepeatMode.OFF, queue.repeat());
        assertEquals(RepeatMode.ALL, queue.cycleRepeat());
        assertEquals(RepeatMode.ONE, queue.cycleRepeat());
        assertEquals(RepeatMode.OFF, queue.cycleRepeat());
    }

    @Test
    void hasNext_IsTrueWhileSomethingCanStillPlay() {
        assertFalse(queue.hasNext());

        queue.add("b");
        assertTrue(queue.hasNext());

        queue.removeFirst();
        queue.setRepeat(RepeatMode.ALL);
        assertTrue(queue.hasNext()); // an empty queue still loops back around
    }

    @Test
    void previous_GoesBackAndRequeuesTheCurrentTrack() {
        queue.addAll(List.of("b", "c"));
        assertFalse(queue.hasPrevious());
        assertNull(queue.previous("a"));

        assertEquals("b", queue.next("a"));
        assertTrue(queue.hasPrevious());
        assertEquals("a", queue.previous("b"));
        // "b" went back to the front of the queue, so "next" returns to it.
        assertEquals("b", queue.get(0));
        assertFalse(queue.hasPrevious());
    }

    @Test
    void clear_AlsoForgetsTheHistory() {
        queue.add("b");
        queue.next("a");
        assertTrue(queue.hasPrevious());

        queue.clear();
        assertFalse(queue.hasPrevious());
        assertNull(queue.previous("b"));
    }
}
