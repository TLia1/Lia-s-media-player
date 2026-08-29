/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.sync;

/**
 * The sending half of watch-together: the mod tells you what the local player just did,
 * and you put it on your own network channel.
 *
 * <p><b>This mod ships no protocol and no server side.</b> It is client-only, and that
 * is not going to change — what it ships is the pair of hooks an addon that <em>does</em>
 * own a channel needs: this to broadcast, and {@link SyncControl} to apply what comes
 * back.</p>
 *
 * <p>Install with {@link MediaSync#setAdapter}. There is one adapter, not a list: two
 * mods both claiming to be the sync authority for the same session is a bug, and the
 * second {@code setAdapter} logs that it replaced the first rather than quietly
 * doubling every packet.</p>
 *
 * <h2>The loop, and how it is broken</h2>
 *
 * <p>Actions the mod applies on your behalf through {@link SyncControl#apply} do
 * <b>not</b> come back out of this method. Without that, a paused remote action would
 * pause locally, be broadcast, be applied by the sender, and the two clients would
 * ping-pong forever. You only ever see what this client actually did.</p>
 *
 * <p>Called on the client thread, from the same once-a-tick sweep the playback events
 * are derived in. An adapter that throws is logged and left installed.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 3.3.0
 */
@FunctionalInterface
public interface PlaybackSyncAdapter {

    /** Called for every local transport action, for the addon to broadcast. */
    void onLocalAction(SyncAction action);
}
