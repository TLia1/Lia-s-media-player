package com.lia.mediaplayer.tools;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The last gate a downloaded binary passes before the mod marks it executable and later
 * runs it.
 *
 * <p>It cannot make an unknown program safe — the trust model in
 * {@link BinaryDownloader}'s javadoc says what it does rest on — but it is what catches
 * the failure that needs no attacker at all: a publisher moving an asset, a proxy or
 * captive portal answering a download with an HTML error page and a 200, an archive
 * whose layout changed and left the unpacker holding a README. Each of those otherwise
 * ends in {@code chmod +x} on a text file and an {@code Exec format error} at the first
 * playback, several minutes and one confusing bug report later.</p>
 */
class BinaryDownloaderTest {

    private static final boolean WINDOWS = true;
    private static final boolean MAC = true;
    private static final boolean NOT = false;

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }

    private static byte[] elf() {
        return bytes(0x7F, 'E', 'L', 'F', 0x02, 0x01);
    }

    private static byte[] pe() {
        return bytes('M', 'Z', 0x90, 0x00);
    }

    @Test
    void acceptsAnElfOnLinux() {
        assertTrue(BinaryDownloader.hasExecutableMagic(elf(), NOT, NOT));
    }

    @Test
    void acceptsAPeOnWindows() {
        assertTrue(BinaryDownloader.hasExecutableMagic(pe(), WINDOWS, NOT));
    }

    @Test
    void acceptsEveryMachOFlavourOnMac() {
        // Both byte orders of 32- and 64-bit Mach-O, plus the universal ("fat") binary,
        // which is what evermeet.cx actually ships.
        assertTrue(BinaryDownloader.hasExecutableMagic(bytes(0xFE, 0xED, 0xFA, 0xCE), NOT, MAC));
        assertTrue(BinaryDownloader.hasExecutableMagic(bytes(0xFE, 0xED, 0xFA, 0xCF), NOT, MAC));
        assertTrue(BinaryDownloader.hasExecutableMagic(bytes(0xCE, 0xFA, 0xED, 0xFE), NOT, MAC));
        assertTrue(BinaryDownloader.hasExecutableMagic(bytes(0xCF, 0xFA, 0xED, 0xFE), NOT, MAC));
        assertTrue(BinaryDownloader.hasExecutableMagic(bytes(0xCA, 0xFE, 0xBA, 0xBE), NOT, MAC));
        assertTrue(BinaryDownloader.hasExecutableMagic(bytes(0xBE, 0xBA, 0xFE, 0xCA), NOT, MAC));
    }

    @Test
    void rejectsAnErrorPageServedWithA200() {
        byte[] html = "<!DOCTYPE html><html><head><title>404".getBytes(StandardCharsets.UTF_8);
        assertFalse(BinaryDownloader.hasExecutableMagic(html, NOT, NOT));
        assertFalse(BinaryDownloader.hasExecutableMagic(html, WINDOWS, NOT));
        assertFalse(BinaryDownloader.hasExecutableMagic(html, NOT, MAC));
    }

    @Test
    void rejectsAReadmePickedOutOfAnArchive() {
        byte[] text = "# ffmpeg\n\nSee the license file.\n".getBytes(StandardCharsets.UTF_8);
        assertFalse(BinaryDownloader.hasExecutableMagic(text, NOT, NOT));
    }

    @Test
    void rejectsAnotherPlatformsExecutable() {
        // A wrong-platform binary means the download URL itself went wrong, so it is a
        // rejection rather than a curiosity: the file would never run here anyway.
        assertFalse(BinaryDownloader.hasExecutableMagic(pe(), NOT, NOT));
        assertFalse(BinaryDownloader.hasExecutableMagic(elf(), WINDOWS, NOT));
        assertFalse(BinaryDownloader.hasExecutableMagic(elf(), NOT, MAC));
    }

    @Test
    void rejectsAFileTooShortToHaveAMagicNumber() {
        assertFalse(BinaryDownloader.hasExecutableMagic(new byte[0], NOT, NOT));
        assertFalse(BinaryDownloader.hasExecutableMagic(bytes(0x7F, 'E', 'L'), NOT, NOT));
    }
}
