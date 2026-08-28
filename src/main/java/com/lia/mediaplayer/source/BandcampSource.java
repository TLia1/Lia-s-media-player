package com.lia.mediaplayer.source;

import com.lia.mediaplayer.api.MediaKind;
import com.lia.mediaplayer.api.MediaSource;
import net.minecraft.network.chat.Component;

/**
 * A Bandcamp track or album page ({@code <artist>.bandcamp.com/track/<name>}).
 *
 * <p>A page, so it is {@linkplain #requiresExtractor() resolved by yt-dlp}; audio-only,
 * so it plays in the audio bar. Artists get their own sub-domain, which is why the host
 * test accepts any {@code *.bandcamp.com} rather than a fixed list.</p>
 */
public final class BandcampSource implements MediaSource {

    private static final Component LABEL = Component.translatable("chat.liasmediaplayer.label.bandcamp");

    @Override
    public boolean matches(String url) {
        return isBandcamp(url);
    }

    @Override
    public MediaKind kind() {
        return MediaKind.AUDIO;
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
     * Whether {@code url} is a Bandcamp {@code /track/} or {@code /album/} page. An
     * artist's landing page is not claimed: there is no one thing on it to play.
     */
    public static boolean isBandcamp(String url) {
        if (!Urls.isHttp(url)) {
            return false;
        }
        String host = Urls.hostLower(url);
        if (host == null || !(host.equals("bandcamp.com") || host.endsWith(".bandcamp.com"))) {
            return false;
        }
        String path = Urls.pathLower(url);
        return path != null && (path.startsWith("/track/") || path.startsWith("/album/"));
    }
}
