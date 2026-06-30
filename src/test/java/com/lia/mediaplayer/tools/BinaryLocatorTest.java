package com.lia.mediaplayer.tools;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;

class BinaryLocatorTest {

    @Test
    void isExecutableFile_NullOrEmpty_ReturnsFalse() {
        assertFalse(BinaryLocator.isExecutableFile(null));
        assertFalse(BinaryLocator.isExecutableFile(""));
        assertFalse(BinaryLocator.isExecutableFile("   "));
    }

    @Test
    void isExecutableFile_NonExistentFile_ReturnsFalse() {
        assertFalse(BinaryLocator.isExecutableFile("/this/path/does/not/exist/executable.exe"));
    }

    @Test
    void isExecutableFile_Directory_ReturnsFalse() throws IOException {
        File tempDir = File.createTempFile("testdir", "");
        tempDir.delete();
        tempDir.mkdir();
        tempDir.deleteOnExit();

        assertFalse(BinaryLocator.isExecutableFile(tempDir.getAbsolutePath()));
    }

    // We cannot easily test isExecutableFile_ValidExecutable_ReturnsTrue without assuming a specific binary on the system or creating one,
    // but the negative tests provide basic coverage for the edge cases.
}
