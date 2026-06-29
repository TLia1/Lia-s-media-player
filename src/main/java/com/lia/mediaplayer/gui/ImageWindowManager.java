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
public class ImageWindowManager {
    /** Hard cap on simultaneous pinned images; the oldest is dropped past this. */
    private final LinkedHashMap<String, ImageWindow> windows = new LinkedHashMap<>();

    public ImageWindowManager() {
    }

    /** Ensures a (visible) pinned window exists for the URL. */
    public ImageWindow show(String url) {
        ImageWindow window = windows.get(url);
        if (window == null) {
            evictIfFull();
            window = new ImageWindow(url);
            windows.put(url, window);
        }
        window.setVisible(true);
        return window;
    }

    @Nullable
    public ImageWindow get(String url) {
        return windows.get(url);
    }

    /** A stable snapshot for iterating during render / input handling. */
    public List<ImageWindow> getWindows() {
        return new ArrayList<>(windows.values());
    }

    public boolean isEmpty() {
        return windows.isEmpty();
    }

    public void close(ImageWindow window) {
        windows.values().remove(window);
    }

    public void disposeAll() {
        windows.clear();
    }

    /** Public entry point for the API: show/pin an image URL. Returns the window ID. */
    public long showPublic(String url) {
        return show(url).getId();
    }

    private void evictIfFull() {
        while (windows.size() >= com.lia.mediaplayer.config.ConfigStore.MAX_PINNED_IMAGES.getValue()) {
            Iterator<Map.Entry<String, ImageWindow>> it = windows.entrySet().iterator();
            if (!it.hasNext()) {
                return;
            }
            it.next();
            it.remove();
        }
    }

    // ------------------------------------------------------------------
    // ID-based API methods
    // ------------------------------------------------------------------

    public ImageWindow getById(long id) {
        for (ImageWindow window : windows.values()) {
            if (window.getId() == id) {
                return window;
            }
        }
        return null;
    }

    public boolean exists(long id) {
        return getById(id) != null;
    }

    public void setVisible(long id, boolean visible) {
        ImageWindow window = getById(id);
        if (window != null) {
            window.setVisible(visible);
        }
    }

    public void closePublic(long id) {
        ImageWindow window = getById(id);
        if (window != null) {
            close(window);
        }
    }
}
