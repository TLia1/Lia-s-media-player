package com.lia.mediaplayer.video;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The frame barrier a seek puts up: frames decoded by the session being replaced must
 * be dropped unseen, and their buffers must come back to the pool.
 *
 * <p>Both halves matter. Showing them is the visible bug — every leftover frame has a
 * timestamp below the position being sought to, so the ordinary path reads the whole
 * backlog as "due" and flushes it to the screen as a burst of the old scene. Recycling
 * them is the invisible one: the buffer pool is fixed-size and allocated once, so a
 * frame dropped without its buffer returned shrinks the pool permanently, and after
 * enough seeks the decoder waits forever for a buffer that no longer exists.</p>
 *
 * <p>Only the drop path is exercised here. Accepting a frame uploads it to a texture,
 * which needs a GL context and therefore a running game.</p>
 */
class VideoRendererBarrierTest {

    private static final int W = 2;
    private static final int H = 2;

    private static VideoFrame frame(long tsMicros, int gen) {
        return new VideoFrame(tsMicros, gen, W, H, ByteBuffer.allocate(W * H * 4));
    }

    @Test
    void framesFromTheReplacedSessionAreDroppedNotShown() {
        Queue<VideoFrame> queued = new ArrayDeque<>();
        Queue<ByteBuffer> free = new ArrayDeque<>();
        for (int i = 0; i < 5; i++) {
            queued.add(frame(i * 33_000L, 3));
        }

        VideoRenderer renderer = new VideoRenderer();
        boolean shown = renderer.showFirstFrameAfter(3, queued, free);

        assertFalse(shown, "nothing from the new session has arrived yet");
        assertTrue(queued.isEmpty(), "the outgoing session's backlog should be cleared");
        assertEquals(5, free.size(), "every dropped frame must return its buffer");
    }

    @Test
    void olderSessionsAreDroppedToo() {
        Queue<VideoFrame> queued = new ArrayDeque<>();
        Queue<ByteBuffer> free = new ArrayDeque<>();
        queued.add(frame(0, 1));
        queued.add(frame(0, 2));
        queued.add(frame(0, 3));

        VideoRenderer renderer = new VideoRenderer();

        assertFalse(renderer.showFirstFrameAfter(3, queued, free));
        assertEquals(3, free.size());
    }

    @Test
    void anEmptyQueueIsNotAFrame() {
        VideoRenderer renderer = new VideoRenderer();
        assertFalse(renderer.showFirstFrameAfter(0, new ArrayDeque<>(), new ArrayDeque<>()));
    }

    @Test
    void aTimestampInTheFutureDoesNotSaveAStaleFrame() {
        // The trap the barrier exists for is the reverse case — stale frames that look
        // due — but a backward seek leaves stale frames that look *early*, and those
        // must go the same way rather than sitting in the queue ahead of the new ones.
        Queue<VideoFrame> queued = new ArrayDeque<>();
        Queue<ByteBuffer> free = new ArrayDeque<>();
        queued.add(frame(9_000_000_000L, 2));

        VideoRenderer renderer = new VideoRenderer();

        assertFalse(renderer.showFirstFrameAfter(2, queued, free));
        assertTrue(queued.isEmpty());
        assertEquals(1, free.size());
    }
}
