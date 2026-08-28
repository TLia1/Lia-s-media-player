package com.lia.mediaplayer.tools;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Locates existing installations of the external tools the media player shells
 * out to ({@code yt-dlp}, {@code ffmpeg}, {@code ffprobe}). This class only
 * <em>finds</em> binaries — it never downloads anything.
 *
 * <p>For each tool it builds an ordered, de-duplicated list of candidate paths
 * by checking, in order:</p>
 * <ol>
 *   <li>an explicit override
 *       ({@code -Dliasmediaplayer.<tool>=...} or the matching {@code *_PATH}
 *       environment variable);</li>
 *   <li>a copy this mod previously downloaded into
 *       {@code <gamedir>/liasmediaplayer/bin/};</li>
 *   <li>every directory listed on {@code PATH};</li>
 *   <li>common per-OS install locations (winget, scoop, chocolatey, Homebrew,
 *       {@code /usr/local/bin}, ...);</li>
 * </ol>
 *
 * <p>If none of those yield a usable file, it tries the bare command name (trusting
 * the launcher's {@code PATH}) by asking the tool for its version.</p>
 *
 * @see BinaryDownloader
 * @see MediaBinaries
 */
final class BinaryLocator {

    private BinaryLocator() {
    }

    /**
     * Scans every known location for {@code tool} and returns the first usable
     * path, or {@code null} if nothing was found.
     *
     * @param tool       the tool to look for
     * @param managedDir the directory where managed (downloaded) copies live
     */
    @Nullable
    static String find(MediaBinaries.Tool tool, Path managedDir) {
        for (String candidate : candidatePaths(tool, managedDir)) {
            if (isUsable(tool, candidate, false)) {
                return candidate;
            }
        }

        // Trust the launcher's PATH with the bare command name.
        if (canRun(tool.exeName(), tool.versionFlag())) {
            if (isUsable(tool, tool.exeName(), true)) {
                return tool.exeName();
            }
        }

        return null;
    }

    /**
     * Returns the ordered, de-duplicated list of absolute paths that should be
     * probed for {@code tool}, without the bare-name fallback (which requires a
     * process spawn and is handled separately by {@link #find}).
     */
    static List<String> candidatePaths(MediaBinaries.Tool tool, Path managedDir) {
        List<String> out = new ArrayList<>();

        // 1. Explicit override (JVM property or environment variable).
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
        out.add(managedDir.resolve(tool.exeName()).toString());

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
        if (MediaBinaries.WINDOWS) {
            String localAppData = firstNonBlank(System.getenv("LOCALAPPDATA"), home + "\\AppData\\Local");
            String appData = firstNonBlank(System.getenv("APPDATA"), home + "\\AppData\\Roaming");
            out.add(localAppData + "\\Microsoft\\WinGet\\Links\\" + tool.exeName());
            out.add(home + "\\scoop\\shims\\" + tool.exeName());
            out.add("C:\\ProgramData\\chocolatey\\bin\\" + tool.exeName());
            if (tool == MediaBinaries.Tool.YT_DLP) {
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

    /**
     * Adds {@code <base>/<Python*>/Scripts/yt-dlp.exe} for each Python install found.
     */
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

    // ---- Small helpers -------------------------------------------------------

    /**
     * Returns {@code true} if the path points at an existing executable file.
     */
    static boolean isExecutableFile(String pathString) {
        try {
            File file = new File(pathString);
            return file.isFile() && file.canExecute();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Probes a bare command by asking for its version (fast and side-effect free).
     */
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

    /**
     * Checks if a binary is usable. For ffmpeg, ensures version >= 8.1.2.
     */
    static boolean isUsable(MediaBinaries.Tool tool, String pathString, boolean isBareCommand) {
        if (!isBareCommand && !isExecutableFile(pathString)) {
            return false;
        }
        if (tool == MediaBinaries.Tool.FFMPEG) {
            if (!isBareCommand) {
                Path parent = Path.of(pathString).getParent();
                if (parent != null) {
                    Path marker = parent.resolve(".ffmpeg-updated-8.1.2");
                    if (Files.exists(marker)) {
                        return true;
                    }
                }
            }
            return checkFfmpegVersion(pathString);
        }
        return true;
    }

    private static boolean checkFfmpegVersion(String executable) {
        try {
            Process process = new ProcessBuilder(executable, "-version")
                    .redirectErrorStream(true)
                    .start();
            String output;
            try (InputStream in = process.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (!process.waitFor(8, TimeUnit.SECONDS) || process.exitValue() != 0) {
                return false;
            }
            Matcher m = Pattern.compile("version\\s+(?:n)?(\\d+)\\.(\\d+)(?:\\.(\\d+))?").matcher(output);
            if (m.find()) {
                int major = Integer.parseInt(m.group(1));
                int minor = Integer.parseInt(m.group(2));
                int patch = m.group(3) != null ? Integer.parseInt(m.group(3)) : 0;
                if (major > 8) return true;
                if (major == 8 && minor > 1) return true;
                if (major == 8 && minor == 1 && patch >= 2) return true;
                return false;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
