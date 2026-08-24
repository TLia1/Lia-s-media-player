package com.lia.mediaplayer.gui;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The palette: {@link Theme#withAlpha}, the one piece of arithmetic in it, and the
 * theme switching around it.
 *
 * <p>{@code withAlpha} is tested because every fade in the mod goes through it, and
 * getting the channel layout wrong would tint colours rather than fade them — a mistake
 * that looks like a design choice on screen. The switching is tested because a theme
 * writes only what it changes: a role one theme forgot would otherwise be picked up from
 * whichever theme happened to be installed before it, which is a bug that only appears
 * after switching twice.</p>
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

    // ------------------------------------------------------------------
    // Theme switching
    // ------------------------------------------------------------------

    /** Every colour role in the palette, read by reflection so none can be forgotten. */
    private static Map<String, Integer> snapshot() {
        Map<String, Integer> colours = new LinkedHashMap<>();
        for (Field field : Theme.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == int.class) {
                try {
                    field.setAccessible(true);
                    colours.put(field.getName(), field.getInt(null));
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                }
            }
        }
        return colours;
    }

    @Test
    void everyThemeLeavesEveryRoleWithAColour() {
        for (ThemeName name : ThemeName.values()) {
            Theme.apply(name);
            snapshot().forEach((role, colour) ->
                    assertNotEquals(0, colour, name + " left " + role + " fully transparent"));
        }
        Theme.apply(ThemeName.DARK);
    }

    @Test
    void switchingAwayAndBackRestoresThePaletteExactly() {
        Theme.apply(ThemeName.DARK);
        Map<String, Integer> dark = snapshot();
        for (ThemeName name : ThemeName.values()) {
            Theme.apply(name);
        }
        Theme.apply(ThemeName.DARK);
        assertEquals(dark, snapshot());
    }

    @Test
    void eachThemeIsActuallyADifferentPalette() {
        Theme.apply(ThemeName.DARK);
        Map<String, Integer> dark = snapshot();
        for (ThemeName name : ThemeName.values()) {
            if (name == ThemeName.DARK) {
                continue;
            }
            Theme.apply(name);
            assertNotEquals(dark, snapshot(), name + " is the dark palette under another name");
        }
        Theme.apply(ThemeName.DARK);
    }

    @Test
    void applyingAThemeRecordsIt() {
        Theme.apply(ThemeName.CONTRAST);
        assertEquals(ThemeName.CONTRAST, Theme.active());
        Theme.apply(ThemeName.DARK);
        assertEquals(ThemeName.DARK, Theme.active());
    }
}
