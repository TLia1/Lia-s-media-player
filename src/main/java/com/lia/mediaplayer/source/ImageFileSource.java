package com.lia.mediaplayer.source;

import net.minecraft.network.chat.Component;

/**
 * A direct image file: a URL whose path ends in a still/animated image extension
 * the preview can decode ({@code .png}, {@code .jpg}, {@code .jpeg}, {@code .gif}
 * or {@code .bmp}). Shown as a {@code [picture]} label.
 */
public final class ImageFileSource implements com.lia.mediaplayer.api.MediaSource {

    private static final Component LABEL = Component.literal("[picture]");

    @Override
    public boolean matches(String url) {
        return isImageFile(url);
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
     * Whether {@code url}'s path ends in a known image extension.
     */
    public static boolean isImageFile(String url) {
        String path = Urls.pathLower(url);
        if (path == null) {
            return false;
        }
        return path.endsWith(".png")
                || path.endsWith(".jpg")
                || path.endsWith(".jpeg")
                || path.endsWith(".gif")
                || path.endsWith(".bmp");
    }
}
