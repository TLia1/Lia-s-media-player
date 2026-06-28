/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api;

import com.lia.mediaplayer.gui.AudioPlayerManager;
import com.lia.mediaplayer.gui.ImageWindowManager;
import com.lia.mediaplayer.gui.VideoPlayerManager;
import com.lia.mediaplayer.media.Volume;
import com.lia.mediaplayer.playlist.Playlist;
import com.lia.mediaplayer.playlist.PlaylistStore;
import com.lia.mediaplayer.source.MediaSources;
import com.lia.mediaplayer.api.config.ConfigOption;
import com.lia.mediaplayer.config.ConfigStore;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The public façade of Lia's Media Player. Other mods interact with the media
 * player exclusively through this class.
 *
 * <h2>Capabilities</h2>
 * <ul>
 *   <li><b>Source registration</b> — {@link #registerSource(MediaSource)}</li>
 *   <li><b>Playback control</b> — play, pause, seek, skip, volume</li>
 *   <li><b>Media queries</b> — {@link #isSupported(String)}, {@link #kindOf(String)}</li>
 *   <li><b>Playlist access</b> — create, list, delete playlists</li>
 * </ul>
 *
 * <p>All methods must be called from the <b>main/render thread</b> unless
 * explicitly documented as thread-safe.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 */
public final class MediaPlayerAPI {

    private MediaPlayerAPI() {
    }

    // ====================================================================
    // Source registration
    // ====================================================================

    /**
     * Registers a custom media source. Registered sources are appended after the
     * built-in ones and are tested in registration order (first match wins).
     *
     * <p>Prefer using {@link com.lia.mediaplayer.api.event.MediaSourceRegistrationEvent}
     * during mod initialization for proper ordering, but this method can be called
     * at any time.</p>
     *
     * @param source the media source to register (must not be null)
     */
    public static void registerSource(MediaSource source) {
        MediaSources.register(source);
    }

    // ====================================================================
    // Config Registration
    // ====================================================================

    /**
     * Registers a custom configuration option. This option will automatically be
     * saved/loaded from config.json and presented in the Config screen.
     *
     * @param option the config option to register
     */
    public static void registerConfigOption(ConfigOption<?> option) {
        ConfigStore.register(option);
    }

    /**
     * Retrieves a registered configuration option by its ID.
     *
     * @param id the unique identifier of the option
     * @return the configuration option, or null if not found
     */
    public static <T> ConfigOption<T> getConfigOption(String id) {
        return ConfigStore.getOption(id);
    }

    // ====================================================================
    // Media queries (thread-safe)
    // ====================================================================

    /**
     * Whether any registered source recognizes {@code url}.
     * This method is thread-safe.
     */
    public static boolean isSupported(String url) {
        return MediaSources.isSupported(url);
    }

    /**
     * The kind of media at {@code url}, or {@code null} if unrecognized.
     * This method is thread-safe.
     */
    @Nullable
    public static MediaKind kindOf(String url) {
        return MediaSources.apiKindOf(url);
    }

    // ====================================================================
    // Playback — Video
    // ====================================================================

    /**
     * Opens a video URL in the in-game video player. If a player window already
     * exists, the URL is appended to its queue; otherwise a new window is created.
     */
    public static void playVideo(String url) {
        VideoPlayerManager.enqueuePublic(url);
    }

    /**
     * Opens a video URL in a <b>new, independent</b> player window (bypassing
     * the queue of any existing window).
     */
    public static void playVideoNewWindow(String url) {
        VideoPlayerManager.openPublic(url);
    }

    // ====================================================================
    // Playback — Audio
    // ====================================================================

    /**
     * Opens an audio URL in the in-game audio player. If an audio bar already
     * exists, the URL is appended to its queue; otherwise a new bar is created.
     */
    public static void playAudio(String url) {
        AudioPlayerManager.enqueuePublic(url);
    }

    /**
     * Plays a list of audio URLs. The first track starts immediately and the
     * rest queue behind it. When {@code shuffle} is true, the order is randomized.
     */
    public static void playAudioAll(List<String> urls, boolean shuffle) {
        AudioPlayerManager.playAll(urls, shuffle);
    }

    // ====================================================================
    // Playback — Image
    // ====================================================================

    /**
     * Shows an image URL in a pinned window (or reveals the existing one for
     * this URL).
     */
    public static void showImage(String url) {
        ImageWindowManager.showPublic(url);
    }

    // ====================================================================
    // Playback controls (act on the front-most player)
    // ====================================================================

    /** Toggles pause/play on the front-most video player. */
    public static void togglePauseVideo() {
        VideoPlayerManager.togglePauseFrontMost();
    }

    /** Toggles pause/play on the front-most audio player. */
    public static void togglePauseAudio() {
        AudioPlayerManager.togglePauseFrontMost();
    }

    /** Skips to the next queued video in the front-most video player. */
    public static void nextVideo() {
        VideoPlayerManager.nextFrontMost();
    }

    /** Skips to the next queued audio track in the front-most audio player. */
    public static void nextAudio() {
        AudioPlayerManager.nextFrontMost();
    }

    /** Goes back to the previous audio track in the front-most audio player. */
    public static void previousAudio() {
        AudioPlayerManager.previousFrontMost();
    }

    /**
     * Seeks the front-most video player to the given fraction (0.0 = start,
     * 1.0 = end).
     */
    public static void seekVideo(double fraction) {
        VideoPlayerManager.seekFrontMost(fraction);
    }

    /**
     * Seeks the front-most audio player to the given fraction (0.0 = start,
     * 1.0 = end).
     */
    public static void seekAudio(double fraction) {
        AudioPlayerManager.seekFrontMost(fraction);
    }

    // ====================================================================
    // Volume (thread-safe)
    // ====================================================================

    /** The current volume level in 0..1. Thread-safe. */
    public static float getVolume() {
        return Volume.level();
    }

    /** Sets the playback volume (0..1), clamped. Thread-safe. */
    public static void setVolume(float level) {
        Volume.set(level);
    }

    /** Whether playback is currently muted. Thread-safe. */
    public static boolean isMuted() {
        return Volume.isMuted();
    }

    /** Toggles mute on/off. Thread-safe. */
    public static void toggleMute() {
        Volume.toggleMute();
    }

    // ====================================================================
    // Playlists
    // ====================================================================

    /**
     * Returns an unmodifiable snapshot of all saved playlists. Each playlist
     * contains a name and a list of URLs.
     */
    public static List<PlaylistInfo> getPlaylists() {
        List<Playlist> raw = PlaylistStore.all();
        List<PlaylistInfo> result = new ArrayList<>(raw.size());
        for (Playlist playlist : raw) {
            result.add(new PlaylistInfo(playlist.name(), Collections.unmodifiableList(new ArrayList<>(playlist.urls()))));
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Creates a new, empty playlist with the given name and saves it to disk.
     *
     * @param name the display name (will be trimmed; defaults to "New playlist" if blank)
     * @return information about the created playlist
     */
    public static PlaylistInfo createPlaylist(String name) {
        Playlist p = PlaylistStore.create(name);
        return new PlaylistInfo(p.name(), Collections.unmodifiableList(new ArrayList<>(p.urls())));
    }

    /**
     * Adds a URL to the playlist with the given name and saves to disk.
     *
     * @param playlistName the name of the playlist (case-sensitive)
     * @param url          the media URL to add
     * @return true if the playlist was found and the URL was added
     */
    public static boolean addToPlaylist(String playlistName, String url) {
        for (Playlist p : PlaylistStore.all()) {
            if (p.name().equals(playlistName)) {
                p.add(url);
                PlaylistStore.save();
                return true;
            }
        }
        return false;
    }

    /**
     * Deletes the first playlist matching the given name.
     *
     * @param playlistName the name of the playlist to delete
     * @return true if a playlist was found and deleted
     */
    public static boolean deletePlaylist(String playlistName) {
        for (Playlist p : PlaylistStore.all()) {
            if (p.name().equals(playlistName)) {
                PlaylistStore.delete(p);
                return true;
            }
        }
        return false;
    }

    // ====================================================================
    // PlaylistInfo — immutable snapshot of a playlist
    // ====================================================================

    /**
     * An immutable snapshot of a saved playlist's name and URLs.
     */
    public record PlaylistInfo(String name, List<String> urls) {
    }
}
