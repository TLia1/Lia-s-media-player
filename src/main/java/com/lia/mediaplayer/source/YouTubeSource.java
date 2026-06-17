package com.lia.mediaplayer.source;

import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * A YouTube watch / share / Shorts link ({@code youtube.com/watch}, {@code youtu.be/...},
 * {@code /shorts/}, {@code /embed/}, {@code /live/}, plus the mobile and music hosts).
 * These are web pages rather than media files, so the playback engine resolves them
 * to a direct stream separately (via {@code yt-dlp}); this source only recognizes and
 * labels them. Shown as a {@code [youtube]} label.
 */
public final class YouTubeSource implements MediaSource {

    private static final Component LABEL = Component.literal("[youtube]");

    @Override
    public boolean matches(String url) {
        return isYouTube(url);
    }

    @Override
    public MediaKind kind() {
        return MediaKind.VIDEO;
    }

    @Override
    public Component label(String url) {
        return LABEL;
    }

    /**
     * Whether {@code url} is a recognized YouTube link. Exposed statically because the
     * playback engine ({@code VideoUrlResolver}, the thumbnail and title caches) also
     * needs to single out YouTube links for their dedicated resolution paths.
     */
    public static boolean isYouTube(String url) {
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
            if (host.equals("youtu.be")) {
                return path != null && path.length() > 1;
            }
            if (host.equals("youtube.com") || host.equals("m.youtube.com") || host.equals("music.youtube.com")) {
                if (path == null) {
                    return false;
                }
                String lower = path.toLowerCase(Locale.ROOT);
                return lower.startsWith("/watch")
                        || lower.startsWith("/shorts/")
                        || lower.startsWith("/embed/")
                        || lower.startsWith("/live/");
            }
            return false;
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
