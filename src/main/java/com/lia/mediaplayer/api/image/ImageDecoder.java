/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.image;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * A picture format the mod does not know.
 *
 * <p>The mod decodes GIF itself and leans on {@code ImageIO} and Minecraft's own PNG
 * reader for the rest, which between them cover what chat actually carries. WebP and
 * APNG are the two that keep coming up and neither is there. This is the way in:
 * register a decoder with
 * {@link com.lia.mediaplayer.api.LiasMediaPlayerApi#registerImageDecoder} and every
 * picture the mod loads — a chat preview, a pinned image, an image surface — is offered
 * to it first.</p>
 *
 * <p>Decoders are asked in registration order and the first that {@linkplain #supports
 * claims} the bytes wins. One that claims a picture and then fails does <b>not</b> fall
 * through to the built-ins: a decoder saying "this is mine" and then throwing is a bug in
 * the decoder, and quietly producing a different picture would hide it.</p>
 *
 * <h2>Threading and limits</h2>
 *
 * <p><b>Called on the IO pool</b>, never on the render thread. Do no GL work and touch
 * nothing of Minecraft's. The bytes handed in have already been size-capped by the
 * download; the mod caps the decoded pixel count afterwards, so a decoder does not have
 * to defend against a decompression bomb on its own — but it should still refuse an
 * implausible header rather than allocating from it.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 3.4.0
 */
public interface ImageDecoder {

    /** How many leading bytes {@link #supports} is guaranteed to be able to look at. */
    int HEADER_BYTES = 32;

    /**
     * Whether this decoder claims the picture these bytes start.
     *
     * <p>{@code header} holds the first {@value #HEADER_BYTES} bytes of the download, or
     * the whole thing if it is shorter — enough for any magic number worth having.
     * Look at the bytes; do not guess from a URL, which the mod does not pass for exactly
     * that reason.</p>
     */
    boolean supports(byte[] header);

    /**
     * Decodes the whole picture.
     *
     * @param data the complete downloaded bytes
     * @return the frames, or {@code null} to say "these were not mine after all", which
     *         falls back to the built-in decoders
     * @throws IOException if the data is this format and is broken. Reported to the user
     *                     as a failed image, and logged.
     */
    @Nullable
    DecodedImage decode(byte[] data) throws IOException;
}
