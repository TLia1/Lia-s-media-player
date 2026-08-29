/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.diag;

import com.lia.mediaplayer.api.MediaKind;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * One playback failure, already turned into something worth showing a player.
 *
 * <p>The raw text is ffmpeg's or yt-dlp's stderr and is nobody's idea of a message; the
 * mod classifies it and writes both a readable line and a suggestion, which is what its
 * own error panel shows. That is what is exposed here — a pack maintainer surfacing
 * "ffmpeg failed on this link" in their own UI should get the same words the mod would
 * have used, not a stack of stderr.</p>
 *
 * <p>{@link #raw} is still there, because it is what a bug report needs.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @param url               what failed to play
 * @param kind              which player was trying
 * @param cause             a stable id for the classified cause — {@code "extractor_outdated"},
 *                          {@code "geo_blocked"}, {@code "network"}, {@code "unknown"} and
 *                          the rest. Match on this, not on the message.
 * @param suggestsToolUpdate whether the fix is "get a newer yt-dlp/ffmpeg" rather than
 *                          "try again". This is the single most common failure the mod
 *                          sees and the one a player cannot guess at.
 * @param message           the readable line, translated
 * @param hint              what to do about it, translated, or {@code null}
 * @param raw               the underlying diagnostic, or {@code null}
 * @param atEpochMillis     when it happened
 * @since API 3.4.0
 */
public record MediaLogEntry(String url, MediaKind kind, String cause, boolean suggestsToolUpdate,
                            Component message, @Nullable Component hint,
                            @Nullable String raw, long atEpochMillis) {
}
