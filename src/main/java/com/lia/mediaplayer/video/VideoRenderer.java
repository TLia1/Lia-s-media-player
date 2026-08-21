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
