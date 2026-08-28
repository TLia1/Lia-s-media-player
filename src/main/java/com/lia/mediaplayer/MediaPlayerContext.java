package com.lia.mediaplayer;

import com.lia.mediaplayer.api.IMediaPlayerAPI;
import com.lia.mediaplayer.api.LiasMediaPlayerApi;
import com.lia.mediaplayer.api.MediaKind;
import com.lia.mediaplayer.api.MediaSource;
import com.lia.mediaplayer.api.config.ConfigOption;
import com.lia.mediaplayer.config.ConfigStore;
import com.lia.mediaplayer.gui.AudioPlayerManager;
import com.lia.mediaplayer.gui.ImageWindowManager;
import com.lia.mediaplayer.gui.VideoPlayerManager;
import com.lia.mediaplayer.gui.WindowStateStore;
import com.lia.mediaplayer.history.HistoryStore;
import com.lia.mediaplayer.image.ImagePreviewCache;
import com.lia.mediaplayer.media.MediaTitleCache;
import com.lia.mediaplayer.media.Volume;
import com.lia.mediaplayer.media.YouTubePlaylistResolver;
import com.lia.mediaplayer.playlist.PlaylistStore;
import com.lia.mediaplayer.source.MediaSources;
import com.lia.mediaplayer.source.Urls;
import com.lia.mediaplayer.source.YouTubePlaylistSource;
import com.lia.mediaplayer.video.VideoThumbnailCache;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class MediaPlayerContext implements IMediaPlayerAPI {

    private final VideoPlayerManager videoManager;
    private final AudioPlayerManager audioManager;
    private final ImageWindowManager imageManager;
    private final MediaSources mediaSources;
    private final ConfigStore configStore;
    private final PlaylistStore playlistStore;
    private final WindowStateStore windowStateStore;
    private final HistoryStore historyStore;
    private final Volume volume;
    private final ImagePreviewCache imagePreviewCache;
    private final VideoThumbnailCache thumbnailCache;
    private final MediaTitleCache titleCache;

    /**
     * The live context, on the understanding that the mod is up.
     *
     * <p>The counterpart of {@link LiasMediaPlayerApi#getInstance()}, and the reason it
     * exists is the same: code that only ever runs once a window, a player or a screen
     * is on the stage cannot be reached before {@code init()}, so making it handle a
     * {@code null} would be noise. Anything that can fire earlier — an event-bus
     * listener, a tick, a render pass — wants {@link #getOrNull()} instead.</p>
     *
     * @throws IllegalStateException if the mod has not finished initializing
     */
    public static MediaPlayerContext get() {
        MediaPlayerContext context = getOrNull();
        if (context == null) {
            throw new IllegalStateException("Lia's Media Player context is not initialized yet.");
        }
        return context;
    }

    /**
     * The live context, or {@code null} if the mod has not finished initializing.
     *
     * <p>The single place in the mod allowed to turn the published API instance back
     * into its implementation. Everything inside the mod needs the implementation —
     * {@link LiasMediaPlayerApi#getInstanceOrNull()} hands back the public interface,
     * which deliberately exposes none of the managers or stores — so without this the
     * same cast is written at every call site, and a change to the API's shape has to
     * be chased through all of them.</p>
     *
     * <p>Nullable because most callers run off an event bus — chat, ticks, rendering —
     * and can fire before {@code LiasMediaPlayer.init()} has run. The {@code instanceof}
     * rather than a cast covers the other way this can be absent: {@code setInstance} is
     * public, so a third party could in principle publish something else, and answering
     * {@code null} routes that into the branch every caller already handles instead of
     * throwing inside someone else's callback.</p>
     */
    @Nullable
    public static MediaPlayerContext getOrNull() {
        return LiasMediaPlayerApi.getInstanceOrNull() instanceof MediaPlayerContext context
                ? context
                : null;
    }

    public MediaPlayerContext() {
        this.videoManager = new VideoPlayerManager();
        this.audioManager = new AudioPlayerManager();
        this.imageManager = new ImageWindowManager();
        this.mediaSources = new MediaSources();
        this.configStore = new ConfigStore();
        this.playlistStore = new PlaylistStore();
        this.windowStateStore = new WindowStateStore();
        this.historyStore = new HistoryStore();
        this.volume = new Volume();
        this.imagePreviewCache = new ImagePreviewCache();
        this.thumbnailCache = new VideoThumbnailCache();
        this.titleCache = new MediaTitleCache();
    }

    public VideoPlayerManager getVideoManager() {
        return videoManager;
    }

    public AudioPlayerManager getAudioManager() {
        return audioManager;
    }

    public ImageWindowManager getImageManager() {
        return imageManager;
    }

    public MediaSources getMediaSources() {
        return mediaSources;
    }

    public ConfigStore getConfigStore() {
        return configStore;
    }

    public PlaylistStore getPlaylistStore() {
        return playlistStore;
    }

    /**
     * Where the windows were left last time — position, size, and each player's queue
     * panel and loop settings. See {@link WindowStateStore}.
     */
    public WindowStateStore getWindowStateStore() {
        return windowStateStore;
    }

    /**
     * What has been played, and what was kept — see {@link HistoryStore}.
     */
    public HistoryStore getHistoryStore() {
        return historyStore;
    }

    public Volume getVolumeManager() {
        return volume;
    }

    // ====================================================================
    // Caches
    //
    // The three of these hold the mod's off-heap weight — decoded frames and their GPU
    // textures — and each is emptied when the player leaves a world. That is a lifecycle
    // and a budget, i.e. a service, which is why they are owned here rather than being
    // static holders reached from wherever: a cache nobody owns is a cache nobody empties.
    // ====================================================================

    /**
     * Downloaded chat images and GIFs, decoded and uploaded once — see
     * {@link ImagePreviewCache}.
     */
    public ImagePreviewCache getImagePreviewCache() {
        return imagePreviewCache;
    }

    /**
     * The still shown for each entry of a video queue — see {@link VideoThumbnailCache}.
     */
    public VideoThumbnailCache getThumbnailCache() {
        return thumbnailCache;
    }

    /**
     * The readable name of a media URL, shared by both players' queue panels — see
     * {@link MediaTitleCache}.
     */
    public MediaTitleCache getTitleCache() {
        return titleCache;
    }

    // ====================================================================
    // Source registration
    // ====================================================================

    @Override
    public void registerSource(MediaSource source) {
        mediaSources.register(source);
    }

    // ====================================================================
    // Config Registration
    // ====================================================================

    @Override
    public void registerConfigOption(ConfigOption<?> option) {
        configStore.register(option);
    }

    @Override
    public <T> ConfigOption<T> getConfigOption(String id) {
        return configStore.getOption(id);
    }

    // ====================================================================
    // Media queries (thread-safe)
    // ====================================================================

    @Override
    public boolean isSupported(String url) {
        return mediaSources.isSupported(url);
    }

    @Override
    @Nullable
    public MediaKind kindOf(String url) {
        return mediaSources.kindOf(url);
    }

    // ====================================================================
    // Playback — Video
    // ====================================================================

    @Override
    public long playVideo(String url) {
        if (isYouTubePlaylist(url)) {
            return playYouTubePlaylist(url, false);
        }
        return videoManager.enqueuePublic(url);
    }

    @Override
    public long playVideoNewWindow(String url) {
        if (isYouTubePlaylist(url)) {
            return playYouTubePlaylist(url, false);
        }
        return videoManager.openPublic(url);
    }

    /**
     * Whether {@code url} is a YouTube playlist page rather than a single media item.
     * Those cannot be handed to the players as-is: they have to be expanded into their
     * videos first, which is a background {@code yt-dlp} call.
     */
    private static boolean isYouTubePlaylist(String url) {
        return YouTubePlaylistSource.isPlaylist(url);
    }

    /**
     * Expands a YouTube playlist and plays all of it in a fresh window. The expansion
     * is asynchronous, so there is no window to return an ID for yet: this always
     * returns {@code -1}.
     */
    private long playYouTubePlaylist(String url, boolean asAudio) {
        YouTubePlaylistResolver.loadAsync(url, result -> {
            if (result == null) {
                return; // the resolver has already reported why
            }
            if (asAudio) {
                audioManager.playAll(result.urls(), false);
            } else {
                videoManager.playAll(result.urls(), false);
            }
        });
        return -1;
    }

    // ====================================================================
    // Playback — Audio
    // ====================================================================

    @Override
    public long playAudio(String url) {
        if (isYouTubePlaylist(url)) {
            return playYouTubePlaylist(url, true);
        }
        return audioManager.enqueuePublic(url);
    }

    @Override
    public long playAudioNewWindow(String url) {
        if (isYouTubePlaylist(url)) {
            return playYouTubePlaylist(url, true);
        }
        return audioManager.openPublic(url);
    }

    @Override
    public long playAudioAll(List<String> urls, boolean shuffle) {
        return audioManager.playAllPublic(urls, shuffle);
    }

    // ====================================================================
    // Playback — Image
    // ====================================================================

    @Override
    public long showImage(String url) {
        return imageManager.showPublic(url);
    }

    // ====================================================================
    // Playback controls (act on the front-most player)
    // ====================================================================

    @Override
    public void togglePauseVideo() {
        videoManager.togglePauseFrontMost();
    }

    @Override
    public void togglePauseAudio() {
        audioManager.togglePauseFrontMost();
    }

    @Override
    public void nextVideo() {
        videoManager.nextFrontMost();
    }

    @Override
    public void nextAudio() {
        audioManager.nextFrontMost();
    }

    @Override
    public void previousAudio() {
        audioManager.previousFrontMost();
    }

    @Override
    public void seekVideo(double fraction) {
        videoManager.seekFrontMost(fraction);
    }

    @Override
    public void seekAudio(double fraction) {
        audioManager.seekFrontMost(fraction);
    }

    // ====================================================================
    // Playback controls (act on a specific player by ID)
    // ====================================================================

    @Override
    public void togglePause(long id) {
        if (videoManager.exists(id)) {
            videoManager.togglePause(id);
        } else if (audioManager.exists(id)) {
            audioManager.togglePause(id);
        }
    }

    @Override
    public void next(long id) {
        if (videoManager.exists(id)) {
            videoManager.next(id);
        } else if (audioManager.exists(id)) {
            audioManager.next(id);
        }
    }

    @Override
    public void previous(long id) {
        if (audioManager.exists(id)) {
            audioManager.previous(id);
        }
    }

    @Override
    public void enqueueTo(long id, String url) {
        if (videoManager.exists(id)) {
            videoManager.enqueueTo(id, url);
        } else if (audioManager.exists(id)) {
            audioManager.enqueueTo(id, url);
        }
    }

    @Override
    public void setVisible(long id, boolean visible) {
        if (videoManager.exists(id)) {
            videoManager.setVisible(id, visible);
        } else if (audioManager.exists(id)) {
            audioManager.setVisible(id, visible);
        } else if (imageManager.exists(id)) {
            imageManager.setVisible(id, visible);
        }
    }

    @Override
    public void close(long id) {
        if (videoManager.exists(id)) {
            videoManager.closePublic(id);
        } else if (audioManager.exists(id)) {
            audioManager.closePublic(id);
        } else if (imageManager.exists(id)) {
            imageManager.closePublic(id);
        }
    }

    // ====================================================================
    // Volume (thread-safe)
    // ====================================================================

    @Override
    public float getVolume() {
        return volume.level();
    }

    @Override
    public void setVolume(float level) {
        volume.set(level);
    }

    @Override
    public boolean isMuted() {
        return volume.isMuted();
    }

    @Override
    public void toggleMute() {
        volume.toggleMute();
    }

    // ====================================================================
    // Playlists
    // ====================================================================

    @Override
    public List<PlaylistInfo> getPlaylists() {
        return playlistStore.all().stream()
                .map(p -> new PlaylistInfo(p.name(), Collections.unmodifiableList(new ArrayList<>(p.urls()))))
                .collect(Collectors.toList());
    }

    @Override
    public PlaylistInfo createPlaylist(String name) {
        var p = playlistStore.create(name);
        return new PlaylistInfo(p.name(), Collections.unmodifiableList(new ArrayList<>(p.urls())));
    }

    @Override
    public boolean addToPlaylist(String playlistName, String url) {
        // An addon's string gets the same gate a chat link gets: a playlist entry ends up
        // in ffmpeg/yt-dlp on a later session, so only real http(s) links are stored. Said
        // here rather than left to Playlist.add so the caller is told it was rejected.
        if (!Urls.isHttp(url)) {
            return false;
        }
        for (var p : playlistStore.all()) {
            if (p.name().equals(playlistName)) {
                p.add(url);
                playlistStore.save();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean deletePlaylist(String playlistName) {
        for (var p : playlistStore.all()) {
            if (p.name().equals(playlistName)) {
                playlistStore.delete(p);
                return true;
            }
        }
        return false;
    }
}
