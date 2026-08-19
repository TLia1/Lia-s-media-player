package com.lia.mediaplayer.image;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GifDecoderTest {

    /**
     * The canonical 43-byte 1x1 GIF89a. Its logical screen descriptor sits at bytes 6..9,
     * which is what the oversized-canvas case below rewrites.
     */
    private static byte[] tinyGif() {
        return Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7");
    }

    @Test
    void decode_HugeLogicalScreen_IsRejectedBeforeAllocating() {
        // A GIF declares its canvas in two bytes per axis, so ~40 bytes on the wire can ask
        // for a 65535x65535 ARGB canvas — 17 GB. Since the bytes come from a link anyone can
        // post in chat, this has to fail loudly rather than exhaust the heap.
        byte[] gif = tinyGif();
        gif[6] = (byte) 0xFF;
        gif[7] = (byte) 0xFF;
        gif[8] = (byte) 0xFF;
        gif[9] = (byte) 0xFF;

        IOException thrown = assertThrows(IOException.class, () -> GifDecoder.decode(gif));
        assertTrue(thrown.getMessage().contains("implausibly large"), thrown.getMessage());
    }
}
