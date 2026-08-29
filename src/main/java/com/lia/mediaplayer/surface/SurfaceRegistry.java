package com.lia.mediaplayer.surface;

import com.lia.mediaplayer.LiasMediaPlayer;
import com.lia.mediaplayer.api.render.MediaSurface;
import com.lia.mediaplayer.api.render.SurfaceOptions;
import com.lia.mediaplayer.config.ConfigStore;
import com.lia.mediaplayer.source.Urls;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Every live off-screen surface, and the budget they share.
 *
 * <p>Owned by {@code MediaPlayerContext} beside the three caches, and emptied by the same
 * disconnect path, because it is the same kind of thing: a lifecycle and a budget over
 * something that costs GPU memory and processes.</p>
 *
 * <h2>Why this is not a {@code MediaCache}</h2>
 *
 * <p>Everything else the mod decodes is bounded by {@code media.MediaCache}, whose whole
 * contract is "past the bound, evict the eldest". That is exactly the wrong thing here:
 * the eldest surface is quite likely the cinema screen someone is standing in front of,
 * and pulling its texture out from under a renderer that is still drawing it is the leak
 * this package exists to avoid, wearing a bound's clothes. Reference counting bounds it
 * instead — an entry lives while anyone holds it and is disposed the moment nobody does —
 * and the configured caps are applied at <em>creation</em>: past them a request is
 * refused and logged, which is something an addon author can see and fix, unlike a screen
 * that mysteriously goes black when the twelfth one opens.</p>
 *
 * <h2>The two caps</h2>
 *
 * <p>One on surfaces in total, and a tighter one on surfaces <em>decoding video</em>,
 * because those are the expensive kind: each is an ffmpeg process with a frame queue and
 * an audio line behind it, while a poster is one launch and then a still picture. An
 * addon looping over block entities can otherwise start fifty processes without ever
 * meaning to.</p>
 *
 * <p>Render thread only.</p>
 */
public final class SurfaceRegistry {

    /**
     * Live entries by key. Insertion-ordered so a listing reads in the order things were
     * asked for; nothing here is evicted by age — see the class note.
     */
    private final Map<String, SurfaceEntry> entries = new LinkedHashMap<>();

    public SurfaceRegistry() {
    }

    // ------------------------------------------------------------------
    // Creating
    // ------------------------------------------------------------------

    /** A still or animated image. */
    public MediaSurface image(String url) {
        return image(url, false);
    }

    /**
     * The same, optionally keeping the decoded pixels readable through
     * {@code MediaSurface.pixels()}.
     *
     * <p>{@code keepPixels} is part of the key, like a video's options are: two callers
     * share one decode only when they asked for the same thing, and one of them wanting
     * to read the picture back is a different thing to decode.</p>
     */
    public MediaSurface image(String url, boolean keepPixels) {
        if (!Urls.isHttp(url)) {
            return refuse(String.valueOf(url), "not an http(s) URL");
        }
        String key = keepPixels ? "image+px " + url : "image " + url;
        return acquire(key, k -> new ImageSurfaceEntry(k, url, keepPixels));
    }

    /** A video decoded off-screen. */
    public MediaSurface video(String url, SurfaceOptions options) {
        if (!Urls.isHttp(url)) {
            return refuse(String.valueOf(url), "not an http(s) URL");
        }
        int videoCap = ConfigStore.MAX_API_VIDEO_SURFACES.getValue();
        if (decodingVideoCount() >= videoCap) {
            return refuse(url, "the video-surface cap (" + videoCap + ") is already reached");
        }
        // The options are part of the key: two callers share a decode only when they
        // asked for the same one, and a different resolution cap is a different picture.
        String key = "video " + url + " " + options.maxWidth() + "x" + options.maxHeight()
                + " " + options.loop() + " " + options.autoplay();
        return acquire(key, k -> new VideoSurfaceEntry(k, url, options));
    }

    /** A single frame at a timestamp. */
    public MediaSurface thumbnail(String url, double atSeconds) {
        if (!Urls.isHttp(url)) {
            return refuse(String.valueOf(url), "not an http(s) URL");
        }
        double at = atSeconds > 0 ? atSeconds : 0;
        return acquire("thumb " + url + " " + at, key -> new ThumbnailSurfaceEntry(key, url, at));
    }

    /**
     * The shared half of the three factories above: find or build the entry for
     * {@code key}, take a reference on it, and hand back one caller's view.
     *
     * <p>Package-private rather than private so the reference counting, the cap and the
     * disposal can be driven from a test with an entry that decodes nothing. Those three
     * are exactly where a leak would hide, and none of them can be exercised through the
     * public factories without a running game.</p>
     */
    MediaSurface acquire(String key, Function<String, SurfaceEntry> factory) {
        SurfaceEntry entry = entries.get(key);
        if (entry == null) {
            int cap = ConfigStore.MAX_API_SURFACES.getValue();
            if (entries.size() >= cap) {
                return refuse(key, "the surface cap (" + cap + ") is already reached");
            }
            entry = factory.apply(key);
            entries.put(key, entry);
        }
        entry.acquire();
        return new SurfaceAcquisition(this, entry);
    }

    private MediaSurface refuse(String what, String why) {
        LiasMediaPlayer.LOGGER.warn("Refusing a media surface for {}: {}", what, why);
        return FailedSurface.INSTANCE;
    }

    // ------------------------------------------------------------------
    // Releasing
    // ------------------------------------------------------------------

    /** One acquisition let go; the decode goes with the last of them. */
    void release(SurfaceEntry entry) {
        if (entry.release()) {
            entries.remove(entry.key);
            entry.disposeOnce();
        }
    }

    /**
     * Drops every surface, whoever is still holding one.
     *
     * <p>Called from the disconnect sweep, with the caches. An addon's surface is not
     * allowed to survive the world it was decoded for: its texture would be a leak for
     * the rest of the session, and the ffmpeg process behind it would keep running for a
     * screen that no longer exists. What the addon is left holding answers
     * {@code isReady() == false} and {@code texture() == null}, which is the same thing it
     * already handles while a surface is loading.</p>
     */
    public void disposeAll() {
        List<SurfaceEntry> live = new ArrayList<>(entries.values());
        entries.clear();
        for (SurfaceEntry entry : live) {
            entry.disposeOnce();
        }
    }

    // ------------------------------------------------------------------
    // Per-tick
    // ------------------------------------------------------------------

    /**
     * Rolls every entry's "wanted" flag over and lets it act on it. Called once a tick,
     * which is the resolution the back-pressure needs: frames are drawn between ticks, so
     * a surface drawn at all during one counts as wanted for it.
     */
    public void clientTick() {
        if (entries.isEmpty()) {
            return;
        }
        for (SurfaceEntry entry : new ArrayList<>(entries.values())) {
            entry.tick();
        }
    }

    // ------------------------------------------------------------------
    // Diagnostics
    // ------------------------------------------------------------------

    /** How many surfaces exist. */
    public int size() {
        return entries.size();
    }

    /** How many of them are running a video decode — the expensive ones. */
    public int decodingVideoCount() {
        int n = 0;
        for (SurfaceEntry entry : entries.values()) {
            if (entry.isDecodingVideo()) {
                n++;
            }
        }
        return n;
    }
}
