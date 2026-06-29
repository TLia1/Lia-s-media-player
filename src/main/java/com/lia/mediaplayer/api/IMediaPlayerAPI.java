/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api;

import com.lia.mediaplayer.api.config.ConfigOption;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The public interface of Lia's Media Player. Other mods interact with the media
 * player exclusively through this interface.
 *
 * <p>Get the instance via {@link LiasMediaPlayerApi#getInstance()}.</p>
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
 */
public interface IMediaPlayerAPI {

    // ====================================================================
    // Source registration
    // ====================================================================

    void registerSource(MediaSource source);

    // ====================================================================
    // Config Registration
    // ====================================================================

    void registerConfigOption(ConfigOption<?> option);

    <T> ConfigOption<T> getConfigOption(String id);

    // ====================================================================
    // Media queries (thread-safe)
    // ====================================================================

    boolean isSupported(String url);

    @Nullable
    MediaKind kindOf(String url);

    // ====================================================================
    // Playback — Video
    // ====================================================================

    long playVideo(String url);

    long playVideoNewWindow(String url);

    // ====================================================================
    // Playback — Audio
    // ====================================================================

    long playAudio(String url);

    long playAudioNewWindow(String url);

    long playAudioAll(List<String> urls, boolean shuffle);

    // ====================================================================
    // Playback — Image
    // ====================================================================

    long showImage(String url);

    // ====================================================================
    // Playback controls (act on the front-most player)
    // ====================================================================

    void togglePauseVideo();

    void togglePauseAudio();

    void nextVideo();

    void nextAudio();

    void previousAudio();

    void seekVideo(double fraction);

    void seekAudio(double fraction);

    // ====================================================================
    // Playback controls (act on a specific player by ID)
    // ====================================================================

    void togglePause(long id);

    void next(long id);

    void previous(long id);

    void enqueueTo(long id, String url);

    void setVisible(long id, boolean visible);

    void close(long id);

    // ====================================================================
    // Volume (thread-safe)
    // ====================================================================

    float getVolume();

    void setVolume(float level);

    boolean isMuted();

    void toggleMute();

    // ====================================================================
    // Playlists
    // ====================================================================

    List<PlaylistInfo> getPlaylists();

    PlaylistInfo createPlaylist(String name);

    boolean addToPlaylist(String playlistName, String url);

    boolean deletePlaylist(String playlistName);

    // ====================================================================
    // PlaylistInfo
    // ====================================================================

    /**
     * An immutable snapshot of a saved playlist's name and URLs.
     */
    record PlaylistInfo(String name, List<String> urls) {
    }
}
