package com.lia.mediaplayer.media;

import com.lia.mediaplayer.LiasMediaPlayer;
import com.lia.mediaplayer.source.YouTubeSource;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Resolves and caches a human-readable <em>title</em> for each queued media URL, so a
 * queue panel (video or audio) can show what an entry actually is instead of a generic
 * {@code "YouTube"} / file-name label:
 *
 * <ul>
 *   <li><b>YouTube links</b> are resolved to the real video title via YouTube's
 *       public oEmbed endpoint (a small JSON request — no {@code yt-dlp} needed).</li>
 *   <li><b>Direct files / streams</b> use the file name from the URL, which is already
 *       available without any network call.</li>
 * </ul>
 *
 * <p>This lives in the shared {@link com.lia.mediaplayer.media} layer so both the video
 * and audio players reuse one cache. Resolution runs on the IO pool; the cache is only
 * mutated on the render/main thread. While a YouTube title is loading,
 * {@link #getOrLoad} returns a sensible placeholder so the panel never shows a blank
 * row. All public methods must be called from the main thread.</p>
 */
public final class MediaTitleCache {
    private static final int MAX_ENTRIES = 128;
    /** Hard cap on the stored title length so a pathological title can't bloat memory. */
    private static final int MAX_TITLE_LEN = 200;

    private static final LinkedHashMap<String, Entry> CACHE = new LinkedHashMap<>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    private MediaTitleCache() {
    }

    /**
     * Returns the best title known for a URL, starting a one-off background load the
     * first time a YouTube link is seen. The returned string is always non-blank: a
     * placeholder while a title is still loading, the file name for direct links, or
     * the resolved video title once available.
     */
    public static String getOrLoad(String url) {
        Entry entry = CACHE.computeIfAbsent(url, MediaTitleCache::newEntry);
        if (entry.state == State.IDLE) {
            // Direct files already have their final title (the file name); only
            // YouTube links need a network round-trip.
            if (YouTubeSource.isYouTube(url)) {
                startLoading(url, entry);
            } else {
                entry.state = State.LOADED;
            }
        }
        return entry.title;
    }

    /** Drops every cached title (e.g. when leaving a server). */
    public static void clear() {
        CACHE.clear();
    }

    private static Entry newEntry(String url) {
        return new Entry(fallbackLabel(url));
    }

    private static void startLoading(String url, Entry entry) {
        entry.state = State.LOADING;
        CompletableFuture
                .supplyAsync(() -> fetchYouTubeTitle(url), Util.ioPool())
                .whenCompleteAsync((title, error) -> onComplete(url, entry, title, error),
                        Minecraft.getInstance());
    }

    // ------------------------------------------------------------------
    // Background work (IO pool) — never touch the cache from here
    // ------------------------------------------------------------------

    private static String fetchYouTubeTitle(String url) {
        try {
            String endpoint = "https://www.youtube.com/oembed?url="
                    + URLEncoder.encode(url, StandardCharsets.UTF_8) + "&format=json";
            HttpURLConnection connection = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 liasmediaplayer title");
            connection.setRequestProperty("Accept", "application/json");
            try {
                int code = connection.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    throw new IOException("HTTP " + code + " for " + endpoint);
                }
                try (InputStream in = connection.getInputStream()) {
                    String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                    JsonElement title = obj.get("title");
                    if (title != null && !title.isJsonNull()) {
                        String text = title.getAsString().strip();
                        if (!text.isBlank()) {
                            return clamp(text);
                        }
                    }
                    throw new IOException("no title in oEmbed response");
                }
            } finally {
                connection.disconnect();
            }
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }

    // ------------------------------------------------------------------
    // Main thread — publish the result
    // ------------------------------------------------------------------

    private static void onComplete(String url, Entry entry, String title, Throwable error) {
        // The entry may have been evicted while the load was in flight.
        if (CACHE.get(url) != entry) {
            return;
        }
        if (error != null || title == null || title.isBlank()) {
            entry.state = State.FAILED; // keep the fallback label already in entry.title
            Throwable cause = error instanceof CompletionException && error.getCause() != null
                    ? error.getCause() : error;
            LiasMediaPlayer.LOGGER.debug("No title for {}: {}", url, cause == null ? "?" : cause.toString());
            return;
        }
        entry.title = title;
        entry.state = State.LOADED;
    }

    // ------------------------------------------------------------------
    // Fallback labels (no network)
    // ------------------------------------------------------------------

    /** The label shown before (or instead of) a resolved title. */
    private static String fallbackLabel(String url) {
        if (YouTubeSource.isYouTube(url)) {
            return "YouTube link…";
        }
        String name = fileName(url);
        return name != null ? name : url;
    }

    /** The file name at the end of a direct-link URL path, or {@code null}. */
    private static String fileName(String url) {
        try {
            String path = URI.create(url).getPath();
            if (path != null) {
                int slash = path.lastIndexOf('/');
                String name = slash >= 0 ? path.substring(slash + 1) : path;
                if (!name.isBlank()) {
                    return clamp(name);
                }
            }
        } catch (IllegalArgumentException ignored) {
            // fall through
        }
        return null;
    }

    private static String clamp(String s) {
        return s.length() <= MAX_TITLE_LEN ? s : s.substring(0, MAX_TITLE_LEN);
    }

    enum State {IDLE, LOADING, LOADED, FAILED}

    /** A single cached title and its load state. */
    private static final class Entry {
        State state = State.IDLE;
        String title;

        Entry(String initial) {
            this.title = initial;
        }
    }
}
