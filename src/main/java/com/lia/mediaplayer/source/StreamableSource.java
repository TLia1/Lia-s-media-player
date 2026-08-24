package com.lia.mediaplayer.source;

import net.minecraft.network.chat.Component;

/**
 * A Streamable clip page ({@code streamable.com/<id>}, or its {@code /e/<id>} embed).
 * A page, so it goes through yt-dlp.
 */
public final class StreamableSource implements com.lia.mediaplayer.api.MediaSource {

    private static final Component LABEL = Component.literal("[streamable]");

    @Override
    public boolean matches(String url) {
        return isStreamable(url);
    }

    @Override
    public com.lia.mediaplayer.api.MediaKind kind() {
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
     * Whether {@code url} is a Streamable clip. The id is a short alphanumeric code;
     * requiring that shape keeps the site's own pages ({@code /login}, {@code /terms})
     * from being offered as a video.
     */
    public static boolean isStreamable(String url) {
        if (!Urls.isHttp(url)) {
            return false;
        }
        String host = Urls.hostLower(url);
        if (host == null || !host.equals("streamable.com")) {
            return false;
        }
        String path = Urls.pathLower(url);
        if (path == null || path.length() < 2) {
            return false;
        }
        String id = path.substring(1);
        if (id.startsWith("e/")) {
            id = id.substring(2);
        }
        int slash = id.indexOf('/');
        if (slash >= 0) {
            id = id.substring(0, slash);
        }
        if (id.isEmpty() || id.length() > 12) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!Character.isLetterOrDigit(c)) {
                return false;
            }
        }
        return true;
    }
}
