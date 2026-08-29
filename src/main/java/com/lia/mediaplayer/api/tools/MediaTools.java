/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.tools;

import com.lia.mediaplayer.api.IMediaPlayerAPI;
import com.lia.mediaplayer.api.LiasMediaPlayerApi;

import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * The downloaded command-line tools, as far as an addon is allowed to see them.
 *
 * <h2>The security line, which is not negotiable</h2>
 *
 * <p>This facade exposes <b>named operations over a URL</b> and nothing else. It never
 * exposes a process builder, never accepts caller-supplied {@code ffmpeg} arguments, and
 * never accepts a local filesystem path. The mod downloads and executes three binaries;
 * an API that let an addon pass arbitrary arguments to one of them would be a
 * remote-code-execution vector wearing a media API's clothes. Every method here that
 * takes a URL rejects anything that is not {@code http(s)}.</p>
 *
 * <p>The trust model for the binaries themselves — TLS to three named publishers, no
 * checksum and why, the magic-byte check before anything is marked executable — is
 * documented on the mod's {@code tools.BinaryDownloader} and is meant to be read
 * alongside this.</p>
 *
 * <h2>Threading</h2>
 *
 * <p>Every method here is safe from any thread. {@link #probe(String)} does its work on
 * the mod's IO pool and completes off the render thread, so hop back yourself before
 * touching anything in the game.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 2.1.0
 */
public final class MediaTools {

    private MediaTools() {
    }

    /**
     * Whether {@code ffmpeg}, {@code ffprobe} and {@code yt-dlp} are all present.
     *
     * <p>{@code false} early in a session while the first-launch download runs, and for
     * the rest of it if the download failed. An addon that plays media does not need to
     * check — the mod reports the failure itself — but one that decides whether to show
     * a button does.</p>
     */
    public static boolean isReady() {
        IMediaPlayerAPI api = LiasMediaPlayerApi.getInstanceOrNull();
        return api != null && api.toolsReady();
    }

    /**
     * Completes once the startup install has finished, whether or not it succeeded —
     * check {@link #isReady()} in the continuation. Never completes exceptionally.
     *
     * <p>Completes on the installer thread. A future returned before the mod has
     * initialized is already complete, so an addon that asks too early is not left
     * waiting forever.</p>
     */
    public static CompletableFuture<Void> whenReady() {
        IMediaPlayerAPI api = LiasMediaPlayerApi.getInstanceOrNull();
        return api == null ? CompletableFuture.completedFuture(null) : api.whenToolsReady();
    }

    /**
     * Reads {@code url}'s stream properties with {@code ffprobe}.
     *
     * <p>Completes with {@code null} when the URL is not {@code http(s)}, when the tools
     * are missing, or when ffprobe could not read it — a probe failing is an ordinary
     * outcome for a link someone pasted, not an exceptional one, so it is not thrown.</p>
     */
    public static CompletableFuture<MediaInfo> probe(String url) {
        IMediaPlayerAPI api = LiasMediaPlayerApi.getInstanceOrNull();
        return api == null ? CompletableFuture.completedFuture(null) : api.probe(url);
    }

    /**
     * The yt-dlp build in use, as the release date it prints ({@code 2025.08.11}), or
     * {@code null} if it could not be asked. Blocking: it launches the process.
     */
    @Nullable
    public static String ytDlpVersion() {
        IMediaPlayerAPI api = LiasMediaPlayerApi.getInstanceOrNull();
        return api == null ? null : api.ytDlpVersion();
    }
}
