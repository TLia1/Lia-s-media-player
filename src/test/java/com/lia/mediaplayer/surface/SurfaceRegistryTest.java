package com.lia.mediaplayer.surface;

import com.lia.mediaplayer.api.PlaybackState;
import com.lia.mediaplayer.api.render.MediaSurface;
import com.lia.mediaplayer.config.ConfigStore;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reference counting, the caps and the disposal — the three places a surface leak
 * would hide, driven with an entry that decodes nothing.
 *
 * <p>A leak here is not a slow one: every video surface is an ffmpeg process and a set of
 * GPU textures, and one that outlives its last holder stays for the session.</p>
 */
class SurfaceRegistryTest {

    private final SurfaceRegistry registry = new SurfaceRegistry();
    private final List<FakeEntry> built = new ArrayList<>();

    @AfterEach
    void restoreCaps() {
        // The options are static singletons shared by the whole process.
        ConfigStore.MAX_API_SURFACES.setValue(16);
        ConfigStore.MAX_API_VIDEO_SURFACES.setValue(3);
    }

    private MediaSurface acquire(String key) {
        return acquire(key, false);
    }

    private MediaSurface acquireVideo(String key) {
        return acquire(key, true);
    }

    private MediaSurface acquire(String key, boolean video) {
        return registry.acquire(key, k -> {
            FakeEntry entry = new FakeEntry(k, video);
            built.add(entry);
            return entry;
        });
    }

    @Test
    void twoCallersAskingForTheSameThingShareOneDecode() {
        MediaSurface first = acquire("a");
        MediaSurface second = acquire("a");

        assertEquals(1, built.size(), "one entry, two views of it");
        assertEquals(1, registry.size());
        assertNotSame(first, second);
        assertTrue(first.isReady());
        assertTrue(second.isReady());
    }

    @Test
    void theDecodeSurvivesUntilTheLastHolderLetsGo() {
        MediaSurface first = acquire("a");
        MediaSurface second = acquire("a");

        first.close();
        assertFalse(built.getFirst().disposed, "the second holder is still drawing it");
        assertEquals(1, registry.size());
        assertTrue(second.isReady());

        second.close();
        assertTrue(built.getFirst().disposed);
        assertEquals(0, registry.size());
    }

    @Test
    void closingTwiceDoesNotReleaseSomebodyElsesReference() {
        MediaSurface first = acquire("a");
        MediaSurface second = acquire("a");

        first.close();
        first.close();
        first.close();

        assertFalse(built.getFirst().disposed);
        assertTrue(second.isReady());
    }

    @Test
    void aClosedViewIsInertRatherThanFatal() {
        MediaSurface surface = acquire("a");
        surface.close();

        assertFalse(surface.isReady());
        assertEquals(PlaybackState.ENDED, surface.state());
        assertEquals(0, surface.sourceWidth());
        assertEquals(0f, surface.aspectRatio());
        surface.markWanted();
        assertTrue(surface.playback().isEmpty());
    }

    @Test
    void differentKeysAreDifferentDecodes() {
        acquire("a");
        acquire("b");
        assertEquals(2, built.size());
        assertEquals(2, registry.size());
    }

    @Test
    void theSurfaceCapRefusesRatherThanEvictingSomethingSomeoneIsDrawing() {
        ConfigStore.MAX_API_SURFACES.setValue(2);
        MediaSurface first = acquire("a");
        acquire("b");

        MediaSurface refused = acquire("c");

        assertEquals(2, registry.size());
        assertFalse(refused.isReady());
        assertEquals(PlaybackState.FAILED, refused.state());
        assertTrue(first.isReady(), "the one already on screen must not have been evicted for it");
        assertFalse(built.getFirst().disposed);
    }

    @Test
    void aFullRegistryStillLetsSomeoneJoinAnExistingDecode() {
        ConfigStore.MAX_API_SURFACES.setValue(1);
        acquire("a");

        MediaSurface sharing = acquire("a");
        assertTrue(sharing.isReady(), "the cap is on decodes, not on how many people watch one");
        assertEquals(1, registry.size());
    }

    @Test
    void theVideoCapCountsOnlyTheDecodingOnes() {
        acquireVideo("v1");
        acquireVideo("v2");
        acquire("still");

        assertEquals(2, registry.decodingVideoCount());
        assertEquals(3, registry.size());
    }

    @Test
    void closingAVideoFreesRoomUnderTheVideoCap() {
        MediaSurface video = acquireVideo("v1");
        assertEquals(1, registry.decodingVideoCount());

        video.close();
        assertEquals(0, registry.decodingVideoCount());
    }

    @Test
    void disposeAllDropsEverythingWhoeverIsStillHoldingIt() {
        MediaSurface held = acquire("a");
        acquire("b");

        registry.disposeAll();

        assertEquals(0, registry.size());
        assertTrue(built.get(0).disposed);
        assertTrue(built.get(1).disposed);
        // What the addon is left holding is the same shape as a surface still loading.
        assertFalse(held.isReady());
        assertEquals(PlaybackState.ENDED, held.state());
    }

    @Test
    void closingAViewAfterDisposeAllDoesNotDisposeTwice() {
        MediaSurface held = acquire("a");
        registry.disposeAll();
        assertEquals(1, built.getFirst().disposeCount);

        held.close();
        assertEquals(1, built.getFirst().disposeCount);
    }

    @Test
    void wantedIsPerTickAndFallsBackToNotWantedOnceATickPassesWithNoDraw() {
        MediaSurface surface = acquire("a");
        FakeEntry entry = built.getFirst();

        surface.markWanted();
        registry.clientTick();
        assertTrue(entry.wasWanted());

        registry.clientTick();
        assertFalse(entry.wasWanted(), "nothing drew it during that tick");

        surface.markWanted();
        surface.markWanted();
        registry.clientTick();
        assertTrue(entry.wasWanted(), "a dozen draws are still one yes");
    }

    /** An entry with no decoder behind it: enough to exercise the bookkeeping. */
    private static final class FakeEntry extends SurfaceEntry {

        private final boolean video;
        boolean disposed;
        int disposeCount;

        FakeEntry(String key, boolean video) {
            super(key);
            this.video = video;
        }

        @Override
        ResourceLocation texture() {
            // Non-null so isReady() is true; nothing here ever draws it.
            return disposed ? null : ResourceLocation.fromNamespaceAndPath("liasmediaplayer", "test/surface");
        }

        @Override
        PlaybackState state() {
            return disposed ? PlaybackState.ENDED : PlaybackState.PLAYING;
        }

        @Override
        int sourceWidth() {
            return 16;
        }

        @Override
        int sourceHeight() {
            return 9;
        }

        @Override
        boolean isDecodingVideo() {
            return video && !disposed;
        }

        @Override
        void dispose() {
            disposed = true;
            disposeCount++;
        }
    }
}
