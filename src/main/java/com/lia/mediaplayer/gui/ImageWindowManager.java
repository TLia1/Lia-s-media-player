package com.lia.mediaplayer.gui;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of the pinned {@link ImageWindow}s, keyed by source URL.
 *
 * <p>Unlike {@link VideoPlayerManager}, the windows here own no native resources
 * of their own — the textures live in {@link com.lia.mediaplayer.image.ImagePreviewCache} — so closing a
 * window is just a map removal.</p>
 *
 * <p>All methods run on the render/main thread (the only place GUI events fire),
 * so no synchronization is needed.</p>
 */
public final class ImageWindowManager {
    /** Hard cap on simultaneous pinned images; the oldest is dropped past this. */
    private static final int MAX_WINDOWS = 6;

    private static final LinkedHashMap<String, ImageWindow> WINDOWS = new LinkedHashMap<>();

    private ImageWindowManager() {
    }

    /** Ensures a (visible) pinned window exists for the URL. */
    static ImageWindow show(String url) {
        ImageWindow window = WINDOWS.get(url);
        if (window == null) {
            evictIfFull();
            window = new ImageWindow(url);
            WINDOWS.put(url, window);
        }
        window.setVisible(true);
        return window;
    }

    @Nullable
    static ImageWindow get(String url) {
        return WINDOWS.get(url);
    }

    /** A stable snapshot for iterating during render / input handling. */
    static List<ImageWindow> windows() {
        return new ArrayList<>(WINDOWS.values());
    }

    static boolean isEmpty() {
        return WINDOWS.isEmpty();
    }

    static void close(ImageWindow window) {
        WINDOWS.values().remove(window);
    }

    public static void disposeAll() {
        WINDOWS.clear();
    }

    /** Public entry point for the API: show/pin an image URL. */
    public static void showPublic(String url) {
        show(url);
    }

    private static void evictIfFull() {
        while (WINDOWS.size() >= MAX_WINDOWS) {
            Iterator<Map.Entry<String, ImageWindow>> it = WINDOWS.entrySet().iterator();
            if (!it.hasNext()) {
                return;
            }
            it.next();
            it.remove();
        }
    }
}
