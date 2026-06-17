package com.lia.mediaplayer.source;

import net.minecraft.network.chat.Component;

/**
 * An adaptive-streaming manifest ffmpeg can open directly: HLS ({@code .m3u8}) or
 * DASH ({@code .mpd}). Shown as a {@code [video]} label, like a direct file.
 */
public final class StreamSource implements MediaSource {

    private static final Component LABEL = Component.literal("[video]");

    @Override
    public boolean matches(String url) {
        return isStream(url);
    }

    @Override
    public MediaKind kind() {
        return MediaKind.VIDEO;
    }

    @Override
    public Component label(String url) {
        return LABEL;
    }

    /** Whether {@code url} points at an HLS or DASH manifest. */
    public static boolean isStream(String url) {
        String path = Urls.pathLower(url);
        return path != null && (path.endsWith(".m3u8") || path.endsWith(".mpd"));
    }
}
