package com.lia.mediaplayer.media;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VolumeTest {

    @BeforeEach
    void setUp() {
        // Reset to default before each test
        Volume.set(1.0f);
        // Force beforeMute to 1.0f
        Volume.toggleMute();
        Volume.toggleMute();
    }

    @Test
    void volume_IsClampedBetweenZeroAndOne() {
        Volume.set(0.5f);
        assertEquals(0.5f, Volume.level(), 0.001f);

        Volume.set(1.5f);
        assertEquals(1.0f, Volume.level(), 0.001f);

        Volume.set(-0.5f);
        assertEquals(0.0f, Volume.level(), 0.001f);
    }

    @Test
    void change_ModifiesVolumeWithClamping() {
        Volume.set(0.5f);
        Volume.change(0.2f);
        assertEquals(0.7f, Volume.level(), 0.001f);

        Volume.change(0.5f); // Should clamp to 1.0
        assertEquals(1.0f, Volume.level(), 0.001f);

        Volume.change(-1.5f); // Should clamp to 0.0
        assertEquals(0.0f, Volume.level(), 0.001f);
    }

    @Test
    void isMuted_ReturnsTrueWhenBelowThreshold() {
        Volume.set(1.0f);
        assertFalse(Volume.isMuted());

        Volume.set(0.00005f); // Below 0.0001f threshold
        assertTrue(Volume.isMuted());

        Volume.set(0.0f);
        assertTrue(Volume.isMuted());
    }

    @Test
    void toggleMute_RestoresPreviousVolume() {
        Volume.set(0.7f);
        
        // Mute
        Volume.toggleMute();
        assertEquals(0.0f, Volume.level(), 0.001f);
        assertTrue(Volume.isMuted());

        // Unmute - should restore to 0.7f
        Volume.toggleMute();
        assertEquals(0.7f, Volume.level(), 0.001f);
        assertFalse(Volume.isMuted());
    }

    @Test
    void toggleMute_WhenAlreadyMutedManually_RestoresToMax() {
        // If we manually set volume to 0 (without toggling), beforeMute is initially 1.0
        Volume.set(0.0f);
        
        Volume.toggleMute();
        assertEquals(1.0f, Volume.level(), 0.001f);
    }
}
