package com.lia.mediaplayer.video;

import com.lia.mediaplayer.LiasMediaPlayer;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import org.lwjgl.system.MemoryUtil;

public class VideoRenderer {
    private static final AtomicInteger TEXTURE_ID = new AtomicInteger(0);
    private static final Field NATIVE_IMAGE_PIXELS_FIELD;

    static {
        try {
            NATIVE_IMAGE_PIXELS_FIELD = NativeImage.class.getDeclaredField("pixels");
            NATIVE_IMAGE_PIXELS_FIELD.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize reflection for VideoRenderer", e);
        }
    }

    @Nullable
    private ResourceLocation textureLocation;
    @Nullable
    private DynamicTexture texture;
    @Nullable
    private NativeImage nativeImage;
    @Nullable
    private VideoFrame currentFrame;

    @Nullable
    public ResourceLocation getTextureLocation() {
        return textureLocation;
    }

    @Nullable
    public VideoFrame getCurrentFrame() {
        return currentFrame;
    }

    public void releaseTexture() {
        if (textureLocation != null) {
            Minecraft.getInstance().getTextureManager().release(textureLocation);
            textureLocation = null;
        }
        if (texture != null) {
            texture.close();
            texture = null;
        }
        nativeImage = null;
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
        return textureLocation;
    }

    private void uploadFrame(VideoFrame frame) {
        Minecraft mc = Minecraft.getInstance();
        if (nativeImage == null || nativeImage.getWidth() != frame.width() || nativeImage.getHeight() != frame.height()) {
            releaseTexture();
            nativeImage = new NativeImage(NativeImage.Format.RGBA, frame.width(), frame.height(), false);
            texture = new DynamicTexture(nativeImage);
            textureLocation = ResourceLocation.fromNamespaceAndPath(
                    LiasMediaPlayer.MODID, "video/" + TEXTURE_ID.getAndIncrement());
            mc.getTextureManager().register(textureLocation, texture);
        }

        ByteBuffer buffer = frame.rgbaBuffer();
        long bufferPtr = MemoryUtil.memAddress(buffer);
        long pixelsPtr;
        try {
            pixelsPtr = NATIVE_IMAGE_PIXELS_FIELD.getLong(nativeImage);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to access NativeImage pixels", e);
        }
        MemoryUtil.memCopy(bufferPtr, pixelsPtr, buffer.capacity());

        if (texture != null) {
            texture.upload();
        }
    }
}
