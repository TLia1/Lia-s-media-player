package com.lia.mediaplayer.source;

import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * A YouTube <em>playlist</em> page ({@code youtube.com/playlist?list=...}, plus the
 * mobile and music hosts). Unlike every other source this is not a single media item:
 * clicking it expands the playlist into its videos (via {@code yt-dlp}, see
 * {@link com.lia.mediaplayer.media.YouTubePlaylistResolver}) and queues them all.
 * Shown as a {@code [youtube playlist]} label.
 *
 * <p>It is registered before {@link YouTubeSource} but stays disjoint from it: a
 * {@code /watch?v=…&list=…} link is one video <em>within</em> a playlist and keeps
 * playing as that single video, which is what the link itself opens on YouTube. Only
 * the dedicated {@code /playlist} page means "the whole list".</p>
 */
public final class YouTubePlaylistSource implements com.lia.mediaplayer.api.MediaSource {

    private static final Component LABEL = Component.literal("[youtube playlist]");

    @Override
    public boolean matches(String url) {
        return isPlaylist(url);
    }

    @Override
    public com.lia.mediaplayer.api.MediaKind kind() {
        // The entries are played as video by default (alt-click still routes to the
        // audio player, exactly like a single YouTube link).
        return com.lia.mediaplayer.api.MediaKind.VIDEO;
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
     * Whether {@code url} is a YouTube playlist page. Exposed statically because the
     * click routing and the playlist editor both need to single these links out before
     * they reach the players.
     */
    public static boolean isPlaylist(String url) {
        if (!Urls.isHttp(url)) {
            return false;
        }
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            String path = uri.getPath();
            if (host == null || path == null) {
                return false;
            }
            host = host.toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            if (!host.equals("youtube.com") && !host.equals("m.youtube.com") && !host.equals("music.youtube.com")) {
                return false;
            }
            return path.toLowerCase(Locale.ROOT).startsWith("/playlist") && listId(uri) != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * The {@code list=} query parameter, or {@code null} when there is none.
     */
    private static String listId(URI uri) {
        String query = uri.getQuery();
        if (query == null) {
            return null;
        }
        for (String param : query.split("&")) {
            int eq = param.indexOf('=');
            if (eq > 0 && param.substring(0, eq).equals("list")) {
                String value = param.substring(eq + 1);
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }
}
