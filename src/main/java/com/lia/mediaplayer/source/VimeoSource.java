package com.lia.mediaplayer.source;

import net.minecraft.network.chat.Component;

/**
 * A Vimeo video page ({@code vimeo.com/<id>}) or its embed player
 * ({@code player.vimeo.com/video/<id>}). A page, so it goes through yt-dlp.
 */
public final class VimeoSource implements com.lia.mediaplayer.api.MediaSource {

    private static final Component LABEL = Component.literal("[vimeo]");

    @Override
    public boolean matches(String url) {
        return isVimeo(url);
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
     * Whether {@code url} is a Vimeo link whose path starts with a numeric video id —
     * which is what separates a video from the site's channels, groups and search pages.
     */
    public static boolean isVimeo(String url) {
        if (!Urls.isHttp(url)) {
            return false;
        }
        String host = Urls.hostLower(url);
        if (host == null) {
            return false;
        }
        String path = Urls.pathLower(url);
        if (path == null) {
            return false;
        }
        if (host.equals("player.vimeo.com")) {
            return path.startsWith("/video/")
                    && isNumericId(firstSegment(path.substring("/video".length())));
        }
        if (!host.equals("vimeo.com")) {
            return false;
        }
        // vimeo.com/<id>, and the unlisted form vimeo.com/<id>/<hash>.
        return isNumericId(firstSegment(path));
    }

    private static String firstSegment(String path) {
        String trimmed = path.startsWith("/") ? path.substring(1) : path;
        int slash = trimmed.indexOf('/');
        return slash >= 0 ? trimmed.substring(0, slash) : trimmed;
    }

    private static boolean isNumericId(String segment) {
        if (segment == null || segment.isEmpty()) {
            return false;
        }
        for (int i = 0; i < segment.length(); i++) {
            if (!Character.isDigit(segment.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
