package com.lia.mediaplayer.gui;

/**
 * The palettes {@link Theme} can wear, as chosen in the settings.
 *
 * <p>Public — like {@link WindowPosition} — because it is the value type of a config
 * option, and {@code config.ConfigStore} declares that option.</p>
 */
public enum ThemeName {
    /** The mod's own palette: near-black surfaces, a blue accent. */
    DARK,
    /** The same shapes at maximum separation: pure black, brighter text, hard edges. */
    CONTRAST,
    /** Vanilla's tones — the translucent menu grey, its greys and its greens. */
    MINECRAFT
}
