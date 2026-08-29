/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api;

import net.minecraft.network.chat.Component;

/**
 * One line of a {@link MediaQueue} — what the queue panel draws, as a value.
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @param title          the resolved name, or the URL while one is still being fetched.
 *                       Never blocks; ask again later, or listen for
 *                       {@code PlaybackEvent.Type.METADATA_RESOLVED}.
 * @param durationMicros the length if it happens to be known, else {@code -1}. It is
 *                       {@code -1} for almost every <em>queued</em> entry, and that is
 *                       not an oversight: nothing has opened the stream yet, and probing
 *                       a hundred queued links to fill in a field would mean a hundred
 *                       {@code ffprobe} launches nobody asked for. Use
 *                       {@code MediaTools.probe} on the one entry you care about.
 * @param hasThumbnail   whether a still has already been decoded for this entry. A peek,
 *                       not a request: reading a queue never starts a download.
 * @since API 2.3.0
 */
public record QueueEntry(String url, Component title, MediaKind kind,
                         long durationMicros, boolean hasThumbnail) {
}
