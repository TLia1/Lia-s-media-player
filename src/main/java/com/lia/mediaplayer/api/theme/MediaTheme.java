/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.theme;

import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * A palette an addon supplies, as an id, a name to show in the settings, and the
 * {@link ThemeRole}s it overrides.
 *
 * <pre>{@code
 * MediaThemes.register(MediaTheme.builder("myaddon:sunset",
 *                 Component.translatable("theme.myaddon.sunset"))
 *         .set(ThemeRole.WINDOW_BG, 0xD0221018)
 *         .set(ThemeRole.FILL, 0xFFFF8A50)
 *         .set(ThemeRole.BORDER_FOCUSED, 0xFFFF8A50)
 *         .build());
 * }</pre>
 *
 * <p><b>Partial by design.</b> The mod lays its own dark palette down first and then
 * writes whatever a theme names over it, exactly as the two built-in alternatives are
 * written. So a theme that only wants a different accent sets two roles, and a theme
 * written a year ago still colours every part of a UI that has grown since.</p>
 *
 * <p>Colours are {@code 0xAARRGGBB}. Alpha matters: the window and panel backgrounds are
 * deliberately translucent, and a theme that sets them opaque will look like a different
 * mod.</p>
 *
 * <p>Immutable and thread-safe. Register once, from your entry point; it may be done
 * before Lia's Media Player has finished initializing.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 3.2.0
 */
public final class MediaTheme {

    private final String id;
    private final Component name;
    private final Map<ThemeRole, Integer> colors;

    private MediaTheme(String id, Component name, Map<ThemeRole, Integer> colors) {
        this.id = id;
        this.name = name;
        this.colors = colors;
    }

    /**
     * Starts a theme.
     *
     * @param id   a stable, namespaced id ({@code "myaddon:sunset"}). It is what the
     *             user's {@code config.json} stores, so changing it later loses their
     *             choice. Must contain a {@code :} and no whitespace; the mod's own
     *             palettes are the unnamespaced {@code dark}, {@code contrast} and
     *             {@code minecraft}, which an addon may not claim.
     * @param name what the theme is called in the settings — translated, from your own
     *             lang files
     * @throws IllegalArgumentException if the id is blank, unnamespaced, whitespaced, or
     *                                  one of the built-in names
     */
    public static Builder builder(String id, Component name) {
        return new Builder(validateId(id), name);
    }

    private static String validateId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("A theme id must not be blank");
        }
        String trimmed = id.strip().toLowerCase(Locale.ROOT);
        if (trimmed.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("A theme id must not contain whitespace: " + id);
        }
        if (!trimmed.contains(":")) {
            throw new IllegalArgumentException(
                    "A theme id must be namespaced with your mod id, e.g. \"myaddon:sunset\": " + id);
        }
        return trimmed;
    }

    /** The stable id, lower-cased — what {@code config.json} stores. */
    public String id() {
        return id;
    }

    /** The name shown in the settings. */
    public Component name() {
        return name;
    }

    /** The roles this theme overrides, and nothing else. Unmodifiable. */
    public Map<ThemeRole, Integer> colors() {
        return colors;
    }

    @Override
    public String toString() {
        return "MediaTheme[" + id + ", " + colors.size() + " role(s)]";
    }

    /** Collects the roles a theme overrides. Not thread-safe; build it on one thread. */
    public static final class Builder {

        private final String id;
        private final Component name;
        private final Map<ThemeRole, Integer> colors = new EnumMap<>(ThemeRole.class);

        private Builder(String id, Component name) {
            this.id = id;
            this.name = name;
        }

        /**
         * Overrides one role.
         *
         * @param argb {@code 0xAARRGGBB}
         */
        public Builder set(ThemeRole role, int argb) {
            if (role != null) {
                colors.put(role, argb);
            }
            return this;
        }

        /** Overrides several roles at once; {@code null} entries are ignored. */
        public Builder setAll(Map<ThemeRole, Integer> values) {
            if (values != null) {
                values.forEach((role, argb) -> {
                    if (role != null && argb != null) {
                        colors.put(role, argb);
                    }
                });
            }
            return this;
        }

        public MediaTheme build() {
            return new MediaTheme(id, name, Collections.unmodifiableMap(new EnumMap<>(colors)));
        }
    }
}
