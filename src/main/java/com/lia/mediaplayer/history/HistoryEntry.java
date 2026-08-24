package com.lia.mediaplayer.history;

import com.lia.mediaplayer.api.MediaKind;

/**
 * One thing that was played, as {@link HistoryStore} remembers it.
 *
 * <p>Only the URL is kept, never the name: titles come from
 * {@link com.lia.mediaplayer.media.MediaTitleCache}, which resolves them for the rows
 * actually on screen. That is the same bargain {@link com.lia.mediaplayer.playlist.Playlist}
 * makes — a saved list of links, not a saved list of names that would go stale the day a
 * video is renamed.</p>
 *
 * @param url      the link as it was played
 * @param kind     which player took it, so the list can say what an entry is
 * @param playedAt when it last started, in epoch milliseconds
 * @param favorite whether the user marked it with the heart
 */
public record HistoryEntry(String url, MediaKind kind, long playedAt, boolean favorite) {

    /**
     * The same entry with its favourite flag flipped. The record is immutable so that
     * the store is the only thing that can change what is on disk.
     */
    public HistoryEntry withFavorite(boolean value) {
        return new HistoryEntry(url, kind, playedAt, value);
    }

    /**
     * The same entry, played again now.
     */
    public HistoryEntry playedNow(long millis) {
        return new HistoryEntry(url, kind, millis, favorite);
    }
}
