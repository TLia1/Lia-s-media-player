package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.MediaPlayerContext;
import com.lia.mediaplayer.api.MediaKind;
import com.lia.mediaplayer.api.MediaQueue;
import com.lia.mediaplayer.api.QueueEntry;
import com.lia.mediaplayer.api.RepeatMode;
import com.lia.mediaplayer.source.Urls;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A player window's {@link PlayQueue} as the public API sees it.
 *
 * <p>The same shape as {@link WindowHandle} and for the same reason: every operation
 * below already existed for the queue panel's buttons, and what was missing was an object
 * an addon could ask them of. {@link PlayQueue} itself stays package-private, so the
 * queue's internals — the played-tracks history that backs "previous" and a looping
 * round — remain the window's business.</p>
 *
 * <p>Every method is a no-op (or a neutral answer) once the window is gone, matching the
 * rule the handles follow.</p>
 *
 * <p>Render thread only.</p>
 */
public final class WindowQueue implements MediaQueue {

    private final QueuedMediaWindow<?> window;

    WindowQueue(QueuedMediaWindow<?> window) {
        this.window = window;
    }

    private boolean dead() {
        return !window.isAlive();
    }

    // ------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------

    @Override
    public List<QueueEntry> entries() {
        if (dead()) {
            return List.of();
        }
        List<String> urls = window.queue.snapshot();
        List<QueueEntry> out = new ArrayList<>(urls.size());
        for (String url : urls) {
            out.add(entryFor(url));
        }
        return List.copyOf(out);
    }

    @Override
    public int size() {
        return dead() ? 0 : window.queue.size();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    @Nullable
    public QueueEntry current() {
        return dead() ? null : entryFor(window.mediaUrl());
    }

    /**
     * One row, built from the caches the queue panel already draws from.
     *
     * <p>Nothing here starts a fetch. The panel's own rows do — that is what warms a
     * title as you scroll to it — but reading a queue through the API must not launch a
     * hundred lookups for a list the caller may only be counting.</p>
     */
    private QueueEntry entryFor(String url) {
        MediaPlayerContext context = MediaPlayerContext.get();
        String title = context.getTitleCache().getOrLoad(url);
        boolean thumb = window.mediaKind() == MediaKind.VIDEO
                && context.getThumbnailCache().hasThumbnail(url);
        long duration = url.equals(window.mediaUrl()) ? window.durationMicros() : -1L;
        // literal: the title is either what the site called it or the URL itself, and
        // both are already the text to show.
        return new QueueEntry(url, Component.literal(title), window.mediaKind(), duration, thumb);
    }

    // ------------------------------------------------------------------
    // Editing
    // ------------------------------------------------------------------

    @Override
    public void add(String url) {
        if (!dead() && isHttp(url)) {
            window.enqueue(url);
        }
    }

    @Override
    public void addAll(List<String> urls) {
        if (dead() || urls == null) {
            return;
        }
        for (String url : urls) {
            add(url);
        }
    }

    @Override
    public void insert(int index, String url) {
        if (!dead() && isHttp(url)) {
            window.queue.insert(index, url);
        }
    }

    @Override
    public void remove(int index) {
        if (!dead() && index >= 0 && index < window.queue.size()) {
            window.queue.remove(index);
        }
    }

    @Override
    public void move(int from, int to) {
        if (!dead()) {
            window.queue.move(from, to);
        }
    }

    @Override
    public void clear() {
        if (!dead()) {
            window.queue.clear();
        }
    }

    // ------------------------------------------------------------------
    // Transport
    // ------------------------------------------------------------------

    @Override
    public void jumpTo(int index) {
        if (!dead()) {
            window.jumpTo(index);
        }
    }

    @Override
    public void next() {
        if (!dead()) {
            window.advance();
        }
    }

    @Override
    public void previous() {
        if (!dead()) {
            window.previous();
        }
    }

    @Override
    public RepeatMode repeat() {
        return dead() ? RepeatMode.OFF : window.queue.repeat();
    }

    @Override
    public void setRepeat(RepeatMode mode) {
        if (!dead()) {
            window.setRepeat(mode);
        }
    }

    @Override
    public boolean shuffle() {
        return !dead() && window.queue.shuffle();
    }

    @Override
    public void setShuffle(boolean shuffle) {
        if (!dead()) {
            window.setShuffle(shuffle);
        }
    }

    /**
     * The gate every URL-taking entry point applies — an addon's string ends up in
     * ffmpeg exactly like a chat link does, so it passes exactly the same check.
     */
    private static boolean isHttp(@Nullable String url) {
        return Urls.isHttp(url);
    }
}
