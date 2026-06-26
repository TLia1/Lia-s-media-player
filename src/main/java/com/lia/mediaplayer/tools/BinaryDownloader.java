package com.lia.mediaplayer.tools;

import com.lia.mediaplayer.LiasMediaPlayer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads and installs the external command-line tools the media player shells
 * out to. This class handles <em>only</em> the network + filesystem work —
 * locating existing copies is {@link BinaryLocator}'s job.
 *
 * <p>Two download shapes are supported:</p>
 * <ul>
 *   <li><b>yt-dlp</b> — a single self-contained executable, downloaded from
 *       the project's GitHub releases.</li>
 *   <li><b>ffmpeg + ffprobe</b> — distributed as a per-platform archive that
 *       bundles both binaries. On Windows, a {@code .zip}; on Linux, a
 *       {@code .tar.xz}; on macOS, two separate single-binary zips from
 *       evermeet.cx.</li>
 * </ul>
 *
 * <p>Archives are unpacked with the JDK's zip support (or the system {@code tar}
 * for {@code .tar.xz}), with zip-slip protection. Every download goes through
 * a temporary file + atomic move so a failed download never leaves a corrupt
 * binary in the managed directory.</p>
 *
 * @see BinaryLocator
 * @see MediaBinaries
 */
final class BinaryDownloader {

    private BinaryDownloader() {
    }

    /** How long the one-time download of a tool may take before we give up. */
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(120);

    /**
     * Reusable HTTP client for all downloads within the session. Configured with
     * redirect-following and a reasonable connect timeout.
     */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    // ---- yt-dlp -------------------------------------------------------------

    /**
     * Downloads the single-file yt-dlp release into {@code managedDir}.
     *
     * @return the absolute path of the installed binary, or {@code null} on failure
     */
    @Nullable
    static String downloadYtDlp(Path managedDir) {
        Path target = managedDir.resolve(MediaBinaries.Tool.YT_DLP.exeName());
        if (BinaryLocator.isExecutableFile(target.toString())) {
            return target.toString();
        }

        String source = ytDlpDownloadUrl();
        try {
            Files.createDirectories(managedDir);
            Path tmp = Files.createTempFile(managedDir, "yt-dlp", ".part");
            LiasMediaPlayer.LOGGER.info("Downloading yt-dlp from {} ...", source);
            if (!download(source, tmp, 100_000)) {
                Files.deleteIfExists(tmp);
                return null;
            }
            makeExecutable(tmp);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            if (!BinaryLocator.isExecutableFile(target.toString())) {
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

    // ---- ffmpeg + ffprobe bundle ---------------------------------------------

    /**
     * Downloads and unpacks the official ffmpeg build, placing both {@code ffmpeg}
     * and {@code ffprobe} in {@code managedDir}.
     *
     * @return {@code true} once both binaries are present and executable
     */
    static boolean downloadFfmpegBundle(Path managedDir) {
        Path ffmpeg = managedDir.resolve(MediaBinaries.Tool.FFMPEG.exeName());
        Path ffprobe = managedDir.resolve(MediaBinaries.Tool.FFPROBE.exeName());
        if (BinaryLocator.isExecutableFile(ffmpeg.toString())
                && BinaryLocator.isExecutableFile(ffprobe.toString())) {
            return true;
        }

        try {
            Files.createDirectories(managedDir);
            if (MediaBinaries.MAC) {
                // evermeet ships ffmpeg and ffprobe as separate single-binary zips.
                boolean a = downloadAndExtractInto(macDownloadUrl(MediaBinaries.Tool.FFMPEG), ffmpeg, managedDir);
                boolean b = downloadAndExtractInto(macDownloadUrl(MediaBinaries.Tool.FFPROBE), ffprobe, managedDir);
                return a && b && verifyBundle(ffmpeg, ffprobe);
            }
            // Windows (.zip) and Linux (.tar.xz): one archive holds both binaries.
            String source = ffmpegArchiveUrl();
            Path archive = Files.createTempFile(managedDir, "ffmpeg", archiveSuffix(source));
            LiasMediaPlayer.LOGGER.info("Downloading ffmpeg from {} ...", source);
            try {
                if (!download(source, archive, 1_000_000)) {
                    return false;
                }
                Path extractDir = Files.createTempDirectory(managedDir, "ffmpeg-unpack");
                try {
                    extract(archive, extractDir);
                    boolean a = placeFromTree(extractDir, MediaBinaries.Tool.FFMPEG.exeName(), ffmpeg);
                    boolean b = placeFromTree(extractDir, MediaBinaries.Tool.FFPROBE.exeName(), ffprobe);
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

    // ---- Download + extract primitives --------------------------------------

    /**
     * Streams {@code source} to {@code target}. Returns {@code false} (and logs)
     * on a non-200 response or a body smaller than {@code minBytes}, which would
     * indicate an error page rather than the real binary/archive.
     */
    private static boolean download(String source, Path target, long minBytes)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(source))
                .timeout(DOWNLOAD_TIMEOUT)
                .header("User-Agent", "liasmediaplayer")
                .GET()
                .build();
        HttpResponse<Path> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofFile(target));
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

    // ---- Filesystem helpers -------------------------------------------------

    /** Downloads a single-binary zip (macOS) and writes the contained file to {@code target}. */
    private static boolean downloadAndExtractInto(String url, Path target, Path managedDir)
            throws IOException, InterruptedException {
        Path archive = Files.createTempFile(managedDir, target.getFileName().toString(), ".zip");
        try {
            LiasMediaPlayer.LOGGER.info("Downloading {} from {} ...", target.getFileName(), url);
            if (!download(url, archive, 1_000_000)) {
                return false;
            }
            Path extractDir = Files.createTempDirectory(managedDir, "unpack");
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
        return BinaryLocator.isExecutableFile(target.toString());
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

    /** Marks a file owner/group/other-executable on POSIX systems; a no-op on Windows. */
    static void makeExecutable(Path file) {
        if (MediaBinaries.WINDOWS) {
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

    private static boolean verifyBundle(Path ffmpeg, Path ffprobe) {
        boolean ok = BinaryLocator.isExecutableFile(ffmpeg.toString())
                && BinaryLocator.isExecutableFile(ffprobe.toString());
        if (ok) {
            LiasMediaPlayer.LOGGER.info("ffmpeg ready at {}", ffmpeg);
        } else {
            LiasMediaPlayer.LOGGER.warn("ffmpeg bundle incomplete (ffmpeg={}, ffprobe={})",
                    BinaryLocator.isExecutableFile(ffmpeg.toString()),
                    BinaryLocator.isExecutableFile(ffprobe.toString()));
        }
        return ok;
    }

    // ---- Download URLs ------------------------------------------------------

    /** The official single-file yt-dlp release asset for the current OS/arch. */
    private static @NotNull String ytDlpDownloadUrl() {
        String base = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/";
        if (MediaBinaries.WINDOWS) {
            return base + "yt-dlp.exe";
        }
        if (MediaBinaries.MAC) {
            return base + "yt-dlp_macos";
        }
        return base + (MediaBinaries.AARCH64 ? "yt-dlp_linux_aarch64" : "yt-dlp_linux");
    }

    /** The BtbN ffmpeg build archive (Windows zip / Linux tar.xz) for this platform. */
    private static @NotNull String ffmpegArchiveUrl() {
        String base = "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/";
        if (MediaBinaries.WINDOWS) {
            return base + "ffmpeg-master-latest-win64-gpl.zip";
        }
        return base + (MediaBinaries.AARCH64
                ? "ffmpeg-master-latest-linuxarm64-gpl.tar.xz"
                : "ffmpeg-master-latest-linux64-gpl.tar.xz");
    }

    /** evermeet.cx single-binary zip for macOS (Intel build; runs under Rosetta on ARM). */
    private static @NotNull String macDownloadUrl(MediaBinaries.Tool tool) {
        return "https://evermeet.cx/ffmpeg/getrelease/" + tool.base + "/zip";
    }

    private static String archiveSuffix(String url) {
        return url.toLowerCase(Locale.ROOT).endsWith(".zip") ? ".zip" : ".tar.xz";
    }
}
