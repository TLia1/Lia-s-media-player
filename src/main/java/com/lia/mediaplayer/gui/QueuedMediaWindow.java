package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.api.MediaKind;
import com.lia.mediaplayer.history.HistoryStore;
import com.lia.mediaplayer.media.MediaPlayback;
import com.lia.mediaplayer.media.PlaybackError;

import net.minecraft.util.Mth;

import java.util.Collection;

/**
 * A {@link MediaWindow} that plays one item at a time out of a queue — everything the
 * video player and the audio bar do <em>as players</em>, as opposed to as windows.
 *
 * <p>{@link MediaWindow} knows about geometry, chrome and the shared z-order; it has no
 * idea that a window might have a list of things to play next. That left "window with a
 * queue" with no representative, and the two players carrying a copy each of the same
 * queue, the same docked {@link QueuePanel}, the same seek/volume dragging, the same
 * transport, and the same state restoration. This is that behaviour, once.</p>
 *
 * <p>The player is held as its own type ({@code P}), not as {@link MediaPlayback}: the
 * shared code below only needs the common transport, but the subclasses need the parts
 * that are genuinely theirs — a frame to blit and a picture size, or an audio-only
 * {@code State} enum — and a generic parameter gives them both without a cast.</p>
 *
 * <p>Render thread only, like every window.</p>
 */
abstract class QueuedMediaWindow<P extends MediaPlayback> extends MediaWindow {

    /**
     * How many entries of a bulk enqueue get their thumbnail/title fetched up front.
     *
     * <p>Both caches are small LRUs backed by a network (or ffmpeg) call per entry, so
     * eagerly warming hundreds of them would thrash the cache and fire hundreds of
     * requests for rows nobody has scrolled to. The panel loads the rest as it draws
     * them.</p>
     */
    private static final int WARM_AHEAD = 10;

    /** The item playing now. Swapped in place by {@link #playUrl} as the queue advances. */
    protected P player;

    /** URLs waiting to play in this same window, in play order. */
    protected final PlayQueue queue = new PlayQueue();

    /**
     * The list of what plays next, docked beside the window. Shared between the two
     * players (see {@link QueuePanel}); each subclass only says which layout it has room
     * for.
     */
    protected final QueuePanel panel = new QueuePanel(queue, this::jumpTo);

    // Seek/volume dragging: identical in both players, and reset whenever the player is
    // swapped out from under the cursor.
    protected boolean draggingSeek;
    protected boolean draggingVolume;
    protected double scrubFraction;

    // Control-bar hit regions, cached by each subclass's layoutControls. The two bars are
    // laid out differently — the audio bar has one more track button and no picture above
    // it — but they are made of the same controls, and the shared drag handling below
    // reads the seek bar and the volume pop-up straight out of these.
    protected int playBtnX, playBtnY;
    protected int backBtnX, backBtnY;
    protected int fwdBtnX, fwdBtnY;
    protected int nextBtnX, nextBtnY;
    protected int loopBtnX, loopBtnY;
    protected int shuffleBtnX, shuffleBtnY;
    protected boolean showQueueBtn;
    protected int queueBtnX, queueBtnY;
    protected int volBtnX, volBtnY;
    protected boolean showVolumePopup;
    protected int volBarX, volBarY;
    protected int seekX, seekY, seekW, seekH;
    protected int timeTextX;

    protected QueuedMediaWindow(P player) {
        this.player = player;
    }

    P player() {
        return player;
    }

    // ------------------------------------------------------------------
    // Per-engine seams
    // ------------------------------------------------------------------

    /** A fresh, unstarted player for {@code url} — the one thing only the engine knows. */
    protected abstract P createPlayer(String url);

    /** Which player this window is, for the history entry every play records. */
    protected abstract MediaKind playbackKind();

    /**
     * Pre-fetches whatever the queue panel will want to draw for {@code url} — a title
     * for both, plus a thumbnail for the video player.
     */
    protected abstract void warmCaches(String url);

    /**
     * A fresh player has replaced the previous one; give it whatever the window already
     * knows that a new player cannot.
     *
     * <p>Called before {@code start()}, so a subclass can set the new player up before
     * it opens anything. What needs it today is visibility: a hidden video window
     * advancing to its next track would otherwise get a player that starts decoding a
     * picture nobody is looking at, since only the window knows it is hidden.</p>
     */
    protected void onPlayerSwapped(P freshPlayer) {
    }

    // ------------------------------------------------------------------
    // Queue
    // ------------------------------------------------------------------

    /**
     * Appends a URL to this window's play queue (it plays after the current ones).
     */
    void enqueue(String url) {
        queue.add(url);
        warmCaches(url);
    }

    /**
     * Appends several URLs in order — an expanded YouTube playlist, typically. Only the
     * first {@link #WARM_AHEAD} are warmed; see that constant for why.
     */
    void enqueueAll(Collection<String> urls) {
        int warmed = 0;
        for (String url : urls) {
            if (warmed++ < WARM_AHEAD) {
                enqueue(url);
            } else {
                queue.add(url);
            }
        }
    }

    /**
     * Number of URLs still waiting to play after the current one.
     */
    int queueSize() {
        return queue.size();
    }

    /**
     * Disposes the current player and starts the next item in the same window — the head
     * of the queue, the current item again under {@link RepeatMode#ONE}, or the start of
     * a fresh round under {@link RepeatMode#ALL}. Returns {@code false} (and leaves the
     * current player untouched) when there is nothing left to play, so callers can close
     * the window instead.
     */
    boolean advance() {
        String next = queue.next(player.url());
        if (next == null) {
            return false;
        }
        playUrl(next);
        return true;
    }

    /**
     * Goes back to the previously played item, re-queuing the current one at the front so
     * "next" returns to it. Returns {@code false} when there is no history.
     *
     * <p>Only the audio bar wires this to a control and to {@link #playPrevious()}: a
     * video window has no "previous" button, and inventing one for it is not what
     * sharing the queue model was for. The logic still belongs here, beside
     * {@link #advance()}, so the two halves of the same {@link PlayQueue} traversal do
     * not drift apart.</p>
     */
    boolean previous() {
        String prev = queue.previous(player.url());
        if (prev == null) {
            return false;
        }
        playUrl(prev);
        return true;
    }

    /**
     * Plays a specific queued entry now (the others keep their order) — what a click on a
     * row of the queue panel means.
     */
    void jumpTo(int index) {
        if (index < 0 || index >= queue.size()) {
            return;
        }
        playUrl(queue.remove(index));
    }

    /**
     * Sets how this window loops (see {@link RepeatMode}).
     */
    void setRepeat(RepeatMode mode) {
        queue.setRepeat(mode);
    }

    /**
     * Keeps shuffle on for this window, so every looped round is reshuffled rather than
     * replaying the order the first round happened to get.
     */
    void setShuffle(boolean value) {
        queue.setShuffle(value);
    }

    /**
     * Swaps in a new player for the given URL, disposing the current one.
     *
     * <p>Any drag in progress is dropped: the bar the cursor was holding belongs to a
     * player that no longer exists, and releasing it would seek the new one to wherever
     * the old one's bar happened to be.</p>
     */
    protected final void playUrl(String url) {
        HistoryStore.record(url, playbackKind());
        player.dispose();
        draggingSeek = false;
        draggingVolume = false;
        player = createPlayer(url);
        onPlayerSwapped(player);
        player.start();
        announceIfHidden(url);
    }

    /**
     * Starts the current item over from scratch — a fresh player, a fresh resolve, a
     * fresh ffmpeg. What the retry button on a failed player does, and the only sensible
     * answer to most of the causes {@link PlaybackError} names: an expired stream URL, a
     * timeout, a network blip.
     */
    void retry() {
        playUrl(player.url());
    }

    /**
     * Starts the player this window was created with. The manager calls this once, after
     * the window has joined the stack, so a player that fails instantly still has a
     * window to report it in.
     */
    void startPlayback() {
        player.start();
    }

    /**
     * Disposes the current player and discards anything still queued.
     */
    void disposeAll() {
        queue.clear();
        player.dispose();
    }

    // ------------------------------------------------------------------
    // MediaWindow contract
    // ------------------------------------------------------------------

    @Override
    protected String mediaUrl() {
        return player.url();
    }

    @Override
    protected WindowStateStore.State decorateState(WindowStateStore.State geometry) {
        return new WindowStateStore.State(geometry.placed(), geometry.x(), geometry.y(),
                geometry.sized(), geometry.width(),
                panel.isOpen(), queue.repeat(), queue.shuffle());
    }

    @Override
    protected void applyRestoredState(WindowStateStore.State state) {
        panel.setOpen(state.queuePanel());
        queue.setRepeat(state.repeat());
        queue.setShuffle(state.shuffle());
    }

    // ------------------------------------------------------------------
    // Transport (keyboard shortcuts; the control bar reaches the same actions)
    // ------------------------------------------------------------------

    @Override
    boolean hasTransport() {
        return true;
    }

    @Override
    boolean togglePlayPause() {
        player.togglePause();
        return true;
    }

    @Override
    boolean seekBy(long deltaMicros) {
        long duration = player.durationMicros();
        if (duration <= 0) {
            return false; // a live stream has no position to seek within
        }
        player.seekTo(Mth.clamp(player.positionMicros() + deltaMicros, 0, duration));
        return true;
    }

    @Override
    long positionMicros() {
        return player.positionMicros();
    }

    @Override
    boolean playNext() {
        return advance();
    }

    @Override
    boolean cycleRepeat() {
        queue.cycleRepeat();
        return true;
    }

    @Override
    boolean toggleShuffle() {
        queue.toggleShuffle();
        return true;
    }

    // ------------------------------------------------------------------
    // Control input
    // ------------------------------------------------------------------

    @Override
    protected boolean onControlDrag(double mouseX, double mouseY) {
        if (draggingVolume) {
            player.setVolume((float) MediaControls.volumeFractionAt(mouseY, volBarY));
            return true;
        }
        if (draggingSeek) {
            scrubFraction = MediaControls.fractionAt(mouseX, seekX, seekW);
            return true;
        }
        return false;
    }

    @Override
    protected boolean onControlRelease() {
        if (draggingVolume) {
            draggingVolume = false;
            return true;
        }
        if (draggingSeek) {
            draggingSeek = false;
            player.seekToFraction(scrubFraction);
            return true;
        }
        return false;
    }

    @Override
    protected boolean overExtraRegion(double mouseX, double mouseY) {
        return panel.contains(mouseX, mouseY);
    }
}
