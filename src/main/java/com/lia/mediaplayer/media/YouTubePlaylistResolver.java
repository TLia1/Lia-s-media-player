package com.lia.mediaplayer.media;

import com.lia.mediaplayer.LiasMediaPlayer;
import com.lia.mediaplayer.config.ConfigStore;
import com.lia.mediaplayer.playlist.Playlist;
import com.lia.mediaplayer.source.Urls;
import com.lia.mediaplayer.source.YouTubePlaylistSource;
import com.lia.mediaplayer.tools.MediaBinaries;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Expands a YouTube playlist page into the watch links it contains, so a playlist can
 * be queued in a player or imported into a saved {@link Playlist}.
 *
 * <p>Like {@link MediaUrlResolver} this shells out to {@code yt-dlp} — there is no way
 * to enumerate a playlist from the watch page alone — but it asks for a
 * {@code --flat-playlist}, which only reads the playlist index and never touches the
 * individual videos. That is one quick request for the whole list; each entry is then
 * resolved to a stream lazily, when it actually plays.</p>
 *
 * <p>It lives in the shared {@code media} layer because both engines (and the playlist
 * editor) expand playlists the same way. {@link #resolve} blocks and must be called off
 * the render thread; {@link #loadAsync} does that for callers and hands the result back
 * on the main thread.</p>
 */
public final class YouTubePlaylistResolver {

    /**
     * Upper bound on the entries taken from one playlist. YouTube playlists can hold
     * thousands of videos, and a queue that long is neither usable nor cheap to keep
     * titles and thumbnails for.
     */
    private static final int MAX_ENTRIES = 500;

    /**
     * One line per entry: the playlist's own name, then the watch URL. The
     * {@code |} default keeps the line shape when a playlist has no title.
     */
    private static final String PRINT_TEMPLATE = "%(playlist_title|)s\t%(url)s";

    private YouTubePlaylistResolver() {
    }

    /**
     * A resolved playlist: its name (possibly blank) and its entries, in playlist order.
     */
    public record Result(String title, List<String> urls) {
    }

    /**
     * Resolves {@code url} on the IO pool and calls {@code onDone} on the main thread
     * with the result — or with {@code null} if the playlist could not be read, having
     * already told the player why.
     */
    public static void loadAsync(String url, Consumer<Result> onDone) {
        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return resolve(url);
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                }, Util.ioPool())
                .whenCompleteAsync((result, error) -> {
                    if (error != null || result == null || result.urls().isEmpty()) {
                        Throwable cause = error instanceof CompletionException && error.getCause() != null
                                ? error.getCause() : error;
                        LiasMediaPlayer.LOGGER.warn("Could not expand YouTube playlist {}: {}",
                                url, cause == null ? "empty playlist" : cause.toString());
                        tellPlayer(Component.translatable("chat.liasmediaplayer.playlist.failed"));
                        onDone.accept(null);
                        return;
                    }
                    onDone.accept(result);
                }, Minecraft.getInstance());
    }

    /**
     * Reads the entries of a YouTube playlist page. Blocking: never call this from the
     * render thread.
     */
    public static Result resolve(String url) throws IOException {
        if (!YouTubePlaylistSource.isPlaylist(url)) {
            throw new IOException("Not a YouTube playlist link: " + url);
        }
        String executable = MediaBinaries.ytDlp();
        if (executable == null) {
            throw new IOException("yt-dlp is required to read a YouTube playlist and could not be found.");
        }

        List<String> command = List.of(
                executable,
                "--flat-playlist",
                "--quiet",
                "--no-warnings",
                "-I", "1:" + MAX_ENTRIES,
                "--print", PRINT_TEMPLATE,
                // End of options: keeps a URL that starts with '-' from being read as a flag.
                "--", url);

        Process process;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(false).start();
        } catch (IOException e) {
            throw new IOException("Failed to run yt-dlp at '" + executable + "': " + e.getMessage(), e);
        }

        StringBuilder stderr = new StringBuilder();
        List<String> lines;
        try {
            // Drain stderr on a side thread so a chatty yt-dlp can't deadlock us.
            Thread errReader = new Thread(() -> drain(process, stderr), "liasmediaplayer-ytdlp-playlist-err");
            errReader.setDaemon(true);
            errReader.start();

            CompletableFuture<List<String>> linesFuture = CompletableFuture.supplyAsync(() -> readLines(process));

            if (!process.waitFor(timeoutSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("yt-dlp timed out reading the playlist " + url);
            }
            try {
                lines = linesFuture.get(2, TimeUnit.SECONDS);
            } catch (Exception e) {
                lines = List.of();
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while reading the playlist " + url, e);
        }

        String title = "";
        List<String> entries = new ArrayList<>();
        for (String line : lines) {
            int tab = line.indexOf('\t');
            String entryUrl = tab >= 0 ? line.substring(tab + 1).strip() : line.strip();
            if (tab > 0 && title.isEmpty()) {
                title = line.substring(0, tab).strip();
            }
            // The entries end up on an ffmpeg command line like any other link, so they
            // go through the same http(s)-only gate a chat link does.
            if (Urls.isHttp(entryUrl)) {
                entries.add(entryUrl);
            }
        }
        if (entries.isEmpty()) {
            String detail = !stderr.isEmpty() ? " — " + stderr.toString().strip() : "";
            throw new IOException("yt-dlp returned no playlist entries for " + url + detail);
        }
        LiasMediaPlayer.LOGGER.info("Expanded YouTube playlist {} into {} entries", url, entries.size());
        return new Result(title, entries);
    }

    /**
     * A playlist index is one request, but a long one takes longer than a single video,
     * so the configured yt-dlp timeout is doubled here.
     */
    private static long timeoutSeconds() {
        return ConfigStore.YT_DLP_TIMEOUT_SECONDS.getValue() * 2L;
    }

    private static List<String> readLines(Process process) {
        List<String> out = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null && out.size() < MAX_ENTRIES) {
                if (!line.isBlank()) {
                    out.add(line);
                }
            }
        } catch (IOException ignored) {
            // Whatever was read before the failure is still usable.
        }
        return out;
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

    /**
     * Shows a one-line notice in the player's own chat (main thread only) — the only
     * place the mod writes to chat itself, which is why the version guard sits here
     * rather than in a seam class.
     *
     * <p>26.1 dropped {@code LocalPlayer.displayClientMessage}; {@code sendSystemMessage}
     * is its replacement and only exists from there on. Both land in the chat box.</p>
     */
    public static void tellPlayer(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        //? if <26.1 {
        minecraft.player.displayClientMessage(message, false);
        //?} else
        /*minecraft.player.sendSystemMessage(message);*/
    }
}
