package com.lia.mediaplayer.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link Theme#withAlpha}, the one piece of arithmetic in the palette. Every fade in
 * the mod goes through it, and getting the channel layout wrong would tint colours
 * rather than fade them — a mistake that looks like a design choice on screen.
 */
class ThemeTest {

    @Test
    void fullFactorLeavesTheColourUntouched() {
        assertEquals(0xD0101010, Theme.withAlpha(0xD0101010, 1.0));
    }

    @Test
    void zeroFactorClearsOnlyTheAlphaChannel() {
        assertEquals(0x004CA6FF, Theme.withAlpha(0xFF4CA6FF, 0.0));
    }

    @Test
    void theRgbChannelsAreNeverTouched() {
        int rgb = 0x4CA6FF;
        for (int i = 0; i <= 10; i++) {
            int faded = Theme.withAlpha(0xFF000000 | rgb, i / 10.0);
            assertEquals(rgb, faded & 0x00FFFFFF, "rgb changed at factor " + (i / 10.0));
        }
    }

    @Test
    void alphaScalesFromTheColourOwnOpacity() {
        // 0xD0 = 208; half of it is 104 = 0x68. A colour that starts translucent must
        // fade from *its* opacity, not from fully opaque.
        assertEquals(0x68, Theme.withAlpha(0xD0101010, 0.5) >>> 24);
    }

    @Test
    void factorsOutsideTheUnitRangeAreClamped() {
        assertEquals(0xFF, Theme.withAlpha(0xFF101010, 7.5) >>> 24);
        assertEquals(0x00, Theme.withAlpha(0xFF101010, -2.0) >>> 24);
    }
}
