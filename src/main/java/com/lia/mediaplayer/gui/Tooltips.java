package com.lia.mediaplayer.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
//? if >=1.21.6 {
/*import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
*///?}
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The mod's single point of contact with tooltips, in both senses: <em>how</em> one is
 * drawn immediately (a call that was renamed twice across the supported versions), and
 * <em>when</em> the windows' own tooltips are drawn.
 *
 * <p>The media windows are not screen widgets — they are drawn by
 * {@link MediaWindowOverlay} over whatever screen is open — so {@code setTooltip} is not
 * available to them. Instead a window that finds one of its controls under the cursor
 * calls {@link #request} while it renders, and the overlay draws the last request at the
 * end of the frame, above every window. "Last request wins" is exactly the behaviour
 * wanted where two windows overlap: they are drawn bottom to top, so the topmost one —
 * the one the click would go to — is the one that gets to speak.</p>
 */
final class Tooltips {

    /** Above the windows, the overlay buttons and the chat image preview. */
    private static final int TOOLTIP_Z = 600;

    @Nullable
    private static Component pending;

    private Tooltips() {
    }

    /**
     * Asks for {@code text} to be shown at the cursor at the end of this frame.
     */
    static void request(Component text) {
        pending = text;
    }

    /**
     * Draws whatever was {@linkplain #request requested} during this frame, and clears
     * it — so a frame in which nothing is hovered shows nothing, and a screen that never
     * calls this (the HUD, which has no cursor) cannot leave one stuck on screen.
     */
    static void renderPending(GuiGraphics g, int mouseX, int mouseY) {
        Component text = pending;
        pending = null;
        if (text != null) {
            render(g, text, mouseX, mouseY, TOOLTIP_Z);
        }
    }

    /**
     * Draws a tooltip at the cursor <em>now</em>, rather than deferring it to the end of
     * the screen's own render pass.
     *
     * <p>That distinction is the whole reason this method is guarded three ways. Every
     * caller here runs from the screen render <em>post</em> hook, which fires after the
     * deferred tooltip of the frame has already been drawn; going through the deferred
     * API would put the tooltip on screen a frame late and leave it up a frame after the
     * cursor moved away.</p>
     *
     * @param legacyZ the layer to draw in — see {@link GuiLayer#push}
     */
    static void render(GuiGraphics g, Component text, int mouseX, int mouseY, int legacyZ) {
        Minecraft mc = Minecraft.getInstance();
        GuiLayer.push(g, legacyZ);
        //? if <1.21.6 {
        g.renderTooltip(mc.font, text, mouseX, mouseY);
        //?} elif <26.1 {
        /*// 1.21.6 renamed the convenience overload to setTooltipForNextFrame, which
        // *defers* the tooltip until Screen.renderWithTooltip draws it — too late, as
        // above. Building the component list by hand and calling renderTooltip keeps it
        // immediate, which is what every version before 1.21.6 did.
        g.renderTooltip(mc.font,
                List.of(ClientTooltipComponent.create(text.getVisualOrderText())),
                mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
        *///?} else {
        /*// 26.1 dropped the immediate renderTooltip overloads entirely, but kept the
        // same escape hatch under a new name: setTooltipForNextFrame only stores a
        // closure that Screen.extractRenderStateWithTooltipAndSubtitles later runs
        // through extractDeferredElements, and that closure calls this public `tooltip`
        // method. Calling it directly keeps the tooltip immediate exactly as on 1.21.6.
        g.tooltip(mc.font,
                List.of(ClientTooltipComponent.create(text.getVisualOrderText())),
                mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
        *///?}
        GuiLayer.pop(g);
    }
}
