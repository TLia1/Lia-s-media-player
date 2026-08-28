package com.lia.mediaplayer.history;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lia.mediaplayer.MediaPlayerContext;
import com.lia.mediaplayer.api.MediaKind;
import com.lia.mediaplayer.gui.WindowStateStore;
import com.lia.mediaplayer.playlist.PlaylistStore;
import com.lia.mediaplayer.source.Urls;
import com.lia.mediaplayer.storage.JsonFileStore;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What has been played, persisted to {@code <gamedir>/liasmediaplayer/history.json}.
 *
 * <p>Everything the mod played used to be gone the moment the window closed: a link
 * someone posted an hour ago could only be found by scrolling chat back to it, and there
 * was no way at all to keep one. This is the library that answers both — a bounded list
 * of what was played, most recent first, and a heart on each entry that takes it out of
 * the bound.</p>
 *
 * <p>Same shape as {@link PlaylistStore} and
 * {@link WindowStateStore}: loaded lazily, written through a
 * {@link JsonFileStore} (temp file plus atomic move), and never thrown from. The JSON is
 * built field by field rather than by reflection, because the Gson that ships with
 * Minecraft ranges from 2.8 to 2.14 across the fourteen targets and record support only
 * arrived in 2.10.</p>
 */
public final class HistoryStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final JsonFileStore file = new JsonFileStore("history.json");

    /**
     * How many ordinary entries are kept. Favourites are <em>not</em> counted against
     * it and are never evicted: the point of the heart is that the entry stops being
     * something that scrolls off the end.
     */
    public static final int MAX_ENTRIES = 200;

    /** Most recently played first — the order the screen shows and the file stores. */
    private final List<HistoryEntry> entries = new ArrayList<>();
    private boolean loaded;

    public HistoryStore() {
    }

    // ------------------------------------------------------------------
    // Static convenience
    // ------------------------------------------------------------------

    /**
     * Records a play against the live store, or does nothing when there is none.
     *
     * <p>Static because playback starts from half a dozen places — a manager opening a
     * window, a window swapping its player as the queue advances — and each of them
     * would otherwise have to find the context to reach a one-line call. The store is
     * the thing that knows how to find itself, so it does.</p>
     */
    public static void record(String url, MediaKind kind) {
        MediaPlayerContext context = MediaPlayerContext.getOrNull();
        if (context != null) {
            context.getHistoryStore().add(url, kind);
        }
    }

    // ------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------

    /**
     * A snapshot of the history, most recent first.
     */
    public synchronized List<HistoryEntry> all() {
        ensureLoaded();
        return new ArrayList<>(entries);
    }

    /**
     * Only the entries carrying the heart, most recent first.
     */
    public synchronized List<HistoryEntry> favorites() {
        ensureLoaded();
        List<HistoryEntry> favorites = new ArrayList<>();
        for (HistoryEntry entry : entries) {
            if (entry.favorite()) {
                favorites.add(entry);
            }
        }
        return favorites;
    }

    public synchronized boolean isFavorite(String url) {
        ensureLoaded();
        HistoryEntry entry = find(url);
        return entry != null && entry.favorite();
    }

    // ------------------------------------------------------------------
    // Writing
    // ------------------------------------------------------------------

    /**
     * Records that {@code url} started playing.
     *
     * <p>An entry already in the list moves back to the top rather than being duplicated
     * — a track played five times is one line in the library, not five — and keeps
     * whatever the user did to it.</p>
     */
    public synchronized void add(String url, MediaKind kind) {
        if (!Urls.isHttp(url) || kind == null) {
            return;
        }
        ensureLoaded();
        long now = System.currentTimeMillis();
        HistoryEntry existing = find(url);
        if (existing != null) {
            entries.remove(existing);
            entries.add(0, existing.playedNow(now));
        } else {
            entries.add(0, new HistoryEntry(url, kind, now, false));
        }
        trim();
        save();
    }

    /**
     * Flips the heart on {@code url} and returns its new state.
     *
     * <p>A URL that is not in the history yet is added by this — favouriting something
     * from a window that outlived its history entry has to have somewhere to put it.</p>
     */
    public synchronized boolean toggleFavorite(String url, MediaKind kind) {
        if (!Urls.isHttp(url)) {
            return false;
        }
        ensureLoaded();
        HistoryEntry existing = find(url);
        if (existing == null) {
            if (kind == null) {
                return false;
            }
            entries.add(0, new HistoryEntry(url, kind, System.currentTimeMillis(), true));
            trim();
            save();
            return true;
        }
        HistoryEntry updated = existing.withFavorite(!existing.favorite());
        entries.set(entries.indexOf(existing), updated);
        save();
        return updated.favorite();
    }

    /**
     * Drops one entry, heart or no heart.
     */
    public synchronized void remove(String url) {
        ensureLoaded();
        HistoryEntry existing = find(url);
        if (existing != null) {
            entries.remove(existing);
            save();
        }
    }

    /**
     * Empties the history, <strong>keeping the favourites</strong>. Clearing a history is
     * about the things that piled up on their own; the ones that were deliberately kept
     * are not part of that, and losing them to a button labelled "clear" would be a
     * surprise with no undo.
     */
    public synchronized void clear() {
        ensureLoaded();
        boolean changed = entries.removeIf(entry -> !entry.favorite());
        if (changed) {
            save();
        }
    }

    @Nullable
    private HistoryEntry find(String url) {
        for (HistoryEntry entry : entries) {
            if (entry.url().equals(url)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Drops the oldest ordinary entries past {@link #MAX_ENTRIES}, skipping favourites.
     */
    private void trim() {
        int ordinary = 0;
        for (HistoryEntry entry : entries) {
            if (!entry.favorite()) {
                ordinary++;
            }
        }
        for (int i = entries.size() - 1; i >= 0 && ordinary > MAX_ENTRIES; i--) {
            if (!entries.get(i).favorite()) {
                entries.remove(i);
                ordinary--;
            }
        }
    }

    // ------------------------------------------------------------------
    // Serialization (no Minecraft types — the unit tests drive these directly)
    // ------------------------------------------------------------------

    static String toJson(List<HistoryEntry> entries) {
        JsonArray array = new JsonArray();
        for (HistoryEntry entry : entries) {
            JsonObject json = new JsonObject();
            json.addProperty("url", entry.url());
            json.addProperty("kind", entry.kind().name());
            json.addProperty("playedAt", entry.playedAt());
            json.addProperty("favorite", entry.favorite());
            array.add(json);
        }
        return GSON.toJson(array);
    }

    /**
     * Reads the file back, skipping anything malformed rather than failing the lot: it
     * may have been written by an older version of the mod, or hand-edited.
     */
    static List<HistoryEntry> fromJson(String text) {
        List<HistoryEntry> parsed = new ArrayList<>();
        JsonArray array;
        try {
            array = GSON.fromJson(text, JsonArray.class);
        } catch (RuntimeException e) {
            return parsed;
        }
        if (array == null) {
            return parsed;
        }
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            HistoryEntry entry = entryFromJson(element.getAsJsonObject());
            if (entry != null) {
                parsed.add(entry);
            }
        }
        return parsed;
    }

    @Nullable
    private static HistoryEntry entryFromJson(JsonObject json) {
        try {
            JsonElement url = json.get("url");
            JsonElement kind = json.get("kind");
            if (url == null || kind == null) {
                return null;
            }
            String link = url.getAsString();
            if (!Urls.isHttp(link)) {
                return null; // a hand-edited file must not smuggle a file:// link back in
            }
            JsonElement playedAt = json.get("playedAt");
            JsonElement favorite = json.get("favorite");
            return new HistoryEntry(
                    link,
                    MediaKind.valueOf(kind.getAsString()),
                    playedAt == null ? 0L : playedAt.getAsLong(),
                    favorite != null && favorite.getAsBoolean());
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // File access
    // ------------------------------------------------------------------

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        String text = file.read();
        if (text != null) {
            entries.addAll(fromJson(text));
        }
    }

    private void save() {
        file.write(toJson(Collections.unmodifiableList(entries)));
    }
}
