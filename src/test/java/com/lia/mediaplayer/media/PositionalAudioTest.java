package com.lia.mediaplayer.media;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two sums behind 2.5D audio. Both are pure functions over plain numbers precisely so
 * they can be pinned here: a pan that comes out mirrored, or an attenuation that never
 * quite reaches silence, is the kind of mistake that is obvious in a game and invisible
 * in a diff.
 *
 * <p>The convention being fixed: Minecraft's yaw is {@code 0} looking towards {@code +Z}
 * and grows clockwise, so facing {@code +Z} the player's right hand points towards
 * {@code -X}. Every expectation below follows from that one sentence.</p>
 */
class PositionalAudioTest {

    private static final float EPSILON = 1e-4f;

    // ------------------------------------------------------------------
    // Attenuation
    // ------------------------------------------------------------------

    @Test
    void aSoundAtTheListenerIsAtFullVolume() {
        assertEquals(1.0f, PositionalAudio.attenuation(0.0, 16.0), EPSILON);
    }

    @Test
    void volumeFallsLinearlyToSilenceAtTheRadius() {
        assertEquals(0.75f, PositionalAudio.attenuation(4.0, 16.0), EPSILON);
        assertEquals(0.5f, PositionalAudio.attenuation(8.0, 16.0), EPSILON);
        assertEquals(0.0f, PositionalAudio.attenuation(16.0, 16.0), EPSILON);
    }

    @Test
    void pastTheRadiusThereIsNothingToHear() {
        assertEquals(0.0f, PositionalAudio.attenuation(1000.0, 16.0), EPSILON);
    }

    @Test
    void aRadiusOfNothingDoesNotAttenuateRatherThanDividingByZero() {
        assertEquals(1.0f, PositionalAudio.attenuation(50.0, 0.0), EPSILON,
                "no reach means no attenuation — what AudioPlacement.screen() asks for");
        assertEquals(1.0f, PositionalAudio.attenuation(50.0, -4.0), EPSILON);
    }

    // ------------------------------------------------------------------
    // Panning
    // ------------------------------------------------------------------

    @Test
    void aSoundStraightAheadIsCentred() {
        assertEquals(0.0f, PositionalAudio.pan(0.0, 10.0, 0f), EPSILON);
    }

    @Test
    void aSoundBehindIsAlsoCentred() {
        // The front/back ambiguity is inherent to stereo panning and is documented, not
        // a bug: this pins it so nobody "fixes" it into a mirrored pan.
        assertEquals(0.0f, PositionalAudio.pan(0.0, -10.0, 0f), EPSILON);
    }

    @Test
    void facingPositiveZTheRightEarIsTowardsNegativeX() {
        assertEquals(1.0f, PositionalAudio.pan(-10.0, 0.0, 0f), EPSILON);
        assertEquals(-1.0f, PositionalAudio.pan(10.0, 0.0, 0f), EPSILON);
    }

    @Test
    void theYawTurnsWithTheListener() {
        // Yaw 90 faces -X, so the right ear points at -Z.
        assertEquals(1.0f, PositionalAudio.pan(0.0, -10.0, 90f), EPSILON);
        assertEquals(-1.0f, PositionalAudio.pan(0.0, 10.0, 90f), EPSILON);
    }

    @Test
    void aSoundHalfwayRoundIsHalfPanned() {
        // 45 degrees off the nose is sin(45) of the way to one ear.
        assertEquals((float) (Math.sqrt(2) / 2), PositionalAudio.pan(-10.0, 10.0, 0f), 1e-3f);
    }

    @Test
    void panningCollapsesToTheCentreCloseUp() {
        float far = PositionalAudio.pan(-10.0, 0.0, 0f);
        float near = PositionalAudio.pan(-0.5, 0.0, 0f);
        assertEquals(1.0f, far, EPSILON);
        assertTrue(near > 0f && near < far,
                "standing on top of a source must not slam it into one ear");
    }

    @Test
    void aSoundInsideTheListenerIsCentredRatherThanUndefined() {
        assertEquals(0.0f, PositionalAudio.pan(0.0, 0.0, 37f), EPSILON);
    }
}
