package com.lia.mediaplayer.tools;

import com.lia.mediaplayer.LiasMediaPlayer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Locates — and, when necessary, downloads — the external command-line tools the
 * media player shells out to. There are two of them:
 *
 * <ul>
 *   <li><b>yt-dlp</b> — resolves a YouTube page link to a direct media URL
 *       (see {@link com.lia.mediaplayer.video.VideoUrlResolver}). Distributed as a single self-contained
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
    private static final boolean AARCH64 =
            OS_ARCH.contains("aarch64") || OS_ARCH.contains("arm64");
    private static final String EXE_SUFFIX = WINDOWS ? ".exe" : "";

    /** How long the one-time download of a tool may take before we give up. */
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(120);

    /** The external tools the player can manage. */
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

        /** Platform executable name, e.g. {@code yt-dlp.exe} on Windows. */
        String exeName() {
            return base + EXE_SUFFIX;
        }

        /** The flag that makes the tool print its version and exit 0. */
        String versionFlag() {
            // yt-dlp uses the long form; ffmpeg/ffprobe use a single dash.
            return this == YT_DLP ? "--version" : "-version";
        }
    }

    /** Resolved absolute path per tool, computed lazily and reused. */
    private static final Map<Tool, String> CACHE = new ConcurrentHashMap<>();

    /**
     * Marks groups whose one-time download has already been attempted (so a
     * failure is not retried on every link). yt-dlp is its own group; ffmpeg and
     * ffprobe share the "ffmpeg" archive and therefore a single group key.
     */
    private static final Set<String> DOWNLOAD_ATTEMPTED = ConcurrentHashMap.newKeySet();

    // ---- Public API ---------------------------------------------------------

    /** Ensures yt-dlp is available and returns its path, or {@code null}. */
    @Nullable
    public static String ytDlp() {
        return locate(Tool.YT_DLP);
    }

    /** Ensures ffmpeg is available and returns its path, or {@code null}. */
    @Nullable
    public static String ffmpeg() {
        return locate(Tool.FFMPEG);
    }

    /** Ensures ffprobe is available and returns its path, or {@code null}. */
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
        }, "liasmediaplayer-binary-installer");
        thread.setDaemon(true);
        thread.start();
    }

    @Nullable
    private static String safeLocate(Tool tool) {
        try {
            return locate(tool);
        } catch (Throwable t) {
            LiasMediaPlayer.LOGGER.warn("Could not install {}: {}", tool.base, t.toString());
            return null;
        }
    }

    // ---- Resolution ---------------------------------------------------------

    /** Finds a usable executable for {@code tool}, downloading it if needed. */
    @Nullable
    private static String locate(Tool tool) {
        String cached = CACHE.get(tool);
        if (cached != null) {
            return cached;
        }

        List<String> tried = new ArrayList<>();
        for (String candidate : candidatePaths(tool)) {
            tried.add(candidate);
            if (isExecutableFile(candidate)) {
                CACHE.put(tool, candidate);
                return candidate;
            }
        }

        // Trust the launcher's PATH with the bare name.
        if (canRun(tool.exeName(), tool.versionFlag())) {
            CACHE.put(tool, tool.exeName());
            return tool.exeName();
        }

        // Nothing installed anywhere we can see: download a managed copy once.
        String downloaded = ensureManaged(tool);
        if (downloaded != null) {
            CACHE.put(tool, downloaded);
            return downloaded;
        }

        LiasMediaPlayer.LOGGER.warn("Could not find or download {}. Checked: {}",
                tool.base, String.join(", ", tried));
        return null;
    }

    /** Ordered, de-duplicated list of absolute candidate paths to probe. */
    private static List<String> candidatePaths(Tool tool) {
        List<String> out = new ArrayList<>();

        // 1. Explicit override.
        String override = System.getProperty(tool.overrideProperty);
        if (override == null || override.isBlank()) {
            for (String env : tool.overrideEnv) {
                String value = System.getenv(env);
                if (value != null && !value.isBlank()) {
                    override = value;
                    break;
                }
            }
        }
        if (override != null && !override.isBlank()) {
            out.add(override.trim());
        }

        // 2. A copy this mod downloaded on a previous run.
        out.add(managedPath(tool).toString());

        // 3. Every directory on PATH.
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                if (!dir.isBlank()) {
                    out.add(new File(dir.trim(), tool.exeName()).getPath());
                }
            }
        }

        // 4. Common per-OS install locations.
        String home = System.getProperty("user.home", "");
        if (WINDOWS) {
            String localAppData = firstNonBlank(System.getenv("LOCALAPPDATA"), home + "\\AppData\\Local");
            String appData = firstNonBlank(System.getenv("APPDATA"), home + "\\AppData\\Roaming");
            out.add(localAppData + "\\Microsoft\\WinGet\\Links\\" + tool.exeName());
            out.add(home + "\\scoop\\shims\\" + tool.exeName());
            out.add("C:\\ProgramData\\chocolatey\\bin\\" + tool.exeName());
            if (tool == Tool.YT_DLP) {
                // pip / pip --user Python "Scripts" directories.
                addPythonScriptsDirs(out, localAppData + "\\Programs\\Python");
                addPythonScriptsDirs(out, appData + "\\Python");
            }
        } else {
            out.add("/usr/local/bin/" + tool.base);
            out.add("/usr/bin/" + tool.base);
            out.add("/bin/" + tool.base);
            out.add("/opt/homebrew/bin/" + tool.base);
            out.add("/snap/bin/" + tool.base);
            out.add(home + "/.local/bin/" + tool.base);
            out.add(home + "/bin/" + tool.base);
        }

        return out.stream().distinct().toList();
    }

    /** Adds {@code <base>/<Python*>/Scripts/yt-dlp.exe} for each Python install found. */
    private static void addPythonScriptsDirs(List<String> out, String base) {
        Path baseDir = Path.of(base);
        if (!Files.isDirectory(baseDir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(baseDir, "Python*")) {
            for (Path dir : stream) {
                out.add(dir.resolve("Scripts").resolve("yt-dlp.exe").toString());
            }
        } catch (IOException ignored) {
            // best-effort
        }
    }

    // ---- Managed (downloaded) copies ---------------------------------------

    /** Where this mod keeps its own downloaded copy of a tool. */
    private static Path managedPath(Tool tool) {
        return managedDir().resolve(tool.exeName());
    }

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
        } catch (Throwable ignored) {
            return Path.of(System.getProperty("user.dir", "."));
        }
    }

    /**
     * Returns the path to a managed copy of {@code tool}, downloading it on first
     * use. Returns {@code null} if the download is unavailable (no network, etc.).
     */
    @Nullable
    private static String ensureManaged(Tool tool) {
        Path target = managedPath(tool);
        if (isExecutableFile(target.toString())) {
            return target.toString();
        }
        return switch (tool) {
            case YT_DLP -> ensureYtDlp();
            case FFMPEG, FFPROBE -> ensureFfmpegBundle() ? managedPath(tool).toString() : null;
        };
    }

    /** Downloads the single-file yt-dlp release, once per session. */
    @Nullable
    private static synchronized String ensureYtDlp() {
        Path target = managedPath(Tool.YT_DLP);
        if (isExecutableFile(target.toString())) {
            return target.toString();
        }
        if (!DOWNLOAD_ATTEMPTED.add("yt-dlp")) {
            return null; // already tried this session
        }

        String source = ytDlpDownloadUrl();
        try {
            Files.createDirectories(target.getParent());
            Path tmp = Files.createTempFile(target.getParent(), "yt-dlp", ".part");
            LiasMediaPlayer.LOGGER.info("Downloading yt-dlp from {} ...", source);
            if (!download(source, tmp, 100_000)) {
                Files.deleteIfExists(tmp);
                return null;
            }
            makeExecutable(tmp);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            if (!isExecutableFile(target.toString())) {
                LiasMediaPlayer.LOGGER.warn("Downloaded yt-dlp is not executable at {}", target);
                return null;
            }
            LiasMediaPlayer.LOGGER.info("yt-dlp ready at {}", target);
            return target.toString();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LiasMediaPlayer.LOGGER.warn("Could not download yt-dlp: {}", e.toString());
            return null;
        }
    }

    /**
     * Downloads and unpacks the official ffmpeg build, placing both {@code ffmpeg}
     * and {@code ffprobe} in the managed directory. Returns {@code true} once both
     * are present. Attempted at most once per session.
     */
    private static synchronized boolean ensureFfmpegBundle() {
        Path ffmpeg = managedPath(Tool.FFMPEG);
        Path ffprobe = managedPath(Tool.FFPROBE);
        if (isExecutableFile(ffmpeg.toString()) && isExecutableFile(ffprobe.toString())) {
            return true;
        }
        if (!DOWNLOAD_ATTEMPTED.add("ffmpeg")) {
            return false; // already tried this session
        }

        try {
            Files.createDirectories(managedDir());
            if (MAC) {
                // evermeet ships ffmpeg and ffprobe as separate single-binary zips.
                boolean a = downloadAndExtractInto(macDownloadUrl(Tool.FFMPEG), ffmpeg);
                boolean b = downloadAndExtractInto(macDownloadUrl(Tool.FFPROBE), ffprobe);
                return a && b && verifyBundle(ffmpeg, ffprobe);
            }
            // Windows (.zip) and Linux (.tar.xz): one archive holds both binaries.
            String source = ffmpegArchiveUrl();
            Path archive = Files.createTempFile(managedDir(), "ffmpeg", archiveSuffix(source));
            LiasMediaPlayer.LOGGER.info("Downloading ffmpeg from {} ...", source);
            try {
                if (!download(source, archive, 1_000_000)) {
                    return false;
                }
                Path extractDir = Files.createTempDirectory(managedDir(), "ffmpeg-unpack");
                try {
                    extract(archive, extractDir);
                    boolean a = placeFromTree(extractDir, Tool.FFMPEG.exeName(), ffmpeg);
                    boolean b = placeFromTree(extractDir, Tool.FFPROBE.exeName(), ffprobe);
                    return a && b && verifyBundle(ffmpeg, ffprobe);
                } finally {
                    deleteRecursively(extractDir);
                }
            } finally {
                Files.deleteIfExists(archive);
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LiasMediaPlayer.LOGGER.warn("Could not download ffmpeg: {}", e.toString());
            return false;
        }
    }

    private static boolean verifyBundle(Path ffmpeg, Path ffprobe) {
        boolean ok = isExecutableFile(ffmpeg.toString()) && isExecutableFile(ffprobe.toString());
        if (ok) {
            LiasMediaPlayer.LOGGER.info("ffmpeg ready at {}", ffmpeg);
        } else {
            LiasMediaPlayer.LOGGER.warn("ffmpeg bundle incomplete (ffmpeg={}, ffprobe={})",
                    isExecutableFile(ffmpeg.toString()), isExecutableFile(ffprobe.toString()));
        }
        return ok;
    }

    /** Downloads a single-binary zip (macOS) and writes the contained file to {@code target}. */
    private static boolean downloadAndExtractInto(String url, Path target)
            throws IOException, InterruptedException {
        Path archive = Files.createTempFile(managedDir(), target.getFileName().toString(), ".zip");
        try {
            LiasMediaPlayer.LOGGER.info("Downloading {} from {} ...", target.getFileName(), url);
            if (!download(url, archive, 1_000_000)) {
                return false;
            }
            Path extractDir = Files.createTempDirectory(managedDir(), "unpack");
            try {
                extractZip(archive, extractDir);
                return placeFromTree(extractDir, target.getFileName().toString(), target);
            } finally {
                deleteRecursively(extractDir);
            }
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    /** Moves the first file named {@code exeName} found under {@code root} to {@code target}. */
    private static boolean placeFromTree(Path root, String exeName, Path target) throws IOException {
        Path found = findFile(root, exeName);
        if (found == null) {
            LiasMediaPlayer.LOGGER.warn("'{}' not found inside the downloaded archive", exeName);
            return false;
        }
        makeExecutable(found);
        Files.move(found, target, StandardCopyOption.REPLACE_EXISTING);
        makeExecutable(target);
        return isExecutableFile(target.toString());
    }

    @Nullable
    private static Path findFile(Path root, String name) throws IOException {
        try (var stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase(name))
                    .findFirst()
                    .orElse(null);
        }
    }

    // ---- Download + extract primitives -------------------------------------

    /**
     * Streams {@code source} to {@code target}. Returns {@code false} (and logs)
     * on a non-200 response or a body smaller than {@code minBytes}, which would
     * indicate an error page rather than the real binary/archive.
     */
    private static boolean download(String source, Path target, long minBytes)
            throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(source))
                .timeout(DOWNLOAD_TIMEOUT)
                .header("User-Agent", "liasmediaplayer")
                .GET()
                .build();
        HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(target));
        if (response.statusCode() != 200) {
            LiasMediaPlayer.LOGGER.warn("Download failed: HTTP {} for {}", response.statusCode(), source);
            return false;
        }
        if (Files.size(target) < minBytes) {
            LiasMediaPlayer.LOGGER.warn("Download from {} looked too small to be valid; discarding.", source);
            return false;
        }
        return true;
    }

    /** Extracts a {@code .zip} or {@code .tar.xz} archive into {@code destDir}. */
    private static void extract(Path archive, Path destDir) throws IOException, InterruptedException {
        String name = archive.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".zip")) {
            extractZip(archive, destDir);
        } else {
            extractTar(archive, destDir);
        }
    }

    /** Unpacks a zip using only the JDK (no third-party archive library). */
    private static void extractZip(Path archive, Path destDir) throws IOException {
        try (InputStream fin = Files.newInputStream(archive);
             ZipInputStream zip = new ZipInputStream(fin)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path out = safeResolve(destDir, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(out))) {
                        zip.transferTo(os);
                    }
                }
                zip.closeEntry();
            }
        }
    }

    /**
     * Unpacks a {@code .tar.xz} (Linux builds) by shelling out to the system
     * {@code tar}, which understands xz everywhere a Linux desktop runs. This
     * avoids pulling in a Java XZ dependency just to slim the jar.
     */
    private static void extractTar(Path archive, Path destDir) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("tar", "-xf", archive.toString(), "-C", destDir.toString())
                .redirectErrorStream(true)
                .start();
        // Drain output so the process can't block on a full pipe.
        try (InputStream in = process.getInputStream()) {
            in.readAllBytes();
        }
        boolean finished = process.waitFor(120, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("tar timed out extracting " + archive.getFileName());
        }
        if (process.exitValue() != 0) {
            throw new IOException("tar failed (exit " + process.exitValue() + ") extracting "
                    + archive.getFileName());
        }
    }

    /** Resolves {@code entryName} under {@code destDir}, rejecting zip-slip escapes. */
    private static Path safeResolve(Path destDir, String entryName) throws IOException {
        Path resolved = destDir.resolve(entryName).normalize();
        if (!resolved.startsWith(destDir.normalize())) {
            throw new IOException("Refusing to extract entry outside target dir: " + entryName);
        }
        return resolved;
    }

    private static void deleteRecursively(Path dir) {
        try (var stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            });
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }

    private static String archiveSuffix(String url) {
        return url.toLowerCase(Locale.ROOT).endsWith(".zip") ? ".zip" : ".tar.xz";
    }

    // ---- Download URLs ------------------------------------------------------

    /** The official single-file yt-dlp release asset for the current OS/arch. */
    private static @NotNull String ytDlpDownloadUrl() {
        String base = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/";
        if (WINDOWS) {
            return base + "yt-dlp.exe";
        }
        if (MAC) {
            return base + "yt-dlp_macos";
        }
        return base + (AARCH64 ? "yt-dlp_linux_aarch64" : "yt-dlp_linux");
    }

    /** The BtbN ffmpeg build archive (Windows zip / Linux tar.xz) for this platform. */
    private static @NotNull String ffmpegArchiveUrl() {
        String base = "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/";
        if (WINDOWS) {
            return base + "ffmpeg-master-latest-win64-gpl.zip";
        }
        return base + (AARCH64
                ? "ffmpeg-master-latest-linuxarm64-gpl.tar.xz"
                : "ffmpeg-master-latest-linux64-gpl.tar.xz");
    }

    /** evermeet.cx single-binary zip for macOS (Intel build; runs under Rosetta on ARM). */
    private static @NotNull String macDownloadUrl(Tool tool) {
        return "https://evermeet.cx/ffmpeg/getrelease/" + tool.base + "/zip";
    }

    // ---- Small shared helpers ----------------------------------------------

    /** Marks a file owner/group/other-executable on POSIX systems; a no-op on Windows. */
    private static void makeExecutable(Path file) {
        if (WINDOWS) {
            return;
        }
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_READ,
                    PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(file, perms);
        } catch (IOException | UnsupportedOperationException e) {
            file.toFile().setExecutable(true);
        }
    }

    private static boolean isExecutableFile(String pathString) {
        try {
            File file = new File(pathString);
            return file.isFile() && file.canExecute();
        } catch (Exception e) {
            return false;
        }
    }

    /** Probes a bare command by asking for its version (fast and side-effect free). */
    private static boolean canRun(String executable, String versionFlag) {
        try {
            Process process = new ProcessBuilder(executable, versionFlag)
                    .redirectErrorStream(true)
                    .start();
            // Drain so a chatty banner can't fill the pipe and block us.
            try (InputStream in = process.getInputStream()) {
                in.readAllBytes();
            }
            boolean finished = process.waitFor(8, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException e) {
            return false; // not found on PATH
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Nullable
    private static String firstNonBlank(@Nullable String a, @Nullable String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b != null && !b.isBlank() ? b : null;
    }
}
