/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.policy;

/**
 * Who asked for something to be played — the piece of context a
 * {@link MediaInterceptor} almost always needs before it can answer.
 *
 * <p>An addon that gates media usually wants to gate what <em>other people</em> put in
 * front of the player, not its own calls: a moderation addon vetoing
 * {@link #CHAT_CLICK} while letting {@link #API} through is the common case, and without
 * this it would have to veto both or neither.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 3.2.0
 */
public enum PlayOrigin {

    /** The user clicked a rewritten link in chat, or pressed a window shortcut over one. */
    CHAT_CLICK,

    /** The {@code /show} command. */
    COMMAND,

    /** One of the mod's key bindings — "play from clipboard" is the one that plays a link. */
    KEYBIND,

    /** A saved playlist was started, from the playlist screen or through the API. */
    PLAYLIST,

    /** Something was replayed from the history screen. */
    HISTORY,

    /** Another mod called {@code IMediaPlayerAPI.play}, or one of its {@code long}-id siblings. */
    API,

    /**
     * The mod is putting back what was playing before — a window restored from
     * {@code windows.json}, or a queue re-opened after an expansion. Vetoing these is
     * rarely what an addon means, which is why they are told apart from the rest.
     */
    RESTORE
}
