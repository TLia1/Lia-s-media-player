package com.lia.mediaplayer;

import com.lia.mediaplayer.api.IMediaPlayerAPI;
import com.lia.mediaplayer.api.MediaKind;
import com.lia.mediaplayer.api.MediaSource;
import com.lia.mediaplayer.api.config.ConfigOption;
import com.lia.mediaplayer.config.ConfigStore;
import com.lia.mediaplayer.gui.AudioPlayerManager;
import com.lia.mediaplayer.gui.ImageWindowManager;
import com.lia.mediaplayer.gui.VideoPlayerManager;
import com.lia.mediaplayer.media.Volume;
import com.lia.mediaplayer.playlist.PlaylistStore;
import com.lia.mediaplayer.source.MediaSources;
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
    private final Volume volume;

    public MediaPlayerContext() {
        this.videoManager = new VideoPlayerManager();
        this.audioManager = new AudioPlayerManager();
        this.imageManager = new ImageWindowManager();
        this.mediaSources = new MediaSources();
        this.configStore = new ConfigStore();
        this.playlistStore = new PlaylistStore();
        this.volume = new Volume();
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

    public Volume getVolumeManager() {
        return volume;
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
        return mediaSources.apiKindOf(url);
    }

    // ====================================================================
    // Playback — Video
    // ====================================================================

    @Override
    public long playVideo(String url) {
        return videoManager.enqueuePublic(url);
    }

    @Override
    public long playVideoNewWindow(String url) {
        return videoManager.openPublic(url);
    }

    // ====================================================================
    // Playback — Audio
    // ====================================================================

    @Override
    public long playAudio(String url) {
        return audioManager.enqueuePublic(url);
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
