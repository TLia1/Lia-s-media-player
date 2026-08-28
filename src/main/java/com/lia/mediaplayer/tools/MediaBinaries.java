package com.lia.mediaplayer.tools;

import com.lia.mediaplayer.LiasMediaPlayer;
import com.lia.mediaplayer.config.ConfigStore;
import com.lia.mediaplayer.media.MediaUrlResolver;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Locates — and, when necessary, downloads — the external command-line tools the
 * media player shells out to. There are two of them:
 *
 * <ul>
 *   <li><b>yt-dlp</b> — resolves a YouTube page link to a direct media URL
 *       (see {@link MediaUrlResolver}). Distributed as a single self-contained
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

    enum InstallState {
        FOUND, INSTALLED, REINSTALLED, UNAVAILABLE
    }

    private static InstallState ytDlpState = InstallState.FOUND;
    private static InstallState ffmpegState = InstallState.FOUND;

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

            InstallState combinedState = InstallState.FOUND;
            if (ytDlpState == InstallState.UNAVAILABLE || ffmpegState == InstallState.UNAVAILABLE) {
                combinedState = InstallState.UNAVAILABLE;
            } else if (ytDlpState == InstallState.REINSTALLED || ffmpegState == InstallState.REINSTALLED) {
                combinedState = InstallState.REINSTALLED;
            } else if (ytDlpState == InstallState.INSTALLED || ffmpegState == InstallState.INSTALLED) {
                combinedState = InstallState.INSTALLED;
            }

            if (combinedState != InstallState.FOUND) {
                String translationKey = switch (combinedState) {
                    case UNAVAILABLE -> "gui.liasmediaplayer.toast.unavailable";
                    case REINSTALLED -> "gui.liasmediaplayer.toast.reinstalled";
                    case INSTALLED -> "gui.liasmediaplayer.toast.installed";
                    default -> "gui.liasmediaplayer.toast.installed";
                };
                toast(translationKey);
            }

            // A yt-dlp that was already there is the case the install states above say
            // nothing about, and it is the one that breaks: YouTube changes its player
            // every few weeks and an extractor from three months ago simply stops
            // resolving links. Checking the version costs one process launch at startup.
            if (ytDlp != null && ytDlpState == InstallState.FOUND) {
                checkYtDlpFreshness();
            }
        }, "liasmediaplayer-binary-installer");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Shows one of the mod's toasts. The version guards for reaching the toast queue
     * live here and nowhere else.
     */
    private static void toast(String translationKey) {
        net.minecraft.client.Minecraft.getInstance().execute(() -> {
            net.minecraft.client.gui.components.toasts.SystemToast.add(
                    // ToastComponent became ToastManager in 1.21.4, and 26.2
                    // moved its owner from Minecraft onto Minecraft.gui along
                    // with the screen stack (see gui/Screens).
                    //? if <1.21.4 {
                    net.minecraft.client.Minecraft.getInstance().getToasts(),
                    //?} elif <26.2 {
                    /*net.minecraft.client.Minecraft.getInstance().getToastManager(),
                    *///?} else {
                    /*net.minecraft.client.Minecraft.getInstance().gui.toastManager(),
                    *///?}
                    net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    net.minecraft.network.chat.Component.translatable("gui.liasmediaplayer.toast.title"),
                    net.minecraft.network.chat.Component.translatable(translationKey)
            );
        });
    }

    // ---- Keeping yt-dlp current ---------------------------------------------

    /**
     * How old a yt-dlp build may be before it is treated as stale. yt-dlp releases every
     * week or two and YouTube breaks extractors at about that pace, so a month is
     * already generous.
     */
    private static final int YT_DLP_MAX_AGE_DAYS = 30;

    /** True while a tools update is running, so the UI can say so and not start a second. */
    private static final AtomicBoolean UPDATING =
            new AtomicBoolean();

    /**
     * Whether a {@linkplain #updateToolsAsync update} is in flight.
     */
    public static boolean isUpdating() {
        return UPDATING.get();
    }

    /**
     * The version string {@code yt-dlp --version} prints (a release date, e.g.
     * {@code 2025.08.11}), or {@code null} if it could not be asked.
     */
    @Nullable
    public static String ytDlpVersion() {
        String executable = CACHE.get(Tool.YT_DLP);
        if (executable == null) {
            executable = safeLocate(Tool.YT_DLP);
        }
        return executable == null ? null : runVersion(executable);
    }

    /**
     * Whether {@code version} — a yt-dlp version string — is older than
     * {@link #YT_DLP_MAX_AGE_DAYS} days on {@code today}.
     *
     * <p>Package-private and taking the date rather than reading the clock so it can be
     * unit-tested. An unparseable version is <em>not</em> stale: a build we cannot read
     * the date of is more likely a distribution's own packaging than an old copy, and
     * nagging about it every launch would be worse than missing one update.</p>
     */
    static boolean isStale(@Nullable String version, LocalDate today) {
        if (version == null || version.isBlank()) {
            return false;
        }
        Matcher matcher =
                Pattern.compile("(\\d{4})\\.(\\d{2})\\.(\\d{2})").matcher(version);
        if (!matcher.find()) {
            return false;
        }
        try {
            LocalDate released = LocalDate.of(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)));
            return released.plusDays(YT_DLP_MAX_AGE_DAYS).isBefore(today);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Runs at startup once yt-dlp has been located: updates it if it is old and the
     * player asked for that, and otherwise says so once, in a toast.
     */
    private static void checkYtDlpFreshness() {
        String version = ytDlpVersion();
        if (!isStale(version, LocalDate.now())) {
            return;
        }
        LiasMediaPlayer.LOGGER.info("yt-dlp {} is more than {} days old", version, YT_DLP_MAX_AGE_DAYS);
        if (ConfigStore.AUTO_UPDATE_TOOLS.getValue()) {
            updateTools();
        } else {
            toast("gui.liasmediaplayer.toast.outdated");
        }
    }

    /**
     * Re-downloads yt-dlp (and ffmpeg, if it is missing or too old) on a background
     * thread, ending in a toast either way. This is what the <em>update the tools</em>
     * buttons — in the settings screen and on a failed player — call.
     */
    public static void updateToolsAsync() {
        if (isUpdating()) {
            return;
        }
        Thread thread = new Thread(MediaBinaries::updateTools, "liasmediaplayer-binary-updater");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * The update itself. Blocking; call it from a background thread.
     *
     * <p>yt-dlp is fetched <em>unconditionally</em> — the whole point is to replace a
     * copy that is present and working-but-outdated, which every other path here
     * deliberately leaves alone. ffmpeg only comes down if the one we have does not pass
     * {@link BinaryLocator#isUsable}: its releases do not go stale the way yt-dlp's do,
     * and it is a ~100 MB download.</p>
     */
    private static void updateTools() {
        if (!UPDATING.compareAndSet(false, true)) {
            return;
        }
        try {
            Path managedDir = managedDir();
            String before = ytDlpVersion();
            String updated = BinaryDownloader.downloadYtDlp(managedDir, true);
            if (updated != null) {
                // Point every later lookup at the copy we just wrote, even if the one
                // found at startup came from PATH: it is now the newest one we know of.
                CACHE.put(Tool.YT_DLP, updated);
                VERSION_CACHE.remove(updated);
                String after = ytDlpVersion();
                LiasMediaPlayer.LOGGER.info("yt-dlp updated: {} -> {}", before, after);
            }

            boolean ffmpegOk = safeLocate(Tool.FFMPEG) != null && safeLocate(Tool.FFPROBE) != null;
            toast(updated != null && ffmpegOk
                    ? "gui.liasmediaplayer.toast.reinstalled"
                    : "gui.liasmediaplayer.toast.update_failed");
        } finally {
            UPDATING.set(false);
        }
    }

    /** Version strings per executable path; asking costs a process launch. */
    private static final Map<String, String> VERSION_CACHE = new ConcurrentHashMap<>();

    /**
     * Asks a tool for its version, cached per path.
     */
    @Nullable
    private static String runVersion(String executable) {
        String cached = VERSION_CACHE.get(executable);
        if (cached != null) {
            return cached;
        }
        try {
            Process process = new ProcessBuilder(executable, Tool.YT_DLP.versionFlag())
                    .redirectErrorStream(true)
                    .start();
            String output;
            try (InputStream in = process.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (!process.waitFor(15, TimeUnit.SECONDS) || process.exitValue() != 0) {
                return null;
            }
            String version = output.strip().lines().findFirst().orElse("").strip();
            if (version.isEmpty()) {
                return null;
            }
            VERSION_CACHE.put(executable, version);
            return version;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
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
            File dir = net.minecraft.client.Minecraft.getInstance().gameDirectory;
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
        // isUsable, not merely isExecutableFile: BinaryLocator has already rejected this
        // path once (that is why we are here), so accepting it on "the file exists" would
        // hand back the very copy that failed the ffmpeg version check and never replace it.
        if (BinaryLocator.isUsable(tool, target.toString(), false)) {
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
        boolean existed = BinaryLocator.isExecutableFile(target.toString());
        if (!DOWNLOAD_ATTEMPTED.add("yt-dlp")) {
            return null; // already tried this session
        }
        String res = BinaryDownloader.downloadYtDlp(managedDir);
        if (res != null) {
            ytDlpState = existed ? InstallState.REINSTALLED : InstallState.INSTALLED;
        } else {
            ytDlpState = InstallState.UNAVAILABLE;
        }
        return res;
    }

    /**
     * Downloads and unpacks the official ffmpeg build, placing both {@code ffmpeg}
     * and {@code ffprobe} in the managed directory. Returns {@code true} once both
     * are present. Attempted at most once per session.
     */
    private static synchronized boolean ensureFfmpegBundle(Path managedDir) {
        Path ffmpeg = managedDir.resolve(Tool.FFMPEG.exeName());
        boolean existed = BinaryLocator.isExecutableFile(ffmpeg.toString());
        if (!DOWNLOAD_ATTEMPTED.add("ffmpeg")) {
            return false; // already tried this session
        }
        boolean res = BinaryDownloader.downloadFfmpegBundle(managedDir);
        if (res) {
            ffmpegState = existed ? InstallState.REINSTALLED : InstallState.INSTALLED;
            try {
                Files.writeString(managedDir.resolve(".ffmpeg-updated-8.1.2"), "updated");
            } catch (Exception ignored) {
            }
        } else {
            ffmpegState = InstallState.UNAVAILABLE;
        }
        return res;
    }
}
