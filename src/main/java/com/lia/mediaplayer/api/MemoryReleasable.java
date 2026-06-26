package com.lia.mediaplayer.api;

/**
 * Interface implemented by classes that hold significant amounts of memory
 * and can release it when the game is under memory pressure.
 */
public interface MemoryReleasable {

    /**
     * Priority of this releasable class. Lower values mean this class
     * will be asked to free its memory first.
     * Caches should have a low value (e.g., 10), while active resources
     * should have a higher value (e.g., 50).
     */
    int getReleasePriority();

    /**
     * Called when the system is under memory pressure.
     *
     * @param isCritical true if the game memory is critically low (e.g., > 95%),
     *                   false if it's moderately low (e.g., > 85%).
     * @return true if memory or resources were actually freed, false otherwise.
     */
    boolean releaseMemory(boolean isCritical);
}
