package com.lia.mediaplayer.gui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

class PlayQueueTest {
    private PlayQueue queue;

    @BeforeEach
    void setUp() {
        queue = new PlayQueue();
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
    void remove_RemovesFromQueue() {
        queue.add("url1");
        queue.add("url2");
        queue.remove(0);
        assertEquals(1, queue.size());
        assertEquals("url2", queue.get(0));
    }

    @Test
    void clear_EmptiesQueue() {
        queue.add("url1");
        queue.clear();
        assertEquals(0, queue.size());
    }
}
