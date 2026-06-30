package com.lia.mediaplayer.tools;

import com.lia.mediaplayer.LiasMediaPlayer;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Locates — and, when necessary, downloads — the external command-line tools the
 * media player shells out to. There are two of them:
 *
 * <ul>
 *   <li><b>yt-dlp</b> — resolves a YouTube page link to a direct media URL
 *       (see {@link com.lia.mediaplayer.media.MediaUrlResolver}). Distributed as a single self-contained
 *       executable.</li>
 *   <li><b>ffmpeg</b> (and its sibling <b>ffprobe</b>) — decodes video frames and
 *       audio (see {@link FFmpegCli}). Distributed as a per-platform archive that
 *       bundles both binaries, so a single download yields the two of them.</li>
 * </ul>
 *
 * <h2>Why this exists</h2>
 * <p>FFmpeg used to be embedded inside the mod jar through the JavaCV/bytedeco
 * native libraries, which made the jar very large. We now treat ffmpeg the same
 * way we already treated yt-dlp: keep it out of the jar and fetch the official
 * build into the game folder on first launch. This class is the shared plumbing
 * for both tools.</p>
 *
 * <h2>Architecture</h2>
 * <p>This class is a thin <b>facade</b> that orchestrates two helpers:</p>
 * <ul>
 *   <li>{@link BinaryLocator} — scans for existing installations (explicit
 *       overrides, the managed directory, {@code PATH}, common per-OS
 *       locations).</li>
 *   <li>{@link BinaryDownloader} — fetches the official release and unpacks it
 *       into {@code <gamedir>/liasmediaplayer/bin/} when no existing copy is
 *       found.</li>
 * </ul>
 *
 * <h2>Resolution order</h2>
 * <p>For each tool we look, in order, for:</p>
 * <ol>
 *   <li>an explicit override
 *       ({@code -Dliasmediaplayer.<tool>=...} or the matching {@code *_PATH}
 *       environment variable);</li>
 *   <li>a copy this mod previously downloaded into
 *       {@code <gamedir>/liasmediaplayer/bin/};</li>
 *   <li>every directory listed on {@code PATH};</li>
 *   <li>common install locations (winget, scoop, chocolatey, Homebrew,
 *       {@code /usr/local/bin}, ...);</li>
 *   <li>the bare command name, in case the launcher's {@code PATH} contains it.</li>
 * </ol>
 *
 * <p>If none of those turn up a usable binary, we download the official release
 * into {@code <gamedir>/liasmediaplayer/bin/} and use that. The download is
 * attempted at most once per tool per game session.</p>
 *
 * <h2>Threading</h2>
 * <p>{@link #installAllAsync()} runs the (potentially slow, network-bound)
 * install on a background daemon thread at launch, so the binaries are ready
 * before the first link is clicked instead of being fetched lazily mid-feature.
 * Every method here is safe to call from any thread; lookups are cached.</p>
 */
public final class MediaBinaries {

    private MediaBinaries() {
    }

    // ---- Platform detection -------------------------------------------------

    private static final String OS_NAME =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    static final boolean WINDOWS = OS_NAME.contains("win");
    static final boolean MAC = OS_NAME.contains("mac") || OS_NAME.contains("darwin");
    private static final String OS_ARCH =
            System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
    static final boolean AARCH64 =
            OS_ARCH.contains("aarch64") || OS_ARCH.contains("arm64");
    private static final String EXE_SUFFIX = WINDOWS ? ".exe" : "";

    /**
     * The external tools the player can manage.
     */
    enum Tool {
        YT_DLP("yt-dlp", "liasmediaplayer.ytdlp", "YT_DLP_PATH", "YTDLP_PATH"),
        FFMPEG("ffmpeg", "liasmediaplayer.ffmpeg", "FFMPEG_PATH"),
        FFPROBE("ffprobe", "liasmediaplayer.ffprobe", "FFPROBE_PATH");

        final String base;
        final String overrideProperty;
        final String[] overrideEnv;

        Tool(String base, String overrideProperty, String... overrideEnv) {
            this.base = base;
            this.overrideProperty = overrideProperty;
            this.overrideEnv = overrideEnv;
        }

        /**
         * Platform executable name, e.g. {@code yt-dlp.exe} on Windows.
         */
        String exeName() {
            return base + EXE_SUFFIX;
        }

        /**
         * The flag that makes the tool print its version and exit 0.
         */
        String versionFlag() {
            // yt-dlp uses the long form; ffmpeg/ffprobe use a single dash.
            return this == YT_DLP ? "--version" : "-version";
        }
    }

    /**
     * Resolved absolute path per tool, computed lazily and reused.
     */
    private static final Map<Tool, String> CACHE = new ConcurrentHashMap<>();

    /**
     * Marks groups whose one-time download has already been attempted (so a
     * failure is not retried on every link). yt-dlp is its own group; ffmpeg and
     * ffprobe share the "ffmpeg" archive and therefore a single group key.
     */
    private static final Set<String> DOWNLOAD_ATTEMPTED = ConcurrentHashMap.newKeySet();

    // ---- Public API ---------------------------------------------------------

    /**
     * Ensures yt-dlp is available and returns its path, or {@code null}.
     */
    @Nullable
    public static String ytDlp() {
        return locate(Tool.YT_DLP);
    }

    /**
     * Ensures ffmpeg is available and returns its path, or {@code null}.
     */
    @Nullable
    public static String ffmpeg() {
        return locate(Tool.FFMPEG);
    }

    /**
     * Ensures ffprobe is available and returns its path, or {@code null}.
     */
    @Nullable
    public static String ffprobe() {
        return locate(Tool.FFPROBE);
    }

    /**
     * Kicks off, on a background daemon thread, the installation of every tool so
     * they are ready by the time the first media link is used. Safe to call once
     * at mod construction; failures are logged and left for a later lazy retry
     * within the same session is not attempted (see {@link #DOWNLOAD_ATTEMPTED}).
     */
    public static void installAllAsync() {
        Thread thread = new Thread(() -> {
            LiasMediaPlayer.LOGGER.info("Checking media tools (yt-dlp, ffmpeg) ...");
            String ytDlp = safeLocate(Tool.YT_DLP);
            String ffmpeg = safeLocate(Tool.FFMPEG);
            safeLocate(Tool.FFPROBE);
            LiasMediaPlayer.LOGGER.info("Media tools ready: yt-dlp={}, ffmpeg={}",
                    ytDlp != null ? ytDlp : "MISSING",
                    ffmpeg != null ? ffmpeg : "MISSING");

            if (ytDlp != null && ffmpeg != null) {
                net.minecraft.client.Minecraft.getInstance().execute(() -> {
                    net.minecraft.client.gui.components.toasts.SystemToast.add(
                            net.minecraft.client.Minecraft.getInstance().getToasts(),
                            net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                            net.minecraft.network.chat.Component.translatable("gui.liasmediaplayer.toast.title"),
                            net.minecraft.network.chat.Component.translatable("gui.liasmediaplayer.toast.downloaded")
                    );
                });
            }
        }, "liasmediaplayer-binary-installer");
        thread.setDaemon(true);
        thread.start();
    }

    @Nullable
    private static String safeLocate(Tool tool) {
        try {
            return locate(tool);
        } catch (Exception e) {
            LiasMediaPlayer.LOGGER.warn("Could not install {}: {}", tool.base, e.toString());
            return null;
        }
    }

    // ---- Resolution ---------------------------------------------------------

    /**
     * Finds a usable executable for {@code tool}, downloading it if needed.
     * Delegates to {@link BinaryLocator} for scanning existing installations,
     * and to {@link BinaryDownloader} for fetching missing ones.
     */
    @Nullable
    private static String locate(Tool tool) {
        String cached = CACHE.get(tool);
        if (cached != null) {
            return cached;
        }

        Path managedDir = managedDir();

        // 1. Try to find an existing installation.
        String found = BinaryLocator.find(tool, managedDir);
        if (found != null) {
            CACHE.put(tool, found);
            return found;
        }

        // 2. Nothing installed anywhere we can see: download a managed copy once.
        String downloaded = ensureManaged(tool, managedDir);
        if (downloaded != null) {
            CACHE.put(tool, downloaded);
            return downloaded;
        }

        List<String> tried = BinaryLocator.candidatePaths(tool, managedDir);
        LiasMediaPlayer.LOGGER.warn("Could not find or download {}. Checked: {}",
                tool.base, String.join(", ", tried));
        return null;
    }

    // ---- Managed (downloaded) copies ----------------------------------------

    private static Path managedDir() {
        return gameDirectory().resolve("liasmediaplayer").resolve("bin");
    }

    /**
     * The Minecraft game directory (where {@code mods/}, {@code config/}, etc.
     * live). Falls back to the JVM working directory if the client instance is
     * not reachable (e.g. called very early or outside a client context).
     */
    private static Path gameDirectory() {
        try {
            java.io.File dir = net.minecraft.client.Minecraft.getInstance().gameDirectory;
            return dir.toPath();
        } catch (Exception ignored) {
            return Path.of(System.getProperty("user.dir", "."));
        }
    }

    /**
     * Returns the path to a managed copy of {@code tool}, downloading it on first
     * use via {@link BinaryDownloader}. Returns {@code null} if the download is
     * unavailable (no network, etc.).
     */
    @Nullable
    private static String ensureManaged(Tool tool, Path managedDir) {
        Path target = managedDir.resolve(tool.exeName());
        if (BinaryLocator.isExecutableFile(target.toString())) {
            return target.toString();
        }
        return switch (tool) {
            case YT_DLP -> ensureYtDlp(managedDir);
            case FFMPEG, FFPROBE -> ensureFfmpegBundle(managedDir)
                    ? managedDir.resolve(tool.exeName()).toString()
                    : null;
        };
    }

    /**
     * Downloads the single-file yt-dlp release, once per session.
     */
    @Nullable
    private static synchronized String ensureYtDlp(Path managedDir) {
        Path target = managedDir.resolve(Tool.YT_DLP.exeName());
        if (BinaryLocator.isExecutableFile(target.toString())) {
            return target.toString();
        }
        if (!DOWNLOAD_ATTEMPTED.add("yt-dlp")) {
            return null; // already tried this session
        }
        return BinaryDownloader.downloadYtDlp(managedDir);
    }

    /**
     * Downloads and unpacks the official ffmpeg build, placing both {@code ffmpeg}
     * and {@code ffprobe} in the managed directory. Returns {@code true} once both
     * are present. Attempted at most once per session.
     */
    private static synchronized boolean ensureFfmpegBundle(Path managedDir) {
        Path ffmpeg = managedDir.resolve(Tool.FFMPEG.exeName());
        Path ffprobe = managedDir.resolve(Tool.FFPROBE.exeName());
        if (BinaryLocator.isExecutableFile(ffmpeg.toString())
                && BinaryLocator.isExecutableFile(ffprobe.toString())) {
            return true;
        }
        if (!DOWNLOAD_ATTEMPTED.add("ffmpeg")) {
            return false; // already tried this session
        }
        return BinaryDownloader.downloadFfmpegBundle(managedDir);
    }
}
