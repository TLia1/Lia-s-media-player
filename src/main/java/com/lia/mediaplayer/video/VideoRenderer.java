package com.lia.mediaplayer.video;

import com.lia.mediaplayer.LiasMediaPlayer;
import com.lia.mediaplayer.gui.TextureBridge;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

public class VideoRenderer {
    private static final AtomicInteger TEXTURE_ID = new AtomicInteger(0);

    @Nullable
    private TextureBridge.Frame texture;
    @Nullable
    private VideoFrame currentFrame;

    @Nullable
    public ResourceLocation getTextureLocation() {
        return texture == null ? null : texture.location();
    }

    @Nullable
    public VideoFrame getCurrentFrame() {
        return currentFrame;
    }

    public void releaseTexture() {
        if (texture != null) {
            texture.close();
            texture = null;
        }
        currentFrame = null;
    }

    @Nullable
    public ResourceLocation prepareFrame(long positionMicros, Queue<VideoFrame> frameQueue, Queue<ByteBuffer> freeBuffers) {
        VideoFrame chosen = null;
        VideoFrame head;
        while ((head = frameQueue.peek()) != null && head.tsMicros() <= positionMicros) {
            if (chosen != null) {
                freeBuffers.offer(chosen.rgbaBuffer());
            }
            chosen = frameQueue.poll();
        }
        if (currentFrame == null && chosen == null) {
            chosen = frameQueue.poll();
        }

        if (chosen != null && chosen != currentFrame) {
            if (currentFrame != null) {
                freeBuffers.offer(currentFrame.rgbaBuffer());
            }
            currentFrame = chosen;
            uploadFrame(chosen);
        }
        return getTextureLocation();
    }

    /**
     * Discards everything queued by session {@code barrierGen} or older and shows the
     * first frame from a newer one, if it has arrived yet.
     *
     * <p>This is the view during a seek. The ordinary {@link #prepareFrame} path cannot
     * serve it: every leftover frame of the previous session has a timestamp below the
     * position being sought to, so it reads as "due" and the whole backlog would be
     * played out in one frame. Here they are dropped unseen, and the picture on screen
     * stays put until the new session has something real to replace it with.</p>
     *
     * @return whether a frame from the new session was found and uploaded
     */
    public boolean showFirstFrameAfter(int barrierGen, Queue<VideoFrame> frameQueue, Queue<ByteBuffer> freeBuffers) {
        VideoFrame frame;
        while ((frame = frameQueue.poll()) != null) {
            if (frame.gen() <= barrierGen) {
                freeBuffers.offer(frame.rgbaBuffer()); // belongs to the session we left
                continue;
            }
            if (currentFrame != null) {
                freeBuffers.offer(currentFrame.rgbaBuffer());
            }
            currentFrame = frame;
            uploadFrame(frame);
            return true;
        }
        return false;
    }

    private void uploadFrame(VideoFrame frame) {
        if (texture == null || texture.width() != frame.width() || texture.height() != frame.height()) {
            releaseTexture();
            texture = TextureBridge.Frame.allocate(
                    ResourceLocation.fromNamespaceAndPath(
                            LiasMediaPlayer.MODID, "video/" + TEXTURE_ID.getAndIncrement()),
                    frame.width(), frame.height());
        }
        texture.upload(frame.rgbaBuffer());
    }
}
