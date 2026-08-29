package com.lia.mediaplayer.media;

import com.lia.mediaplayer.MediaPlayerContext;
import com.lia.mediaplayer.api.MediaHandle;
import com.lia.mediaplayer.api.MediaKind;
import com.lia.mediaplayer.api.MediaQueue;
import com.lia.mediaplayer.api.PlaybackState;
import com.lia.mediaplayer.api.audio.AudioControls;
import com.lia.mediaplayer.api.event.PlaybackEvent;
import com.lia.mediaplayer.api.event.PlaybackEvents;
import com.lia.mediaplayer.api.event.PlaybackListener;
import com.lia.mediaplayer.api.window.MediaWindowHandle;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A {@link MediaHandle} over a player with no window behind it.
 *
 * <p>The counterpart of {@code gui.WindowHandle}: the same interface, over a
 * {@link MediaPlayback} that nothing draws. {@code window()} and {@code queue()} are
 * empty, which is exactly what those {@link Optional}s were put there for — a windowless
 * player has no geometry to move and no list of what plays next.</p>
 *
 * <p>Two things are built on this and it lives in {@code media} because of it: an
 * off-screen video {@code surface.SurfaceEntry}, and a headless track from
 * {@code audio.HeadlessAudio}. They differ only in which engine is behind them and which
 * {@link MediaKind} they report, so neither owns the other's copy.</p>
 *
 * <p>Render thread only, like every handle.</p>
 */
public final class PlayerHandle implements MediaHandle {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Handed out by the same kind of counter the windows use, and never overlapping one. */
    private static long idSeq;

    private final long id = --idSeq;
    private final MediaPlayback player;
    private final MediaKind kind;
    private final Runnable onClose;
    private final List<PlaybackListener> listeners = new CopyOnWriteArrayList<>();

    /** Derives this player's transitions once a tick — see {@link PlaybackWatcher}. */
    private final PlaybackWatcher watcher = new PlaybackWatcher();

    private boolean alive = true;
    private boolean pendingPause;
    private boolean startedPosted;

    public PlayerHandle(MediaPlayback player, MediaKind kind, Runnable onClose) {
        this.player = player;
        this.kind = kind;
        this.onClose = onClose;
    }

    /**
     * Ask for a pause as soon as there is something to pause — see
     * {@code SurfaceOptions.autoplay} and {@code AudioOptions}. A player spends its first
     * moments opening a stream, where {@code pause()} has nothing to act on and is
     * ignored.
     */
    public void requestPauseOnStart() {
        pendingPause = true;
    }

    /** Whether a {@link #requestPauseOnStart()} has not been honoured yet. */
    public boolean isPausePending() {
        return pendingPause;
    }

    /** Called once a tick by the entry that owns this. */
    public void applyPendingPause() {
        if (pendingPause && player.playbackState() == PlaybackState.PLAYING) {
            pendingPause = false;
            player.pause();
        }
    }

    /**
     * Derives and posts this tick's playback events. Called once a tick by whatever owns
     * this handle, the same way {@code gui.QueuedMediaWindow} does for a window: an addon
     * that asked for a sound with no interface still wants to be told when it ended or
     * failed, and there is no window stack to notice that for it.
     */
    public void pollPlaybackEvents() {
        if (!alive) {
            return;
        }
        if (!startedPosted && player.playbackState() == PlaybackState.PLAYING) {
            // STARTED is posted where it happens rather than derived, for the reason
            // PlaybackWatcher's javadoc gives: a player's first state is LOADING.
            startedPosted = true;
            postEvent(PlaybackEvent.Type.STARTED);
        }
        for (PlaybackEvent.Type type : watcher.poll(player.playbackState(), player.isSeeking())) {
            postEvent(type);
        }
    }

    /**
     * Announces that this player has gone for good and makes the handle inert. Called
     * from the owner's disposal path, whichever of them ran.
     */
    public void markDead() {
        if (!alive) {
            return;
        }
        postEvent(PlaybackEvent.Type.STOPPED);
        alive = false;
        listeners.clear();
    }

    /**
     * Builds an event describing this player right now and dispatches it to this
     * handle's own listeners and to the global {@link PlaybackEvents} — the same two
     * places a window's events go.
     */
    private void postEvent(PlaybackEvent.Type type) {
        PlaybackEvent event = new PlaybackEvent(type, playerKind(), player.url(),
                player.playbackState(), Math.max(0, player.positionMicros()),
                Math.max(0, player.durationMicros()), this);
        dispatch(event);
        PlaybackEvents.post(event);
    }

    private PlaybackEvent.PlayerKind playerKind() {
        return switch (kind) {
            case VIDEO -> PlaybackEvent.PlayerKind.VIDEO;
            case AUDIO -> PlaybackEvent.PlayerKind.AUDIO;
            case IMAGE -> PlaybackEvent.PlayerKind.IMAGE;
        };
    }

    private void dispatch(PlaybackEvent event) {
        for (PlaybackListener listener : listeners) {
            try {
                listener.onPlayback(event);
            } catch (RuntimeException e) {
                LOGGER.error("A handle listener threw on {}", event.getType(), e);
            }
        }
    }

    // ------------------------------------------------------------------
    // Identity
    // ------------------------------------------------------------------

    /**
     * Negative, deliberately: window ids count up from one, so a windowless player's
     * handle can never collide with a window's and {@code api.getHandle(id)} cannot be
     * made to answer with the wrong thing.
     */
    @Override
    public long id() {
        return id;
    }

    @Override
    public String url() {
        return player.url();
    }

    @Override
    public MediaKind kind() {
        return kind;
    }

    @Override
    public PlaybackState state() {
        return alive ? player.playbackState() : PlaybackState.ENDED;
    }

    @Override
    public boolean isAlive() {
        return alive;
    }

    // ------------------------------------------------------------------
    // Transport
    // ------------------------------------------------------------------

    @Override
    public void play() {
        if (alive) {
            pendingPause = false;
            player.resume();
        }
    }

    @Override
    public void pause() {
        if (alive) {
            player.pause();
        }
    }

    @Override
    public void togglePause() {
        if (alive) {
            player.togglePause();
        }
    }

    @Override
    public void stop() {
        if (alive) {
            player.pause();
            player.seekTo(0);
        }
    }

    @Override
    public void close() {
        if (alive) {
            onClose.run();
        }
    }

    @Override
    public long positionMicros() {
        return alive ? Math.max(0, player.positionMicros()) : 0L;
    }

    @Override
    public long durationMicros() {
        return alive ? player.durationMicros() : -1L;
    }

    @Override
    public double progress() {
        return alive ? player.progress() : 0.0;
    }

    @Override
    public void seekTo(long micros) {
        if (alive) {
            player.seekTo(Math.max(0, micros));
        }
    }

    @Override
    public void seekToFraction(double fraction) {
        if (alive) {
            player.seekToFraction(fraction);
        }
    }

    // ------------------------------------------------------------------
    // Presentation — there is none
    // ------------------------------------------------------------------

    @Override
    public Component title() {
        return Component.literal(MediaPlayerContext.get().getTitleCache().getOrLoad(player.url()));
    }

    @Override
    public boolean isVisible() {
        // Whether anyone draws this player is the caller's business and the caller's
        // secret; the mod does not draw it and has no honest answer but "no".
        return false;
    }

    @Override
    public void setVisible(boolean visible) {
    }

    @Override
    public void bringToFront() {
    }

    @Override
    public Optional<MediaWindowHandle> window() {
        return Optional.empty();
    }

    @Override
    public Optional<MediaQueue> queue() {
        return Optional.empty();
    }

    @Override
    public Optional<AudioControls> audio() {
        // Present even for a video: a soundtrack is placed and scaled by exactly the same
        // controls a headless track's is.
        return Optional.of(player.audioGain());
    }

    // ------------------------------------------------------------------
    // Events
    // ------------------------------------------------------------------

    @Override
    public void addListener(PlaybackListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeListener(PlaybackListener listener) {
        listeners.remove(listener);
    }
}
