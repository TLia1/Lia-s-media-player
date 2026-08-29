package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.chat.ChatEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;

//? if >=1.21.11
/*import net.minecraft.client.gui.ActiveTextCollector;*/

/**
 * The mod's single point of contact with "which chat component is under the
 * cursor?".
 *
 * <p>Up to 1.21.10 the chat overlay could answer that directly:
 * {@code ChatComponent.getClickedComponentStyleAt(x, y)} mapped screen
 * coordinates back onto the trimmed message lines and asked the font splitter
 * for the style at that width. 1.21.9's render rewrite removed it. Chat is now
 * drawn by replaying it into a {@code ChatGraphicsAccess}, and the hit test is
 * expressed as a second replay into an {@code ActiveTextCollector} that keeps
 * whichever style its glyph rectangles contain the cursor.</p>
 *
 * <p>The two answers are not quite the same. The old method returned the style
 * of any component under the cursor; {@code ClickableStyleFinder} only keeps
 * one that carries a click event. That makes no difference here — both call
 * sites feed the result straight to {@link ChatEvents}
 * {@code .clickedUrl}, which returns null for a style without one — but it is a
 * narrower query, not a rename.</p>
 *
 * <p>This lives in {@code gui} rather than next to the click/hover seam in
 * {@code chat}: it is a query against the live client screen and needs
 * {@link Minecraft}, which nothing in the {@code chat} package touches. Finding the
 * overlay in the first place is {@link ChatOverlay}'s job, not this one's.</p>
 */
final class ChatHitTest {

    // --- The per-tick memo (see hoveredStyleCached) --------------------------
    private static double cachedX = Double.NaN;
    private static double cachedY = Double.NaN;
    @Nullable
    private static Style cachedStyle;
    private static boolean cacheValid;

    private ChatHitTest() {
    }

    /**
     * {@link #hoveredStyle} for callers that ask once a frame with a cursor that mostly
     * is not moving.
     *
     * <p>From 1.21.11 the hit test is no longer a lookup: chat is drawn by replaying it,
     * and asking what is under the cursor means replaying it a second time into a
     * collector. The hover preview asks on every frame the chat screen is open — a
     * hundred and more times a second, for an answer that can only change when the
     * cursor moves or the chat does. This remembers it for both.</p>
     *
     * <p>Invalidated by {@link #invalidate()} once a client tick, which is the coarsest
     * rate at which the chat itself can change, and by the cursor moving at all. The
     * click path deliberately does not come through here: a click is rare, and it should
     * read the screen as it is rather than as it was up to 50 ms ago.</p>
     */
    @Nullable
    static Style hoveredStyleCached(double mouseX, double mouseY) {
        if (cacheValid && mouseX == cachedX && mouseY == cachedY) {
            return cachedStyle;
        }
        cachedStyle = hoveredStyle(mouseX, mouseY);
        cachedX = mouseX;
        cachedY = mouseY;
        cacheValid = true;
        return cachedStyle;
    }

    /**
     * Drops the memo. Called once a client tick, because a new message or a scroll moves
     * every line under the cursor without the cursor having moved.
     */
    static void invalidate() {
        cacheValid = false;
        cachedStyle = null;
    }

    /**
     * The style of the chat component under {@code (mouseX, mouseY)} — in
     * gui-scaled coordinates — or {@code null} if the cursor is not over a
     * clickable one.
     */
    @Nullable
    static Style hoveredStyle(double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        //? if <1.21.11 {
        return ChatOverlay.chat(mc).getClickedComponentStyleAt(mouseX, mouseY);
        //?} elif <26.1 {
        /*// The collector is fed by replaying the chat, so it needs the same
        // inputs the real render pass gets: the gui-scaled height the lines are
        // laid out from the bottom of, the gui tick count that drives the
        // unfocused fade-out (a line faded past visibility is not hoverable),
        // and whether chat is focused, which disables that fade entirely.
        ActiveTextCollector.ClickableStyleFinder finder =
                new ActiveTextCollector.ClickableStyleFinder(mc.font, (int) mouseX, (int) mouseY);
        ChatOverlay.chat(mc).captureClickableText(
                finder,
                mc.getWindow().getGuiScaledHeight(),
                ChatOverlay.guiTicks(mc),
                ChatOverlay.chat(mc).isChatFocused());
        return finder.result();
        *///?} else {
        /*// As above, except that 26.1 replaced the focused flag with a tri-state
        // DisplayMode. FOREGROUND is what the old `true` meant — chat drawn at
        // full opacity with no fade — and BACKGROUND what `false` meant. The
        // third value, FOREGROUND_RESTRICTED, only adds the restricted-chat
        // prompt, which changes nothing about where a link sits.
        ActiveTextCollector.ClickableStyleFinder finder =
                new ActiveTextCollector.ClickableStyleFinder(mc.font, (int) mouseX, (int) mouseY);
        ChatOverlay.chat(mc).captureClickableText(
                finder,
                mc.getWindow().getGuiScaledHeight(),
                ChatOverlay.guiTicks(mc),
                ChatOverlay.chat(mc).isChatFocused()
                        ? ChatComponent.DisplayMode.FOREGROUND
                        : ChatComponent.DisplayMode.BACKGROUND);
        return finder.result();
        *///?}
    }

}
