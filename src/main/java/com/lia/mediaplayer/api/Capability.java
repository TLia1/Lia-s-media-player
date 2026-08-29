/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api;

/**
 * One feature of the API an addon can ask for by name — see
 * {@link ApiVersion#supports(Capability)}.
 *
 * <p><b>Every planned capability is declared here from the day the enum exists</b>,
 * carrying the API version it becomes available in, and one that has not landed yet
 * simply answers {@code false}. That is deliberate and is the whole point of the type:
 * a constant an addon references has to <em>resolve</em> on the version it runs against,
 * or the JVM throws {@link NoSuchFieldError} at the very moment the addon was trying to
 * be careful. Constants are therefore never removed and never renamed.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 2.1.0
 */
public enum Capability {

    /**
     * {@link MediaHandle} — a live object for one playing thing, rather than a
     * write-only {@code long} id.
     */
    HANDLES(2, 1),

    /**
     * Reading and writing the local history and its favourites through
     * {@link IMediaPlayerAPI#getHistory(int)} and friends.
     */
    HISTORY_ACCESS(2, 1),

    /**
     * The full playlist surface: rename, remove, reorder, fetch one by name, play one.
     */
    PLAYLIST_EDITING(2, 1),

    /**
     * Probing a URL and asking after the downloaded binaries — see
     * {@code com.lia.mediaplayer.api.tools.MediaTools}.
     */
    TOOLS(2, 1),

    /** Size and position as parameters — {@code MediaRequest}, {@code Placement}, {@code Sizing}. */
    PLACEMENT(2, 2),

    /** Reading and editing a player's queue through {@code MediaQueue}. */
    QUEUE_ACCESS(2, 3),

    /** Supplying titles, durations and thumbnails for custom sources. */
    METADATA_PROVIDERS(2, 3),

    /** Turning an addon's own link into something ffmpeg can open. */
    RESOLVERS(2, 3),

    /** Media decoded into a texture the caller draws itself. */
    SURFACES(3, 0),

    /** Playing sound with no window. */
    HEADLESS_AUDIO(3, 1),

    /** Distance attenuation and panning for a handle placed in the world. */
    POSITIONAL_AUDIO(3, 1),

    /** Per-channel and per-handle gain multiplied into the single master volume. */
    MIXER(3, 1),

    /** Vetoing or rewriting a play request, and vetoing a chat link. */
    INTERCEPTORS(3, 2),

    /** Addon-registered colour palettes. */
    THEMES(3, 2),

    /** An addon's own button in a media window's corner row. */
    WINDOW_ACTIONS(3, 2),

    /**
     * Registering a key binding the mod collects, and a fixed shortcut over the screens
     * that host the window stack.
     */
    KEYBINDS(3, 2),

    /** Watch-together hooks for an addon that owns its own network channel. */
    SYNC(3, 3),

    /** A decoder for a picture format the mod does not know. */
    IMAGE_DECODERS(3, 4),

    /** Reading a surface's decoded pixels back out. */
    SURFACE_PIXELS(3, 4),

    /** A way into an addon's own screen from the mod's library screens. */
    SCREEN_TABS(3, 4),

    /** Reading and writing playlists as m3u. */
    PLAYLIST_IMPORT_EXPORT(3, 4),

    /** Counters, and a sink for playback failures. */
    DIAGNOSTICS(3, 4);

    private final int sinceMajor;
    private final int sinceMinor;

    Capability(int sinceMajor, int sinceMinor) {
        this.sinceMajor = sinceMajor;
        this.sinceMinor = sinceMinor;
    }

    /** The API major version this capability first shipped in. */
    public int sinceMajor() {
        return sinceMajor;
    }

    /** The API minor version this capability first shipped in. */
    public int sinceMinor() {
        return sinceMinor;
    }

    /** {@code "3.0"} — the version to name in a "requires" message. */
    public String since() {
        return sinceMajor + "." + sinceMinor;
    }
}
