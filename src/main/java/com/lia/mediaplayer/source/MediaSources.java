package com.lia.mediaplayer.source;

import com.lia.mediaplayer.api.MediaKind;
import com.lia.mediaplayer.api.MediaSource;
import com.lia.mediaplayer.api.event.MediaSourceRegistrationEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
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
 * an existing one. All methods are safe to call from any thread.</p>
 *
 * <h2>The lookup cache</h2>
 *
 * <p>{@link #find} used to be a pure function and nothing else, which read well and cost
 * more than it looked. Answering it walks up to fourteen sources, and almost every one
 * of them parses the URL — {@code Urls.isHttp} builds a {@code URI}, then the source
 * builds another for the host or the path — so one lookup is a couple of dozen parses.
 * That would be fine if it were asked once per link. It is not: the chat rewriter asks
 * three times per URL per message (once per media kind), and the hover preview asks
 * again <em>every frame</em> the cursor rests on a link. Caching the answer per URL turns
 * the per-frame cost into a hash lookup, and the URLs repeat by construction — a message
 * stays on screen for tens of seconds.</p>
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

    /**
     * How many resolved URLs to remember. A cap rather than an eviction policy: the map
     * is a memo of a pure function, so throwing all of it away and refilling it costs
     * only the lookups that come back, and there is nothing to release.
     */
    private static final int MAX_CACHED_LOOKUPS = 512;

    /**
     * URL to the source that claims it — {@link #NONE} standing in for "nothing does",
     * since a {@link ConcurrentHashMap} cannot hold a null value and "no source" is
     * exactly the answer worth not recomputing.
     */
    private final Map<String, MediaSource> lookupCache = new ConcurrentHashMap<>();

    /**
     * The sentinel for "no source claims this URL". Never registered, never returned.
     */
    private static final MediaSource NONE = new MediaSource() {
        @Override
        public boolean matches(String url) {
            return false;
        }

        @Override
        public MediaKind kind() {
            return MediaKind.VIDEO;
        }

        @Override
        public Component label(String url) {
            return Component.literal(url);
        }
    };

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
            // A new source may claim links an earlier answer said nothing claimed, so
            // every cached "no" is now suspect.
            lookupCache.clear();
        }
    }

    /**
     * The first source that recognizes {@code url}, if any.
     */
    public Optional<MediaSource> find(String url) {
        if (url == null) {
            return Optional.empty();
        }
        MediaSource cached = lookupCache.get(url);
        if (cached != null) {
            return cached == NONE ? Optional.empty() : Optional.of(cached);
        }
        MediaSource found = NONE;
        for (MediaSource source : registered) {
            if (source.matches(url)) {
                found = source;
                break;
            }
        }
        if (lookupCache.size() >= MAX_CACHED_LOOKUPS) {
            lookupCache.clear();
        }
        lookupCache.put(url, found);
        return found == NONE ? Optional.empty() : Optional.of(found);
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
