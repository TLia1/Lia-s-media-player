package com.lia.mediaplayer.source;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * The registry of every {@link MediaSource} the mod knows about, and the single
 * place the rest of the mod asks "what, if anything, is this link?".
 *
 * <p>Previously this knowledge was scattered: image rules lived in the image chat
 * handler, Tenor rules in the Tenor resolver, and all video rules in a separate
 * {@code VideoSupport} helper, so adding a new media source meant editing several
 * unrelated files. Centralizing it here makes the system <em>open for extension</em>
 * (register a new {@code MediaSource}) but <em>closed for modification</em> (no
 * caller changes): the chat handlers, the overlay's click routing and the labels
 * all flow through these lookups.</p>
 *
 * <p>Sources are tested in registration order and the first match wins. The built-in
 * sources are mutually exclusive, so order only matters if a future source overlaps
 * an existing one. All methods are stateless and safe to call from any thread.</p>
 */
public final class MediaSources {

    /** The built-in sources, in match order. */
    private static final List<MediaSource> REGISTERED = List.of(
            new TenorSource(),       // a tenor.com/view page (resolved to a GIF later)
            new ImageFileSource(),   // a direct .png/.jpg/.gif/... file
            new YouTubeSource(),     // a youtube.com / youtu.be link
            new StreamSource(),      // an .m3u8 / .mpd manifest
            new DirectVideoSource(), // a direct .mp4/.webm/... file
            new AudioFileSource()    // a direct .mp3/.ogg/.wav/... file
    );

    private MediaSources() {
    }

    /** The first source that recognizes {@code url}, if any. */
    public static Optional<MediaSource> find(String url) {
        for (MediaSource source : REGISTERED) {
            if (source.matches(url)) {
                return Optional.of(source);
            }
        }
        return Optional.empty();
    }

    /** The kind of {@code url}, or {@code null} if no source recognizes it. */
    @Nullable
    public static MediaKind kindOf(String url) {
        return find(url).map(MediaSource::kind).orElse(null);
    }

    /** Whether {@code url} is a recognized image/GIF link. */
    public static boolean isImage(String url) {
        return kindOf(url) == MediaKind.IMAGE;
    }

    /** Whether {@code url} is a recognized video/stream/YouTube link. */
    public static boolean isVideo(String url) {
        return kindOf(url) == MediaKind.VIDEO;
    }

    /** Whether {@code url} is a recognized direct audio file. */
    public static boolean isAudio(String url) {
        return kindOf(url) == MediaKind.AUDIO;
    }

    /** Whether any source recognizes {@code url}. */
    public static boolean isSupported(String url) {
        return find(url).isPresent();
    }

    /** The chat label for {@code url}, or the raw URL text if nothing recognizes it. */
    public static Component labelFor(String url) {
        return find(url).map(source -> source.label(url)).orElseGet(() -> Component.literal(url));
    }
}
