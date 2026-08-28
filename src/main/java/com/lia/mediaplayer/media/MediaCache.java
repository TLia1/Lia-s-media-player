package com.lia.mediaplayer.media;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.ToLongFunction;

/**
 * The bounded, URL-keyed store the three media caches are built on: the image previews,
 * the queue thumbnails and the resolved titles.
 *
 * <p>All three had the same anonymous {@link LinkedHashMap} subclass copied into them —
 * an {@code removeEldestEntry} that drops the oldest entry past a cap and releases
 * whatever it was holding. Two of them also hold a <em>GPU texture</em> per entry, which
 * is what makes the duplication worth removing rather than merely untidy: an entry that
 * leaves the map without its {@code onEvict} running is a texture the game never gets
 * back, and there is no way to notice from inside the game. Written once, it can be
 * tested — which is the point, since none of the three could be before.</p>
 *
 * <h2>Order</h2>
 *
 * <p>Eviction is by <b>insertion order, not access order</b>: the oldest entry goes,
 * however recently it was looked at. That is deliberate and it is what the callers want.
 * A preview is displayed for as long as the message carrying it can still be scrolled
 * to, and vanilla drops the oldest message first; a queue thumbnail lives as long as its
 * queue entry. Re-reading an entry every frame — which the render code does — must not
 * be what decides who gets thrown out.</p>
 *
 * <h2>Two budgets</h2>
 *
 * <p>The <b>entry cap</b> is checked on every insertion and is never exceeded. The
 * optional <b>byte budget</b> cannot be: an entry's size is not known until whatever it
 * was loading has arrived, so the owner calls {@link #enforceByteBudget()} once it has
 * filled an entry in. Both caps are read through suppliers rather than captured, because
 * they are configuration a player can change while the game is running.</p>
 *
 * <p>Not thread-safe, and deliberately so: every caller is on the render/main thread —
 * the loads run elsewhere but publish their results back — and a lock here would suggest
 * otherwise. See the class docs of the three caches.</p>
 *
 * @param <V> what one entry holds
 */
public final class MediaCache<V> {

    private final IntSupplier maxEntries;
    private final LongSupplier maxBytes;
    private final ToLongFunction<V> sizeOf;
    private final Consumer<V> onEvict;
    private final LinkedHashMap<String, V> entries;

    /**
     * A cache bounded by a number of entries alone.
     *
     * @param maxEntries how many entries to keep, re-read on every insertion
     * @param onEvict    what to release when an entry is dropped; {@code null} for
     *                   entries that hold nothing but memory
     */
    public MediaCache(IntSupplier maxEntries, @Nullable Consumer<V> onEvict) {
        this(maxEntries, () -> 0L, v -> 0L, onEvict);
    }

    /**
     * A cache bounded by a number of entries <em>and</em> a total size in bytes.
     *
     * @param maxBytes the byte budget, applied by {@link #enforceByteBudget()};
     *                 a value of {@code 0} or less means "no byte budget"
     * @param sizeOf   what one entry costs, asked fresh each time so an entry that has
     *                 not finished loading counts as nothing
     */
    public MediaCache(IntSupplier maxEntries, LongSupplier maxBytes,
                      ToLongFunction<V> sizeOf, @Nullable Consumer<V> onEvict) {
        this.maxEntries = maxEntries;
        this.maxBytes = maxBytes;
        this.sizeOf = sizeOf;
        this.onEvict = onEvict == null ? v -> {
        } : onEvict;
        this.entries = new LinkedHashMap<>(16, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, V> eldest) {
                if (size() > Math.max(1, MediaCache.this.maxEntries.getAsInt())) {
                    MediaCache.this.onEvict.accept(eldest.getValue());
                    return true;
                }
                return false;
            }
        };
    }

    /**
     * The entry for {@code key}, creating it with {@code factory} the first time. This is
     * an insertion, so it may evict the oldest entry.
     */
    public V computeIfAbsent(String key, Function<String, V> factory) {
        return entries.computeIfAbsent(key, factory);
    }

    /**
     * The entry for {@code key}, or {@code null} if it is not held.
     *
     * <p>What the background loads use to check they are still publishing into the entry
     * they started from: an entry evicted mid-flight must have its result thrown away
     * rather than written into a slot somebody else now owns.</p>
     */
    @Nullable
    public V get(String key) {
        return entries.get(key);
    }

    /**
     * Drops everything, releasing each entry — e.g. on leaving a server.
     */
    public void clear() {
        entries.values().forEach(onEvict);
        entries.clear();
    }

    /**
     * Drops the oldest entries until the total is back inside the byte budget.
     *
     * <p>Called by the owner once an entry's real size is known. A no-op for a cache
     * declared without a budget.</p>
     */
    public void enforceByteBudget() {
        long budget = maxBytes.getAsLong();
        if (budget <= 0) {
            return;
        }
        long total = 0;
        for (V value : entries.values()) {
            total += sizeOf.applyAsLong(value);
        }
        var iterator = entries.entrySet().iterator();
        while (iterator.hasNext() && total > budget) {
            V value = iterator.next().getValue();
            total -= sizeOf.applyAsLong(value);
            onEvict.accept(value);
            iterator.remove();
        }
    }

    public int size() {
        return entries.size();
    }
}
