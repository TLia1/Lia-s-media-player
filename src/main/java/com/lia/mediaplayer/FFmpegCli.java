package com.lia.mediaplayer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Thin wrapper around the {@code ffmpeg} / {@code ffprobe} command-line tools.
 *
 * <p>This replaces the previous JavaCV/bytedeco {@code FFmpegFrameGrabber}: instead
 * of linking the FFmpeg native libraries into the jar, we shell out to the standalone
 * binaries that {@link MediaBinaries} downloads into the game folder (the same model
 * already used for yt-dlp). Decoding works by piping raw output out of ffmpeg:</p>
 *
 * <ul>
 *   <li><b>Video</b> — {@code -f rawvideo -pix_fmt rgba} writes tightly-packed
 *       {@code W*H*4}-byte frames to stdout, already scaled to the target size.</li>
 *   <li><b>Audio</b> — {@code -f s16le} writes signed 16-bit little-endian PCM to
 *       stdout, ready to hand straight to a {@code SourceDataLine}.</li>
 * </ul>
 *
 * <p>Stream metadata (dimensions, frame rate, duration, audio layout) is read up
 * front with {@code ffprobe}, whose JSON output we parse with Gson (already on the
 * Minecraft classpath).</p>
 *
 * <p>Everything here runs on background threads (never the render thread).</p>
 */
final class FFmpegCli {

    private FFmpegCli() {
    }

    private static final long PROBE_TIMEOUT_SECONDS = 20;

    /** Stream properties needed to set up playback, gathered from ffprobe. */
    record MediaInfo(int width, int height, double fps, long durationMicros,
                     boolean hasAudio, int sampleRate, int channels) {

        boolean hasVideo() {
            return width > 0 && height > 0;
        }

        /** Microseconds between two consecutive frames at the reported rate. */
        long frameDurationMicros() {
            double f = fps > 0 ? fps : 30.0;
            return Math.max(1L, Math.round(1_000_000.0 / f));
        }
    }

    // ------------------------------------------------------------------
    // Probing
    // ------------------------------------------------------------------

    /** Reads stream metadata for {@code url}. Throws if ffprobe is unavailable or fails. */
    static MediaInfo probe(String url) throws IOException {
        String ffprobe = MediaBinaries.ffprobe();
        if (ffprobe == null) {
            throw new IOException(ffmpegMissingMessage());
        }

        List<String> command = new ArrayList<>(List.of(
                ffprobe,
                "-v", "error",
                "-print_format", "json",
                "-show_format",
                "-show_streams"));
        addInputNetworkOptions(command, url);
        command.add(url);

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process process = builder.start();

        String json;
        try (InputStream in = process.getInputStream()) {
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("ffprobe timed out for " + url);
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while probing " + url, e);
        }

        if (process.exitValue() != 0 || json.isBlank()) {
            throw new IOException("ffprobe could not read " + url + " (exit " + process.exitValue() + ")");
        }
        return parseProbe(json);
    }

    private static MediaInfo parseProbe(String json) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            int width = 0;
            int height = 0;
            double fps = 0;
            boolean hasAudio = false;
            int sampleRate = 0;
            int channels = 0;

            JsonArray streams = root.has("streams") ? root.getAsJsonArray("streams") : new JsonArray();
            for (JsonElement element : streams) {
                JsonObject stream = element.getAsJsonObject();
                String type = optString(stream, "codec_type", "");
                if ("video".equals(type) && width == 0) {
                    width = optInt(stream, "width", 0);
                    height = optInt(stream, "height", 0);
                    fps = parseRate(optString(stream, "r_frame_rate", null));
                    if (fps <= 0) {
                        fps = parseRate(optString(stream, "avg_frame_rate", null));
                    }
                } else if ("audio".equals(type) && !hasAudio) {
                    hasAudio = true;
                    sampleRate = optInt(stream, "sample_rate", 0);
                    channels = optInt(stream, "channels", 0);
                }
            }

            long durationMicros = 0;
            if (root.has("format")) {
                double seconds = parseDouble(optString(root.getAsJsonObject("format"), "duration", null));
                durationMicros = seconds > 0 ? Math.round(seconds * 1_000_000.0) : 0;
            }

            return new MediaInfo(width, height, fps, durationMicros, hasAudio, sampleRate, channels);
        } catch (RuntimeException e) {
            throw new IOException("Could not parse ffprobe output: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // Decode processes
    // ------------------------------------------------------------------

    /**
     * Starts an ffmpeg process that writes scaled {@code rgba} video frames (each
     * exactly {@code width*height*4} bytes) to its stdout, beginning at
     * {@code startSeconds}. The caller reads {@link Process#getInputStream()}.
     */
    static Process openVideo(String url, int width, int height, double startSeconds) throws IOException {
        String ffmpeg = requireFfmpeg();
        List<String> command = new ArrayList<>(List.of(
                ffmpeg, "-hide_banner", "-loglevel", "error", "-nostdin"));
        addSeek(command, startSeconds);
        // Pace output to the native frame rate. Without this, ffmpeg decodes the
        // whole file as fast as it can: the reader races to EOF in a few seconds,
        // the picture freezes and seeks stop working, while the (separately paced)
        // audio keeps playing. "-re" makes the video pipe advance in real time,
        // which is what keeps it roughly in step with the audio master clock.
        command.add("-re");
        addInputNetworkOptions(command, url);
        command.add("-i");
        command.add(url);
        command.add("-an"); // no audio on this process
        command.add("-vf");
        command.add("scale=" + width + ":" + height);
        command.add("-pix_fmt");
        command.add("rgba");
        command.add("-f");
        command.add("rawvideo");
        command.add("-");
        return start(command);
    }

    /**
     * Starts an ffmpeg process that writes signed 16-bit little-endian PCM audio
     * to its stdout at the given rate/channel count, beginning at
     * {@code startSeconds}. The caller reads {@link Process#getInputStream()} and
     * forwards it to a {@code SourceDataLine}.
     */
    static Process openAudio(String url, int sampleRate, int channels, double startSeconds) throws IOException {
        String ffmpeg = requireFfmpeg();
        List<String> command = new ArrayList<>(List.of(
                ffmpeg, "-hide_banner", "-loglevel", "error", "-nostdin"));
        addSeek(command, startSeconds);
        addInputNetworkOptions(command, url);
        command.add("-i");
        command.add(url);
        command.add("-vn"); // no video on this process
        command.add("-f");
        command.add("s16le");
        command.add("-acodec");
        command.add("pcm_s16le");
        command.add("-ar");
        command.add(Integer.toString(sampleRate));
        command.add("-ac");
        command.add(Integer.toString(channels));
        command.add("-");
        return start(command);
    }

    /**
     * Grabs a single scaled {@code rgba} frame at {@code atSeconds} and returns its
     * raw {@code width*height*4} bytes, or {@code null} if no frame was produced.
     */
    @Nullable
    static byte[] grabRawFrame(String url, int width, int height, double atSeconds) throws IOException {
        String ffmpeg = requireFfmpeg();
        List<String> command = new ArrayList<>(List.of(
                ffmpeg, "-hide_banner", "-loglevel", "error", "-nostdin"));
        addSeek(command, atSeconds);
        addInputNetworkOptions(command, url);
        command.add("-i");
        command.add(url);
        command.add("-frames:v");
        command.add("1");
        command.add("-an");
        command.add("-vf");
        command.add("scale=" + width + ":" + height);
        command.add("-pix_fmt");
        command.add("rgba");
        command.add("-f");
        command.add("rawvideo");
        command.add("-");

        Process process = start(command);
        int needed = width * height * 4;
        try (InputStream in = process.getInputStream()) {
            byte[] data = in.readNBytes(needed);
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
            }
            return data.length == needed ? data : null;
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while grabbing frame from " + url, e);
        }
    }

    // ------------------------------------------------------------------
    // Command building helpers
    // ------------------------------------------------------------------

    private static Process start(List<String> command) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        // We never read stderr from the streaming processes, so discard it to
        // avoid a full-pipe stall; real failures surface as an early stdout EOF
        // (and ffprobe has already validated the URL up front).
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        return builder.start();
    }

    private static String requireFfmpeg() throws IOException {
        String ffmpeg = MediaBinaries.ffmpeg();
        if (ffmpeg == null) {
            throw new IOException(ffmpegMissingMessage());
        }
        return ffmpeg;
    }

    private static String ffmpegMissingMessage() {
        return "ffmpeg is required to play videos. It could not be found, and the automatic "
                + "download into the game folder failed (no internet access?). Install it from "
                + "https://ffmpeg.org/download.html, then either add it to PATH or launch Minecraft "
                + "with -Dliasmediaplayer.ffmpeg=C:\\\\path\\\\to\\\\ffmpeg.exe "
                + "(and -Dliasmediaplayer.ffprobe=... for ffprobe).";
    }

    private static void addSeek(List<String> command, double startSeconds) {
        if (startSeconds > 0) {
            command.add("-ss");
            command.add(String.format(Locale.ROOT, "%.3f", startSeconds));
        }
    }

    /**
     * Adds HTTP resilience options, but only for {@code http(s)} inputs: ffmpeg
     * rejects these as unknown for local files or other protocols.
     */
    private static void addInputNetworkOptions(List<String> command, String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return;
        }
        command.add("-user_agent");
        command.add("Mozilla/5.0 liasmediaplayer video player");
        command.add("-reconnect");
        command.add("1");
        command.add("-reconnect_streamed");
        command.add("1");
        command.add("-reconnect_delay_max");
        command.add("5");
        command.add("-rw_timeout");
        command.add("15000000"); // 15s, microseconds
    }

    // ------------------------------------------------------------------
    // JSON helpers
    // ------------------------------------------------------------------

    private static double parseRate(@Nullable String rate) {
        if (rate == null || rate.isBlank()) {
            return 0;
        }
        int slash = rate.indexOf('/');
        try {
            if (slash < 0) {
                return Double.parseDouble(rate.trim());
            }
            double num = Double.parseDouble(rate.substring(0, slash).trim());
            double den = Double.parseDouble(rate.substring(slash + 1).trim());
            return den != 0 ? num / den : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double parseDouble(@Nullable String value) {
        if (value == null || value.isBlank() || "N/A".equalsIgnoreCase(value)) {
            return 0;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String optString(JsonObject object, String key, @Nullable String fallback) {
        JsonElement element = object.get(key);
        return element != null && !element.isJsonNull() ? element.getAsString() : fallback;
    }

    private static int optInt(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (NumberFormatException e) {
            // sample_rate sometimes arrives as a quoted string.
            try {
                return Integer.parseInt(element.getAsString().trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
    }
}
