package com.lia.mediaplayer.media;

import com.lia.mediaplayer.LiasMediaPlayer;
import com.lia.mediaplayer.source.TwitchSource;
import com.lia.mediaplayer.source.YouTubeSource;
import com.lia.mediaplayer.tools.MediaBinaries;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Turns a link seen in chat into a URL that {@link com.lia.mediaplayer.tools.FFmpegCli}
 * can open. Shared by both media engines — the video player and the audio player — so
 * the (non-trivial) YouTube resolution lives in exactly one place.
 *
 * <p>Direct media files and HLS/DASH manifests are already openable, so they are
 * returned untouched. YouTube links are web pages: ffmpeg cannot open them, and there
 * is no reliable pure-Java extractor, so we delegate to a
 * <a href="https://github.com/yt-dlp/yt-dlp">yt-dlp</a> binary. (The audio player opens
 * the resolved stream with {@code -vn}, so a YouTube link plays as sound only.)</p>
 *
 * <p>Locating (and, if missing, downloading) yt-dlp is handled by the shared
 * {@link MediaBinaries} helper, which also manages ffmpeg. This class only deals with
 * <em>using</em> yt-dlp once it has been found.</p>
 *
 * <p>All methods here run on a background thread (never the render thread).</p>
 */
public final class MediaUrlResolver {
    /**
     * yt-dlp can be slow on first call (it sometimes self-updates / probes formats).
     */
    private static long getYtDlpTimeoutSeconds() {
        return com.lia.mediaplayer.config.ConfigStore.YT_DLP_TIMEOUT_SECONDS.getValue();
    }

    /**
     * Prefer a single progressive stream that already muxes audio+video and is
     * not too large, so we get one URL with sound instead of separate tracks.
     */
    private static final String YT_DLP_FORMAT =
            "best[height<=720][acodec!=none][vcodec!=none]/best[height<=720]/best";

    /**
     * YouTube player clients yt-dlp is asked to extract from, most-preferred first.
     *
     * <p>yt-dlp's own default ({@code android_vr}) hands out {@code googlevideo} URLs that
     * only serve a couple of megabytes before answering {@code 403 Forbidden}: they are meant
     * to be fetched in small ranged chunks, which yt-dlp does but ffmpeg does not (ffmpeg opens
     * a stream with an open-ended {@code Range: bytes=0-}, and that request is refused outright).
     * The {@code android} client still returns plain, fully streamable URLs, so we ask for it
     * first and keep yt-dlp's own default as the fallback for videos it cannot extract.</p>
     *
     * <p>YouTube changes this often, so the list can be overridden at launch with
     * {@code -Dliasmediaplayer.ytdlp.clients=...} (a comma-separated yt-dlp client list, or an
     * empty value to let yt-dlp decide on its own).</p>
     */
    private static final String YT_DLP_PLAYER_CLIENTS =
            System.getProperty("liasmediaplayer.ytdlp.clients", "android,default");

    private MediaUrlResolver() {
    }

    /**
     * Resolves a chat link to a directly-playable media URL.
     */
    public static String resolve(String url) throws IOException {
        // Everything downstream of here is a command line or a socket, so refuse
        // anything that is not a plain http(s) link before it gets that far.
        if (!com.lia.mediaplayer.source.Urls.isHttp(url)) {
            throw new IOException("Refusing to play a non-http(s) link: " + url);
        }
        if (requiresExtractor(url)) {
            return resolveYtDlp(url);
        }
        // Direct files and HLS/DASH manifests are opened by ffmpeg as-is.
        return url;
    }

    /**
     * Whether {@code url} is a page that has to go through yt-dlp, asked of the source
     * registry rather than decided here.
     *
     * <p>This method used to name YouTube and Twitch itself, which meant every new
     * page-based source had to be taught to the playback engine as well as registered.
     * {@link com.lia.mediaplayer.api.MediaSource#requiresExtractor()} moved that answer
     * to the source, so adding one now really does touch nothing else.</p>
     *
     * <p>The two originals stay as the fallback for the window before the context
     * exists: playback cannot start that early, but the resolver is also reachable from
     * the caches, and answering "no extractor" there would send a YouTube page straight
     * to ffmpeg.</p>
     */
    private static boolean requiresExtractor(String url) {
        com.lia.mediaplayer.MediaPlayerContext context =
                (com.lia.mediaplayer.MediaPlayerContext) com.lia.mediaplayer.api.LiasMediaPlayerApi.getInstanceOrNull();
        if (context == null) {
            return YouTubeSource.isYouTube(url) || TwitchSource.isTwitch(url);
        }
        return context.getMediaSources().requiresExtractor(url);
    }

    private static String resolveYtDlp(String url) throws IOException {
        String executable = MediaBinaries.ytDlp();
        if (executable == null) {
            throw new IOException("yt-dlp is required to play YouTube links. It could not be found, "
                    + "and the automatic download into the game folder failed (no internet access?). "
                    + "Install it manually from https://github.com/yt-dlp/yt-dlp, then either add it to PATH "
                    + "or launch Minecraft with -Dliasmediaplayer.ytdlp=C:\\\\path\\\\to\\\\yt-dlp.exe");
        }

        List<String> command = new java.util.ArrayList<>(List.of(
                executable,
                "--no-playlist",
                "--quiet",
                "--no-warnings"));
        if (YouTubeSource.isYouTube(url) && !YT_DLP_PLAYER_CLIENTS.isBlank()) {
            command.add("--extractor-args");
            command.add("youtube:player_client=" + YT_DLP_PLAYER_CLIENTS);
        }
        command.add("-f");
        command.add(YT_DLP_FORMAT);
        command.add("-g");
        // End of options: keeps a URL that starts with '-' from being read as a flag.
        command.add("--");
        command.add(url);

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(false);

        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new IOException("Failed to run yt-dlp at '" + executable + "': " + e.getMessage(), e);
        }

        String firstLine;
        StringBuilder stderr = new StringBuilder();
        try {
            // Drain stderr on a side thread so a chatty yt-dlp can't deadlock us.
            Thread errReader = new Thread(() -> drain(process, stderr), "liasmediaplayer-ytdlp-err");
            errReader.setDaemon(true);
            errReader.start();

            java.util.concurrent.Future<String> lineFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    return reader.readLine();
                } catch (IOException e) {
                    return null;
                }
            });

            long timeoutSeconds = getYtDlpTimeoutSeconds();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("yt-dlp timed out resolving " + url);
            }
            try {
                firstLine = lineFuture.get(1, TimeUnit.SECONDS);
            } catch (Exception e) {
                firstLine = null;
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while resolving " + url, e);
        }

        if (firstLine == null || firstLine.isBlank()) {
            // yt-dlp's own words, verbatim: they are what PlaybackError reads to tell a
            // private video from an expired extractor, and what a bug report needs. This
            // used to be replaced by one translated sentence ("no video found"), which
            // said the same thing for every one of those and left nothing to diagnose.
            String detail = stderr.toString().trim();
            throw new IOException(detail.isEmpty()
                    ? "yt-dlp found no playable media for " + url
                    : "yt-dlp: " + detail);
        }
        LiasMediaPlayer.LOGGER.info("Resolved link {} -> direct stream via {}", url, executable);
        return firstLine.trim();
    }

    private static void drain(Process process, StringBuilder sink) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (sink.length() < 2000) {
                    sink.append(line).append('\n');
                }
            }
        } catch (IOException ignored) {
            // best-effort
        }
    }
}
