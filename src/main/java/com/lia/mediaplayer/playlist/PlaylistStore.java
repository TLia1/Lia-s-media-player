package com.lia.mediaplayer.playlist;

import com.lia.mediaplayer.LiasMediaPlayer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The user's saved {@link Playlist}s, persisted to
 * {@code <gamedir>/liasmediaplayer/playlists.json}. Loaded lazily on first access and
 * re-written after every change, so playlists survive between sessions.
 *
 * <p>All access goes through this class (the GUI never touches the file directly), and
 * every mutating call saves immediately. Methods are {@code synchronized} for safety,
 * though in practice they are only called from the render/main thread.</p>
 */
public final class PlaylistStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<Playlist>>() {
    }.getType();

    private static final List<Playlist> PLAYLISTS = new ArrayList<>();
    private static boolean loaded;

    private PlaylistStore() {
    }

    /** The live list of playlists (mutating a {@link Playlist} then calling {@link #save()} persists it). */
    public static synchronized List<Playlist> all() {
        ensureLoaded();
        return PLAYLISTS;
    }

    /** Creates and saves a new, empty playlist. */
    public static synchronized Playlist create(String name) {
        ensureLoaded();
        Playlist playlist = new Playlist(name == null || name.isBlank() ? "New playlist" : name.strip());
        PLAYLISTS.add(playlist);
        save();
        return playlist;
    }

    public static synchronized void delete(Playlist playlist) {
        ensureLoaded();
        if (PLAYLISTS.remove(playlist)) {
            save();
        }
    }

    /** Persists the current playlists to disk. Best-effort: a failure is logged, not thrown. */
    public static synchronized void save() {
        Path path = file();
        if (path == null) {
            return;
        }
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling("playlists.json.tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(PLAYLISTS, LIST_TYPE, writer);
            }
            Files.move(tmp, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            LiasMediaPlayer.LOGGER.warn("Could not save playlists to {}: {}", path, e.toString());
        }
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        load();
    }

    private static void load() {
        Path path = file();
        if (path == null || !Files.isRegularFile(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            List<Playlist> parsed = GSON.fromJson(reader, LIST_TYPE);
            if (parsed != null) {
                for (Playlist playlist : parsed) {
                    if (playlist != null && playlist.name() != null) {
                        PLAYLISTS.add(playlist);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            LiasMediaPlayer.LOGGER.warn("Could not read playlists from {}: {}", path, e.toString());
        }
    }

    private static Path file() {
        try {
            return Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("liasmediaplayer").resolve("playlists.json");
        } catch (Exception e) {
            return null;
        }
    }
}
