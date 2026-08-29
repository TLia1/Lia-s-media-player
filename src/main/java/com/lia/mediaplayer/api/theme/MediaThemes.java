/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.theme;

import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Where addon palettes are registered, and the list the settings screen cycles through.
 *
 * <p>A static registry rather than something on {@code IMediaPlayerAPI}, for the reason
 * every other extension point here is: {@code api} knows about no mod loader, and a
 * theme has to be registrable from an addon's entry point, which runs before this mod
 * has finished initializing.</p>
 *
 * <p>Registering the same id twice replaces the first — that is what makes a theme
 * re-registrable from a reload without piling up duplicates. The mod's own three
 * palettes ({@code dark}, {@code contrast}, {@code minecraft}) are not in here; they are
 * built in, and {@link MediaTheme#builder} refuses those ids.</p>
 *
 * <p>Thread-safe.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 3.2.0
 */
public final class MediaThemes {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Guarded by itself; read from the render thread, written from mod construction. */
    private static final List<MediaTheme> THEMES = new ArrayList<>();

    private MediaThemes() {
    }

    /**
     * Adds {@code theme} to the palettes the user can choose. Safe before the mod is
     * initialized, and safe to call again with the same id to replace one.
     */
    public static void register(MediaTheme theme) {
        if (theme == null) {
            return;
        }
        synchronized (THEMES) {
            THEMES.removeIf(existing -> existing.id().equals(theme.id()));
            THEMES.add(theme);
        }
        LOGGER.info("Registered media player theme {}", theme.id());
    }

    /** Every registered theme, in registration order. A snapshot. */
    public static List<MediaTheme> all() {
        synchronized (THEMES) {
            return Collections.unmodifiableList(new ArrayList<>(THEMES));
        }
    }

    /** One theme by id, or {@code null} — which is what a config file naming a removed addon gets. */
    @Nullable
    public static MediaTheme byId(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String wanted = id.strip().toLowerCase(Locale.ROOT);
        synchronized (THEMES) {
            for (MediaTheme theme : THEMES) {
                if (theme.id().equals(wanted)) {
                    return theme;
                }
            }
        }
        return null;
    }
}
