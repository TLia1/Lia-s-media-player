package com.lia.mediaplayer.source;

import net.minecraft.network.chat.Component;

/**
 * A single recognizable kind of media link (a direct image file, a Tenor share
 * page, a direct video file, an adaptive stream, a YouTube link, ...).
 *
 * <p>This is the mod's main extension point. Teaching the mod about a new media
 * source is a matter of writing one {@code MediaSource} and registering it in
 * {@link MediaSources}; nothing in the chat handlers, the windows or the playback
 * engine needs to change. Implementations must be stateless and side-effect free
 * so they can be queried freely from any thread.</p>
 */
public interface MediaSource {

    /** Whether this source recognizes (and can present) {@code url}. */
    boolean matches(String url);

    /** Which feature handles a matching link — see {@link MediaKind}. */
    MediaKind kind();

    /**
     * The compact, clickable label shown in chat in place of the raw {@code url}
     * (for example {@code [picture]}, {@code [gif]}, {@code [video]} or
     * {@code [youtube]}). The caller applies the colour/click style.
     */
    Component label(String url);
}
