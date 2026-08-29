/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.source;

import com.lia.mediaplayer.api.LiasMediaPlayerApi;

import java.util.concurrent.CompletableFuture;

/**
 * Supplies the readable name (and, in time, the duration and a thumbnail) for links a
 * custom {@link com.lia.mediaplayer.api.MediaSource} claims.
 *
 * <p>Without one, an addon's own source shows a raw URL everywhere the mod would
 * otherwise show a title — the queue panel, the window's title bar, the "now playing"
 * banner — because the mod's title cache only knows its own built-in strategies. This is
 * how an addon fills that in, and it is a small interface for a very visible payoff.</p>
 *
 * <p>Register with {@link LiasMediaPlayerApi#registerMetadataProvider}. Providers are
 * asked in registration order and the first one that {@linkplain #handles claims} a link
 * wins; the mod's own strategies run only when none does.</p>
 *
 * <p><b>Threading.</b> {@link #handles} is called from the render thread and must be
 * cheap and side-effect free. {@link #fetch} is called from the render thread but must
 * not block in it — do the work on your own executor and complete the future from there.
 * A future that completes with {@code null}, or completes exceptionally, simply falls
 * back to the mod's own answer.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 2.3.0
 */
public interface MediaMetadataProvider {

    /** Whether this provider knows anything about {@code url}. Must not block. */
    boolean handles(String url);

    /** Starts the lookup. Must not block the calling thread. */
    CompletableFuture<MediaMetadata> fetch(String url);
}
