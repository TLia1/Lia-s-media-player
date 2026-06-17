package com.lia.mediaplayer;

import com.mojang.blaze3d.platform.NativeImage;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Node;

/**
 * Decodes an animated GIF into a sequence of fully-composited (coalesced) frames
 * ready to be uploaded as textures.
 *
 * <p>GIF stores each frame as a (potentially partial) patch relative to the
 * previous canvas, together with a disposal method describing how the canvas
 * should be reset before the next frame. Rendering only the raw patches would
 * produce garbage, so every frame is composited onto a persistent canvas here,
 * once, on the IO thread. At render time the client just blits the already
 * built frame matching the wall clock, with no per-frame decoding or texture
 * re-upload.</p>
 *
 * <p>To keep VRAM bounded, the total number of pixels kept across all frames is
 * capped; GIFs that would exceed the budget have frames dropped evenly (their
 * delays are folded into the kept frames so the timing stays correct).</p>
 */
final class GifDecoder {
    /** Hard cap on frames regardless of size, to bound texture handles. */
    private static final int MAX_FRAMES = 256;
    /** Total RGBA pixels kept across every frame (~96 MB of VRAM at 4 bytes). */
    private static final long MAX_TOTAL_PIXELS = 24_000_000L;
    /** GIFs with a 0 (or absent) delay are shown at this rate, like browsers. */
    private static final int DEFAULT_DELAY_MS = 100;
    /** Never animate faster than this; protects against 0/1-centisecond frames. */
    private static final int MIN_DELAY_MS = 20;

    private GifDecoder() {
    }

    /**
     * Holds the decoded frames and the per-frame display duration in millis.
     */
        record Result(NativeImage[] frames, int[] delaysMs) {
    }

    /**
     * Decodes the GIF bytes into coalesced frames. Runs on the IO pool; does not
     * touch GL. The caller owns the returned {@link NativeImage}s.
     */
    static Result decode(byte[] data) throws IOException {
        ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
        List<BufferedImage> composited = new ArrayList<>();
        List<Integer> delays = new ArrayList<>();

        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            reader.setInput(input, false);

            int frameCount = reader.getNumImages(true);
            if (frameCount <= 0) {
                throw new IOException("GIF has no frames");
            }

            int[] canvasSize = readLogicalScreenSize(reader);
            int canvasWidth = canvasSize[0];
            int canvasHeight = canvasSize[1];

            BufferedImage canvas = null;
            Graphics2D g = null;
            // State needed to apply the *previous* frame's disposal before drawing.
            int prevX = 0, prevY = 0, prevW = 0, prevH = 0, prevDisposal = 0;
            BufferedImage restorePoint = null;

            try {
                for (int i = 0; i < frameCount && composited.size() < MAX_FRAMES; i++) {
                    BufferedImage frame = reader.read(i);
                    FrameMeta meta = readFrameMeta(reader, i);

                    if (canvas == null) {
                        if (canvasWidth <= 0 || canvasHeight <= 0) {
                            canvasWidth = Math.max(frame.getWidth(), meta.x + frame.getWidth());
                            canvasHeight = Math.max(frame.getHeight(), meta.y + frame.getHeight());
                        }
                        canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
                        g = canvas.createGraphics();
                    } else {
                        // Apply the disposal of the frame we drew last time.
                        switch (prevDisposal) {
                            case 2 -> clearRect(g, prevX, prevY, prevW, prevH); // restore to background
                            case 3 -> { // restore to previous
                                if (restorePoint != null) {
                                    g.drawImage(restorePoint, prevX, prevY, null);
                                }
                            }
                            default -> { /* none / doNotDispose: keep canvas as-is */ }
                        }
                    }

                    // If this frame asks to be restored later, snapshot the area it covers.
                    if (meta.disposal == 3) {
                        restorePoint = canvas.getSubimage(
                                meta.x, meta.y, frame.getWidth(), frame.getHeight());
                        // getSubimage shares the buffer, so copy it before we overwrite.
                        BufferedImage copy = new BufferedImage(
                                frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_ARGB);
                        copy.createGraphics().drawImage(restorePoint, 0, 0, null);
                        restorePoint = copy;
                    } else {
                        restorePoint = null;
                    }

                    g.drawImage(frame, meta.x, meta.y, null);

                    BufferedImage snapshot = new BufferedImage(
                            canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
                    snapshot.createGraphics().drawImage(canvas, 0, 0, null);
                    composited.add(snapshot);
                    delays.add(normalizeDelay(meta.delayMs));

                    prevX = meta.x;
                    prevY = meta.y;
                    prevW = frame.getWidth();
                    prevH = frame.getHeight();
                    prevDisposal = meta.disposal;
                }
            } finally {
                if (g != null) {
                    g.dispose();
                }
            }
        } finally {
            reader.dispose();
        }

        return toResult(thinIfNeeded(composited, delays));
    }

    /** Drops frames evenly until the total pixel budget is met. */
    private static Frames thinIfNeeded(List<BufferedImage> frames, List<Integer> delays) {
        if (frames.isEmpty()) {
            return new Frames(frames, delays);
        }
        long pixelsPerFrame = (long) frames.getFirst().getWidth() * frames.getFirst().getHeight();
        if (pixelsPerFrame <= 0) {
            return new Frames(frames, delays);
        }
        long maxFrames = Math.max(1, MAX_TOTAL_PIXELS / pixelsPerFrame);
        if (frames.size() <= maxFrames) {
            return new Frames(frames, delays);
        }

        List<BufferedImage> keptFrames = new ArrayList<>();
        List<Integer> keptDelays = new ArrayList<>();
        double step = frames.size() / (double) maxFrames;
        int next = 0;
        int accumulated = 0;
        for (int i = 0; i < frames.size(); i++) {
            accumulated += delays.get(i);
            if (i >= Math.round(next * step) && keptFrames.size() < maxFrames) {
                keptFrames.add(frames.get(i));
                keptDelays.add(accumulated);
                accumulated = 0;
                next++;
            }
        }
        if (!keptDelays.isEmpty() && accumulated > 0) {
            // Fold any trailing remainder into the last kept frame.
            int last = keptDelays.size() - 1;
            keptDelays.set(last, keptDelays.get(last) + accumulated);
        }
        return new Frames(keptFrames, keptDelays);
    }

    private record Frames(List<BufferedImage> images, List<Integer> delays) {
    }

    private static Result toResult(Frames frames) {
        NativeImage[] images = new NativeImage[frames.images().size()];
        int[] delays = new int[frames.images().size()];
        for (int i = 0; i < images.length; i++) {
            images[i] = toNativeImage(frames.images().get(i));
            delays[i] = frames.delays().get(i);
        }
        return new Result(images, delays);
    }

    /** Converts an ARGB BufferedImage into Minecraft's ABGR NativeImage. */
    static NativeImage toNativeImage(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] argb = source.getRGB(0, 0, width, height, null, 0, width);
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = argb[y * width + x];
                int abgr = (pixel & 0xFF00FF00)
                        | ((pixel & 0x00FF0000) >>> 16)
                        | ((pixel & 0x000000FF) << 16);
                image.setPixelRGBA(x, y, abgr);
            }
        }
        return image;
    }

    private static void clearRect(Graphics2D g, int x, int y, int w, int h) {
        java.awt.Composite previous = g.getComposite();
        g.setComposite(java.awt.AlphaComposite.Clear);
        g.fillRect(x, y, w, h);
        g.setComposite(previous);
    }

    private static int normalizeDelay(int delayMs) {
        if (delayMs <= 0) {
            return DEFAULT_DELAY_MS;
        }
        return Math.max(MIN_DELAY_MS, delayMs);
    }

    private static int[] readLogicalScreenSize(ImageReader reader) {
        try {
            IIOMetadata streamMeta = reader.getStreamMetadata();
            if (streamMeta != null) {
                IIOMetadataNode root = (IIOMetadataNode)
                        streamMeta.getAsTree("javax_imageio_gif_stream_1.0");
                Node lsd = findChild(root, "LogicalScreenDescriptor");
                if (lsd != null) {
                    int w = intAttr(lsd, "logicalScreenWidth", -1);
                    int h = intAttr(lsd, "logicalScreenHeight", -1);
                    return new int[]{w, h};
                }
            }
        } catch (IOException ignored) {
            // Fall back to the first frame's size below.
        }
        return new int[]{-1, -1};
    }

    private static FrameMeta readFrameMeta(ImageReader reader, int index) {
        FrameMeta meta = new FrameMeta();
        try {
            IIOMetadata frameMeta = reader.getImageMetadata(index);
            if (frameMeta == null) {
                return meta;
            }
            IIOMetadataNode root = (IIOMetadataNode)
                    frameMeta.getAsTree("javax_imageio_gif_image_1.0");

            Node descriptor = findChild(root, "ImageDescriptor");
            if (descriptor != null) {
                meta.x = intAttr(descriptor, "imageLeftPosition", 0);
                meta.y = intAttr(descriptor, "imageTopPosition", 0);
            }

            Node gce = findChild(root, "GraphicControlExtension");
            if (gce != null) {
                meta.delayMs = intAttr(gce, "delayTime", 0) * 10; // centiseconds -> ms
                meta.disposal = disposalCode(stringAttr(gce, "disposalMethod"));
            }
        } catch (IOException ignored) {
            // Defaults are fine.
        }
        return meta;
    }

    private static int disposalCode(String method) {
        if (method == null) {
            return 0;
        }
        return switch (method) {
            case "restoreToBackgroundColor" -> 2;
            case "restoreToPrevious" -> 3;
            default -> 0; // none / doNotDispose / unspecified
        };
    }

    private static Node findChild(Node parent, String name) {
        if (parent == null) {
            return null;
        }
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (name.equalsIgnoreCase(child.getNodeName())) {
                return child;
            }
        }
        return null;
    }

    private static int intAttr(Node node, String name, int fallback) {
        String value = stringAttr(node, name);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String stringAttr(Node node, String name) {
        if (node == null || node.getAttributes() == null) {
            return null;
        }
        Node attr = node.getAttributes().getNamedItem(name);
        return attr == null ? null : attr.getNodeValue();
    }

    private static final class FrameMeta {
        int x = 0;
        int y = 0;
        int delayMs = 0;
        int disposal = 0;
    }
}
