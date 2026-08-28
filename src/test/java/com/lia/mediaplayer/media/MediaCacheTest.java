package com.lia.mediaplayer.media;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bookkeeping behind the three media caches.
 *
 * <p>Two of them hand out GPU textures, and their whole correctness condition is that
 * <em>every</em> entry which leaves the map has its release run exactly once: a miss
 * leaks a texture for the rest of the session, and there is nothing inside the game that
 * would say so — the picture that leaked is still on screen. So most of what is checked
 * here is not "the right entries were evicted" but "the evicted entries were handed
 * back", counted.</p>
 *
 * <p>Before this store existed the same policy was written out three times inside three
 * classes that each needed a GL context and a running client to instantiate, which is
 * precisely why none of it was tested.</p>
 */
class MediaCacheTest {

    /** Something with a size and a release, standing in for a preview or a thumbnail. */
    private static final class Held {
        final String name;
        final long bytes;
        int released;

        Held(String name, long bytes) {
            this.name = name;
            this.bytes = bytes;
        }
    }

    private final List<Held> released = new ArrayList<>();

    private void release(Held held) {
        held.released++;
        released.add(held);
    }

    private List<String> releasedNames() {
        return released.stream().map(h -> h.name).toList();
    }

    private MediaCache<Held> capped(int maxEntries) {
        return new MediaCache<>(() -> maxEntries, this::release);
    }

    private static Held put(MediaCache<Held> cache, String name) {
        return cache.computeIfAbsent(name, key -> new Held(key, 0));
    }

    // ------------------------------------------------------------------
    // The entry cap
    // ------------------------------------------------------------------

    @Test
    void keepsWhatFitsWithinTheCap() {
        MediaCache<Held> cache = capped(3);
        put(cache, "a");
        put(cache, "b");
        put(cache, "c");

        assertEquals(3, cache.size());
        assertTrue(released.isEmpty());
    }

    @Test
    void dropsTheOldestEntryPastTheCap() {
        MediaCache<Held> cache = capped(2);
        put(cache, "a");
        put(cache, "b");
        put(cache, "c");

        assertEquals(2, cache.size());
        assertNull(cache.get("a"));
        assertNotNull(cache.get("b"));
        assertNotNull(cache.get("c"));
        assertEquals(List.of("a"), releasedNames());
    }

    @Test
    void evictsByInsertionOrderNotByUse() {
        // The render code reads the front-most entry every frame. If that counted as
        // "recently used", the entry on screen would keep evicting the ones that are
        // about to scroll into view — the exact opposite of what a chat preview wants.
        MediaCache<Held> cache = capped(2);
        put(cache, "a");
        put(cache, "b");
        for (int frame = 0; frame < 100; frame++) {
            cache.get("a");
        }
        put(cache, "c");

        assertNull(cache.get("a"), "the oldest entry goes even though it was just read");
        assertEquals(List.of("a"), releasedNames());
    }

    @Test
    void releasesEveryEntryItDropsExactlyOnce() {
        MediaCache<Held> cache = capped(1);
        List<Held> all = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            all.add(put(cache, "url" + i));
        }

        assertEquals(1, cache.size());
        assertEquals(19, released.size());
        for (Held held : all) {
            assertTrue(held.released <= 1, held.name + " was released twice");
        }
    }

    @Test
    void returnsTheEntryItAlreadyHoldsRatherThanBuildingASecond() {
        MediaCache<Held> cache = capped(4);
        Held first = put(cache, "a");
        assertSame(first, put(cache, "a"));
        assertEquals(1, cache.size());
        assertTrue(released.isEmpty());
    }

    @Test
    void followsACapThatChangesWhileRunning() {
        // Both caps are settings on the config screen, so a player can shrink them with
        // the cache already full; the next insertion has to obey the new figure.
        AtomicInteger cap = new AtomicInteger(4);
        MediaCache<Held> cache = new MediaCache<>(cap::get, this::release);
        put(cache, "a");
        put(cache, "b");
        put(cache, "c");
        put(cache, "d");
        assertEquals(4, cache.size());

        cap.set(2);
        put(cache, "e");

        // One insertion evicts one entry: the map is bounded on the way in, not swept.
        assertEquals(4, cache.size());
        assertEquals(List.of("a"), releasedNames());
    }

    @Test
    void neverShrinksToNothing() {
        // A cap of zero would mean every insertion immediately evicting what was just
        // put in, so getOrLoad would restart the same download on every frame.
        MediaCache<Held> cache = capped(0);
        put(cache, "a");
        assertEquals(1, cache.size());
        assertNotNull(cache.get("a"));
    }

    // ------------------------------------------------------------------
    // Clearing
    // ------------------------------------------------------------------

    @Test
    void releasesEverythingWhenCleared() {
        MediaCache<Held> cache = capped(10);
        put(cache, "a");
        put(cache, "b");

        cache.clear();

        assertEquals(0, cache.size());
        assertEquals(List.of("a", "b"), releasedNames());
    }

    @Test
    void clearingAnEmptyCacheIsFine() {
        MediaCache<Held> cache = capped(10);
        cache.clear();
        assertEquals(0, cache.size());
        assertTrue(released.isEmpty());
    }

    @Test
    void worksWithoutAnythingToRelease() {
        // The title cache holds strings; there is nothing to hand back.
        MediaCache<String> cache = new MediaCache<>(() -> 2, null);
        cache.computeIfAbsent("a", k -> k);
        cache.computeIfAbsent("b", k -> k);
        cache.computeIfAbsent("c", k -> k);
        assertEquals(2, cache.size());
        assertNull(cache.get("a"));
    }

    // ------------------------------------------------------------------
    // The byte budget
    // ------------------------------------------------------------------

    private MediaCache<Held> budgeted(long maxBytes) {
        return new MediaCache<>(() -> 1000, () -> maxBytes, h -> h.bytes, this::release);
    }

    private static Held put(MediaCache<Held> cache, String name, long bytes) {
        return cache.computeIfAbsent(name, key -> new Held(key, bytes));
    }

    @Test
    void leavesTheCacheAloneWhileItIsInsideTheBudget() {
        MediaCache<Held> cache = budgeted(100);
        put(cache, "a", 30);
        put(cache, "b", 30);

        cache.enforceByteBudget();

        assertEquals(2, cache.size());
        assertTrue(released.isEmpty());
    }

    @Test
    void dropsTheOldestEntriesUntilItIsBackInsideTheBudget() {
        MediaCache<Held> cache = budgeted(100);
        put(cache, "a", 60);
        put(cache, "b", 60);
        put(cache, "c", 30);

        cache.enforceByteBudget();

        assertNull(cache.get("a"));
        assertNotNull(cache.get("b"));
        assertNotNull(cache.get("c"));
        assertEquals(List.of("a"), releasedNames());
    }

    @Test
    void dropsNoMoreThanItHasTo() {
        MediaCache<Held> cache = budgeted(100);
        put(cache, "a", 50);
        put(cache, "b", 50);
        put(cache, "c", 10);

        cache.enforceByteBudget();

        assertEquals(2, cache.size());
        assertEquals(List.of("a"), releasedNames());
    }

    @Test
    void countsAnEntryThatHasNotLoadedYetAsCostingNothing() {
        // An entry exists from the moment its URL is seen and only gains a size when its
        // download lands. Charging a placeholder for bytes it does not hold would evict
        // real images to make room for nothing.
        MediaCache<Held> cache = budgeted(100);
        put(cache, "loaded", 90);
        put(cache, "pending", 0);

        cache.enforceByteBudget();

        assertEquals(2, cache.size());
        assertTrue(released.isEmpty());
    }

    @Test
    void doesNothingWithoutABudget() {
        MediaCache<Held> cache = new MediaCache<>(() -> 1000, () -> 0L, h -> h.bytes, this::release);
        put(cache, "a", 10_000_000);

        cache.enforceByteBudget();

        assertEquals(1, cache.size());
        assertTrue(released.isEmpty());
    }

    @Test
    void terminatesWhenOneEntryIsBiggerThanTheWholeBudget() {
        // It empties the cache rather than looping, which is the honest reading of a
        // hard cap; the alternative is silently spending more memory than was allowed.
        MediaCache<Held> cache = budgeted(10);
        put(cache, "huge", 5000);

        cache.enforceByteBudget();

        assertEquals(0, cache.size());
        assertEquals(List.of("huge"), releasedNames());
    }

    @Test
    void followsABudgetThatChangesWhileRunning() {
        AtomicLong budget = new AtomicLong(1000);
        MediaCache<Held> cache = new MediaCache<>(() -> 1000, budget::get, h -> h.bytes, this::release);
        put(cache, "a", 400);
        put(cache, "b", 400);

        cache.enforceByteBudget();
        assertEquals(2, cache.size());

        budget.set(500);
        cache.enforceByteBudget();

        assertEquals(1, cache.size());
        assertEquals(List.of("a"), releasedNames());
    }
}
