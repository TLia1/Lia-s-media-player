package com.lia.mediaplayer.source;

import com.lia.mediaplayer.api.MediaKind;
import com.lia.mediaplayer.api.MediaSource;
import net.minecraft.network.chat.Component;

/**
 * A SoundCloud track or set page ({@code soundcloud.com/<artist>/<track>}).
 *
 * <p>Like {@link YouTubeSource} and {@link TwitchSource} these are web pages rather than
 * media files, so they are {@linkplain #requiresExtractor() resolved by yt-dlp} before
 * ffmpeg sees them. SoundCloud has no video, so the link is claimed as
 * {@link MediaKind#AUDIO} and opens the compact audio bar.</p>
 */
public final class SoundCloudSource implements MediaSource {

    private static final Component LABEL = Component.translatable("chat.liasmediaplayer.label.soundcloud");

    @Override
    public boolean matches(String url) {
        return isSoundCloud(url);
    }

    @Override
    public MediaKind kind() {
        return MediaKind.AUDIO;
    }

    @Override
    public Component label(String url) {
        return LABEL;
    }

    @Override
    public boolean requiresExtractor() {
        return true;
    }

    /**
     * Whether {@code url} is a SoundCloud link that names something to play.
     *
     * <p>A bare {@code soundcloud.com/} (or the site's own front-page sections) has no
     * track behind it, so a path segment is required — the same rule
     * {@link TwitchSource} applies.</p>
     */
    public static boolean isSoundCloud(String url) {
        if (!Urls.isHttp(url)) {
            return false;
        }
        String host = Urls.hostLower(url);
        if (host == null) {
            return false;
        }
        if (!host.equals("soundcloud.com") && !host.equals("m.soundcloud.com")
                && !host.equals("on.soundcloud.com") && !host.equals("snd.sc")) {
            return false;
        }
        String path = Urls.pathLower(url);
        return path != null && path.length() > 1;
    }
}
