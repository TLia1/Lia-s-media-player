package com.lia.mediaplayer.video;

import java.nio.ByteBuffer;

/** A single decoded, display-ready frame backed by off-heap memory. */
public record VideoFrame(long tsMicros, int width, int height, ByteBuffer rgbaBuffer) {
}
