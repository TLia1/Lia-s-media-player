package com.lia.mediaplayer.storage;

import com.lia.mediaplayer.LiasMediaPlayer;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Supplier;

/**
 * One file under {@code <gamedir>/liasmediaplayer/}, read and written as text.
 *
 * <p>The four stores the mod persists — the config, the playlists, the history and the
 * window arrangement — all had the same twenty lines of file handling copied into them:
 * resolve the game directory defensively, {@code createDirectories}, write to a
 * {@code .tmp} sibling, {@code ATOMIC_MOVE} it over the real file, and swallow whatever
 * went wrong into a warning. This is that, once. Each store keeps its own
 * serialization — deliberately, since the Gson shipped with Minecraft ranges from 2.8 to
 * 2.14 across the fourteen targets and every store hand-rolls its JSON to stay inside
 * that range — and only the I/O is shared.</p>
 *
 * <p><b>Best-effort by design.</b> Nothing here throws. A media player that refused to
 * start because a preferences file is unreadable would be a worse failure than
 * forgetting where the window was, so a broken read reports {@code null} and a broken
 * write reports nothing at all; both leave a line in the log.</p>
 *
 * <p>The temp file and the real file are siblings on purpose: {@code ATOMIC_MOVE} is only
 * guaranteed within one filesystem, and the system temp directory frequently is not
 * one.</p>
 */
public final class JsonFileStore {

    /** The mod's own folder inside the game directory. */
    public static final String DIRECTORY = "liasmediaplayer";

    /**
     * Resolved lazily, not in the constructor: the stores are built by
     * {@code MediaPlayerContext}, which on Fabric runs early enough that
     * {@code Minecraft.getInstance()} is still null.
     */
    private final Supplier<Path> directory;
    private final String fileName;

    public JsonFileStore(String fileName) {
        this(JsonFileStore::gameDirectory, fileName);
    }

    /**
     * A store rooted anywhere — the seam the unit tests use to drive the atomic write
     * against a temp directory instead of a game directory that does not exist.
     */
    JsonFileStore(Supplier<Path> directory, String fileName) {
        this.directory = directory;
        this.fileName = fileName;
    }

    /**
     * Where this store's file lives, or {@code null} if there is no game directory to
     * put it in (which is every unit test, and any point before the client is up).
     */
    @Nullable
    public Path path() {
        Path dir = directory.get();
        return dir == null ? null : dir.resolve(fileName);
    }

    /**
     * The file's contents, or {@code null} if it does not exist yet or could not be
     * read. Callers treat both the same way: keep whatever defaults they started with.
     */
    @Nullable
    public String read() {
        Path path = path();
        if (path == null || !Files.isRegularFile(path)) {
            return null;
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            LiasMediaPlayer.LOGGER.warn("Could not read {}: {}", path, e.toString());
            return null;
        }
    }

    /**
     * Replaces the file's contents, atomically as far as the filesystem allows.
     *
     * <p>Written to a sibling {@code .tmp} and moved over the target, so a crash
     * mid-write leaves the previous version intact rather than a half-written file the
     * next launch cannot parse.</p>
     */
    public void write(String content) {
        Path path = path();
        if (path == null) {
            return;
        }
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(fileName + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                writer.write(content);
            }
            Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            LiasMediaPlayer.LOGGER.warn("Could not write {}: {}", path, e.toString());
        }
    }

    /**
     * {@code <gamedir>/liasmediaplayer}, or {@code null} when there is no client — the
     * {@code catch} is what makes every store usable from a plain JUnit test.
     */
    @Nullable
    private static Path gameDirectory() {
        try {
            return Minecraft.getInstance().gameDirectory.toPath().resolve(DIRECTORY);
        } catch (Exception e) {
            return null;
        }
    }
}
