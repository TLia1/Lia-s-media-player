package com.lia.mediaplayer;

import com.lia.mediaplayer.api.HistoryItem;
import com.lia.mediaplayer.api.IMediaPlayerAPI;
import com.lia.mediaplayer.api.LiasMediaPlayerApi;
import com.lia.mediaplayer.api.MediaHandle;
import com.lia.mediaplayer.api.MediaKind;
import com.lia.mediaplayer.api.MediaRequest;
import com.lia.mediaplayer.api.MediaSource;
import com.lia.mediaplayer.api.audio.AudioOptions;
import com.lia.mediaplayer.api.audio.MediaMixer;
import com.lia.mediaplayer.api.config.ConfigOption;
import com.lia.mediaplayer.api.diag.MediaPlayerStats;
import com.lia.mediaplayer.api.policy.MediaInterceptors;
import com.lia.mediaplayer.api.policy.PlayOrigin;
import com.lia.mediaplayer.api.render.MediaSurface;
import com.lia.mediaplayer.api.render.SurfaceOptions;
import com.lia.mediaplayer.api.sync.SyncControl;
import com.lia.mediaplayer.api.tools.MediaInfo;
import com.lia.mediaplayer.audio.HeadlessAudio;
import com.lia.mediaplayer.config.ConfigStore;
import com.lia.mediaplayer.gui.AudioPlayerManager;
import com.lia.mediaplayer.gui.ImageWindowManager;
import com.lia.mediaplayer.gui.MediaSyncControl;
import com.lia.mediaplayer.gui.MediaWindowOverlay;
import com.lia.mediaplayer.gui.VideoPlayerManager;
import com.lia.mediaplayer.gui.WindowStateStore;
import com.lia.mediaplayer.history.HistoryEntry;
import com.lia.mediaplayer.history.HistoryStore;
import com.lia.mediaplayer.image.ImagePreviewCache;
import com.lia.mediaplayer.media.AudioMixer;
import com.lia.mediaplayer.media.MediaTitleCache;
import com.lia.mediaplayer.media.Volume;
import com.lia.mediaplayer.media.YouTubePlaylistResolver;
import com.lia.mediaplayer.playlist.M3u;
import com.lia.mediaplayer.playlist.Playlist;
import com.lia.mediaplayer.playlist.PlaylistStore;
import com.lia.mediaplayer.source.MediaSources;
import com.lia.mediaplayer.source.Urls;
import com.lia.mediaplayer.source.YouTubePlaylistSource;
import com.lia.mediaplayer.surface.SurfaceRegistry;
import com.lia.mediaplayer.surface.SurfaceRenderer;
import com.lia.mediaplayer.tools.FFmpegCli;
import com.lia.mediaplayer.tools.MediaBinaries;
import com.lia.mediaplayer.video.VideoThumbnailCache;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
    private final AudioMixer mixer;
    private final HeadlessAudio headlessAudio;
    private final ImagePreviewCache imagePreviewCache;
    private final VideoThumbnailCache thumbnailCache;
    private final MediaTitleCache titleCache;
    private final SurfaceRegistry surfaceRegistry;
    /**
     * Watch-together, stateless — every call finds its window by id. One instance rather
     * than a fresh one per {@code getSyncControl()} so an addon may hold it.
     */
    private final SyncControl syncControl = new MediaSyncControl();

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
        this.mixer = new AudioMixer(volume);
        this.headlessAudio = new HeadlessAudio(mixer);
        this.imagePreviewCache = new ImagePreviewCache();
        this.thumbnailCache = new VideoThumbnailCache();
        this.titleCache = new MediaTitleCache();
        this.surfaceRegistry = new SurfaceRegistry();
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

    /**
     * The channel gains around {@link #getVolumeManager() the one master level}, and the
     * factory for the per-sound gain every player multiplies in — see {@link AudioMixer}.
     *
     * <p>Covariant: this is also {@link IMediaPlayerAPI#getMixer()}, which promises the
     * public {@link MediaMixer}. One method rather than two, because there is one mixer
     * and the interface's view of it is a subset of this one's.</p>
     */
    @Override
    public AudioMixer getMixer() {
        return mixer;
    }

    /**
     * The sounds addons are playing with no window — see {@link HeadlessAudio}.
     *
     * <p>Owned here for the reason the surfaces and the caches are: a lifecycle (emptied
     * on disconnect) and a budget, which is what makes it a service rather than a static
     * holder somebody would forget to empty.</p>
     */
    public HeadlessAudio getHeadlessAudio() {
        return headlessAudio;
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

    /**
     * The off-screen surfaces addons decode media into — see {@link SurfaceRegistry}.
     *
     * <p>Owned here for exactly the reason the three caches above are: it has a
     * lifecycle (emptied on disconnect) and a budget, which is what makes it a service
     * rather than a static holder somebody would forget to empty.</p>
     */
    public SurfaceRegistry getSurfaceRegistry() {
        return surfaceRegistry;
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

    /**
     * The interceptor gate for the 2.0 {@code long}-id entry points — see
     * {@code api.policy.MediaInterceptor}.
     *
     * <p>Answers the URL to play: the same one when nothing rewrote it, a different one
     * when something did, and {@code null} when an interceptor said no — in which case
     * the caller hands back {@code -1}, the same "nothing happened" these methods
     * already use.</p>
     *
     * <p>These methods take a URL and give back an id, so <b>only the URL</b> of a
     * rewritten request can be honoured here; a placement or a chrome an interceptor also
     * set has nowhere to go and is not applied. That is the price of the 2.0 shape, and it
     * is written down rather than half-applied — {@code play(MediaRequest)} is the entry
     * point that honours everything.</p>
     */
    @Nullable
    private static String gate(String url, @Nullable MediaKind kind, boolean newWindow) {
        if (!MediaInterceptors.any() || !Urls.isHttp(url)) {
            return url;
        }
        MediaRequest allowed = MediaInterceptors.beforePlay(
                MediaRequest.of(url).as(kind).newWindow(newWindow), PlayOrigin.API);
        return allowed == null ? null : allowed.url();
    }

    @Override
    public long playVideo(String url) {
        String allowed = gate(url, MediaKind.VIDEO, false);
        if (allowed == null) {
            return -1;
        }
        if (isYouTubePlaylist(allowed)) {
            return playYouTubePlaylist(allowed, false);
        }
        return videoManager.enqueuePublic(allowed);
    }

    @Override
    public long playVideoNewWindow(String url) {
        String allowed = gate(url, MediaKind.VIDEO, true);
        if (allowed == null) {
            return -1;
        }
        if (isYouTubePlaylist(allowed)) {
            return playYouTubePlaylist(allowed, false);
        }
        return videoManager.openPublic(allowed);
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
        String allowed = gate(url, MediaKind.AUDIO, false);
        if (allowed == null) {
            return -1;
        }
        if (isYouTubePlaylist(allowed)) {
            return playYouTubePlaylist(allowed, true);
        }
        return audioManager.enqueuePublic(allowed);
    }

    @Override
    public long playAudioNewWindow(String url) {
        String allowed = gate(url, MediaKind.AUDIO, true);
        if (allowed == null) {
            return -1;
        }
        if (isYouTubePlaylist(allowed)) {
            return playYouTubePlaylist(allowed, true);
        }
        return audioManager.openPublic(allowed);
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
        String allowed = gate(url, MediaKind.IMAGE, true);
        return allowed == null ? -1 : imageManager.showPublic(allowed);
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
    // Playback — anything, with options
    // ====================================================================

    @Override
    @Nullable
    public MediaHandle play(MediaRequest request) {
        return MediaWindowOverlay.play(request);
    }

    // ====================================================================
    // Surfaces
    // ====================================================================

    @Override
    public MediaSurface createImageSurface(String url) {
        return surfaceRegistry.image(url);
    }

    @Override
    public MediaSurface createImageSurface(String url, boolean keepPixels) {
        return surfaceRegistry.image(url, keepPixels);
    }

    @Override
    public MediaSurface createVideoSurface(String url, SurfaceOptions options) {
        return surfaceRegistry.video(url, options);
    }

    @Override
    public MediaSurface createThumbnailSurface(String url, double atSeconds) {
        return surfaceRegistry.thumbnail(url, atSeconds);
    }

    @Override
    public void drawSurface(GuiGraphics graphics, MediaSurface surface,
                            int x, int y, int width, int height, boolean stretch) {
        SurfaceRenderer.draw(graphics, surface, x, y, width, height, stretch);
    }

    // ====================================================================
    // Headless audio and the mixer
    // ====================================================================

    @Override
    @Nullable
    public MediaHandle playHeadlessAudio(String url, AudioOptions options) {
        return headlessAudio.play(url, options == null ? AudioOptions.defaults() : options);
    }

    // ====================================================================
    // Watch-together
    // ====================================================================

    @Override
    public SyncControl getSyncControl() {
        return syncControl;
    }

    // ====================================================================
    // Diagnostics
    // ====================================================================

    @Override
    public MediaPlayerStats stats() {
        return new MediaPlayerStats(
                videoManager.getWindows().size(),
                audioManager.getWindows().size(),
                imageManager.getWindows().size(),
                headlessAudio.size(),
                surfaceRegistry.size(),
                surfaceRegistry.decodingVideoCount(),
                imagePreviewCache.estimatedBytes(),
                imagePreviewCache.size(),
                thumbnailCache.size(),
                titleCache.size(),
                MediaBinaries.isReady());
    }

    // ====================================================================
    // Handles
    // ====================================================================

    @Override
    @Nullable
    public MediaHandle getHandle(long id) {
        return MediaWindowOverlay.handleOf(id);
    }

    @Override
    public List<MediaHandle> getHandles() {
        return MediaWindowOverlay.handles();
    }

    @Override
    @Nullable
    public MediaHandle getFrontMost(MediaKind kind) {
        return kind == null ? null : MediaWindowOverlay.frontMostHandle(kind);
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
                .map(MediaPlayerContext::info)
                .collect(Collectors.toList());
    }

    @Override
    public PlaylistInfo createPlaylist(String name) {
        return info(playlistStore.create(name));
    }

    @Override
    public boolean addToPlaylist(String playlistName, String url) {
        // An addon's string gets the same gate a chat link gets: a playlist entry ends up
        // in ffmpeg/yt-dlp on a later session, so only real http(s) links are stored. Said
        // here rather than left to Playlist.add so the caller is told it was rejected.
        if (!Urls.isHttp(url)) {
            return false;
        }
        Playlist playlist = findPlaylist(playlistName);
        if (playlist == null) {
            return false;
        }
        playlist.add(url);
        playlistStore.save();
        return true;
    }

    @Override
    public boolean deletePlaylist(String playlistName) {
        Playlist playlist = findPlaylist(playlistName);
        if (playlist == null) {
            return false;
        }
        playlistStore.delete(playlist);
        return true;
    }

    @Override
    @Nullable
    public PlaylistInfo getPlaylist(String playlistName) {
        Playlist playlist = findPlaylist(playlistName);
        return playlist == null ? null : info(playlist);
    }

    @Override
    public boolean renamePlaylist(String from, String to) {
        if (to == null || to.isBlank()) {
            return false;
        }
        String name = to.strip();
        Playlist playlist = findPlaylist(from);
        // Names are how a playlist is addressed through this API, so two of them sharing
        // one would make every other method here ambiguous.
        if (playlist == null || (!name.equals(from) && findPlaylist(name) != null)) {
            return false;
        }
        playlist.setName(name);
        playlistStore.save();
        return true;
    }

    @Override
    public boolean removeFromPlaylist(String playlistName, String url) {
        Playlist playlist = findPlaylist(playlistName);
        if (playlist == null) {
            return false;
        }
        int index = playlist.urls().indexOf(url);
        if (index < 0) {
            return false;
        }
        playlist.removeAt(index);
        playlistStore.save();
        return true;
    }

    @Override
    public boolean reorderPlaylist(String playlistName, List<String> urls) {
        Playlist playlist = findPlaylist(playlistName);
        if (playlist == null || urls == null) {
            return false;
        }
        // A permutation and nothing else: this method reorders, and letting it quietly
        // add or drop entries would make it a second, undocumented way to edit a
        // playlist — one that skips Playlist.add's http(s) gate.
        List<String> wanted = new ArrayList<>(urls);
        List<String> current = new ArrayList<>(playlist.urls());
        List<String> sortedWanted = new ArrayList<>(wanted);
        List<String> sortedCurrent = new ArrayList<>(current);
        Collections.sort(sortedWanted);
        Collections.sort(sortedCurrent);
        if (!sortedWanted.equals(sortedCurrent)) {
            return false;
        }
        playlist.urls().clear();
        playlist.urls().addAll(wanted);
        playlistStore.save();
        return true;
    }

    @Override
    @Nullable
    public MediaHandle playPlaylist(String playlistName, boolean shuffle) {
        Playlist playlist = findPlaylist(playlistName);
        if (playlist == null || playlist.isEmpty()) {
            return null;
        }
        // Through the audio player, which is what the playlist screen's own play button
        // does — a saved playlist is a listening queue, whatever its links happen to be —
        // and through the request path, so a registered MediaInterceptor is asked, with
        // PLAYLIST as the origin.
        return MediaWindowOverlay.play(MediaRequest.ofAll(new ArrayList<>(playlist.urls()))
                .as(MediaKind.AUDIO)
                .newWindow(true)
                .shuffle(shuffle), PlayOrigin.PLAYLIST);
    }

    @Override
    @Nullable
    public MediaHandle playPlaylist(String playlistName, MediaRequest template) {
        Playlist playlist = findPlaylist(playlistName);
        if (playlist == null || playlist.isEmpty() || template == null) {
            return null;
        }
        return MediaWindowOverlay.play(template.withUrls(new ArrayList<>(playlist.urls())),
                PlayOrigin.PLAYLIST);
    }

    @Nullable
    private Playlist findPlaylist(String name) {
        if (name == null) {
            return null;
        }
        for (Playlist playlist : playlistStore.all()) {
            if (name.equals(playlist.name())) {
                return playlist;
            }
        }
        return null;
    }

    private static PlaylistInfo info(Playlist playlist) {
        return new PlaylistInfo(playlist.name(),
                Collections.unmodifiableList(new ArrayList<>(playlist.urls())));
    }

    @Override
    @Nullable
    public String exportM3u(String playlistName) {
        Playlist playlist = findPlaylist(playlistName);
        if (playlist == null) {
            return null;
        }
        // Whatever titles the mod has already resolved, and no probing: exporting a
        // playlist must not be the thing that launches a hundred ffprobes.
        return M3u.export(playlist, titleCache::peek);
    }

    @Override
    @Nullable
    public PlaylistInfo importM3u(String playlistName, String content) {
        if (playlistName == null || playlistName.isBlank() || findPlaylist(playlistName) != null) {
            return null;
        }
        List<String> urls = M3u.parse(content);
        if (urls.isEmpty()) {
            return null;
        }
        Playlist playlist = playlistStore.create(playlistName);
        // Through Playlist.add, so the http(s) gate is applied here as well as in the
        // parser: that is the choke point every stored entry passes, and an import is
        // exactly the path that would otherwise get round it.
        urls.forEach(playlist::add);
        playlistStore.save();
        return info(playlist);
    }

    // ====================================================================
    // History (thread-safe — HistoryStore is synchronized throughout)
    // ====================================================================

    @Override
    public List<HistoryItem> getHistory(int limit) {
        List<HistoryEntry> all = historyStore.all();
        if (limit > 0 && all.size() > limit) {
            all = all.subList(0, limit);
        }
        return toItems(all);
    }

    @Override
    public List<HistoryItem> getFavorites() {
        return toItems(historyStore.favorites());
    }

    @Override
    public boolean isFavorite(String url) {
        return historyStore.isFavorite(url);
    }

    @Override
    public void setFavorite(String url, boolean favorite) {
        if (historyStore.isFavorite(url) != favorite) {
            // toggleFavorite needs a kind for a URL it has never seen, which is exactly
            // the case an addon hits when it keeps something straight off a link.
            historyStore.toggleFavorite(url, mediaSources.kindOf(url));
        }
    }

    @Override
    public void clearHistory() {
        historyStore.clear();
    }

    private static List<HistoryItem> toItems(List<HistoryEntry> entries) {
        List<HistoryItem> items = new ArrayList<>(entries.size());
        for (HistoryEntry entry : entries) {
            items.add(new HistoryItem(entry.url(), entry.kind(), entry.playedAt(), entry.favorite()));
        }
        return Collections.unmodifiableList(items);
    }

    // ====================================================================
    // Tools (thread-safe)
    // ====================================================================

    @Override
    public boolean toolsReady() {
        return MediaBinaries.isReady();
    }

    @Override
    public CompletableFuture<Void> whenToolsReady() {
        return MediaBinaries.whenReady();
    }

    @Override
    public CompletableFuture<MediaInfo> probe(String url) {
        // The gate from §17 of the API rules, applied in the API layer rather than left
        // to the caller: an addon may not hand a local path to a downloaded binary.
        if (!Urls.isHttp(url)) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                FFmpegCli.MediaInfo probed = FFmpegCli.probe(url);
                return new MediaInfo(probed.width(), probed.height(), probed.fps(),
                        probed.durationMicros(), probed.hasVideo(), probed.hasAudio());
            } catch (IOException e) {
                // A link someone pasted failing to probe is an ordinary outcome, not an
                // exceptional one; the caller gets null and decides what that means.
                LiasMediaPlayer.LOGGER.debug("API probe failed for {}", url, e);
                return null;
            }
        }, Util.ioPool());
    }

    @Override
    @Nullable
    public String ytDlpVersion() {
        return MediaBinaries.ytDlpVersion();
    }
}
