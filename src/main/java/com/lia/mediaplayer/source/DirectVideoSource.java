package com.lia.mediaplayer.source;

import net.minecraft.network.chat.Component;

/**
 * A direct video file: a URL whose path ends in a container extension ffmpeg can
 * open straight away (a Discord {@code .mp4}/{@code .webm}/{@code .mov} attachment
 * and friends). Shown as a {@code [video]} label.
 */
public final class DirectVideoSource implements MediaSource {

    private static final Component LABEL = Component.literal("[video]");

    @Override
    public boolean matches(String url) {
        return isDirectVideo(url);
    }

    @Override
    public com.lia.mediaplayer.api.MediaKind kind() {
        return com.lia.mediaplayer.api.MediaKind.VIDEO;
    }

    @Override
    public Component label(String url) {
        return LABEL;
    }

    /** Whether {@code url}'s path ends in a known video container extension. */
    public static boolean isDirectVideo(String url) {
        String path = Urls.pathLower(url);
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
}
