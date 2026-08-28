package com.lia.mediaplayer.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The file handling every store shares.
 *
 * <p>All four stores are best-effort: they must come back with defaults rather than an
 * exception when the game directory is missing, the file is not there yet, or the write
 * cannot happen. That contract used to be repeated (and separately unverified) in each
 * of them; it is pinned here once.</p>
 */
class JsonFileStoreTest {

    private static JsonFileStore rootedAt(Path directory, String name) {
        return new JsonFileStore(() -> directory, name);
    }

    @Test
    void readsBackWhatItWrote(@TempDir Path dir) {
        JsonFileStore store = rootedAt(dir.resolve("liasmediaplayer"), "history.json");

        store.write("{\"hello\":\"world\"}");

        assertEquals("{\"hello\":\"world\"}", store.read());
    }

    @Test
    void createsTheModDirectoryOnFirstWrite(@TempDir Path dir) {
        Path modDir = dir.resolve("liasmediaplayer");
        rootedAt(modDir, "config.json").write("{}");

        assertTrue(Files.isRegularFile(modDir.resolve("config.json")));
    }

    @Test
    void readingAFileThatIsNotThereYetIsNull(@TempDir Path dir) {
        assertNull(rootedAt(dir, "playlists.json").read());
    }

    /**
     * No game directory means no client — every unit test, and anything that runs before
     * {@code Minecraft.getInstance()} exists. Neither call may throw there.
     */
    @Test
    void survivesHavingNoDirectoryAtAll() {
        JsonFileStore store = new JsonFileStore(() -> null, "windows.json");

        store.write("{}");

        assertNull(store.path());
        assertNull(store.read());
    }

    /**
     * The reason for the temp file: an interrupted write must not leave a half-written
     * file behind for the next launch to choke on. Nothing observes the intermediate
     * state, so the check is that the previous contents survive a failed write — here,
     * one made to fail by handing the store a name that is a directory.
     */
    @Test
    void aFailedWriteLeavesThePreviousContentsIntact(@TempDir Path dir) throws IOException {
        JsonFileStore store = rootedAt(dir, "history.json");
        store.write("[1]");

        // The .tmp sibling cannot be created as a file because a directory owns the name.
        Files.createDirectory(dir.resolve("history.json.tmp"));
        store.write("[2]");

        assertEquals("[1]", store.read());
    }

    @Test
    void doesNotLeaveTheTempFileBehindOnSuccess(@TempDir Path dir) throws IOException {
        JsonFileStore store = rootedAt(dir, "windows.json");

        store.write("{}");

        try (var entries = Files.list(dir)) {
            List<String> names = entries.map(p -> p.getFileName().toString()).toList();
            assertEquals(List.of("windows.json"), names);
        }
    }

    @Test
    void writesUtf8(@TempDir Path dir) throws IOException {
        JsonFileStore store = rootedAt(dir, "playlists.json");

        store.write("[{\"name\":\"Été 🎧\"}]");

        assertEquals("[{\"name\":\"Été 🎧\"}]",
                Files.readString(dir.resolve("playlists.json"), StandardCharsets.UTF_8));
    }
}
