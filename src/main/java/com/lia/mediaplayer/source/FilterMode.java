package com.lia.mediaplayer.source;

/**
 * How the client-side link filter treats the two host lists (see
 * {@link com.lia.mediaplayer.chat.MediaFilters}).
 */
public enum FilterMode {
    /** No host filtering: every recognized link is offered, whoever posted it. */
    OFF,
    /** Everything except the blocked hosts. */
    BLOCKLIST,
    /** Nothing except the allowed hosts. */
    ALLOWLIST
}
