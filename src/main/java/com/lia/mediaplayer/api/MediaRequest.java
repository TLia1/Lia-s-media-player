/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api;

import com.lia.mediaplayer.api.window.Placement;
import com.lia.mediaplayer.api.window.Sizing;
import com.lia.mediaplayer.api.window.WindowChromeOptions;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Everything an addon can say about how something should be played, in one object.
 *
 * <pre>{@code
 * MediaHandle handle = api.play(MediaRequest.of(url)
 *         .newWindow(true)
 *         .placement(Placement.anchored(Anchor.TOP_RIGHT, 4, 4))
 *         .sizing(Sizing.fractionOfScreen(0.25))
 *         .chrome(WindowChromeOptions.bare())
 *         .closeWhenEnded(true));
 * }</pre>
 *
 * <p>A builder rather than a dozen overloads on the facade, because the options are
 * genuinely independent and the combinations are the point. Every one of them has a
 * default that reproduces exactly what the mod does today, so a bare
 * {@code api.play(MediaRequest.of(url))} behaves like a click on a chat link.</p>
 *
 * <p><b>Mutable and fluent.</b> The setters return {@code this}, which makes a request a
 * poor thing to share: {@link #copy()} before handing one to something that might keep
 * it, and before reusing a template. Nothing here reads any game state, so a request can
 * be built on any thread — it is {@code play} that is render-thread only.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 2.2.0
 */
public final class MediaRequest {

    private final List<String> urls;
    private MediaKind kind;
    private boolean newWindow;
    private Placement placement = Placement.remembered();
    private Sizing sizing = Sizing.auto();
    private long startMicros;
    private boolean autoplay = true;
    private boolean shuffle;
    private RepeatMode repeat = RepeatMode.OFF;
    private Component title;
    private WindowChromeOptions chrome = WindowChromeOptions.full();
    private boolean persistGeometry;
    private boolean closeWhenEnded = true;

    private MediaRequest(List<String> urls) {
        this.urls = urls;
    }

    /**
     * A request to play one link.
     *
     * @throws IllegalArgumentException if {@code url} is not an absolute {@code http(s)}
     *                                  URL with a host. Not a silent no-op, because a
     *                                  {@code file:} or {@code concat:} URL reaching a
     *                                  downloaded binary is the one thing this API exists
     *                                  to prevent, and an addon handing one over has a
     *                                  bug worth being told about.
     */
    public static MediaRequest of(String url) {
        if (!isHttpUrl(url)) {
            throw new IllegalArgumentException("Not an http(s) URL with a host: " + url);
        }
        List<String> list = new ArrayList<>(1);
        list.add(url);
        return new MediaRequest(list);
    }

    /**
     * A request to play several links: the first starts, the rest queue behind it.
     * Entries that are not {@code http(s)} URLs are dropped.
     *
     * @throws IllegalArgumentException if nothing usable is left
     */
    public static MediaRequest ofAll(List<String> urls) {
        List<String> kept = new ArrayList<>();
        if (urls != null) {
            for (String url : urls) {
                if (isHttpUrl(url)) {
                    kept.add(url);
                }
            }
        }
        if (kept.isEmpty()) {
            throw new IllegalArgumentException("No http(s) URLs in the list");
        }
        return new MediaRequest(kept);
    }

    /**
     * Forces which player takes this — {@link MediaKind#AUDIO} on a video link is the
     * "play it as sound only" path the alt modifier gives a chat link. Leave it unset and
     * the registered {@link MediaSource}s decide, which is almost always right.
     */
    public MediaRequest as(@Nullable MediaKind kind) {
        this.kind = kind;
        return this;
    }

    /**
     * {@code true} to open a player of its own; the default queues into the front-most
     * one, which is what a chat click does.
     */
    public MediaRequest newWindow(boolean value) {
        this.newWindow = value;
        return this;
    }

    public MediaRequest placement(Placement value) {
        this.placement = value == null ? Placement.remembered() : value;
        return this;
    }

    public MediaRequest sizing(Sizing value) {
        this.sizing = value == null ? Sizing.auto() : value;
        return this;
    }

    /**
     * Starts this many microseconds in. Applied once, as soon as the player is running.
     *
     * @throws IllegalArgumentException if negative
     */
    public MediaRequest startAt(long micros) {
        if (micros < 0) {
            throw new IllegalArgumentException("startAt must not be negative, was " + micros);
        }
        this.startMicros = micros;
        return this;
    }

    /** {@code false} opens the window paused on its first frame. */
    public MediaRequest autoplay(boolean value) {
        this.autoplay = value;
        return this;
    }

    /**
     * Shuffles the queue, and keeps shuffling: every looped round is reshuffled rather
     * than replaying the order the first round happened to get.
     */
    public MediaRequest shuffle(boolean value) {
        this.shuffle = value;
        return this;
    }

    /**
     * How the window loops once the queue runs out. {@link RepeatMode#OFF} by default,
     * which is the mod's own behaviour.
     *
     * @since API 2.3.0
     */
    public MediaRequest repeat(RepeatMode mode) {
        this.repeat = mode == null ? RepeatMode.OFF : mode;
        return this;
    }

    /**
     * Overrides the title the window shows, instead of the one the mod resolves. Use a
     * {@code Component.translatable} from your own lang files for anything you wrote;
     * {@code literal} is for a name that came from elsewhere.
     */
    public MediaRequest title(@Nullable Component value) {
        this.title = value;
        return this;
    }

    public MediaRequest chrome(WindowChromeOptions value) {
        this.chrome = value == null ? WindowChromeOptions.full() : value;
        return this;
    }

    /**
     * Whether this window's position and size are written back to the user's
     * {@code windows.json}. <b>Off by default for API-opened windows</b> — see
     * {@code MediaWindowHandle.persistsGeometry()} for why that matters.
     */
    public MediaRequest persistGeometry(boolean value) {
        this.persistGeometry = value;
        return this;
    }

    /**
     * Whether the window closes itself once it has nothing left to play. On by default,
     * which is the mod's own behaviour; {@code false} leaves it open on its last frame.
     */
    public MediaRequest closeWhenEnded(boolean value) {
        this.closeWhenEnded = value;
        return this;
    }

    /**
     * This request's options over a different set of links — how a template is applied
     * to a playlist whose contents the caller did not have when the template was built.
     *
     * @throws IllegalArgumentException if no {@code http(s)} URL is left
     * @since API 2.3.0
     */
    public MediaRequest withUrls(List<String> replacement) {
        MediaRequest fresh = ofAll(replacement);
        MediaRequest source = copy();
        fresh.kind = source.kind;
        fresh.newWindow = source.newWindow;
        fresh.placement = source.placement;
        fresh.sizing = source.sizing;
        fresh.startMicros = source.startMicros;
        fresh.autoplay = source.autoplay;
        fresh.shuffle = source.shuffle;
        fresh.repeat = source.repeat;
        fresh.title = source.title;
        fresh.chrome = source.chrome;
        fresh.persistGeometry = source.persistGeometry;
        fresh.closeWhenEnded = source.closeWhenEnded;
        return fresh;
    }

    /** An independent copy, for reusing a request as a template. */
    public MediaRequest copy() {
        MediaRequest copy = new MediaRequest(new ArrayList<>(urls));
        copy.kind = kind;
        copy.newWindow = newWindow;
        copy.placement = placement;
        copy.sizing = sizing;
        copy.startMicros = startMicros;
        copy.autoplay = autoplay;
        copy.shuffle = shuffle;
        copy.repeat = repeat;
        copy.title = title;
        copy.chrome = chrome;
        copy.persistGeometry = persistGeometry;
        copy.closeWhenEnded = closeWhenEnded;
        return copy;
    }

    // ------------------------------------------------------------------
    // Reading (the mod's side)
    // ------------------------------------------------------------------

    /** The links, in play order; never empty. */
    public List<String> urls() {
        return Collections.unmodifiableList(urls);
    }

    /** The first link — the one that plays now. */
    public String url() {
        return urls.get(0);
    }

    @Nullable
    public MediaKind kind() {
        return kind;
    }

    public boolean isNewWindow() {
        return newWindow;
    }

    public Placement placement() {
        return placement;
    }

    public Sizing sizing() {
        return sizing;
    }

    public long startMicros() {
        return startMicros;
    }

    public boolean isAutoplay() {
        return autoplay;
    }

    public boolean isShuffle() {
        return shuffle;
    }

    /** @since API 2.3.0 */
    public RepeatMode repeat() {
        return repeat;
    }

    @Nullable
    public Component title() {
        return title;
    }

    public WindowChromeOptions chrome() {
        return chrome;
    }

    public boolean isPersistGeometry() {
        return persistGeometry;
    }

    public boolean isCloseWhenEnded() {
        return closeWhenEnded;
    }

    /**
     * Whether {@code url} is an absolute {@code http} or {@code https} URL with a host.
     *
     * <p>The same rule as the mod's own {@code source.Urls.isHttp}, written out again
     * because {@code api} imports nothing from the mod — and applied here, in the API
     * layer, rather than left to the caller, so that every URL-taking entry point of the
     * API gates the same way.</p>
     */
    static boolean isHttpUrl(@Nullable String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null) {
                return false;
            }
            scheme = scheme.toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                return false;
            }
            if (uri.getHost() != null && !uri.getHost().isBlank()) {
                return true;
            }
            // getHost() is null for hosts URI considers non-compliant (an underscore, for
            // one). The raw authority still tells us a host was given, and that is what
            // matters: "https:///x.mp4" has none, and that is the shape being kept out.
            String authority = uri.getRawAuthority();
            return authority != null && !authority.isBlank();
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
