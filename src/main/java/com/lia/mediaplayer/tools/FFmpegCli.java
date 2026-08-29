package com.lia.mediaplayer.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lia.mediaplayer.config.ConfigStore;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
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
public final class FFmpegCli {

    private FFmpegCli() {
    }

    /**
     * Scales width x height to fit within the max box, keeping even dimensions.
     */
    public static int[] fitWithin(int width, int height, int maxWidth, int maxHeight) {
        double scale = Math.min(1.0, Math.min(maxWidth / (double) width, maxHeight / (double) height));
        int w = Math.max(2, (int) Math.round(width * scale));
        int h = Math.max(2, (int) Math.round(height * scale));
        if ((w & 1) == 1) {
            w++;
        }
        if ((h & 1) == 1) {
            h++;
        }
        return new int[]{w, h};
    }

    private static final long PROBE_TIMEOUT_SECONDS = 20;

    /**
     * Stream properties needed to set up playback, gathered from ffprobe.
     */
    public record MediaInfo(int width, int height, double fps, long durationMicros,
                            boolean hasAudio, int sampleRate, int channels) {

        public boolean hasVideo() {
            return width > 0 && height > 0;
        }

        /**
         * Microseconds between two consecutive frames at the reported rate.
         */
        public long frameDurationMicros() {
            double f = fps > 0 ? fps : 30.0;
            return Math.max(1L, Math.round(1_000_000.0 / f));
        }
    }

    // ------------------------------------------------------------------
    // Probing
    // ------------------------------------------------------------------

    /**
     * Reads stream metadata for {@code url}. Throws if ffprobe is unavailable or fails.
     */
    public static MediaInfo probe(String url) throws IOException {
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
        // "-i url" rather than a bare positional argument: a URL that happens to start
        // with '-' would otherwise be parsed by ffprobe as an option of its own.
        command.add("-i");
        command.add(url);

        ProcessBuilder builder = new ProcessBuilder(command);
        Process process = builder.start();

        // Keep ffprobe's diagnostics: they carry the actual reason (an HTTP 403 on an
        // expired/refused stream URL, a missing codec, ...) that "exit 1" alone hides.
        StringBuilder stderr = new StringBuilder();
        Thread errReader = new Thread(() -> drainStderr(process, stderr), "liasmediaplayer-ffprobe-err");
        errReader.setDaemon(true);
        errReader.start();

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
            String detail = stderr.isEmpty() ? "" : " — " + stderr.toString().trim();
            throw new IOException("ffprobe could not read " + url + " (exit " + process.exitValue() + ")" + detail);
        }
        return parseProbe(json);
    }

    /**
     * Reads a process' stderr to the end, capped so a very chatty run cannot grow unbounded.
     */
    private static void drainStderr(Process process, StringBuilder sink) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                synchronized (sink) {
                    if (sink.length() < MAX_STDERR_CHARS) {
                        sink.append(line).append('\n');
                    }
                }
            }
        } catch (IOException ignored) {
            // best-effort
        }
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
    public static Process openVideo(String url, int width, int height, double startSeconds) throws IOException {
        String ffmpeg = requireFfmpeg();
        List<String> command = new ArrayList<>(List.of(
                ffmpeg, "-hide_banner", "-loglevel", "error", "-nostdin"));
        addSeek(command, startSeconds);
        addHardwareDecoding(command);
        // We intentionally do NOT pass "-re" here. "-re" caps ffmpeg's output at the
        // native frame rate, which leaves no slack to pre-buffer and — worse — stops
        // the video catching back up after a network stall, so the picture drifts
        // permanently behind the audio clock. Instead the decode thread applies
        // back-pressure (see VideoPlayer#enqueue): ffmpeg reads ahead and fills the
        // frame queue as fast as the connection allows (a jitter cushion), then blocks
        // once the queue is full. Playback is paced on the consumer side from the audio
        // master clock, so the picture still stays in step with the sound.
        addInputNetworkOptions(command, url);
        command.add("-i");
        command.add(url);
        command.add("-an"); // no audio on this process
        command.add("-vf");
        // Bilinear rather than swscale's bicubic default. Every frame is scaled, at
        // frame rate, on the CPU — and then scaled again by the GPU when the window
        // blits it into a box that is smaller still. Paying for bicubic's extra taps at
        // the first of those two steps buys nothing anyone can see at the second.
        command.add("scale=" + width + ":" + height + ":flags=bilinear");
        command.add("-pix_fmt");
        command.add("rgba");
        command.add("-f");
        command.add("rawvideo");
        command.add("-");
        return start(command);
    }

    /**
     * Starts <em>one</em> ffmpeg that decodes the stream once and writes both of its
     * outputs at the same time: raw {@code rgba} video frames to stdout, exactly as
     * {@link #openVideo} does, and {@code s16le} PCM to a TCP connection it opens back
     * to {@code audioHost}:{@code audioPort}.
     *
     * <h4>Why a socket, and why one process</h4>
     *
     * <p>Playing a video used to mean two ffmpeg processes on the same URL — one with
     * {@code -an}, one with {@code -vn}. Both of them downloaded the stream, and both
     * demuxed it; only the decode of the track each one kept was not duplicated. For a
     * network stream that is twice the bandwidth and two connections to the same host,
     * and every seek paid for two relaunches instead of one.
     *
     * <p>One process cannot simply write both to stdout — they would interleave into
     * nonsense — and Java cannot hand a child an extra file descriptor to write the
     * second one to. ffmpeg's {@code tcp://} output is the way out that works on every
     * platform the mod ships to: the mod listens on the loopback interface, ffmpeg
     * connects back, and the PCM arrives on a stream that behaves exactly like the pipe
     * it replaces. A named pipe would do the same on Linux and macOS and not on Windows.
     *
     * <h4>What changes for the caller</h4>
     *
     * <p><b>The two outputs now pace each other.</b> One process has one muxing loop, so
     * a consumer that stops reading either output stops <em>both</em> — measured, not
     * assumed: leaving the audio socket undrained freezes the video within a second, as
     * soon as the socket buffer fills. That is not a new hazard so much as an old one
     * made symmetric. {@code VideoPlayer.resume} already relaunches unconditionally
     * because "while paused nobody reads ffmpeg's pipes"; the same reasoning now covers
     * the sound. What it does add is that {@code VideoPlayer.discardDueFrames} — which
     * keeps an undrawn window's frame queue moving — is load-bearing for the
     * <em>audio</em> of that window too, not only for its progress.
     *
     * <h4>The listening socket</h4>
     *
     * <p>Bound to the loopback address with a backlog of one, and closed the moment the
     * connection is accepted. Another process on the same machine could in principle win
     * the race and feed PCM to a sound line; the window is the few milliseconds between
     * bind and connect, it requires local code execution to exploit, and the payload it
     * could deliver is audible noise. Binding to anything but loopback, on the other
     * hand, would put that port on the network, so it is spelled out rather than left to
     * a default.
     *
     * @param audioHost the address the listener is actually bound to, already in the
     *                  literal form a URL wants — bracketed when it is IPv6. It is passed
     *                  in rather than assumed to be {@code 127.0.0.1}: the loopback
     *                  address a JVM picks is {@code ::1} whenever IPv6 is preferred, and
     *                  a listener on {@code [::1]} refuses an IPv4 connection outright.
     *                  Hardcoding one family here is what made ffmpeg answer
     *                  "Connection refused" against a port that was demonstrably open.
     * @param audioPort the port {@link java.net.ServerSocket} is already listening on
     */
    public static Process openVideoWithAudio(String url, int width, int height, double startSeconds,
                                             int sampleRate, int channels,
                                             String audioHost, int audioPort) throws IOException {
        String ffmpeg = requireFfmpeg();
        List<String> command = new ArrayList<>(List.of(
                ffmpeg, "-hide_banner", "-loglevel", "error", "-nostdin"));
        addSeek(command, startSeconds);
        addHardwareDecoding(command);
        addInputNetworkOptions(command, url);
        command.add("-i");
        command.add(url);

        // Output 1 — video to stdout. Same shape as openVideo; see the "no -re" note there.
        command.add("-map");
        command.add("0:v:0");
        command.add("-vf");
        command.add("scale=" + width + ":" + height + ":flags=bilinear");
        command.add("-pix_fmt");
        command.add("rgba");
        command.add("-f");
        command.add("rawvideo");
        command.add("pipe:1");

        // Output 2 — audio to the loopback listener. Same shape as openAudio.
        command.add("-map");
        command.add("0:a:0");
        command.add("-f");
        command.add("s16le");
        command.add("-acodec");
        command.add("pcm_s16le");
        command.add("-ar");
        command.add(Integer.toString(sampleRate));
        command.add("-ac");
        command.add(Integer.toString(channels));
        command.add("tcp://" + audioHost + ":" + audioPort);

        return start(command);
    }

    /**
     * Starts an ffmpeg process that writes signed 16-bit little-endian PCM audio
     * to its stdout at the given rate/channel count, beginning at
     * {@code startSeconds}. The caller reads {@link Process#getInputStream()} and
     * forwards it to a {@code SourceDataLine}.
     */
    public static Process openAudio(String url, int sampleRate, int channels, double startSeconds) throws IOException {
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
    public static byte[] grabRawFrame(String url, int width, int height, double atSeconds) throws IOException {
        String ffmpeg = requireFfmpeg();
        List<String> command = new ArrayList<>(List.of(
                ffmpeg, "-hide_banner", "-loglevel", "error", "-nostdin"));
        addSeek(command, atSeconds);
        addInputNetworkOptions(command, url);
        command.add("-i");
        command.add(url);
        command.add("-frames:v");
        command.add("1");
        // One frame is one frame: ffmpeg's default thread count would spin up a worker
        // per core for it. A queue panel asks for these in bursts — one per track the
        // moment a playlist opens — so left alone they take the whole machine for a
        // second at exactly the wrong time. See the pool in VideoThumbnailCache.
        command.add("-threads");
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
        Future<byte[]> readFuture = CompletableFuture.supplyAsync(() -> {
            try (InputStream in = process.getInputStream()) {
                return in.readNBytes(needed);
            } catch (IOException e) {
                return null;
            }
        });

        try {
            boolean finished = process.waitFor(15, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
            }
            byte[] data = readFuture.get(1, TimeUnit.SECONDS);
            return data != null && data.length == needed ? data : null;
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while grabbing frame from " + url, e);
        } catch (Exception e) {
            process.destroyForcibly();
            throw new IOException("Failed to read frame", e);
        }
    }

    // ------------------------------------------------------------------
    // Command building helpers
    // ------------------------------------------------------------------

    private static final Set<Process> ACTIVE_PROCESSES = ConcurrentHashMap.newKeySet();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ACTIVE_PROCESSES.forEach(Process::destroyForcibly);
        }, "FFmpeg-ShutdownHook"));
    }

    /**
     * The last few lines each running process wrote to stderr.
     *
     * <p>These used to go to {@code Redirect.DISCARD}, on the reasoning that a real
     * failure would surface as an early EOF on stdout and that ffprobe had already
     * validated the URL. The first half is true and the second is not enough: an EOF says
     * only <em>that</em> ffmpeg stopped, never <em>why</em>, and the caller's log then
     * shows a player that closed itself with nothing to explain it. ffmpeg puts the
     * reason on stderr — a rejected option, a stream map that matches nothing, a decoder
     * that would not open — and it costs one bounded buffer to keep it.</p>
     *
     * <p>Bounded twice over: {@link #MAX_STDERR_CHARS} per process, and the entry is
     * dropped when the process exits.</p>
     */
    private static final Map<Process, StringBuilder> STDERR = new ConcurrentHashMap<>();

    /** Enough for the handful of lines ffmpeg writes before giving up. */
    private static final int MAX_STDERR_CHARS = 4000;

    /**
     * What {@code process} wrote to stderr, trimmed, or an empty string.
     *
     * <p>Worth reading whenever a stream ends sooner than it should have — which for the
     * streaming processes is the only symptom that ever reaches the caller.</p>
     */
    public static String stderrOf(Process process) {
        StringBuilder captured = STDERR.get(process);
        if (captured == null) {
            return "";
        }
        synchronized (captured) {
            return captured.toString().trim();
        }
    }

    private static Process start(List<String> command) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        // Piped rather than discarded, and drained on a thread of its own: a pipe nobody
        // reads fills up and stalls the process behind it, which is what DISCARD was
        // there to avoid. Draining keeps that safety and keeps the diagnosis.
        builder.redirectErrorStream(false);
        Process p = builder.start();
        ACTIVE_PROCESSES.add(p);

        StringBuilder captured = new StringBuilder();
        STDERR.put(p, captured);
        Thread drain = new Thread(() -> drainStderr(p, captured), "liasmediaplayer-ffmpeg-err");
        drain.setDaemon(true);
        drain.start();

        p.onExit().thenAccept(process -> {
            ACTIVE_PROCESSES.remove(process);
            STDERR.remove(process);
        });
        return p;
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

    /**
     * The hardware decoder override, or {@code "auto"} to let ffmpeg pick.
     *
     * <p>Forcing a method by name is an escape hatch, not a setting, and it is sharp:
     * ffmpeg falls back to software when {@code auto} finds nothing and when a
     * <em>recognized</em> method has no usable device, but it refuses to open the input
     * at all when the name is one it does not know — so a typo here is a video that
     * never plays rather than one that plays slowly. Same shape as
     * {@code liasmediaplayer.ytdlp.clients}: the thing a bug report can ask someone to
     * try, without a config option nobody else should touch.</p>
     */
    private static final String HWACCEL_METHOD =
            System.getProperty("liasmediaplayer.hwaccel", "auto");

    /**
     * Asks the decoder to run on the GPU, unless the player has turned it off.
     *
     * <p>Decoding is the single largest slice of what playing a video costs, and on
     * H.264 or HEVC it is the slice a GPU does essentially for free. The frames still
     * come back to system memory to be scaled and converted — no {@code
     * -hwaccel_output_format} here, because keeping them on the GPU would mean a
     * {@code scale_vaapi} / {@code scale_cuda} chain that differs per platform — so this
     * moves the decode off the CPU and leaves the rest where it was.</p>
     *
     * <p>An input option, so it must be added before {@code -i}.</p>
     */
    private static void addHardwareDecoding(List<String> command) {
        if (!ConfigStore.HARDWARE_DECODING.getValue() || HWACCEL_METHOD.isBlank()) {
            return;
        }
        command.add("-hwaccel");
        command.add(HWACCEL_METHOD);
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
        // A manifest fetched over the network (HLS/DASH) lists its own segment URLs, and
        // those are attacker-controlled for any link posted in chat. Current ffmpeg already
        // defaults to a network-only whitelist for an http input, so this is not closing an
        // open hole — it states the requirement explicitly instead of inheriting it from
        // whichever ffmpeg build happens to be on the user's PATH, and narrows it to the
        // protocols an http(s) stream actually needs.
        command.add("-protocol_whitelist");
        command.add("http,https,tcp,tls,crypto,data");
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
