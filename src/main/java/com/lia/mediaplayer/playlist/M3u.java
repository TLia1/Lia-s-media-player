package com.lia.mediaplayer.playlist;

import com.lia.mediaplayer.source.Urls;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Playlists as m3u text, in both directions.
 *
 * <p>m3u is what every other player on the machine reads and writes, which is the whole
 * reason it is here: a user with a playlist in VLC, foobar or a text file should be able
 * to bring it in, and should be able to take theirs out.</p>
 *
 * <p>Pure — no file, no store, no Minecraft beyond {@link Component} for a title — so the
 * parsing rules below are unit-tested rather than argued about. {@code PlaylistStore}
 * stays the only thing that touches {@code playlists.json}.</p>
 *
 * <h2>What is written</h2>
 *
 * <p>Extended m3u: the {@code #EXTM3U} header, then a {@code #EXTINF:-1,<title>} line and
 * the URL for each entry. The duration is always {@code -1}: the mod does not know how
 * long a queued link is and would have to launch {@code ffprobe} a hundred times to find
 * out, which is the same reason {@code QueueEntry.durationMicros()} answers {@code -1}.</p>
 *
 * <h2>What is read</h2>
 *
 * <p>Every line that is not blank and does not start with {@code #} is taken as a URL and
 * passed through {@link Urls#isHttp}, which is the same gate {@link Playlist#add} applies
 * — an m3u file is a list of paths, and a list of paths is exactly the shape that would
 * otherwise hand {@code ffmpeg} a {@code file:} URL. {@code #EXTINF} titles are read to
 * be skipped: the mod resolves its own titles, and one stored in a file goes stale the
 * first time a video is renamed.</p>
 */
public final class M3u {

    /** The header every extended-m3u file starts with. */
    private static final String HEADER = "#EXTM3U";

    private M3u() {
    }

    /**
     * {@code playlist} as extended-m3u text, ending in a newline.
     *
     * @param titleOf what to write on each entry's {@code #EXTINF} line — the mod passes
     *                its resolved title, and a caller with nothing better passes the URL
     */
    public static String export(Playlist playlist, Function<String, Component> titleOf) {
        StringBuilder out = new StringBuilder(HEADER).append('\n');
        for (String url : playlist.urls()) {
            Component title = titleOf == null ? null : titleOf.apply(url);
            String text = title == null ? url : title.getString();
            // A newline inside a title would end the #EXTINF line early and turn the rest
            // of it into a URL. Titles come from page metadata, so this is not paranoia.
            out.append("#EXTINF:-1,").append(oneLine(text)).append('\n');
            out.append(url).append('\n');
        }
        return out.toString();
    }

    /**
     * The {@code http(s)} URLs in {@code content}, in order, with duplicates kept —
     * a playlist may legitimately hold the same track twice.
     *
     * <p>An empty list for {@code null}, for text that is not m3u at all, and for a file
     * whose every entry is a local path. Never throws: this is fed by a clipboard.</p>
     */
    public static List<String> parse(@Nullable String content) {
        List<String> urls = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return urls;
        }
        // Split on either line ending, and on a lone \r, because a file that has been
        // through a Windows editor and a Unix one is a normal thing to be handed.
        for (String rawLine : content.split("\\R")) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (Urls.isHttp(line)) {
                urls.add(line);
            }
        }
        return urls;
    }

    /** Whether {@code content} looks like an m3u file rather than a bare link. */
    public static boolean looksLikeM3u(@Nullable String content) {
        return content != null && content.stripLeading().startsWith(HEADER);
    }

    private static String oneLine(String text) {
        return text.replace('\r', ' ').replace('\n', ' ').strip();
    }
}
