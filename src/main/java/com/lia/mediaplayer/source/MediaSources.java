package com.lia.mediaplayer.source;

import com.lia.mediaplayer.api.MediaKind;
import com.lia.mediaplayer.api.MediaSource;
import com.lia.mediaplayer.api.event.MediaSourceRegistrationEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

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
public class MediaSources {

    /**
     * The registered sources, in match order. Mutable so addons can append.
     */
    private final List<MediaSource> registered = new CopyOnWriteArrayList<>(List.of(
            new TenorSource(),       // a tenor.com/view page (resolved to a GIF later)
            new GiphySource(),       // a giphy.com/gifs page (rewritten to a GIF)
            new ImageFileSource(),   // a direct .png/.jpg/.gif/... file
            new YouTubePlaylistSource(), // a youtube.com/playlist page (expanded on click)
            new YouTubeSource(),     // a youtube.com / youtu.be link
            new TwitchSource(),      // a twitch.tv link
            new VimeoSource(),       // a vimeo.com/<id> page
            new StreamableSource(),  // a streamable.com/<id> page
            new RedditVideoSource(), // a v.redd.it link or a Reddit comment page
            new SoundCloudSource(),  // a soundcloud.com track page
            new BandcampSource(),    // a *.bandcamp.com track/album page
            new StreamSource(),      // an .m3u8 / .mpd manifest
            new DirectVideoSource(), // a direct .mp4/.webm/... file
            new AudioFileSource()    // a direct .mp3/.ogg/.wav/... file
    ));

    public MediaSources() {
    }

    /**
     * Registers a custom media source. Called by the API facade and by the
     * {@link MediaSourceRegistrationEvent}.
     * Sources are appended after the built-in ones.
     */
    public void register(MediaSource source) {
        if (source != null) {
            registered.add(source);
        }
    }

    /**
     * The first source that recognizes {@code url}, if any.
     */
    public Optional<MediaSource> find(String url) {
        for (MediaSource source : registered) {
            if (source.matches(url)) {
                return Optional.of(source);
            }
        }
        return Optional.empty();
    }

    /**
     * The kind of {@code url}, or {@code null} if no source recognizes it. {@link MediaKind}
     * is an {@code api} type, so this is also what {@code MediaPlayerContext} hands to addons.
     */
    @Nullable
    public MediaKind kindOf(String url) {
        return find(url).map(MediaSource::kind).orElse(null);
    }

    /**
     * Whether {@code url} is a recognized image/GIF link.
     */
    public boolean isImage(String url) {
        return kindOf(url) == MediaKind.IMAGE;
    }

    /**
     * Whether {@code url} is a recognized video/stream/YouTube link.
     */
    public boolean isVideo(String url) {
        return kindOf(url) == MediaKind.VIDEO;
    }

    /**
     * Whether {@code url} is a recognized direct audio file.
     */
    public boolean isAudio(String url) {
        return kindOf(url) == MediaKind.AUDIO;
    }

    /**
     * Whether any source recognizes {@code url}.
     */
    public boolean isSupported(String url) {
        return find(url).isPresent();
    }

    /**
     * Whether the source claiming {@code url} needs the external extractor
     * ({@code yt-dlp}) to turn it into something ffmpeg can open — see
     * {@link MediaSource#requiresExtractor()}. {@code false}
     * for a link nothing recognizes, which is the same answer a direct file gives:
     * hand it to ffmpeg and let it say why it could not be opened.
     */
    public boolean requiresExtractor(String url) {
        return find(url).map(MediaSource::requiresExtractor).orElse(false);
    }

    /**
     * The chat label for {@code url}, or the raw URL text if nothing recognizes it.
     */
    public Component labelFor(String url) {
        return find(url).map(source -> source.label(url)).orElseGet(() -> Component.literal(url));
    }
}
