/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.source;

import com.lia.mediaplayer.api.LiasMediaPlayerApi;
import com.lia.mediaplayer.api.MediaSource;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Turns a link into something {@code ffmpeg} can open.
 *
 * <p>{@link MediaSource} answers "what is this link?"; this answers "how do I play it?".
 * Until this existed the second question had exactly two answers — hand it to ffmpeg
 * directly, or shell out to {@code yt-dlp} — so an addon with its own service (a
 * token-signed CDN, a LAN media server, a stream the addon itself hosts) could register a
 * source and still not play a single thing unless yt-dlp happened to support it.</p>
 *
 * <p>Register with {@link LiasMediaPlayerApi#registerResolver}. Resolvers are asked in
 * registration order, <em>before</em> the mod's own resolution, and the first non-null
 * answer wins; returning {@code null} falls through to the next one and finally to the
 * mod itself.</p>
 *
 * <p><b>Threading.</b> Both methods are called from a background thread, never the render
 * thread, so {@link #resolve} may block — and should, since that is where the work goes.
 * It is called again for every playback attempt, so a URL that expires is not a
 * problem.</p>
 *
 * <h2>What you must return</h2>
 *
 * <p>An absolute {@code http(s)} URL. Anything else — a local path, a {@code file:} URL,
 * a {@code concat:} expression, a string beginning with {@code -} — is <b>rejected</b>
 * and treated as a failure to resolve. The mod hands what comes back to a downloaded
 * binary on a command line; that is the whole reason the restriction exists, and it is
 * not negotiable.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 2.3.0
 */
public interface MediaResolver {

    /** Whether this resolver wants to try {@code url}. Must be cheap and side-effect free. */
    boolean handles(String url);

    /**
     * The direct stream URL, or {@code null} to fall through to the next resolver.
     *
     * @throws IOException when the lookup itself failed; playback reports it and stops,
     *                     rather than falling through
     */
    @Nullable
    String resolve(String url) throws IOException;
}
