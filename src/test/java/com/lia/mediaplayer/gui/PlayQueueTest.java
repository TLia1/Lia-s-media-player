package com.lia.mediaplayer.gui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
}
