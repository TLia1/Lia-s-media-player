package com.lia.mediaplayer.gui;

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
 * sites feed the result straight to {@link com.lia.mediaplayer.chat.ChatEvents}
 * {@code .clickedUrl}, which returns null for a style without one — but it is a
 * narrower query, not a rename.</p>
 *
 * <p>This lives in {@code gui} rather than next to the click/hover seam in
 * {@code chat}: it is a query against the live client screen and needs
 * {@link Minecraft}, which nothing in the {@code chat} package touches.</p>
 */
final class ChatHitTest {
    private ChatHitTest() {
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
        return chat(mc).getClickedComponentStyleAt(mouseX, mouseY);
        //?} elif <26.1 {
        /*// The collector is fed by replaying the chat, so it needs the same
        // inputs the real render pass gets: the gui-scaled height the lines are
        // laid out from the bottom of, the gui tick count that drives the
        // unfocused fade-out (a line faded past visibility is not hoverable),
        // and whether chat is focused, which disables that fade entirely.
        ActiveTextCollector.ClickableStyleFinder finder =
                new ActiveTextCollector.ClickableStyleFinder(mc.font, (int) mouseX, (int) mouseY);
        chat(mc).captureClickableText(
                finder,
                mc.getWindow().getGuiScaledHeight(),
                guiTicks(mc),
                chat(mc).isChatFocused());
        return finder.result();
        *///?} else {
        /*// As above, except that 26.1 replaced the focused flag with a tri-state
        // DisplayMode. FOREGROUND is what the old `true` meant — chat drawn at
        // full opacity with no fade — and BACKGROUND what `false` meant. The
        // third value, FOREGROUND_RESTRICTED, only adds the restricted-chat
        // prompt, which changes nothing about where a link sits.
        ActiveTextCollector.ClickableStyleFinder finder =
                new ActiveTextCollector.ClickableStyleFinder(mc.font, (int) mouseX, (int) mouseY);
        chat(mc).captureClickableText(
                finder,
                mc.getWindow().getGuiScaledHeight(),
                guiTicks(mc),
                chat(mc).isChatFocused()
                        ? ChatComponent.DisplayMode.FOREGROUND
                        : ChatComponent.DisplayMode.BACKGROUND);
        return finder.result();
        *///?}
    }

    // 26.2 split the in-game HUD out of Gui: Gui kept the screen stack and the
    // HUD it now owns, and the chat overlay moved onto that Hud. Both accessors
    // keep their names, only their owner changed.
    private static ChatComponent chat(Minecraft mc) {
        //? if <26.2 {
        return mc.gui.getChat();
        //?} else
        /*return mc.gui.hud.getChat();*/
    }

    private static int guiTicks(Minecraft mc) {
        //? if <26.2 {
        return mc.gui.getGuiTicks();
        //?} else
        /*return mc.gui.hud.getGuiTicks();*/
    }
}
