/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.render;

import com.lia.mediaplayer.api.MediaHandle;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.Optional;

/**
 * A drop-in widget that shows media inside somebody else's {@code Screen} — the easiest
 * possible integration, and probably the most-used thing in this package.
 *
 * <pre>{@code
 * MediaWidget widget = MediaWidget.video(10, 30, 320, 180, url);
 * addRenderableWidget(widget);
 * // in your screen's removed()/onClose():
 * widget.close();
 * }</pre>
 *
 * <p><b>You must {@link #close()} it</b>, from wherever your screen tears itself down.
 * A widget holds a surface, a surface holds a decode, and a screen that is closed without
 * saying so leaves an ffmpeg process running for a menu nobody is looking at. (Leaving the
 * world still cleans up regardless — but that is a backstop, not the contract.)</p>
 *
 * <p>Drawing is the only thing this does. It takes no clicks, has no controls of its own,
 * and reports nothing to the narrator beyond its message: the transport belongs to
 * {@link #handle()}, so a screen that wants buttons draws its own and drives them through
 * that, in its own layout and its own style.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 3.0.0
 */
public class MediaWidget extends AbstractWidget {

    private final MediaSurface surface;
    private boolean stretch;

    /**
     * Wraps a surface you already have — the form to use when two widgets show the same
     * media, since two surfaces for one URL share a decode but two widgets over
     * <em>one</em> surface share an acquisition.
     *
     * @param message what the narrator reads; a {@code Component.translatable} from your
     *                own lang files
     */
    public MediaWidget(int x, int y, int width, int height, MediaSurface surface, Component message) {
        super(x, y, width, height, message);
        this.surface = surface == null ? DeadSurface.INSTANCE : surface;
    }

    /** A widget over a freshly acquired video surface. */
    public static MediaWidget video(int x, int y, int width, int height, String url, SurfaceOptions options) {
        return new MediaWidget(x, y, width, height, MediaSurfaces.video(url, options), Component.empty());
    }

    /** A widget over a freshly acquired video surface, with the default options. */
    public static MediaWidget video(int x, int y, int width, int height, String url) {
        return video(x, y, width, height, url, SurfaceOptions.defaults());
    }

    /** A widget over a still or animated image. */
    public static MediaWidget image(int x, int y, int width, int height, String url) {
        return new MediaWidget(x, y, width, height, MediaSurfaces.image(url), Component.empty());
    }

    /** The surface being drawn — for {@code isReady()}, the source size, and so on. */
    public MediaSurface surface() {
        return surface;
    }

    /** The transport, for a widget showing a video. Empty for an image. */
    public Optional<MediaHandle> handle() {
        return surface.playback();
    }

    /**
     * {@code true} to fill the widget's rectangle exactly, distorting the picture;
     * {@code false} (the default) to letterbox it at its own aspect ratio.
     */
    public void setStretch(boolean value) {
        this.stretch = value;
    }

    /** Releases the surface. Call this when your screen goes away. Idempotent. */
    public void close() {
        surface.close();
    }

    /**
     * 26.1 renamed the widget's draw method — {@code renderWidget} became
     * {@code extractWidgetRenderState} when GUI drawing moved to an extract-then-render
     * pass. It is the same call with the same arguments (the graphics parameter's own
     * type rename is handled project-wide), so the body is shared through
     * {@link #drawInto}.
     */
    //? if <26.1 {
    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawInto(graphics);
    }
    //?} else {
    /*@Override
    protected void extractWidgetRenderState(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawInto(graphics);
    }
    *///?}

    private void drawInto(GuiGraphics graphics) {
        if (!visible) {
            return;
        }
        // Through MediaGraphics rather than straight at the surface, so the widget gets
        // the markWanted() for free and cannot be the one place that forgets it.
        if (stretch) {
            MediaGraphics.drawStretched(graphics, surface, getX(), getY(), width, height);
        } else {
            MediaGraphics.draw(graphics, surface, getX(), getY(), width, height);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
