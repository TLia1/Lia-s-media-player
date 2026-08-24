package com.lia.mediaplayer.source;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A Giphy share page ({@code giphy.com/gifs/<slug>-<id>} or {@code giphy.com/clips/...}).
 *
 * <p>The sibling of {@link TenorSource}: a page rather than an image, claimed as
 * {@link com.lia.mediaplayer.api.MediaKind#IMAGE} and turned into the GIF behind it
 * before the preview cache downloads anything. Unlike Tenor, no page fetch is needed —
 * the media id is the last dash-separated token of the slug, and Giphy serves every GIF
 * from a fixed endpoint built out of it ({@link #directGif}). Giphy's own
 * {@code media*.giphy.com/.../giphy.gif} links end in {@code .gif} and are already
 * claimed by {@link ImageFileSource}.</p>
 */
public final class GiphySource implements com.lia.mediaplayer.api.MediaSource {

    private static final Component LABEL = Component.literal("[gif]");

    @Override
    public boolean matches(String url) {
        return directGif(url) != null;
    }

    @Override
    public com.lia.mediaplayer.api.MediaKind kind() {
        return com.lia.mediaplayer.api.MediaKind.IMAGE;
    }

    @Override
    public Component label(String url) {
        return LABEL;
    }

    /**
     * Whether {@code url} is a Giphy page this source can turn into a GIF.
     */
    public static boolean isGiphyPage(String url) {
        return directGif(url) != null;
    }

    /**
     * The direct GIF behind a Giphy page, or {@code null} when {@code url} is not one.
     *
     * <p>Pure string work, so the image pipeline can call it from anywhere without a
     * network round-trip — the reason Giphy needs no resolver of its own.</p>
     */
    @Nullable
    public static String directGif(String url) {
        String id = mediaId(url);
        return id == null ? null : "https://i.giphy.com/media/" + id + "/giphy.gif";
    }

    /**
     * The media id at the end of a Giphy page path, or {@code null}.
     *
     * <p>A share path is {@code /gifs/<words-describing-it>-<id>}; the id is the final
     * dash-separated token and is always alphanumeric. Ids are long enough that a
     * one-word slug with no id at all ({@code /gifs/cats}) is rejected by the length
     * floor rather than mistaken for one.</p>
     */
    @Nullable
    private static String mediaId(String url) {
        if (!Urls.isHttp(url)) {
            return null;
        }
        String host = Urls.hostLower(url);
        if (host == null || !host.equals("giphy.com")) {
            return null;
        }
        String path = Urls.pathLower(url);
        if (path == null) {
            return null;
        }
        if (!path.startsWith("/gifs/") && !path.startsWith("/clips/") && !path.startsWith("/stickers/")) {
            return null;
        }
        String slug = path.substring(path.indexOf('/', 1) + 1);
        int end = slug.indexOf('/');
        if (end >= 0) {
            slug = slug.substring(0, end);
        }
        String id = slug.substring(slug.lastIndexOf('-') + 1);
        if (id.length() < 8) {
            return null;
        }
        for (int i = 0; i < id.length(); i++) {
            if (!Character.isLetterOrDigit(id.charAt(i))) {
                return null;
            }
        }
        return id;
    }
}
