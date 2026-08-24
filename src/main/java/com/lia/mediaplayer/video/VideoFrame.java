package com.lia.mediaplayer.video;

import java.nio.ByteBuffer;

/**
 * A single decoded, display-ready frame backed by off-heap memory.
 *
 * @param tsMicros when this frame is due, on the playback clock
 * @param gen      the ffmpeg session that produced it. A seek (and a resume, which
 *                 seeks) replaces the session, and the frames the previous one already
 *                 decoded are still sitting in the queue — at timestamps that are all
 *                 "due" relative to the new position. Without this stamp they get
 *                 flushed to the screen as a burst of the old scene the moment the seek
 *                 is asked for. It is what tells a frame apart from a frame that is
 *                 merely late.
 */
public record VideoFrame(long tsMicros, int gen, int width, int height, ByteBuffer rgbaBuffer) {
}
