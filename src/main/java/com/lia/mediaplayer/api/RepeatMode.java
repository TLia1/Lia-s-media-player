/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api;

/**
 * What a player does once the current track finishes.
 *
 * <p>The three states are cycled by the loop button on the player windows, in
 * declaration order, so {@link #next()} is the button's whole behaviour.</p>
 *
 * <p>This lived in the mod's {@code gui} package until API 2.3.0, where an addon could
 * not name it — which made {@code MediaQueue.setRepeat} and {@code MediaRequest.repeat}
 * impossible to express. Moving it was safe because {@code gui} was never public API;
 * the one thing that serializes it, {@code windows.json}, reads it by name and is
 * unaffected.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 2.3.0 — in {@code com.lia.mediaplayer.gui} before that
 */
public enum RepeatMode {
    /**
     * Stop when the queue runs out (the window then closes itself, unless the caller
     * asked otherwise through {@code MediaRequest.closeWhenEnded}).
     */
    OFF,
    /**
     * Start the queue over once its last entry has played. Everything played during
     * the round comes back, so the whole playlist loops — reshuffled each round when
     * shuffle is on.
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
