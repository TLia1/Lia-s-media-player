package com.lia.mediaplayer.gui;

import java.util.ArrayList;
import java.util.Collection;
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
 * <p>All access happens on the render/main thread, so no synchronization is needed.</p>
 */
final class PlayQueue {

    private final List<String> urls = new ArrayList<>();

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
    }
}
