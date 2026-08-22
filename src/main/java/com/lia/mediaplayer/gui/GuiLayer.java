package com.lia.mediaplayer.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * The mod's single point of contact with GUI draw ordering — how a media window,
 * a button or a preview is put <em>on top of</em> whatever was drawn before it.
 *
 * <p>Up to 1.21.5 the GUI was drawn immediately, in one depth-tested pass, so
 * "on top" meant a Z translation on the pose stack plus a
 * {@code GuiGraphics.flush()} to stop the batcher from reordering the draw. The
 * 1.21.6 GUI rewrite records draws into a {@code GuiRenderState} instead of
 * issuing them, {@code pose()} became a purely 2D {@code Matrix3x2fStack} with
 * no Z at all, and {@code flush()} is gone. Layering is expressed there by
 * {@code nextStratum()}: every stratum is drawn strictly after — and therefore
 * over — the ones opened before it.
 *
 * <p>The two models line up because the mod's Z values were already assigned in
 * draw order, so "increasing Z" and "a later stratum" mean the same thing here.
 * Callers keep passing the Z they always passed; it is the legacy path's
 * mechanism, and documents the intended stacking on the new one.
 */
final class GuiLayer {
    private GuiLayer() {
    }

    /**
     * Opens a layer that draws on top of everything submitted so far. Must be
     * closed by {@link #pop} or {@link #popAndFlush}.
     *
     * @param legacyZ the depth used on 1.21.1–1.21.5, where layering is a Z
     *                translation. Ignored from 1.21.6 on, which orders strata by
     *                the order they are opened in.
     */
    static void push(GuiGraphics g, int legacyZ) {
        //? if <1.21.6 {
        g.pose().pushPose();
        g.pose().translate(0, 0, legacyZ);
        //?} else
        /*g.nextStratum();*/
    }

    /** Closes the layer opened by {@link #push}. */
    static void pop(GuiGraphics g) {
        //? if <1.21.6 {
        g.pose().popPose();
        //?}
        // 1.21.6+: a stratum is closed by the next one being opened, or by the
        // frame ending. There is nothing to unwind.
    }

    /**
     * Closes the layer opened by {@link #push} and forces its contents to the
     * screen before anything else is drawn, so a later layer cannot be batched
     * ahead of it.
     */
    static void popAndFlush(GuiGraphics g) {
        //? if <1.21.6 {
        g.pose().popPose();
        g.flush();
        //?}
        // 1.21.6+: draws are recorded, not issued, and strata are replayed in
        // order at the end of the frame. Flushing has no meaning and no
        // equivalent.
    }
}
