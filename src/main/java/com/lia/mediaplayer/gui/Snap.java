package com.lia.mediaplayer.gui;

/**
 * The magnetism a window feels while it is being dragged: one axis at a time, one
 * pure function, no state.
 *
 * <p>A window is dragged by the raw cursor delta, which lands it a pixel or two off
 * every edge it was aimed at. {@link #axis} takes the coordinate that drag produced and
 * the <em>guides</em> it could plausibly have meant — the screen edges and centre line,
 * and the edges and centre of every other window — and returns the nearest one within
 * {@link #THRESHOLD}, or the coordinate untouched when nothing is close enough.</p>
 *
 * <p>Three lines of the moving box are candidates for each guide: its leading edge, its
 * trailing edge and its centre. That is what makes the same function do all three kinds
 * of alignment users expect — flush against an edge, butted up against a neighbour, and
 * centred on it — without the caller having to say which one it wants.</p>
 *
 * <p>Deliberately free of Minecraft types so it can be unit-tested: the caller collects
 * the guides, this decides where the box lands.</p>
 */
final class Snap {

    /**
     * How near a guide has to be, in GUI pixels, before the window jumps to it. Small
     * enough that a deliberate placement two pixels off an edge is still possible by
     * holding shift, large enough that aiming roughly at an edge lands on it.
     */
    static final int THRESHOLD = 6;

    private Snap() {
    }

    /**
     * Aligns one axis of a box to the nearest guide.
     *
     * @param start   the leading coordinate the drag produced (left edge, or top edge)
     * @param length  the box's extent along this axis
     * @param guides  coordinates worth aligning to; may be empty
     * @param threshold how far a guide may be and still attract the box
     * @return the leading coordinate to use — {@code start} when nothing is in range
     */
    static int axis(int start, int length, int[] guides, int threshold) {
        int bestDelta = 0;
        int bestDistance = threshold + 1;
        // The three lines of the box a guide can attract, in preference order: an edge
        // beats a centre at equal distance, because "flush with" is the more common
        // intent than "centred on".
        int[] lines = {start, start + length, start + length / 2};
        for (int line : lines) {
            for (int guide : guides) {
                int delta = guide - line;
                int distance = Math.abs(delta);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestDelta = delta;
                }
            }
        }
        return bestDistance <= threshold ? start + bestDelta : start;
    }
}
