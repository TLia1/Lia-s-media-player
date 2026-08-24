package com.lia.mediaplayer.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * The mod's panel shape: a rectangle with its four corners softened, and the 1 px
 * outline that follows the same silhouette.
 *
 * <p>Every surface the mod draws used to be a single {@code fill} — a hard rectangle
 * with square corners, which is what made the windows read as debug overlays rather
 * than as windows. Two pixels of corner is enough to fix that, and it costs nothing:
 * there is no texture, no rounding maths and no per-version API, just five fills laid
 * out as a stepped silhouette.</p>
 *
 * <p>The shape is fixed rather than parameterised on a radius on purpose — one corner
 * radius everywhere is what makes a window, its title bar, the queue panel and the
 * banner look like parts of the same UI. It is the {@link Theme} argument, not the
 * geometry, that a call site chooses.</p>
 */
final class Panels {

    /** How far the corner is cut in, in pixels. */
    private static final int R = 2;

    private Panels() {
    }

    /**
     * Fills the rectangle {@code [x0, y0) .. (x1, y1)} with softened corners. Boxes too
     * small for the corner treatment are filled square, so a caller never has to check.
     */
    static void fill(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        if (x1 - x0 < R * 3 || y1 - y0 < R * 3) {
            g.fill(x0, y0, x1, y1, color);
            return;
        }
        g.fill(x0 + 2, y0, x1 - 2, y0 + 1, color);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, color);
        g.fill(x0, y0 + 2, x1, y1 - 2, color);
        g.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, color);
        g.fill(x0 + 2, y1 - 1, x1 - 2, y1, color);
    }

    /**
     * Fills a rectangle whose <em>top</em> corners are softened and whose bottom edge is
     * square — a title bar sitting inside a rounded box.
     */
    static void fillTop(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        if (x1 - x0 < R * 3 || y1 - y0 < R) {
            g.fill(x0, y0, x1, y1, color);
            return;
        }
        g.fill(x0 + 2, y0, x1 - 2, y0 + 1, color);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, color);
        g.fill(x0, y0 + 2, x1, y1, color);
    }

    /**
     * Fills a rectangle whose <em>bottom</em> corners are softened and whose top edge is
     * square — a control bar sitting inside a rounded box.
     */
    static void fillBottom(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        if (x1 - x0 < R * 3 || y1 - y0 < R) {
            g.fill(x0, y0, x1, y1, color);
            return;
        }
        g.fill(x0, y0, x1, y1 - 2, color);
        g.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, color);
        g.fill(x0 + 2, y1 - 1, x1 - 2, y1, color);
    }

    /**
     * Traces the 1 px outline of the same silhouette {@link #fill} paints, just inside
     * the given bounds.
     */
    static void border(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        if (x1 - x0 < R * 3 || y1 - y0 < R * 3) {
            g.fill(x0, y0, x1, y0 + 1, color);
            g.fill(x0, y1 - 1, x1, y1, color);
            g.fill(x0, y0, x0 + 1, y1, color);
            g.fill(x1 - 1, y0, x1, y1, color);
            return;
        }
        // Straight runs, stopping short of the corners.
        g.fill(x0 + 2, y0, x1 - 2, y0 + 1, color);
        g.fill(x0 + 2, y1 - 1, x1 - 2, y1, color);
        g.fill(x0, y0 + 2, x0 + 1, y1 - 2, color);
        g.fill(x1 - 1, y0 + 2, x1, y1 - 2, color);
        // The single pixel that turns each corner.
        g.fill(x0 + 1, y0 + 1, x0 + 2, y0 + 2, color);
        g.fill(x1 - 2, y0 + 1, x1 - 1, y0 + 2, color);
        g.fill(x0 + 1, y1 - 2, x0 + 2, y1 - 1, color);
        g.fill(x1 - 2, y1 - 2, x1 - 1, y1 - 1, color);
    }
}
