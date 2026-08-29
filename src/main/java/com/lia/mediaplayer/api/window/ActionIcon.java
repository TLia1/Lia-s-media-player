/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.window;

/**
 * The picture on a {@link WindowAction}'s button, named rather than drawn.
 *
 * <p>The sketch for window actions had an addon supply {@code int glyph()} — an atlas
 * sprite or a registered glyph id. It ships as a closed enum instead, because the mod's
 * icons are not sprites: {@code gui.Glyphs} draws every one of them from primitives, at
 * the one size the corner row uses, in whatever colour the hover state calls for. There
 * is no id to hand out. A name from this list is also what keeps an addon's button
 * looking like the mod's own in every theme, which is the point of the row.</p>
 *
 * <p>Constants are only ever added, never removed or renamed — an addon naming one has
 * to be able to resolve it on the version it runs against.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 3.2.0
 */
public enum ActionIcon {
    /** A heart. */
    HEART,
    /** Two stacked sheets — "copy". */
    COPY,
    /** A box with an arrow leaving it — "open elsewhere". */
    EXTERNAL_LINK,
    /** Lines with a plus — "add to a list". */
    ADD_TO_PLAYLIST,
    /** Stacked lines — "a queue". */
    QUEUE,
    /** A magnifying glass. */
    SEARCH,
    /** A circular arrow — "again". */
    REFRESH,
    /** A waste basket. */
    TRASH,
    /** A push pin. */
    PIN,
    /** A musical note. */
    NOTE,
    /** A filled square — "stop". */
    STOP,
    /** Corner brackets — "make it bigger". */
    FULLSCREEN,
    /** Crossing arrows — "shuffle". */
    SHUFFLE,
    /** A gauge — "speed". */
    SPEED,
    /** A triangle pointing up. */
    ARROW_UP,
    /** A triangle pointing down. */
    ARROW_DOWN
}
