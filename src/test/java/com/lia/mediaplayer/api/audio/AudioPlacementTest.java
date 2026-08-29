package com.lia.mediaplayer.api.audio;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a sound is allowed to be. Unlike {@code AudioOptions}, the factories here
 * <em>do</em> throw: a radius of zero is not a quieter sound, it is a placement that can
 * never be heard, and an addon is better told at the call site than left wondering why
 * its speaker is silent.
 *
 * <p>Only the two cases that need no world are exercised — a {@code Following} placement
 * would need a live entity, which is what {@code AudioGain} handles and a unit test
 * cannot build.</p>
 */
class AudioPlacementTest {

    @Test
    void screenIsNotPositionalAndHasNoReach() {
        AudioPlacement placement = AudioPlacement.screen();

        assertFalse(placement.isPositional());
        assertEquals(0.0, placement.radius());
        assertNull(placement.position());
        assertSame(AudioPlacement.screen(), AudioPlacement.screen(), "a constant, not a new object");
    }

    @Test
    void aWorldPlacementKeepsWhatItWasGiven() {
        Vec3 speaker = new Vec3(10.5, 65.0, -3.25);
        AudioPlacement placement = AudioPlacement.world(speaker, 24.0);

        assertTrue(placement.isPositional());
        assertEquals(24.0, placement.radius());
        assertEquals(speaker, placement.position());
    }

    @Test
    void aPlacementWithNoReachIsRefused() {
        Vec3 speaker = new Vec3(0, 0, 0);

        assertThrows(IllegalArgumentException.class, () -> AudioPlacement.world(speaker, 0.0));
        assertThrows(IllegalArgumentException.class, () -> AudioPlacement.world(speaker, -1.0));
        assertThrows(IllegalArgumentException.class,
                () -> AudioPlacement.world(speaker, Double.POSITIVE_INFINITY));
    }

    @Test
    void aPlacementWithNowhereToBeIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> AudioPlacement.world(null, 8.0));
        assertThrows(IllegalArgumentException.class, () -> AudioPlacement.entity(null, 8.0));
    }
}
