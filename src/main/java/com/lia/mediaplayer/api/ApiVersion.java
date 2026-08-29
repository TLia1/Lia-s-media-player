/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api;

/**
 * The version of the API surface, and the one honest answer to "can I call that here?".
 *
 * <p>The mod's version is <em>not</em> this number. On Fabric the API is a
 * {@code provides} id, which carries the mod's version and so tells an addon nothing
 * about the API; on NeoForge it is a second {@code [[mods]]} entry that moves whenever
 * the mod does. Neither is something an addon can usefully pin against, which left
 * {@code try { Class.forName(...) } catch} as the only way to ask whether a feature
 * exists. This is the replacement.</p>
 *
 * <p>Written so that an addon compiled against an <em>older</em> API still runs against
 * a newer mod: every constant here only ever grows, {@link Capability} is only ever
 * added to, and nothing is removed inside a major version (see the deprecation policy in
 * {@code API-DOCUMENTATION.md}).</p>
 *
 * <pre>{@code
 * if (ApiVersion.supports(Capability.PLACEMENT)) {
 *     api.play(MediaRequest.of(url).placement(Placement.anchored(Anchor.TOP_RIGHT, 4, 4)));
 * } else {
 *     api.playVideo(url);   // the 2.0 path, still there
 * }
 * }</pre>
 *
 * <p>This is part of the <b>public API</b>. Thread-safe: everything here is a constant.</p>
 *
 * @since API 2.1.0
 */
public final class ApiVersion {

    /**
     * The milestone. Bumped when the API grows a whole new area — 3.0 is the one where
     * media stopped being something only the mod's own window could draw.
     *
     * <p>A bump here is <em>allowed</em> to remove something, and is the only place that
     * ever may; it does not mean something was. 3.0 removed nothing, and every 2.x call
     * still compiles and still behaves the same way. What is removed spends a full minor
     * release {@code @Deprecated(forRemoval = true)} first, with a javadoc pointer to its
     * replacement, and a {@code long}-id method never goes away merely because a handle
     * exists.</p>
     */
    public static final int MAJOR = 3;

    /** Additive changes — a new capability, a new method, a new event type. */
    public static final int MINOR = 4;

    /** Fixes that change no signature. */
    public static final int PATCH = 0;

    private ApiVersion() {
    }

    /** {@code "3.4.0"} — for logs and for a version shown to a user. */
    public static String asString() {
        return MAJOR + "." + MINOR + "." + PATCH;
    }

    /**
     * Whether the running API is at least {@code major.minor}.
     *
     * <p>The blunt instrument; {@link #supports(Capability)} says what an addon
     * actually means and survives a capability being back-ported.</p>
     */
    public static boolean atLeast(int major, int minor) {
        return MAJOR > major || (MAJOR == major && MINOR >= minor);
    }

    /**
     * Whether {@code capability} is present in the running mod.
     *
     * <p>A {@code null} capability answers {@code false} rather than throwing: an addon
     * built against a newer API may hand over a constant this version has never heard
     * of, and "no" is the right answer to that question.</p>
     */
    public static boolean supports(Capability capability) {
        return capability != null && atLeast(capability.sinceMajor(), capability.sinceMinor());
    }
}
