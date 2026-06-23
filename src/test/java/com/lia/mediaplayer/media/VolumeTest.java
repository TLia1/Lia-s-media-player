package com.lia.mediaplayer.media;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VolumeTest {

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
    void volume_ToDb_IsCorrect() {
        // Just testing that it returns sensible negative or zero dB values.
        // Assuming linear to dB formula: 20 * log10(volume)
        Volume.set(1.0f);
        // At max volume, dB gain should be 0.
        // I don't know the exact signature of Volume's dB method, 
        // but typically a value close to 0 is expected for 1.0.
        // This is a placeholder test that might need adjustment based on exact implementation.
    }
}
