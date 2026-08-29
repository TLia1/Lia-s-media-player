package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.api.event.PlaybackEvent;
import com.lia.mediaplayer.api.sync.MediaSync;
import com.lia.mediaplayer.api.sync.SyncAction;
import com.lia.mediaplayer.api.sync.SyncControl;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Watch-together, on the mod's side of the fence — the implementation of
 * {@link SyncControl}, and the one place a local transport action is turned into a
 * {@link SyncAction} for an addon to broadcast.
 *
 * <p><b>The mod ships no protocol.</b> There is no packet, no channel and no server side
 * anywhere in here, and there is not going to be: this is a client-only mod. What it
 * ships is the pair of hooks an addon that owns a channel needs — this, and
 * {@code api.sync.PlaybackSyncAdapter}.</p>
 *
 * <p>It lives in {@code gui} because everything it acts on is a window: locking is a
 * property of a window's input handling, and {@link MediaWindowOverlay#windowOf} is how
 * an id becomes something to act on. Nothing below {@code gui} needs to know sync
 * exists.</p>
 *
 * <p>Render thread only.</p>
 */
public final class MediaSyncControl implements SyncControl {

    @Override
    public void apply(SyncAction action) {
        if (action == null || action.type() == null) {
            return;
        }
        MediaWindow window = MediaWindowOverlay.windowOf(action.handleId());
        if (window == null) {
            // A session that has fallen this far behind is repaired by playing the media
            // again, not by this. Silently, because a late packet for a window that was
            // just closed is ordinary, not exceptional.
            return;
        }
        // Everything below happens with broadcasting suppressed, so the transitions it
        // causes do not go back out and start a ping-pong between the two clients.
        MediaSync.whileApplying(() -> applyTo(window, action));
    }

    private void applyTo(MediaWindow window, SyncAction action) {
        switch (action.type()) {
            case PLAY -> {
                window.play();
                // The position on the wire is where the sender was when they pressed the
                // key; the packet has been in flight since. Converge on where they are
                // *now* rather than on where they were.
                driftCorrect(window, action.projectedPositionMicros(), 0L);
            }
            case PAUSE -> {
                window.pause();
                window.seekTo(action.positionMicros());
            }
            case SEEK -> window.seekTo(action.positionMicros());
            case NEXT -> window.playNext();
            case PREVIOUS -> window.playPrevious();
            case STOP -> {
                // What the handle's stop() means: paused at the start, still open. A
                // remote STOP is not a remote close — closing someone else's window is
                // not something a sync packet may do.
                window.pause();
                window.seekTo(0);
            }
            case ENQUEUE -> {
                if (action.url() != null && window instanceof QueuedMediaWindow<?> queued) {
                    queued.enqueue(action.url());
                }
            }
        }
    }

    @Override
    public void setLocked(long handleId, boolean locked, @Nullable Component reason) {
        MediaWindow window = MediaWindowOverlay.windowOf(handleId);
        if (window != null) {
            window.setLocked(locked, reason);
        }
    }

    @Override
    public boolean isLocked(long handleId) {
        MediaWindow window = MediaWindowOverlay.windowOf(handleId);
        return window != null && window.isLocked();
    }

    @Override
    public boolean driftCorrect(long handleId, long targetMicros, long toleranceMicros) {
        MediaWindow window = MediaWindowOverlay.windowOf(handleId);
        return window != null && driftCorrect(window, targetMicros, toleranceMicros);
    }

    private static boolean driftCorrect(MediaWindow window, long targetMicros, long toleranceMicros) {
        return window.driftCorrect(Math.max(0L, targetMicros), toleranceMicros);
    }

    // ------------------------------------------------------------------
    // Broadcasting
    // ------------------------------------------------------------------

    /**
     * Turns a playback event a window just posted into a {@link SyncAction}, when it is
     * one an addon would want to put on the wire.
     *
     * <p>Derived from the events rather than emitted at each transport call site for the
     * same reason the events themselves are derived: pause, resume and seek can be
     * reached from the control bar, the keyboard, a key binding and the API, and one
     * derivation covers all four. The three the events cannot express — a queue advancing
     * forward or back, and something being added to it — are posted by
     * {@code QueuedMediaWindow} where they happen.</p>
     *
     * <p>{@link MediaSync#broadcast} drops everything while a remote action is being
     * applied, which is what keeps two clients from echoing each other forever.</p>
     */
    static void broadcast(long handleId, @Nullable String url, PlaybackEvent.Type type,
                          long positionMicros) {
        SyncAction.Type mapped = switch (type) {
            case STARTED, RESUMED -> SyncAction.Type.PLAY;
            case PAUSED -> SyncAction.Type.PAUSE;
            case SEEKED -> SyncAction.Type.SEEK;
            case ENDED, STOPPED -> SyncAction.Type.STOP;
            default -> null;
        };
        if (mapped != null) {
            MediaSync.broadcast(SyncAction.now(handleId, url, mapped, Math.max(0L, positionMicros)));
        }
    }

    /** The three a playback event cannot express — see {@link #broadcast}. */
    static void broadcast(long handleId, @Nullable String url, SyncAction.Type type,
                          long positionMicros) {
        MediaSync.broadcast(SyncAction.now(handleId, url, type, Math.max(0L, positionMicros)));
    }
}
