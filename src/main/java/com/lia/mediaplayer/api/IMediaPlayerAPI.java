/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api;

import com.lia.mediaplayer.api.audio.AudioOptions;
import com.lia.mediaplayer.api.audio.MediaAudio;
import com.lia.mediaplayer.api.audio.MediaMixer;
import com.lia.mediaplayer.api.config.ConfigOption;
import com.lia.mediaplayer.api.diag.MediaDiagnostics;
import com.lia.mediaplayer.api.diag.MediaPlayerStats;
import com.lia.mediaplayer.api.render.MediaGraphics;
import com.lia.mediaplayer.api.render.MediaSurface;
import com.lia.mediaplayer.api.render.MediaSurfaces;
import com.lia.mediaplayer.api.render.SurfaceOptions;
import com.lia.mediaplayer.api.sync.MediaSync;
import com.lia.mediaplayer.api.sync.SyncControl;
import com.lia.mediaplayer.api.tools.MediaInfo;
import com.lia.mediaplayer.api.tools.MediaTools;

import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

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
 *   <li><b>Handles</b> — {@link #getHandle(long)}, {@link #getHandles()}, a live object
 *       per playing thing rather than a write-only id</li>
 *   <li><b>Playlist access</b> — create, list, rename, reorder, delete, play</li>
 *   <li><b>History</b> — {@link #getHistory(int)} and its favourites</li>
 *   <li><b>Tools</b> — {@link #probe(String)} and friends, better reached through
 *       {@link MediaTools}</li>
 *   <li><b>Headless audio and the mixer</b> — {@link #playHeadlessAudio(String, AudioOptions)}
 *       and {@link #getMixer()}, better reached through {@link MediaAudio}</li>
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
    // Playback — anything, with options (since API 2.2.0)
    // ====================================================================

    /**
     * Plays a {@link MediaRequest} — one entry point covering all three kinds and every
     * option there is, so the facade does not grow a combinatorial set of overloads.
     *
     * <p>{@code null} when no registered {@link MediaSource} claims the link, and also
     * for a YouTube playlist page: expanding one is a background {@code yt-dlp}
     * round-trip, so there is no window to hand back yet.</p>
     *
     * <p><b>Render thread only.</b></p>
     *
     * @since API 2.2.0
     */
    @Nullable
    MediaHandle play(MediaRequest request);

    // ====================================================================
    // Surfaces (since API 3.0.0)
    //
    // Reached through MediaSurfaces and MediaGraphics, which are the documented front
    // doors. These are here because those facades have to have something to delegate to.
    // ====================================================================

    /**
     * @see MediaSurfaces#image(String)
     * @since API 3.0.0
     */
    MediaSurface createImageSurface(String url);

    /**
     * @see MediaSurfaces#image(String, boolean)
     * @since API 3.4.0
     */
    MediaSurface createImageSurface(String url, boolean keepPixels);

    /**
     * @see MediaSurfaces#video(String, SurfaceOptions)
     * @since API 3.0.0
     */
    MediaSurface createVideoSurface(String url, SurfaceOptions options);

    /**
     * @see MediaSurfaces#thumbnail(String, double)
     * @since API 3.0.0
     */
    MediaSurface createThumbnailSurface(String url, double atSeconds);

    /**
     * @see MediaGraphics#draw(GuiGraphics, MediaSurface, int, int, int, int)
     * @since API 3.0.0
     */
    void drawSurface(GuiGraphics graphics, MediaSurface surface,
                     int x, int y, int width, int height, boolean stretch);

    // ====================================================================
    // Headless audio and the mixer (since API 3.1.0)
    //
    // Reached through MediaAudio, which is the documented front door. These are here
    // because that facade has to have something to delegate to.
    // ====================================================================

    /**
     * @see MediaAudio#play(String, AudioOptions)
     * @since API 3.1.0
     */
    @Nullable
    MediaHandle playHeadlessAudio(String url, AudioOptions options);

    /**
     * @see MediaAudio#mixer()
     * @since API 3.1.0
     */
    MediaMixer getMixer();

    // ====================================================================
    // Watch-together (since API 3.3.0)
    //
    // Reached through MediaSync, which is the documented front door. This is here
    // because that facade has to have something to delegate to.
    // ====================================================================

    /**
     * @see MediaSync#control()
     * @since API 3.3.0
     */
    SyncControl getSyncControl();

    // ====================================================================
    // Diagnostics (since API 3.4.0)
    // ====================================================================

    /**
     * @see MediaDiagnostics#stats()
     * @since API 3.4.0
     */
    MediaPlayerStats stats();

    // ====================================================================
    // Handles (since API 2.1.0)
    //
    // The read side the `long` ids never had. Every id above addresses a handle here,
    // and a handle keeps working through a queue advancing under it — what it points at
    // is the window, not the track.
    // ====================================================================

    /**
     * The live player with this id, or {@code null} if there is none (it was closed, or
     * evicted past the window cap).
     *
     * @since API 2.1.0
     */
    @Nullable
    MediaHandle getHandle(long id);

    /**
     * Every open window, video, audio and pinned image alike, in stacking order with the
     * front-most last. A snapshot; it does not update itself.
     *
     * @since API 2.1.0
     */
    List<MediaHandle> getHandles();

    /**
     * The most recently focused player of {@code kind} — the one the "front-most"
     * transport methods above act on — or {@code null} if none is open.
     *
     * @since API 2.1.0
     */
    @Nullable
    MediaHandle getFrontMost(MediaKind kind);

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

    /**
     * One playlist by name, or {@code null} if there is no such playlist.
     *
     * @since API 2.1.0
     */
    @Nullable
    PlaylistInfo getPlaylist(String playlistName);

    /**
     * Renames a playlist. {@code false} if there is no such playlist, if the new name is
     * blank, or if it is already taken — names are how playlists are addressed here, so
     * two of them may not share one.
     *
     * @since API 2.1.0
     */
    boolean renamePlaylist(String from, String to);

    /**
     * Removes the first occurrence of {@code url}. {@code false} if the playlist or the
     * URL was not there.
     *
     * @since API 2.1.0
     */
    boolean removeFromPlaylist(String playlistName, String url);

    /**
     * Replaces a playlist's contents with {@code urls}, which must be a permutation of
     * what it already holds — this reorders, it does not add or remove. {@code false}
     * (and nothing changed) otherwise.
     *
     * @since API 2.1.0
     */
    boolean reorderPlaylist(String playlistName, List<String> urls);

    /**
     * Plays a whole playlist in a fresh audio player, the way the playlist screen does.
     * {@code null} for an unknown or empty playlist.
     *
     * @since API 2.1.0
     */
    @Nullable
    MediaHandle playPlaylist(String playlistName, boolean shuffle);

    /**
     * Plays a whole playlist through a {@code template} whose own URLs are replaced by
     * the playlist's — the way to say "this playlist, in a bare window in the corner,
     * looping".
     *
     * <p>{@code null} for an unknown or empty playlist.</p>
     *
     * @since API 2.3.0
     */
    @Nullable
    MediaHandle playPlaylist(String playlistName, MediaRequest template);

    /**
     * A playlist as an extended-m3u file: the {@code #EXTM3U} header, one
     * {@code #EXTINF} line per entry carrying whatever title the mod has resolved, and
     * the URL under it.
     *
     * <p>{@code null} for an unknown playlist. An empty playlist exports its header and
     * nothing else, which re-imports as an empty playlist.</p>
     *
     * @since API 3.4.0
     */
    @Nullable
    String exportM3u(String playlistName);

    /**
     * Creates a playlist from m3u (or plain-m3u) text.
     *
     * <p>Every non-comment line is taken as a URL and passed through the same
     * {@code http(s)} gate every other way into a playlist uses, so a file naming a local
     * path imports as nothing rather than as something {@code ffmpeg} would open.
     * {@code #EXTINF} titles are read and discarded: the mod resolves its own titles, and
     * a stored one would go stale the first time a video was renamed.</p>
     *
     * <p>{@code null} if the name is blank or already taken, or if the text held no
     * usable link.</p>
     *
     * @since API 3.4.0
     */
    @Nullable
    PlaylistInfo importM3u(String playlistName, String content);

    // ====================================================================
    // History (thread-safe)
    //
    // This is the user's personal listening history. See HistoryItem.
    // ====================================================================

    /**
     * The most recently played media, newest first, at most {@code limit} entries
     * ({@code limit <= 0} means all of them).
     *
     * @since API 2.1.0
     */
    List<HistoryItem> getHistory(int limit);

    /**
     * Only what the user kept. Favourites are never evicted by the history's bound.
     *
     * @since API 2.1.0
     */
    List<HistoryItem> getFavorites();

    /**
     * @since API 2.1.0
     */
    boolean isFavorite(String url);

    /**
     * Keeps or un-keeps {@code url}. A URL that is not in the history yet is added by
     * this, provided the mod recognises it as media.
     *
     * @since API 2.1.0
     */
    void setFavorite(String url, boolean favorite);

    /**
     * Empties the history, <b>keeping the favourites</b> — the same rule the "clear"
     * button in the history screen follows, because the entries that were deliberately
     * kept are not the ones that piled up on their own.
     *
     * @since API 2.1.0
     */
    void clearHistory();

    // ====================================================================
    // Tools (thread-safe)
    //
    // Reached through com.lia.mediaplayer.api.tools.MediaTools, which is the documented
    // front door and the place the security rules are written down. These are here
    // because that facade has to have something to delegate to.
    // ====================================================================

    /**
     * @see MediaTools#isReady()
     * @since API 2.1.0
     */
    boolean toolsReady();

    /**
     * @see MediaTools#whenReady()
     * @since API 2.1.0
     */
    CompletableFuture<Void> whenToolsReady();

    /**
     * @see MediaTools#probe(String)
     * @since API 2.1.0
     */
    CompletableFuture<MediaInfo> probe(String url);

    /**
     * @see MediaTools#ytDlpVersion()
     * @since API 2.1.0
     */
    @Nullable
    String ytDlpVersion();

    // ====================================================================
    // PlaylistInfo
    // ====================================================================

    /**
     * An immutable snapshot of a saved playlist's name and URLs.
     */
    record PlaylistInfo(String name, List<String> urls) {
    }
}
