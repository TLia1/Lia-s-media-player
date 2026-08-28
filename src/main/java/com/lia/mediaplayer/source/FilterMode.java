package com.lia.mediaplayer.source;

import com.lia.mediaplayer.chat.MediaFilters;

/**
 * How the client-side link filter treats the two host lists (see
 * {@link MediaFilters}).
 */
public enum FilterMode {
    /** No host filtering: every recognized link is offered, whoever posted it. */
    OFF,
    /** Everything except the blocked hosts. */
    BLOCKLIST,
    /** Nothing except the allowed hosts. */
    ALLOWLIST
}
