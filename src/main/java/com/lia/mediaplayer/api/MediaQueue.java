/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * What a player is going to play next, and the operations the queue panel already
 * performs on it — reachable at last.
 *
 * <p>Reached through {@code MediaHandle.queue()}, which is empty for a handle with no
 * queue behind it (a pinned image).</p>
 *
 * <h2>The current track is not in the list</h2>
 *
 * <p>{@link #entries()} is what is <em>waiting</em>; what is playing right now is
 * {@link #current()}, and it has no index. That is the mod's own model rather than a
 * simplification of it: a player owns one track and a list of what comes after, which is
 * why {@link #next()} both advances the player and shortens the queue. An index-into-a-
 * playlist API would read more familiarly and would be a lie about what happens when the
 * track ends.</p>
 *
 * <p><b>Render thread only.</b> Every method here reaches into a live player.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 2.3.0
 */
public interface MediaQueue {

    /** An immutable snapshot of what is waiting, in play order. */
    List<QueueEntry> entries();

    /** How many entries are waiting. The current track is not counted. */
    int size();

    boolean isEmpty();

    /** What is playing now, or {@code null} for a player with nothing open. */
    @Nullable
    QueueEntry current();

    /**
     * Appends a URL. Anything that is not an {@code http(s)} link is ignored — the same
     * gate every other URL-taking entry point of this API applies.
     */
    void add(String url);

    void addAll(List<String> urls);

    /** Inserts at {@code index}, clamped into the list. */
    void insert(int index, String url);

    /** Drops the entry at {@code index}; out-of-range indices do nothing. */
    void remove(int index);

    /**
     * Moves the entry at {@code from} to sit at {@code to}, both read against the list as
     * it is now. Out-of-range indices do nothing.
     */
    void move(int from, int to);

    /** Empties the queue. The track playing now keeps playing. */
    void clear();

    /**
     * Plays the queued entry at {@code index} now, leaving the rest in their order —
     * what a click on a row of the queue panel means.
     */
    void jumpTo(int index);

    /**
     * Advances to the next entry, honouring {@link #repeat()} and {@link #shuffle()}.
     * Does nothing when there is nothing to advance to.
     */
    void next();

    /**
     * Goes back to the previously played entry, re-queuing the current one so
     * {@link #next()} returns to it. Does nothing when nothing has played yet.
     */
    void previous();

    RepeatMode repeat();

    void setRepeat(RepeatMode mode);

    boolean shuffle();

    /**
     * Turning shuffle <em>on</em> also reorders what is already queued, and it stays on,
     * so a looping queue is reshuffled every round rather than replaying the order the
     * first round happened to get.
     */
    void setShuffle(boolean shuffle);
}
