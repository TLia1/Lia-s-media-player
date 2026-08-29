/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.sync;

import org.jetbrains.annotations.Nullable;

/**
 * One transport action, in a shape an addon can put on the wire.
 *
 * <p>Deliberately flat and primitive: this record is what a watch-together addon
 * serializes into its own packet, so it holds no Minecraft types, no handle and nothing
 * that only means something in this process.</p>
 *
 * <p>{@link #wallClockMillis} is what makes drift correction possible at the other end —
 * a {@code PLAY} that took 180 ms to arrive should resume 180 ms further in, not where
 * the sender was when they pressed the key. Compare it against the receiver's own
 * {@link System#currentTimeMillis()} and hand the difference to
 * {@link SyncControl#driftCorrect}; two clients whose clocks disagree will converge on
 * the offset rather than on the truth, which is the right trade for playback.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @param handleId       the local player this happened to. Meaningful only in the
 *                       process that produced it — the receiving side maps its own
 *                       session's handle, and {@link SyncControl#apply} is given a
 *                       {@code handleId} of its own choosing.
 * @param url            what was playing when it happened, or {@code null} for an action
 *                       with nothing playing
 * @param type           what happened
 * @param positionMicros where playback was, in microseconds
 * @param wallClockMillis {@link System#currentTimeMillis()} at the moment it happened
 * @since API 3.3.0
 */
public record SyncAction(long handleId, @Nullable String url, Type type,
                         long positionMicros, long wallClockMillis) {

    /** What happened. */
    public enum Type {
        /** Playback started or resumed. */
        PLAY,
        /** Playback was paused. */
        PAUSE,
        /** The position jumped. */
        SEEK,
        /** The queue advanced to the next track. */
        NEXT,
        /** The queue went back a track. */
        PREVIOUS,
        /** Playback stopped, or the window went away. */
        STOP,
        /** Something was added to the queue; {@link SyncAction#url()} is what. */
        ENQUEUE
    }

    /** This action, stamped with the current wall clock — what a sender builds. */
    public static SyncAction now(long handleId, @Nullable String url, Type type, long positionMicros) {
        return new SyncAction(handleId, url, type, positionMicros, System.currentTimeMillis());
    }

    /**
     * Where playback should be <em>now</em>, given that this action was produced
     * {@code (currentTimeMillis - wallClockMillis)} ago and has been running since.
     *
     * <p>Only meaningful for {@link Type#PLAY}; every other type describes a moment, not
     * a state that keeps moving. Never answers less than the position it carries, so a
     * receiver whose clock runs behind the sender's does not rewind.</p>
     */
    public long projectedPositionMicros() {
        long elapsed = System.currentTimeMillis() - wallClockMillis;
        return positionMicros + Math.max(0L, elapsed) * 1000L;
    }
}
