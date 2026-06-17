package com.lia.mediaplayer;

import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * URL classification for the in-game video player.
 *
 * <p>Recognizes two families of links that the Discord chat bridge can relay:</p>
 * <ul>
 *   <li><b>Direct media</b> — a file URL ending in a known video extension
 *       (Discord attachments such as {@code .mp4}/{@code .webm}/{@code .mov}),
 *       or an HLS/DASH manifest ({@code .m3u8}/{@code .mpd}). FFmpeg can open
 *       these straight away.</li>
 *   <li><b>YouTube</b> — {@code youtube.com/watch}, {@code youtu.be/...} and
 *       Shorts links. These are web pages, not media, so they go through
 *       {@link VideoUrlResolver} (which shells out to {@code yt-dlp}).</li>
 * </ul>
 *
 * <p>The set of recognized shapes mirrors how {@code ChatImagePreviewHandler}
 * recognizes images, so the two features can coexist without fighting over the
 * same link (a {@code .gif} is an image; a {@code .mp4} is a video).</p>
 */
final class VideoSupport {

    private VideoSupport() {
    }

    /** Any link the player can attempt to play: a direct media file, a stream, or YouTube. */
    static boolean isVideoUrl(String url) {
        return isDirectVideoUrl(url) || isStreamUrl(url) || isYouTubeUrl(url);
    }

    /** A direct video file: the path ends in a container extension we know FFmpeg handles. */
    static boolean isDirectVideoUrl(String url) {
        String path = pathOf(url);
        if (path == null) {
            return false;
        }
        return path.endsWith(".mp4")
                || path.endsWith(".webm")
                || path.endsWith(".mov")
                || path.endsWith(".mkv")
                || path.endsWith(".m4v")
                || path.endsWith(".avi")
                || path.endsWith(".flv")
                || path.endsWith(".ogv")
                || path.endsWith(".ts");
    }

    /** An adaptive-streaming manifest FFmpeg can open directly. */
    static boolean isStreamUrl(String url) {
        String path = pathOf(url);
        return path != null && (path.endsWith(".m3u8") || path.endsWith(".mpd"));
    }

    /** A YouTube watch / share / Shorts link that needs resolving to a media URL. */
    static boolean isYouTubeUrl(String url) {
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

    /** The clickable chat label shown in place of the raw URL. */
    static Component labelFor(String url) {
        if (isYouTubeUrl(url)) {
            return Component.literal("[youtube]");
        }
        return Component.literal("[video]");
    }

    private static String pathOf(String url) {
        try {
            String path = URI.create(url).getPath();
            return path == null ? null : path.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
