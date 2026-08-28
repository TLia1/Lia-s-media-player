/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api;

import com.lia.mediaplayer.api.event.MediaSourceRegistrationEvent;
import com.lia.mediaplayer.media.MediaUrlResolver;
import net.minecraft.network.chat.Component;

/**
 * A single recognizable kind of media link (a direct image file, a Tenor share
 * page, a direct video file, an adaptive stream, a YouTube link, ...).
 *
 * <p>This is the mod's main extension point. Teaching the mod about a new media
 * source is a matter of writing one {@code MediaSource} and registering it via
 * the {@link IMediaPlayerAPI} or the {@link MediaSourceRegistrationEvent};
 * nothing in the chat handlers, the windows or the playback engine needs to change.</p>
 *
 * <p>Implementations must be stateless and side-effect free so they can be
 * queried freely from any thread.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 */
public interface MediaSource {

    /**
     * Whether this source recognizes (and can present) {@code url}.
     */
    boolean matches(String url);

    /**
     * Which feature handles a matching link — see {@link MediaKind}.
     */
    MediaKind kind();

    /**
     * The compact, clickable label shown in chat in place of the raw {@code url}
     * (for example {@code [picture]}, {@code [gif]}, {@code [video]} or
     * {@code [youtube]}). The caller applies the colour/click style.
     *
     * <p>Return a {@link Component#translatable} with a key from your own language
     * files, not a literal: this is the most-read text the mod puts on screen, and it
     * is read by whoever is in the chat, in whatever language they play in. The
     * built-in sources use {@code chat.liasmediaplayer.label.*}.</p>
     */
    Component label(String url);

    /**
     * Whether a link this source claims is a <em>web page</em> that has to be run
     * through the external extractor ({@code yt-dlp}) before {@code ffmpeg} can open
     * it, rather than a media file {@code ffmpeg} can be pointed at directly.
     *
     * <p>{@code false} by default, which is right for every direct-file source. A
     * source for a site whose links are pages — YouTube, Twitch, SoundCloud,
     * Vimeo — returns {@code true}, and that is the whole of what
     * {@link MediaUrlResolver} needs to know about it. Before
     * this, the resolver named the two page sources it knew about, so a new one meant
     * editing the playback engine as well as adding a source; now it asks the registry.</p>
     *
     * <p>Ignored for {@link MediaKind#IMAGE} sources: the image pipeline does not use
     * the extractor at all.</p>
     */
    default boolean requiresExtractor() {
        return false;
    }
}
