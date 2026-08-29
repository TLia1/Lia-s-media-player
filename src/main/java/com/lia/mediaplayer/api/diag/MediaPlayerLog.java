/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.diag;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Playback failures, as they happen and as a short backlog.
 *
 * <p>Subscribe with {@link #addSink} to be told when something fails to play; read
 * {@link #recent()} to see what already has. The backlog exists because the addon that
 * wants to show a failure is very often opened <em>after</em> it happened — a "why did
 * that not play?" screen has nothing to say if it only sees what fails while it is
 * open.</p>
 *
 * <p>The backlog is bounded at {@value #BACKLOG} entries and cleared when the world
 * unloads, like everything else in the mod that holds per-session state.</p>
 *
 * <p>Sinks are called on the client thread. One that throws is logged and stays
 * subscribed. Remember {@link #removeSink}: this dispatcher is static and lives for the
 * process, so a sink that is never removed outlives every world.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 3.4.0
 */
public final class MediaPlayerLog {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** How many past failures {@link #recent()} remembers. */
    public static final int BACKLOG = 32;

    private static final List<Sink> SINKS = new CopyOnWriteArrayList<>();

    /** Guarded by itself. Written from the client thread, read from anywhere. */
    private static final List<MediaLogEntry> RECENT = new ArrayList<>();

    private MediaPlayerLog() {
    }

    /** Told about a playback failure. */
    @FunctionalInterface
    public interface Sink {
        void onFailure(MediaLogEntry entry);
    }

    public static void addSink(Sink sink) {
        if (sink != null) {
            SINKS.add(sink);
        }
    }

    public static void removeSink(Sink sink) {
        SINKS.remove(sink);
    }

    /** The most recent failures, newest last, at most {@value #BACKLOG}. A snapshot. */
    public static List<MediaLogEntry> recent() {
        synchronized (RECENT) {
            return Collections.unmodifiableList(new ArrayList<>(RECENT));
        }
    }

    /** Empties the backlog. Called by the mod on disconnect; an addon has no reason to. */
    public static void clear() {
        synchronized (RECENT) {
            RECENT.clear();
        }
    }

    /** Records a failure and tells every sink. Posted by the mod. */
    public static void post(MediaLogEntry entry) {
        if (entry == null) {
            return;
        }
        synchronized (RECENT) {
            RECENT.add(entry);
            while (RECENT.size() > BACKLOG) {
                RECENT.removeFirst();
            }
        }
        for (Sink sink : SINKS) {
            try {
                sink.onFailure(entry);
            } catch (RuntimeException e) {
                LOGGER.error("A media player log sink threw on {}", entry.url(), e);
            }
        }
    }
}
