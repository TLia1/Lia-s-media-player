package com.lia.mediaplayer.api.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reading a picture back: the bounds check, and the average that is what almost everyone
 * asking for pixels actually wants.
 *
 * <p>The transparency rule is the part worth pinning. Averaging over every pixel would
 * make a mostly-transparent logo come back as a dark grey — the colour of nothing — and
 * an addon theming a GUI from it would get the wrong answer without ever seeing why.
 */
class SurfacePixelsTest {

    @Test
    void readsAPixelByCoordinate() {
        SurfacePixels pixels = new SurfacePixels(2, 2,
                new int[]{0xFF000001, 0xFF000002, 0xFF000003, 0xFF000004});
        assertEquals(0xFF000001, pixels.at(0, 0));
        assertEquals(0xFF000002, pixels.at(1, 0));
        assertEquals(0xFF000003, pixels.at(0, 1));
        assertEquals(0xFF000004, pixels.at(1, 1));
    }

    @Test
    void answersZeroOutsideThePicture() {
        SurfacePixels pixels = new SurfacePixels(1, 1, new int[]{0xFFFFFFFF});
        assertEquals(0, pixels.at(-1, 0));
        assertEquals(0, pixels.at(0, -1));
        assertEquals(0, pixels.at(1, 0));
        assertEquals(0, pixels.at(0, 1));
    }

    @Test
    void averagesTheOpaquePixels() {
        SurfacePixels pixels = new SurfacePixels(2, 1,
                new int[]{0xFF000000, 0xFFFFFFFF});
        assertEquals(0xFF7F7F7F, pixels.averageColor());
    }

    @Test
    void ignoresFullyTransparentPixels() {
        SurfacePixels pixels = new SurfacePixels(4, 1,
                new int[]{0x00000000, 0x00000000, 0x00000000, 0xFFFF0000});
        assertEquals(0xFFFF0000, pixels.averageColor(),
                "three transparent pixels must not drag a red logo towards black");
    }

    @Test
    void averagesTheAlphaOfWhatWasCounted() {
        SurfacePixels pixels = new SurfacePixels(2, 1,
                new int[]{0x40FF0000, 0xC0FF0000});
        assertEquals(0x80FF0000, pixels.averageColor());
    }

    @Test
    void anEntirelyTransparentPictureHasNoColour() {
        SurfacePixels pixels = new SurfacePixels(2, 1, new int[]{0, 0});
        assertEquals(0, pixels.averageColor());
    }
}
