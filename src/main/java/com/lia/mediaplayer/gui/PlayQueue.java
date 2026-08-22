package com.lia.mediaplayer.gui;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * The ordered list of URLs waiting to play in a single player window, plus the small
 * set of operations a queue panel needs (append, jump, remove, reorder).
 *
 * <p>Both {@link VideoWindow} and {@link AudioWindow} keep one of these instead of each
 * carrying its own {@code List} plumbing, so the queue mechanics live in one place. The
 * method names deliberately mirror {@link List} (so a window can treat it like the list
 * it replaced) with a couple of extras ({@link #removeFirst()}, {@link #addFirst(String)})
 * used by play-next / play-previous.</p>
 *
 * <p>The queue also owns the tracks it has already played (the <em>history</em>) and the
 * two playback modes that need them:</p>
 * <ul>
 *   <li>{@link RepeatMode} — {@code ONE} replays the current track, {@code ALL} refills
 *       the queue from the history once the last entry has played, so the whole playlist
 *       loops.</li>
 *   <li>{@link #shuffle()} — a sticky flag rather than a one-off reorder, so each
 *       {@code ALL} round is <em>reshuffled</em> instead of replaying the same order.</li>
 * </ul>
 * <p>{@link #next(String)} and {@link #previous(String)} apply both, which keeps the
 * windows free of any transport logic beyond swapping their player.</p>
 *
 * <p>All access happens on the render/main thread, so no synchronization is needed.</p>
 */
final class PlayQueue {

    /**
     * How many played tracks to remember (bounds both "previous" and one loop round).
     */
    private static final int MAX_HISTORY = 256;

    private final List<String> urls = new ArrayList<>();
    /**
     * Tracks already played in this round, most recent last. Backs the "previous"
     * control and, under {@link RepeatMode#ALL}, becomes the next round.
     */
    private final List<String> history = new ArrayList<>();

    private RepeatMode repeat = RepeatMode.OFF;
    private boolean shuffle;

    boolean isEmpty() {
        return urls.isEmpty();
    }

    int size() {
        return urls.size();
    }

    String get(int index) {
        return urls.get(index);
    }

    /**
     * A defensive copy, in play order, for rendering.
     */
    List<String> snapshot() {
        return new ArrayList<>(urls);
    }

    /**
     * Appends a URL to the end of the queue.
     */
    void add(String url) {
        urls.add(url);
    }

    /**
     * Appends several URLs in order.
     */
    void addAll(Collection<String> more) {
        urls.addAll(more);
    }

    /**
     * Inserts a URL at the front (used by "previous" to restore the current track).
     */
    void addFirst(String url) {
        urls.add(0, url);
    }

    /**
     * Removes and returns the first queued URL, or throws if empty (guard with {@link #isEmpty()}).
     */
    String removeFirst() {
        return urls.remove(0);
    }

    /**
     * Removes and returns the entry at {@code index}.
     */
    String remove(int index) {
        return urls.remove(index);
    }

    /**
     * Moves an entry one place earlier in the queue.
     */
    void moveUp(int index) {
        if (index > 0 && index < urls.size()) {
            urls.add(index - 1, urls.remove(index));
        }
    }

    /**
     * Moves an entry one place later in the queue.
     */
    void moveDown(int index) {
        if (index >= 0 && index < urls.size() - 1) {
            urls.add(index + 1, urls.remove(index));
        }
    }

    void clear() {
        urls.clear();
        history.clear();
    }

    // ------------------------------------------------------------------
    // Repeat / shuffle
    // ------------------------------------------------------------------

    RepeatMode repeat() {
        return repeat;
    }

    void setRepeat(RepeatMode mode) {
        repeat = mode == null ? RepeatMode.OFF : mode;
    }

    /**
     * Steps to the next repeat mode (the loop button) and returns it.
     */
    RepeatMode cycleRepeat() {
        repeat = repeat.next();
        return repeat;
    }

    boolean shuffle() {
        return shuffle;
    }

    /**
     * Turns shuffle on or off. Turning it <em>on</em> also reorders what is already
     * queued, so the effect is immediate rather than only from the next loop round.
     */
    void setShuffle(boolean value) {
        shuffle = value;
        if (shuffle) {
            Collections.shuffle(urls);
        }
    }

    boolean toggleShuffle() {
        setShuffle(!shuffle);
        return shuffle;
    }

    /**
     * Whether {@link #next(String)} would have something to play — i.e. whether the
     * player should advance rather than close when the current track ends.
     */
    boolean hasNext() {
        return !urls.isEmpty() || !repeat.isOff();
    }

    boolean hasPrevious() {
        return !history.isEmpty();
    }

    /**
     * The track to play after {@code current}, or {@code null} when playback is over.
     *
     * <p>{@link RepeatMode#ONE} returns {@code current} itself (the window restarts it).
     * Otherwise the head of the queue is taken and {@code current} is remembered as
     * history; when the queue has run dry and {@link RepeatMode#ALL} is set, the round
     * just finished becomes the next one — reshuffled if {@link #shuffle()} is on.</p>
     */
    @Nullable
    String next(String current) {
        if (repeat == RepeatMode.ONE) {
            return current;
        }
        if (urls.isEmpty()) {
            if (repeat != RepeatMode.ALL) {
                return null;
            }
            // The finished track closes the round before the round is recycled, so it
            // takes its place in the next one exactly once.
            pushHistory(current);
            if (!startNextRound(current)) {
                return null;
            }
            return urls.remove(0);
        }
        pushHistory(current);
        return urls.remove(0);
    }

    /**
     * The previously played track, re-queuing {@code current} at the front so "next"
     * returns to it. {@code null} when nothing has been played yet.
     */
    @Nullable
    String previous(String current) {
        if (history.isEmpty()) {
            return null;
        }
        addFirst(current);
        return history.remove(history.size() - 1);
    }

    /**
     * Refills the queue with the round that just finished (reshuffling it when shuffle
     * is on). Returns {@code false} when there was nothing to replay.
     */
    private boolean startNextRound(String justPlayed) {
        List<String> round = new ArrayList<>(history);
        history.clear();
        if (round.isEmpty()) {
            return false;
        }
        if (shuffle) {
            Collections.shuffle(round);
            // A reshuffle that happens to start with the track that just ended would
            // sound like a stuck player, so push it further down the round.
            if (round.size() > 1 && round.get(0).equals(justPlayed)) {
                Collections.swap(round, 0, 1 + (int) (Math.random() * (round.size() - 1)));
            }
        }
        urls.addAll(round);
        return true;
    }

    private void pushHistory(String url) {
        history.add(url);
        while (history.size() > MAX_HISTORY) {
            history.remove(0);
        }
    }
}
