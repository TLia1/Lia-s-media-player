package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.MediaPlayerContext;
import com.lia.mediaplayer.history.HistoryEntry;
import com.lia.mediaplayer.history.HistoryStore;
import com.lia.mediaplayer.media.MediaTitleCache;
import com.lia.mediaplayer.playlist.Playlist;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

//? if >=1.21.11
/*import net.minecraft.client.input.MouseButtonEvent;*/

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The library: everything the mod has played, most recent first, and the favourites kept
 * out of it. Opened from the playlist screen.
 *
 * <p>Two columns, the same shape as {@link PlaylistScreen}: the saved playlists on the
 * left — here they are the <em>target</em> a history entry is added to, not something
 * being edited — and the entries on the right. A row plays; its three buttons keep it
 * ({@link Glyphs#heart}), file it into the selected playlist ({@link Glyphs#plus}) or
 * drop it.</p>
 *
 * <p>Titles are resolved through {@link MediaTitleCache} for the rows on screen only, so
 * scrolling a two-hundred-entry history does not fire two hundred lookups.</p>
 */
public final class HistoryScreen extends Screen {

    private static final int ROW_PL = 14;
    private static final int ROW_EN = 18;
    /** Pitch of the row's action buttons: the glyph box plus the gap after it. */
    private static final int ROW_BUTTON_PITCH = MediaWindow.BUTTON + 7;
    /** Room the three row buttons take on the right of an entry, margin included. */
    private static final int ROW_BUTTONS_W = 3 * ROW_BUTTON_PITCH + 2;
    /** The row of controls above the two lists: the search box and its two buttons. */
    private static final int HEADER_Y = 32;
    private static final int HEADER_H = 18;

    @Nullable
    private final Screen lastScreen;

    @Nullable
    private Playlist target;
    private int playlistScroll;
    private int entryScroll;
    private boolean favoritesOnly;
    private String query = "";

    @Nullable
    private EditBox searchBox;

    public HistoryScreen(@Nullable Screen lastScreen) {
        super(Component.translatable("gui.liasmediaplayer.history.title"));
        this.lastScreen = lastScreen;
    }

    private static MediaPlayerContext context() {
        return MediaPlayerContext.get();
    }

    private HistoryStore history() {
        return context().getHistoryStore();
    }

    @Override
    public boolean isPauseScreen() {
        return false; // whatever is playing keeps playing while you browse what has
    }

    // ------------------------------------------------------------------
    // Layout (read live so the screen reflows on resize)
    // ------------------------------------------------------------------

    private int leftX() {
        return 16;
    }

    private int leftW() {
        return 150;
    }

    /**
     * Where each column's caption sits: below the header row, above the list.
     *
     * <p>It used to be derived from {@code listTop()} and landed <em>inside</em> the
     * search box, which is a widget and drew over it. The three bands are now measured
     * from the top down instead, so none of them can reach into another.</p>
     */
    private int captionY() {
        return HEADER_Y + HEADER_H + 7;
    }

    private int listTop() {
        return captionY() + 12;
    }

    private int listBottom() {
        return height - 40;
    }

    private int rightX() {
        return leftX() + leftW() + 16;
    }

    private int rightW() {
        return Math.max(120, width - rightX() - 16);
    }

    private int visiblePlaylistRows() {
        return Math.max(1, (listBottom() - listTop()) / ROW_PL);
    }

    private int visibleEntryRows() {
        return Math.max(1, (listBottom() - listTop()) / ROW_EN);
    }

    // ------------------------------------------------------------------
    // Widgets
    // ------------------------------------------------------------------

    @Override
    protected void init() {
        // The two buttons are placed first and the search box fills what is left, so the
        // box can never grow into them however wide the screen is.
        int toggleX = rightX() + rightW() - 146;
        this.searchBox = new EditBox(font, rightX(), HEADER_Y,
                Math.max(60, toggleX - rightX() - 6), HEADER_H,
                Component.translatable("gui.liasmediaplayer.history.search"));
        this.searchBox.setHint(Component.translatable("gui.liasmediaplayer.history.search"));
        this.searchBox.setValue(query);
        // Filtering happens where the list is drawn, so the responder only has to note
        // what was typed — no widget is rebuilt and the box never loses what is in it.
        this.searchBox.setResponder(value -> {
            query = value;
            entryScroll = 0;
        });
        addRenderableWidget(this.searchBox);

        addRenderableWidget(Button.builder(favoritesLabel(), b -> {
                    favoritesOnly = !favoritesOnly;
                    entryScroll = 0;
                    b.setMessage(favoritesLabel());
                }).bounds(toggleX, HEADER_Y, 90, HEADER_H)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable("gui.liasmediaplayer.history.tooltip.favorites")))
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.liasmediaplayer.history.clear"),
                        b -> history().clear())
                .bounds(toggleX + 94, HEADER_Y, 52, HEADER_H)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable("gui.liasmediaplayer.history.tooltip.clear")))
                .build());

        addRenderableWidget(Button.builder(Component.translatable("gui.liasmediaplayer.playlists.button.done"),
                        b -> onClose())
                .bounds(width / 2 - 80, height - 26, 160, 20).build());
    }

    private Component favoritesLabel() {
        return Component.translatable(favoritesOnly
                ? "gui.liasmediaplayer.history.favorites.on"
                : "gui.liasmediaplayer.history.favorites.off");
    }

    // ------------------------------------------------------------------
    // The list being shown
    // ------------------------------------------------------------------

    /**
     * The entries after the favourites toggle and the search box have had their say.
     * Recomputed per frame and per click rather than cached, so an entry favourited or
     * dropped is gone from the list on the very next draw.
     */
    private List<HistoryEntry> shownEntries() {
        List<HistoryEntry> all = favoritesOnly ? history().favorites() : history().all();
        String needle = query.strip().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return all;
        }
        List<HistoryEntry> matches = new ArrayList<>();
        for (HistoryEntry entry : all) {
            String title = MediaPlayerContext.get().getTitleCache().getOrLoad(entry.url()).toLowerCase(Locale.ROOT);
            if (title.contains(needle) || entry.url().toLowerCase(Locale.ROOT).contains(needle)) {
                matches.add(entry);
            }
        }
        return matches;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    // See the note in ConfigScreen: 26.1 renamed Renderable.render to
    // extractRenderState. Only the override wrapper differs, so the drawing below
    // stays in one place.
    //? if <26.1 {
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        draw(g, mouseX, mouseY);
    }
    //?} else {
    /*@Override
    public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        draw(g, mouseX, mouseY);
    }
    *///?}

    private void draw(GuiGraphics g, int mouseX, int mouseY) {
        g.drawCenteredString(font, title, width / 2, 14, Theme.TEXT);
        renderPlaylists(g, mouseX, mouseY);
        renderEntries(g, mouseX, mouseY);
        // The rows are drawn by hand, not by widgets, so they have no setTooltip of their
        // own — and the vanilla call that would defer one was renamed twice across the
        // supported versions. The mod's own seam covers both: a row asks while it draws
        // and the request is drawn here, at the end of the frame, above everything.
        Tooltips.renderPending(g, mouseX, mouseY);
    }

    private void renderPlaylists(GuiGraphics g, int mouseX, int mouseY) {
        List<Playlist> playlists = context().getPlaylistStore().all();
        int x = leftX();
        int w = leftW();
        int top = listTop();
        g.drawString(font, Component.translatable("gui.liasmediaplayer.history.add_to"), x, captionY(), Theme.TEXT_SUBTLE);
        g.fill(x, top, x + w, listBottom(), Theme.LIST_BG);

        if (playlists.isEmpty()) {
            g.drawString(font, Component.translatable("gui.liasmediaplayer.history.no_playlists"),
                    x + 4, top + 6, Theme.TEXT_DIM);
            return;
        }
        int rows = visiblePlaylistRows();
        for (int i = 0; i < rows; i++) {
            int index = playlistScroll + i;
            if (index >= playlists.size()) {
                break;
            }
            int rowY = top + i * ROW_PL;
            Playlist playlist = playlists.get(index);
            boolean selected = playlist == target;
            boolean over = MediaWindow.inRect(mouseX, mouseY, x, rowY, w, ROW_PL - 1);
            int bg = selected ? Theme.ROW_SELECTED_BG : (over ? Theme.ROW_HOVER_BG : Theme.ROW_BG);
            g.fill(x + 1, rowY, x + w - 1, rowY + ROW_PL - 1, bg);
            String label = playlist.name() + "  (" + playlist.size() + ")";
            g.drawString(font, Component.literal(Glyphs.fit(font, label, w - 8)), x + 4, rowY + 3, Theme.TEXT);
        }
    }

    private void renderEntries(GuiGraphics g, int mouseX, int mouseY) {
        List<HistoryEntry> entries = shownEntries();
        int x = rightX();
        int w = rightW();
        int top = listTop();
        g.drawString(font, Component.translatable("gui.liasmediaplayer.history.count", entries.size()),
                x, captionY(), Theme.TEXT_SUBTLE);
        g.fill(x, top, x + w, listBottom(), Theme.LIST_BG);

        if (entries.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("gui.liasmediaplayer.history.empty"),
                    x + w / 2, top + (listBottom() - top) / 2 - 4, Theme.TEXT_SUBTLE);
            return;
        }

        int rows = visibleEntryRows();
        for (int i = 0; i < rows; i++) {
            int index = entryScroll + i;
            if (index >= entries.size()) {
                break;
            }
            renderEntryRow(g, entries.get(index), top + i * ROW_EN, mouseX, mouseY);
        }
    }

    private void renderEntryRow(GuiGraphics g, HistoryEntry entry, int rowY, int mouseX, int mouseY) {
        int x = rightX();
        int w = rightW();
        int heartX = heartX();
        int btnY = rowY + (ROW_EN - MediaWindow.BUTTON) / 2;

        boolean overButtons = MediaWindow.inRect(mouseX, mouseY, heartX - 3, rowY, ROW_BUTTONS_W, ROW_EN - 1);
        boolean overRow = MediaWindow.inRect(mouseX, mouseY, x, rowY, w, ROW_EN - 1);
        g.fill(x + 1, rowY, x + w - 1, rowY + ROW_EN - 1,
                (overRow && !overButtons) ? Theme.ROW_HOVER_BG : Theme.ROW_BG);

        String label = kindPrefix(entry) + MediaPlayerContext.get().getTitleCache().getOrLoad(entry.url());
        g.drawString(font, Component.literal(Glyphs.fit(font, label, heartX - 8 - x)),
                x + 4, rowY + (ROW_EN - font.lineHeight) / 2, Theme.TEXT);

        // The heart is filled once the entry is kept and hollow while it is not, so the
        // state is legible without comparing two greys — see Glyphs.heartOutline.
        boolean overHeart = rowButtonBackdrop(g, heartX, btnY, mouseX, mouseY, true);
        if (entry.favorite()) {
            Glyphs.heart(g, heartX, btnY, overHeart ? Theme.ICON_HOVER : Theme.DANGER);
        } else {
            Glyphs.heartOutline(g, heartX, btnY, overHeart ? Theme.ICON_HOVER : Theme.ICON_INACTIVE);
        }
        if (overHeart) {
            Tooltips.request(Component.translatable(entry.favorite()
                    ? "gui.liasmediaplayer.control.unfavorite"
                    : "gui.liasmediaplayer.control.favorite"));
        }

        boolean overAdd = rowButtonBackdrop(g, addX(), btnY, mouseX, mouseY, target != null);
        Glyphs.addToPlaylist(g, addX(), btnY, target == null ? Theme.ICON_DISABLED
                : (overAdd ? Theme.ICON_HOVER : Theme.ICON));
        if (overAdd || (target == null && overButton(addX(), btnY, mouseX, mouseY))) {
            // With no playlist picked the button cannot do anything, so the tooltip says
            // what to do about that rather than what the button would have done.
            Tooltips.request(target == null
                    ? Component.translatable("gui.liasmediaplayer.history.tooltip.add_none")
                    : Component.translatable("gui.liasmediaplayer.history.tooltip.add", target.name()));
        }

        boolean overRemove = rowButtonBackdrop(g, removeX(), btnY, mouseX, mouseY, true);
        Glyphs.close(g, removeX(), btnY, overRemove ? Theme.DANGER : Theme.ICON);
        if (overRemove) {
            Tooltips.request(Component.translatable("gui.liasmediaplayer.history.tooltip.remove"));
        }
    }

    /**
     * Draws the chip behind a hovered row button and reports whether it was hovered.
     *
     * <p>The three glyphs used to sit bare on the row, which left them reading as
     * decoration on a line that is itself clickable — there was nothing to say where one
     * button ended and the next began. The chip is what makes them buttons.</p>
     *
     * @param enabled a button that cannot act gets no chip, so "greyed out" is not
     *                contradicted by a hover state
     */
    private boolean rowButtonBackdrop(GuiGraphics g, int x, int y, int mouseX, int mouseY, boolean enabled) {
        boolean over = overButton(x, y, mouseX, mouseY);
        if (over && enabled) {
            Panels.fill(g, x - 3, y - 2, x + MediaWindow.BUTTON + 3, y + MediaWindow.BUTTON + 2,
                    Theme.CHIP_HOVER_BG);
        }
        return over && enabled;
    }

    private static boolean overButton(int x, int y, int mouseX, int mouseY) {
        return MediaWindow.inRect(mouseX, mouseY, x, y, MediaWindow.BUTTON, MediaWindow.BUTTON);
    }

    /**
     * A one-word tag saying which player an entry belongs to, so a list mixing a picture,
     * a song and a video is readable without opening any of them.
     */
    private static String kindPrefix(HistoryEntry entry) {
        return switch (entry.kind()) {
            case IMAGE -> "[picture] ";
            case VIDEO -> "[video] ";
            case AUDIO -> "[audio] ";
        };
    }

    private int heartX() {
        return rightX() + rightW() - ROW_BUTTONS_W;
    }

    private int addX() {
        return heartX() + ROW_BUTTON_PITCH;
    }

    private int removeX() {
        return addX() + ROW_BUTTON_PITCH;
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    // See the note in PlaylistScreen: 1.21.11 folded the mouse arguments into a
    // MouseButtonEvent record. Only the wrapper differs.
    //? if <1.21.11 {
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return onClick(mouseX, mouseY, button);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        return onClick(event.x(), event.y(), event.button());
    }
    *///?}

    private boolean onClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }

        List<Playlist> playlists = context().getPlaylistStore().all();
        int prows = visiblePlaylistRows();
        for (int i = 0; i < prows; i++) {
            int index = playlistScroll + i;
            if (index >= playlists.size()) {
                break;
            }
            int rowY = listTop() + i * ROW_PL;
            if (MediaWindow.inRect(mouseX, mouseY, leftX(), rowY, leftW(), ROW_PL - 1)) {
                // A second click on the selected playlist unselects it, which is the only
                // way back to "no target" once one has been picked.
                target = playlists.get(index) == target ? null : playlists.get(index);
                return true;
            }
        }

        List<HistoryEntry> entries = shownEntries();
        int rows = visibleEntryRows();
        for (int i = 0; i < rows; i++) {
            int index = entryScroll + i;
            if (index >= entries.size()) {
                break;
            }
            int rowY = listTop() + i * ROW_EN;
            if (!MediaWindow.inRect(mouseX, mouseY, rightX(), rowY, rightW(), ROW_EN - 1)) {
                continue;
            }
            HistoryEntry entry = entries.get(index);
            int btnY = rowY + (ROW_EN - MediaWindow.BUTTON) / 2;
            if (MediaWindow.inRect(mouseX, mouseY, heartX(), btnY, MediaWindow.BUTTON, MediaWindow.BUTTON)) {
                history().toggleFavorite(entry.url(), entry.kind());
            } else if (MediaWindow.inRect(mouseX, mouseY, addX(), btnY, MediaWindow.BUTTON, MediaWindow.BUTTON)) {
                addToTarget(entry);
            } else if (MediaWindow.inRect(mouseX, mouseY, removeX(), btnY, MediaWindow.BUTTON, MediaWindow.BUTTON)) {
                history().remove(entry.url());
                clampScroll();
            } else {
                // A click on the row itself plays it, exactly as clicking the link in
                // chat would have — same routing, same modifiers.
                MediaWindowOverlay.play(entry.url(), Keys.altDown(), Keys.shiftDown());
                onClose();
            }
            return true;
        }
        return false;
    }

    private void addToTarget(HistoryEntry entry) {
        if (target == null) {
            return;
        }
        target.add(entry.url());
        context().getPlaylistStore().save();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int dir = (int) -Math.signum(scrollY);
        if (dir != 0) {
            if (MediaWindow.inRect(mouseX, mouseY, leftX(), listTop(), leftW(), listBottom() - listTop())) {
                playlistScroll = Math.max(0, playlistScroll + dir);
                clampScroll();
                return true;
            }
            if (MediaWindow.inRect(mouseX, mouseY, rightX(), listTop(), rightW(), listBottom() - listTop())) {
                entryScroll = Math.max(0, entryScroll + dir);
                clampScroll();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void clampScroll() {
        int playlists = context().getPlaylistStore().all().size();
        playlistScroll = Mth.clamp(playlistScroll, 0, Math.max(0, playlists - visiblePlaylistRows()));
        entryScroll = Mth.clamp(entryScroll, 0, Math.max(0, shownEntries().size() - visibleEntryRows()));
    }

    @Override
    public void onClose() {
        Screens.open(lastScreen);
    }
}
