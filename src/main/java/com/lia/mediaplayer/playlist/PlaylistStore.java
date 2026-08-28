package com.lia.mediaplayer.playlist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.lia.mediaplayer.source.Urls;
import com.lia.mediaplayer.storage.JsonFileStore;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * The user's saved {@link Playlist}s, persisted to
 * {@code <gamedir>/liasmediaplayer/playlists.json}. Loaded lazily on first access and
 * re-written after every change, so playlists survive between sessions.
 *
 * <p>All access goes through this class (the GUI never touches the file directly), and
 * every mutating call saves immediately, through a {@link JsonFileStore}.</p>
 *
 * <p><b>Render thread only.</b> {@link #all()} deliberately hands back the live list so
 * the screens can edit a {@link Playlist} in place and call {@link #save()}; that makes
 * locking here pointless — a caller mutating the escaped list would hold no lock anyway —
 * so this class does not pretend to be thread-safe. Every caller (the playlist and history
 * screens, and {@code MediaPlayerContext}, whose API contract already says main/render
 * thread) is on that thread.</p>
 */
public class PlaylistStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<Playlist>>() {
    }.getType();

    private final JsonFileStore file = new JsonFileStore("playlists.json");
    private final List<Playlist> playlists = new ArrayList<>();
    private boolean loaded;

    public PlaylistStore() {
    }

    /**
     * The live list of playlists (mutating a {@link Playlist} then calling {@link #save()} persists it).
     */
    public List<Playlist> all() {
        ensureLoaded();
        return playlists;
    }

    /**
     * Creates and saves a new, empty playlist.
     */
    public Playlist create(String name) {
        ensureLoaded();
        Playlist playlist = new Playlist(name == null || name.isBlank() ? "New playlist" : name.strip());
        playlists.add(playlist);
        save();
        return playlist;
    }

    public void delete(Playlist playlist) {
        ensureLoaded();
        if (playlists.remove(playlist)) {
            save();
        }
    }

    /**
     * Persists the current playlists to disk. Best-effort: a failure is logged, not thrown.
     */
    public void save() {
        file.write(GSON.toJson(playlists, LIST_TYPE));
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        load();
    }

    private void load() {
        String text = file.read();
        if (text == null) {
            return;
        }
        List<Playlist> parsed;
        try {
            parsed = GSON.fromJson(text, LIST_TYPE);
        } catch (RuntimeException e) {
            return; // a malformed file starts the session empty rather than failing it
        }
        if (parsed == null) {
            return;
        }
        for (Playlist playlist : parsed) {
            if (playlist != null && playlist.name() != null) {
                // Gson writes the urls field directly, so Playlist.add's gate was
                // never applied to what is on disk: re-apply it here, for the same
                // reason HistoryStore does — a hand-edited file must not smuggle a
                // file:// link back into ffmpeg on the next launch.
                playlist.urls().removeIf(url -> !Urls.isHttp(url));
                playlists.add(playlist);
            }
        }
    }
}
