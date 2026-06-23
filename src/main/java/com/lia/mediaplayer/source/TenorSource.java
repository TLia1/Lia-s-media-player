package com.lia.mediaplayer.source;

import net.minecraft.network.chat.Component;

/**
 * A Tenor share/view page (e.g. {@code https://tenor.com/view/...-gif-12345}), the
 * link Discord sends when a user picks a GIF from the Tenor picker. The page is an
 * HTML document rather than an image file, so it never matches
 * {@link ImageFileSource}; the actual GIF behind it is resolved later, on download.
 * Shown as a {@code [gif]} label.
 *
 * <p>Direct Tenor media links ({@code media.tenor.com/...gif}, {@code c.tenor.com/...gif})
 * already end in a known extension and are handled by {@link ImageFileSource}, so
 * they are deliberately excluded here.</p>
 */
public final class TenorSource implements com.lia.mediaplayer.api.MediaSource {

    private static final Component LABEL = Component.literal("[gif]");

    @Override
    public boolean matches(String url) {
        return isTenorPage(url);
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
     * True for Tenor share/view pages that still need resolving to a direct GIF.
     * The path may carry a locale prefix, e.g. {@code /fr/view/...} or {@code /view/...}.
     */
    public static boolean isTenorPage(String url) {
        String host = Urls.hostLower(url);
        String path = Urls.pathLower(url);
        if (host == null || path == null) {
            return false;
        }
        return host.equals("tenor.com") && path.contains("/view/");
    }
}
