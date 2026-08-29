/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.source;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * What a {@link MediaMetadataProvider} found out about a link.
 *
 * <p>Every field is optional: a provider that only knows the title returns one with the
 * rest {@code null} / {@code -1}, and the mod falls back to what it can work out itself.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @param title          the readable name, or {@code null} to leave the mod's own guess
 * @param durationMicros the length, or {@code -1} when unknown
 * @param thumbnailUrl   an {@code http(s)} image URL, or {@code null}. Anything else is
 *                       ignored — the mod will not fetch a local path on an addon's say-so.
 * @param author         the uploader / channel / artist, or {@code null}
 * @since API 2.3.0
 */
public record MediaMetadata(@Nullable Component title, long durationMicros,
                            @Nullable String thumbnailUrl, @Nullable Component author) {

    /** The common case: a name and nothing else. */
    public static MediaMetadata ofTitle(Component title) {
        return new MediaMetadata(title, -1L, null, null);
    }
}
