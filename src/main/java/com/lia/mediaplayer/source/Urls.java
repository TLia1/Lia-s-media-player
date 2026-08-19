package com.lia.mediaplayer.source;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Small URL-parsing helpers shared by the {@link com.lia.mediaplayer.api.MediaSource}
 * implementations, so the extension-by-file-suffix sources don't each re-implement the
 * same defensive path/host parsing.
 *
 * <p>{@link #isHttp(String)} is the gate every built-in source applies before looking at
 * a link: media URLs reach {@code ffmpeg}, {@code yt-dlp}, {@link java.net.HttpURLConnection}
 * and the system browser, and all of those interpret far more than {@code http(s)} —
 * {@code file:}, {@code concat:}, a custom OS protocol handler, or a string starting with
 * {@code -} that a command-line tool would read as an option. Chat links can be crafted by
 * anyone on the server, so the whole pipeline is restricted to real {@code http(s)} URLs
 * with a host at the point where a link is first recognized.</p>
 */
public final class Urls {

    private Urls() {
    }

    /**
     * Whether {@code url} is an absolute {@code http} or {@code https} URL with a host.
     *
     * <p>This is the only shape the mod is willing to hand to an external tool, so every
     * built-in source requires it before claiming a link.</p>
     */
    public static boolean isHttp(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null) {
                return false;
            }
            scheme = scheme.toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                return false;
            }
            // getHost() is null for hosts URI considers non-compliant (an underscore, for
            // one), which are unusual but real; the raw authority still tells us a host was
            // given. What matters here is that there *is* one — "file:///x.mp4" and
            // "https:///x.mp4" have none, and those are the shapes being kept out.
            if (uri.getHost() != null && !uri.getHost().isBlank()) {
                return true;
            }
            String authority = uri.getAuthority();
            return authority != null && !authority.isBlank();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * The lower-cased path component of {@code url}, or {@code null} if it can't be parsed.
     */
    static String pathLower(String url) {
        try {
            String path = URI.create(url).getPath();
            return path == null ? null : path.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The lower-cased host of {@code url} with a leading {@code www.} stripped, or {@code null}.
     */
    static String hostLower(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) {
                return null;
            }
            host = host.toLowerCase(Locale.ROOT);
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            return null;
        }
    }
}
