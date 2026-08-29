package com.lia.mediaplayer.api.image;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The validation on what an addon's decoder hands back.
 *
 * <p>It is checked rather than trusted for one concrete reason: these arrays become the
 * length of a native write into a texture's pixel block, so a frame that is not
 * {@code width * height} long is an out-of-bounds write rather than a wrong picture. The
 * defensive copies matter for the same sort of reason — a decoder that keeps and later
 * mutates the array it handed over would be editing a picture mid-upload.
 */
class DecodedImageTest {

    private static int[] pixels(int n) {
        return new int[n];
    }

    @Test
    void acceptsAWellFormedAnimation() {
        DecodedImage image = new DecodedImage(2, 2,
                List.of(pixels(4), pixels(4)), new int[]{100, 100});
        assertEquals(2, image.frameCount());
        assertEquals(2, image.width());
        assertEquals(2, image.height());
    }

    @Test
    void stillBuildsASingleFrame() {
        DecodedImage image = DecodedImage.still(3, 2, pixels(6));
        assertEquals(1, image.frameCount());
        assertEquals(0, image.delaysMs()[0]);
    }

    @Test
    void rejectsANonPositiveSize() {
        assertThrows(IllegalArgumentException.class,
                () -> new DecodedImage(0, 2, List.of(pixels(0)), new int[]{0}));
        assertThrows(IllegalArgumentException.class,
                () -> new DecodedImage(2, -1, List.of(pixels(0)), new int[]{0}));
    }

    @Test
    void rejectsAnEmptyOrMissingFrameList() {
        assertThrows(IllegalArgumentException.class,
                () -> new DecodedImage(2, 2, List.of(), new int[0]));
        assertThrows(IllegalArgumentException.class,
                () -> new DecodedImage(2, 2, null, new int[0]));
    }

    @Test
    void rejectsADelayCountThatDisagreesWithTheFrameCount() {
        assertThrows(IllegalArgumentException.class,
                () -> new DecodedImage(2, 2, List.of(pixels(4), pixels(4)), new int[]{100}));
        assertThrows(IllegalArgumentException.class,
                () -> new DecodedImage(2, 2, List.of(pixels(4)), null));
    }

    @Test
    void rejectsAFrameThatIsNotTheStatedSize() {
        assertThrows(IllegalArgumentException.class,
                () -> new DecodedImage(2, 2, List.of(pixels(3)), new int[]{0}),
                "a short frame would be an out-of-bounds native write, not a wrong picture");
        assertThrows(IllegalArgumentException.class,
                () -> new DecodedImage(2, 2, List.of(pixels(4), pixels(5)), new int[]{0, 0}));
    }

    @Test
    void copiesTheDelaysSoTheDecoderCannotChangeThemLater() {
        int[] delays = {100};
        DecodedImage image = new DecodedImage(1, 1, List.of(pixels(1)), delays);
        delays[0] = 999;
        assertEquals(100, image.delaysMs()[0]);
        assertNotSame(delays, image.delaysMs());
    }
}
