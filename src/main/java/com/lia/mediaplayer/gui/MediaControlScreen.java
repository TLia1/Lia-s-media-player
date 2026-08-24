package com.lia.mediaplayer.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A screen whose entire content is the media windows: open it and the stack that was
 * being watched over the world becomes something you can click, drag, resize and scrub.
 *
 * <p>Until this existed, every one of those gestures needed the chat open, because the
 * chat screen was the only place {@link MediaWindowOverlay} routed input to — so pausing
 * a video meant opening a text field first, and a player who wanted the controls also
 * got a chat prompt they had not asked for. This screen is that same host with nothing
 * on it: it draws no widgets of its own, and the windows do all the work.</p>
 *
 * <p>It is a real {@link Screen} rather than a "grab the cursor on the HUD" mode on
 * purpose. Releasing the mouse from the camera, giving keys somewhere to go and putting
 * them back on {@code Escape} is exactly what a screen is, and everything the mod
 * already has for the chat screen — the window hit-testing, the shortcut table, the
 * overlay chips — works here by adding this type to
 * {@code MediaWindowOverlay.acceptsWindows} and nothing else.</p>
 *
 * <p>The windows are not drawn from here: they are drawn by the screen-render hook that
 * fires <em>after</em> a screen has drawn itself, the same one that puts them over the
 * chat. Which is why {@code super} is called below rather than the drawing being taken
 * over — on Fabric that hook is injected into the vanilla method, so a screen that never
 * calls it would show nothing at all.</p>
 */
public final class MediaControlScreen extends Screen {

    public MediaControlScreen() {
        super(Component.translatable("gui.liasmediaplayer.controls.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false; // whatever is playing keeps playing, and so does the world
    }

    // See the note in ConfigScreen: 26.1 renamed Renderable.render to
    // extractRenderState. Only the override wrapper differs, so the drawing below
    // stays in one place.
    //? if <26.1 {
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        draw(g);
    }
    //?} else {
    /*@Override
    public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        draw(g);
    }
    *///?}

    /**
     * The screen's own contribution: a line at the very bottom saying what this is, and
     * — when there is nothing to control — saying that instead of leaving an empty
     * screen that looks broken.
     *
     * <p>Drawn low and small because the windows are drawn on top of it and are the
     * point; this is a caption, not a header.</p>
     */
    private void draw(GuiGraphics g) {
        if (!MediaWindowOverlay.hasWindows()) {
            g.drawCenteredString(this.font, Component.translatable("gui.liasmediaplayer.controls.empty"),
                    this.width / 2, this.height / 2 - 4, Theme.TEXT_SUBTLE);
        }
        g.drawCenteredString(this.font, Component.translatable("gui.liasmediaplayer.controls.hint"),
                this.width / 2, this.height - 12, Theme.TEXT_SUBTLE);
    }
}
