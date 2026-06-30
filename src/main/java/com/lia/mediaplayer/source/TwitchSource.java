package com.lia.mediaplayer.source;

import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * A Twitch stream or VOD link ({@code twitch.tv/...}).
 * These are web pages rather than media files, so the playback engine resolves them
 * to a direct stream separately (via {@code yt-dlp}); this source only recognizes and
 * labels them. Shown as a {@code [twitch]} label.
 */
public final class TwitchSource implements com.lia.mediaplayer.api.MediaSource {

    private static final Component LABEL = Component.literal("[twitch]");

    @Override
    public boolean matches(String url) {
        return isTwitch(url);
    }

    @Override
    public com.lia.mediaplayer.api.MediaKind kind() {
        return com.lia.mediaplayer.api.MediaKind.VIDEO;
    }

    @Override
    public Component label(String url) {
        return LABEL;
    }

    /**
     * Whether {@code url} is a recognized Twitch link. Exposed statically because the
     * playback engine ({@code MediaUrlResolver}, the thumbnail and title caches) also
     * needs to single out Twitch links for their dedicated resolution paths.
     */
    public static boolean isTwitch(String url) {
        if (url == null) return false;
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            String path = uri.getPath();
            if (host == null) {
                return false;
            }
            host = host.toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            if (host.equals("twitch.tv") || host.equals("m.twitch.tv")) {
                if (path == null) {
                    return false;
                }
                return path.length() > 1;
            }
            return false;
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
