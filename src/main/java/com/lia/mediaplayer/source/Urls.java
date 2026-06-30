package com.lia.mediaplayer.source;

import java.net.URI;
import java.util.Locale;

/**
 * Small URL-parsing helpers shared by the {@link MediaSource} implementations, so
 * the extension-by-file-suffix sources don't each re-implement the same defensive
 * path/host parsing. Package-private: it is an implementation detail of the source
 * registry, not part of the public extension API.
 */
final class Urls {

    private Urls() {
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
