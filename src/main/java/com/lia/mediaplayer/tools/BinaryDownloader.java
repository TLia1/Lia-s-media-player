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
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
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
 * <h2>Trust model</h2>
 *
 * <p>This mod downloads executables and then runs them, so it owes an explicit account
 * of what that trust rests on. It rests on <b>TLS to three named publishers</b>, and on
 * nothing else:</p>
 * <ul>
 *   <li>{@code github.com/yt-dlp/yt-dlp} — the upstream project's own release assets;</li>
 *   <li>{@code github.com/BtbN/FFmpeg-Builds} — the build most ffmpeg documentation
 *       points Windows and Linux users at;</li>
 *   <li>{@code evermeet.cx} — the equivalent for macOS.</li>
 * </ul>
 *
 * <p>There is deliberately <b>no checksum check</b>, and that is a limitation rather
 * than an oversight: none of the three publishes a checksum that is any more trustworthy
 * than the asset beside it (same host, same TLS, same release), so verifying one would
 * describe a guarantee the mod does not actually have. A hash baked into this file
 * instead would pin the bytes, but only until the next build replaces them — which for
 * yt-dlp is the point of the tool, see below.</p>
 *
 * <p>What is checked is what can be: the response is a 200, the body is large enough to
 * be a real binary rather than an error page ({@link #download}), it starts with the
 * magic bytes of an executable for the platform we are on ({@link #looksExecutable}),
 * and archives cannot write outside their extraction directory
 * ({@link #safeResolve}).</p>
 *
 * <p>Versions are pinned as far as each tool allows. {@code ffmpeg} names a release
 * branch ({@link #FFMPEG_RELEASE}) rather than following {@code master}'s daily
 * autobuilds, so the decoder under the player only moves when this constant does.
 * {@code yt-dlp} deliberately stays on {@code latest}: it breaks whenever YouTube
 * changes its player, and being current <em>is</em> its function — pinning it would
 * guarantee the mod stops playing YouTube some weeks after release.</p>
 *
 * @see BinaryLocator
 * @see MediaBinaries
 */
final class BinaryDownloader {

    private BinaryDownloader() {
    }

    /**
     * How long the one-time download of a tool may take before we give up.
     */
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(120);

    /**
     * The ffmpeg release branch the mod asks BtbN for, e.g. {@code n9.0}.
     *
     * <p>BtbN's {@code latest} release holds two families of asset: {@code master}
     * autobuilds, which are whatever ffmpeg's trunk looked like this morning, and
     * per-release-branch builds like this one. Naming a branch is the difference
     * between "the ffmpeg that happened to be built today" and a version this mod has
     * actually been run against — a decoder regression upstream can otherwise arrive on
     * a player's machine without a single line of this repository changing.</p>
     *
     * <p>Bump it deliberately, and leave {@link #FFMPEG_FALLBACK_RELEASE} pointing at
     * {@code master}: BtbN retires a branch's assets some time after the branch itself
     * is retired, and a mod that cannot get ffmpeg at all is worse than one that gets a
     * newer build than it expected. The fallback logs, so it is visible when it happens.</p>
     */
    private static final String FFMPEG_RELEASE = "n9.0";

    /**
     * What {@link #FFMPEG_RELEASE} degrades to once its assets are gone — see there.
     */
    private static final String FFMPEG_FALLBACK_RELEASE = "master";

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
        return downloadYtDlp(managedDir, false);
    }

    /**
     * Downloads the single-file yt-dlp release into {@code managedDir}.
     *
     * @param force fetch the latest release even when a usable copy is already there —
     *              what "update the tools" means, since the copy being replaced is
     *              working, just too old for what YouTube serves today
     * @return the absolute path of the installed binary, or {@code null} on failure
     */
    @Nullable
    static String downloadYtDlp(Path managedDir, boolean force) {
        Path target = managedDir.resolve(MediaBinaries.Tool.YT_DLP.exeName());
        if (!force && BinaryLocator.isUsable(MediaBinaries.Tool.YT_DLP, target.toString(), false)) {
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
            if (!looksExecutable(tmp)) {
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
        // An existing pair is only good enough if it also passes the version gate;
        // otherwise an outdated managed ffmpeg would be kept forever.
        if (BinaryLocator.isUsable(MediaBinaries.Tool.FFMPEG, ffmpeg.toString(), false)
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
            // The pinned release first, master only if it is no longer published.
            for (String source : ffmpegArchiveUrls()) {
                Path archive = Files.createTempFile(managedDir, "ffmpeg", archiveSuffix(source));
                LiasMediaPlayer.LOGGER.info("Downloading ffmpeg from {} ...", source);
                try {
                    if (!download(source, archive, 1_000_000)) {
                        continue;
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
            }
            return false;
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

    /**
     * Extracts a {@code .zip} or {@code .tar.xz} archive into {@code destDir}.
     */
    private static void extract(Path archive, Path destDir) throws IOException, InterruptedException {
        String name = archive.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".zip")) {
            extractZip(archive, destDir);
        } else {
            extractTar(archive, destDir);
        }
    }

    /**
     * Unpacks a zip using only the JDK (no third-party archive library).
     */
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

    /**
     * Downloads a single-binary zip (macOS) and writes the contained file to {@code target}.
     */
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

    /**
     * Moves the first file named {@code exeName} found under {@code root} to {@code target}.
     */
    private static boolean placeFromTree(Path root, String exeName, Path target) throws IOException {
        Path found = findFile(root, exeName);
        if (found == null) {
            LiasMediaPlayer.LOGGER.warn("'{}' not found inside the downloaded archive", exeName);
            return false;
        }
        if (!looksExecutable(found)) {
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

    /**
     * Resolves {@code entryName} under {@code destDir}, rejecting zip-slip escapes.
     */
    private static Path safeResolve(Path destDir, String entryName) throws IOException {
        Path resolved = destDir.resolve(entryName).normalize();
        if (!resolved.startsWith(destDir.normalize())) {
            throw new IOException("Refusing to extract entry outside target dir: " + entryName);
        }
        return resolved;
    }

    private static void deleteRecursively(Path dir) {
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
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

    /**
     * Whether {@code file} starts with the magic bytes of a program this platform could
     * actually run.
     *
     * <p>The last thing checked before a downloaded file is marked executable, and the
     * cheapest useful one. It does not make an unknown binary safe — nothing here can,
     * see the trust model above — but it does catch the failure that is plausible
     * without an attacker: a publisher moving an asset, a captive-portal or proxy error
     * page served with a 200, an archive whose layout changed and left us pointing at a
     * README. Every one of those ends today with the mod chmod +x'ing a text file and
     * reporting a confusing {@code Exec format error} at the first playback instead.</p>
     *
     * <p>Only the current platform's format is accepted: a Linux client has no use for a
     * PE, and taking one would mean the platform-specific download URL had gone wrong.</p>
     */
    private static boolean looksExecutable(Path file) {
        byte[] head = new byte[4];
        try (InputStream in = Files.newInputStream(file)) {
            if (in.readNBytes(head, 0, head.length) < head.length) {
                LiasMediaPlayer.LOGGER.warn("Downloaded {} is too short to be a program", file.getFileName());
                return false;
            }
        } catch (IOException e) {
            LiasMediaPlayer.LOGGER.warn("Could not read back {}: {}", file.getFileName(), e.toString());
            return false;
        }
        if (!hasExecutableMagic(head, MediaBinaries.WINDOWS, MediaBinaries.MAC)) {
            LiasMediaPlayer.LOGGER.warn("Downloaded {} is not a {} executable; discarding.",
                    file.getFileName(), MediaBinaries.WINDOWS ? "Windows" : MediaBinaries.MAC ? "macOS" : "Linux");
            return false;
        }
        return true;
    }

    /**
     * The magic-number half of {@link #looksExecutable}, kept apart from the file I/O so
     * the table of formats can be unit-tested.
     *
     * <ul>
     *   <li>Windows: {@code MZ}, the DOS header every PE still carries.</li>
     *   <li>Linux: {@code 0x7F E L F}.</li>
     *   <li>macOS: Mach-O in either byte order ({@code 0xFEEDFACE} / {@code 0xFEEDFACF}
     *       and their reversed forms) or a universal binary ({@code 0xCAFEBABE}), which
     *       is what evermeet.cx actually ships.</li>
     * </ul>
     *
     * <p>The platform is a parameter rather than read from {@link MediaBinaries}: those
     * are constants of the machine the test happens to run on, and a check whose whole
     * content is a per-platform table is worth exercising for the two platforms that
     * machine is not.</p>
     */
    static boolean hasExecutableMagic(byte[] head, boolean windows, boolean mac) {
        if (head.length < 4) {
            return false;
        }
        int b0 = head[0] & 0xFF;
        int b1 = head[1] & 0xFF;
        int b2 = head[2] & 0xFF;
        int b3 = head[3] & 0xFF;
        if (windows) {
            return b0 == 'M' && b1 == 'Z';
        }
        if (mac) {
            long word = ((long) b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
            return word == 0xFEEDFACEL || word == 0xFEEDFACFL
                    || word == 0xCEFAEDFEL || word == 0xCFFAEDFEL
                    || word == 0xCAFEBABEL || word == 0xBEBAFECAL;
        }
        return b0 == 0x7F && b1 == 'E' && b2 == 'L' && b3 == 'F';
    }

    /**
     * Marks a file owner/group/other-executable on POSIX systems; a no-op on Windows.
     */
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

    /**
     * The official single-file yt-dlp release asset for the current OS/arch.
     */
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

    /**
     * The BtbN ffmpeg build archives (Windows zip / Linux tar.xz) for this platform, in
     * the order they should be tried: the pinned release branch, then {@code master}.
     *
     * @see #FFMPEG_RELEASE
     */
    private static @NotNull List<String> ffmpegArchiveUrls() {
        return List.of(
                ffmpegArchiveUrl(FFMPEG_RELEASE),
                ffmpegArchiveUrl(FFMPEG_FALLBACK_RELEASE));
    }

    /**
     * One BtbN asset URL for {@code release} on this platform.
     *
     * <p>The asset names of a release branch repeat its version at the end
     * ({@code ffmpeg-n9.0-latest-linux64-gpl-9.0.tar.xz}); {@code master}'s do not
     * ({@code ffmpeg-master-latest-linux64-gpl.tar.xz}). The {@code -latest} in the
     * middle is BtbN's own, and means "the newest build of this branch" — the release
     * tag it hangs under is stable, which is why the URL keeps working.</p>
     */
    private static @NotNull String ffmpegArchiveUrl(String release) {
        String base = "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/";
        // n9.0 -> "-9.0"; master -> "".
        String versionSuffix = release.startsWith("n") ? "-" + release.substring(1) : "";
        String platform = MediaBinaries.WINDOWS ? "win64"
                : MediaBinaries.AARCH64 ? "linuxarm64" : "linux64";
        String extension = MediaBinaries.WINDOWS ? ".zip" : ".tar.xz";
        return base + "ffmpeg-" + release + "-latest-" + platform + "-gpl" + versionSuffix + extension;
    }

    /**
     * evermeet.cx single-binary zip for macOS (Intel build; runs under Rosetta on ARM).
     */
    private static @NotNull String macDownloadUrl(MediaBinaries.Tool tool) {
        return "https://evermeet.cx/ffmpeg/getrelease/" + tool.base + "/zip";
    }

    private static String archiveSuffix(String url) {
        return url.toLowerCase(Locale.ROOT).endsWith(".zip") ? ".zip" : ".tar.xz";
    }
}
