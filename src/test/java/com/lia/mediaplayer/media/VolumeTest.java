package com.lia.mediaplayer.media;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VolumeTest {

    private Volume volume;

    @BeforeEach
    void setUp() {
        volume = new Volume();
        // Reset to default before each test
        volume.set(1.0f);
        // Force beforeMute to 1.0f
        volume.toggleMute();
        volume.toggleMute();
    }

    @Test
    void volume_IsClampedBetweenZeroAndOne() {
        volume.set(0.5f);
        assertEquals(0.5f, volume.level(), 0.001f);

        volume.set(1.5f);
        assertEquals(1.0f, volume.level(), 0.001f);

        volume.set(-0.5f);
        assertEquals(0.0f, volume.level(), 0.001f);
    }

    @Test
    void change_ModifiesVolumeWithClamping() {
        volume.set(0.5f);
        volume.change(0.2f);
        assertEquals(0.7f, volume.level(), 0.001f);

        volume.change(0.5f); // Should clamp to 1.0
        assertEquals(1.0f, volume.level(), 0.001f);

        volume.change(-1.5f); // Should clamp to 0.0
        assertEquals(0.0f, volume.level(), 0.001f);
    }

    @Test
    void isMuted_ReturnsTrueWhenBelowThreshold() {
        volume.set(1.0f);
        assertFalse(volume.isMuted());

        volume.set(0.00005f); // Below 0.0001f threshold
        assertTrue(volume.isMuted());

        volume.set(0.0f);
        assertTrue(volume.isMuted());
    }

    @Test
    void toggleMute_RestoresPreviousVolume() {
        volume.set(0.7f);

        // Mute
        volume.toggleMute();
        assertEquals(0.0f, volume.level(), 0.001f);
        assertTrue(volume.isMuted());

        // Unmute - should restore to 0.7f
        volume.toggleMute();
        assertEquals(0.7f, volume.level(), 0.001f);
        assertFalse(volume.isMuted());
    }

    @Test
    void toggleMute_WhenAlreadyMutedManually_RestoresToMax() {
        // If we manually set volume to 0 (without toggling), beforeMute is initially 1.0
        volume.set(0.0f);

        volume.toggleMute();
        assertEquals(1.0f, volume.level(), 0.001f);
    }
}
