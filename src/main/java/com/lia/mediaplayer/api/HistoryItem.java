/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api;

/**
 * One line of the local play history — see {@link IMediaPlayerAPI#getHistory(int)}.
 *
 * <p><b>This is personal data.</b> It is local-only and never leaves the machine on its
 * own, but it is a record of what the user watched and listened to, and an addon that
 * reads it is reading that. Exposed deliberately, and documented as such so a pack author
 * can make an informed choice about an addon that uses it.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @param playedAtEpochMillis when it was last played, as {@code System.currentTimeMillis()}
 * @param favorite            whether the user kept it (favourites are never evicted)
 * @since API 2.1.0
 */
public record HistoryItem(String url, MediaKind kind, long playedAtEpochMillis, boolean favorite) {
}
