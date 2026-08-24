package com.lia.mediaplayer.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The magnetism a dragged window feels. Pure geometry, so it is checked here rather
 * than by dragging a window around in a running game.
 */
class SnapTest {

    private static final int T = Snap.THRESHOLD;

    @Test
    void leavesACoordinateAloneWhenNothingIsNear() {
        assertEquals(100, Snap.axis(100, 50, new int[] {0, 400}, T));
    }

    @Test
    void leavesACoordinateAloneWhenThereAreNoGuidesAtAll() {
        assertEquals(100, Snap.axis(100, 50, new int[0], T));
    }

    @Test
    void pullsTheLeadingEdgeOntoAGuide() {
        // Dragged to 4, with an edge guide at 2: close enough to be meant.
        assertEquals(2, Snap.axis(4, 50, new int[] {2}, T));
    }

    @Test
    void pullsTheTrailingEdgeOntoAGuide() {
        // Box 100..150 dragged so its right edge lands at 150; guide at 152.
        assertEquals(102, Snap.axis(100, 50, new int[] {152}, T));
    }

    @Test
    void pullsTheCentreOntoAGuide() {
        // Box of 50 starting at 100 has its centre at 125; a guide at 127 moves it by 2.
        assertEquals(102, Snap.axis(100, 50, new int[] {127}, T));
    }

    @Test
    void ignoresAGuideJustOutOfRange() {
        assertEquals(100, Snap.axis(100, 50, new int[] {100 + T + 1}, T));
    }

    @Test
    void takesAGuideExactlyAtTheThreshold() {
        assertEquals(100 + T, Snap.axis(100, 50, new int[] {100 + T}, T));
    }

    @Test
    void prefersTheNearestOfSeveralGuides() {
        // Leading edge is 5 from 95 and 2 from 102; the closer one wins.
        assertEquals(102, Snap.axis(100, 50, new int[] {95, 102}, T));
    }

    @Test
    void snapsBackwardsAsReadilyAsForwards() {
        assertEquals(97, Snap.axis(100, 50, new int[] {97}, T));
    }

    @Test
    void buttsOneWindowUpAgainstAnother() {
        // A 60-wide window dragged to 238, with a neighbour occupying 300..400: its
        // trailing edge (298) is 2 short of the neighbour's left edge.
        int[] guides = {300, 400, 350};
        assertEquals(240, Snap.axis(238, 60, guides, T));
    }

    @Test
    void anEdgeBeatsACentreAtTheSameDistance() {
        // Box of 50 at 100: leading edge 100, centre 125. Guides 103 and 128 are both
        // three away; the edge is checked first, so the edge alignment is the one taken.
        assertEquals(103, Snap.axis(100, 50, new int[] {103, 128}, T));
    }
}
