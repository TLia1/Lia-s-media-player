package com.lia.mediaplayer.gui;

/**
 * What a {@link PlayQueue} does once the current track finishes.
 *
 * <p>The three states are cycled by the loop button on the player windows, in
 * declaration order, so {@link #next()} is the button's whole behaviour.</p>
 */
public enum RepeatMode {
    /**
     * Stop when the queue runs out (the window then closes itself).
     */
    OFF,
    /**
     * Start the queue over once its last entry has played. Everything played during
     * the round comes back, so the whole playlist loops — reshuffled each round when
     * {@linkplain PlayQueue#shuffle() shuffle} is on.
     */
    ALL,
    /**
     * Replay the current track forever; the queue is left untouched.
     */
    ONE;

    /**
     * The next mode in the button's cycle ({@code OFF → ALL → ONE → OFF}).
     */
    public RepeatMode next() {
        RepeatMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public boolean isOff() {
        return this == OFF;
    }
}
