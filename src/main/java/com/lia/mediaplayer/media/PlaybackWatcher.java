package com.lia.mediaplayer.media;

import com.lia.mediaplayer.api.PlaybackState;
import com.lia.mediaplayer.api.event.PlaybackEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a player's polled state into {@link PlaybackEvent}s.
 *
 * <p>The events the API has always declared were never actually posted by anything, and
 * the reason is visible in the two engines: nothing in them wants to know that a public
 * event bus exists, and the moments an addon cares about — paused, resumed, ended,
 * failed, seeked — are <em>transitions</em>, which a player that only ever holds its
 * current state cannot report. So they are derived here instead, from the once-a-tick
 * sweep {@code MediaWindowOverlay.clientTick} already makes over every window.</p>
 *
 * <p>This class is the pure half of that: state in, event types out, no Minecraft and no
 * player. Whatever owns a player holds one and posts whatever it returns —
 * {@code gui.QueuedMediaWindow} for a window, {@link PlayerHandle} for the windowless
 * players an off-screen surface and a headless track are built on. It lives in
 * {@code media} for the usual reason: it is the same derivation for both, and neither
 * side should own the other's copy.</p>
 *
 * <p>Render/client thread only, like everything that polls a player.</p>
 */
public final class PlaybackWatcher {

    /** The state at the previous poll, or {@code null} before the first one. */
    private PlaybackState last;

    /** Whether a seek was in flight at the previous poll. */
    private boolean wasSeeking;

    /**
     * The events {@code now} implies, given what was seen last time. Empty when nothing
     * changed, which is the overwhelmingly common answer.
     *
     * <p>{@link PlaybackEvent.Type#STARTED} is <em>not</em> derived here: a player
     * spends its first moments in {@link PlaybackState#LOADING}, so "started" would
     * either fire before anything was playing or not at all for a track that failed to
     * open. The window posts it where it actually happens, when it swaps a player in.</p>
     *
     * <p>A seek is reported from {@code seeking} going false → true rather than from a
     * position jump: both engines expose the flag, it is set by every seek path there is
     * (the bar, the keyboard, the API), and a position comparison would also fire on the
     * ordinary discontinuity of a track change.</p>
     */
    public List<PlaybackEvent.Type> poll(PlaybackState now, boolean seeking) {
        List<PlaybackEvent.Type> events = new ArrayList<>(2);
        PlaybackState previous = last;
        last = now;
        boolean seekStarted = seeking && !wasSeeking;
        wasSeeking = seeking;

        if (previous != null && now != previous) {
            switch (now) {
                case PAUSED -> events.add(PlaybackEvent.Type.PAUSED);
                case PLAYING -> {
                    // Only a pause has a resume; LOADING → PLAYING is the track opening,
                    // which STARTED already covered.
                    if (previous == PlaybackState.PAUSED) {
                        events.add(PlaybackEvent.Type.RESUMED);
                    }
                }
                case ENDED -> events.add(PlaybackEvent.Type.ENDED);
                case FAILED -> events.add(PlaybackEvent.Type.FAILED);
                default -> {
                    // LOADING is only ever entered by a fresh player, and a fresh player
                    // gets a reset() rather than a transition.
                }
            }
        }
        if (seekStarted) {
            events.add(PlaybackEvent.Type.SEEKED);
        }
        return events;
    }

    /**
     * Forgets everything, for a window that has just swapped in a new player. Without
     * this the new player's {@code LOADING} would read as a transition out of the old
     * one's {@code ENDED}, and its first {@code PLAYING} as a resume.
     */
    public void reset() {
        last = null;
        wasSeeking = false;
    }
}
