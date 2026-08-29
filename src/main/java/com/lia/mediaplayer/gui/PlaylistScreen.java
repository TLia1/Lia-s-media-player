package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.MediaPlayerContext;
import com.lia.mediaplayer.api.MediaKind;
import com.lia.mediaplayer.api.MediaRequest;
import com.lia.mediaplayer.api.RepeatMode;
import com.lia.mediaplayer.api.policy.PlayOrigin;
import com.lia.mediaplayer.media.YouTubePlaylistResolver;
import com.lia.mediaplayer.playlist.Playlist;
import com.lia.mediaplayer.playlist.PlaylistStore;
import com.lia.mediaplayer.source.Urls;
import com.lia.mediaplayer.source.YouTubePlaylistSource;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
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
 * The playlist manager, opened from the chat "Playlists" button or its keybind. The
 * left column lists saved playlists (click to select, plus a box to create one); the
 * right column edits the selected playlist — rename it, search it, add links, reorder or
 * remove entries, and play it in order or shuffled (both hand the URLs to
 * {@link AudioPlayerManager}).
 *
 * <p>Playlists are stored by {@link PlaylistStore}, which saves to disk after every
 * change, so edits made here persist between sessions.</p>
 *
 * <h2>The widgets are built once</h2>
 *
 * <p>This screen used to rebuild every widget on every edit — a selection, an added
 * link, a finished import — which threw away the focus and whatever was half-typed at
 * the time. It no longer rebuilds at all: the right-hand widgets are created up front
 * and merely {@linkplain AbstractWidget#visible hidden} while nothing is selected, and
 * the two lists are drawn by hand, so adding, removing or reordering an entry changes
 * only what the next frame draws. An import that lands while you are typing now leaves
 * the box alone.</p>
 *
 * <h2>Dragging a track</h2>
 *
 * <p>The arrows move an entry one place; a drag moves it to where you point, which is
 * the difference between reordering a playlist of five and one of fifty. It is driven by
 * {@link DragTarget} rather than by overriding vanilla's drag callbacks — see there for
 * why. Dragging is off while a search is filtering the list: the rows on screen are then
 * not consecutive, and "drop it here" would have no honest answer.</p>
 */
public final class PlaylistScreen extends Screen implements DragTarget {

    private static final int ROW_PL = 14;
    /** Tall enough for a thumbnail, which is what makes the list readable at a glance. */
    private static final int ROW_EN = Thumbnail.H + 3;
    private static final int BUTTON = MediaWindow.BUTTON;
    /** Pitch of a row's action buttons: the glyph box plus the gap after it. */
    private static final int ROW_BUTTON_PITCH = BUTTON + 7;
    /** Room the three row buttons take on the right of an entry, margin included. */
    private static final int ROW_BUTTONS_W = 3 * ROW_BUTTON_PITCH + 2;
    /** How far the cursor must move before a press on a row becomes a drag. */
    private static final int DRAG_SLOP = 3;
    /** How often a drag held against the top or bottom edge scrolls the list. */
    private static final int DRAG_SCROLL_MS = 120;

    @Nullable
    private Playlist selected;
    private int playlistScroll;
    /** Scroll position within {@link #shownEntries()}, not within the playlist. */
    private int entryScroll;
    /**
     * Whether the "Play" / "Shuffle" buttons start the playlist looping. Shuffle stays
     * on for the player, so a looping shuffled playlist is reshuffled every round.
     */
    private boolean loopOnPlay;
    /**
     * YouTube playlist expansions still in flight (each one is a background yt-dlp
     * call), so the screen can say it is working instead of looking like it ignored
     * the link.
     */
    private int pendingImports;
    /** The search text, kept here so the box is never rebuilt to apply it. */
    private String query = "";

    // The drag in progress: which entry was picked up, whether the cursor has moved far
    // enough for it to count, and where it is now.
    private int dragIndex = -1;
    private boolean dragging;
    private double dragY;
    private double dragStartY;
    private long lastDragScrollAt;

    @Nullable
    private EditBox newNameBox;
    @Nullable
    private EditBox nameBox;
    @Nullable
    private EditBox addBox;
    @Nullable
    private EditBox searchBox;
    /** The widgets that only mean anything with a playlist selected. */
    private final List<AbstractWidget> selectionWidgets = new ArrayList<>();

    public PlaylistScreen() {
        super(Component.translatable("gui.liasmediaplayer.playlists.title"));
    }

    private static MediaPlayerContext getContext() {
        return MediaPlayerContext.get();
    }

    @Override
    public boolean isPauseScreen() {
        return false; // keep the game (and any other players) running while editing
    }

    // ------------------------------------------------------------------
    // Layout helpers (read live so the screen reflows on resize)
    // ------------------------------------------------------------------

    private int leftX() {
        return 16;
    }

    private int leftW() {
        return 150;
    }

    private int listTop() {
        return 44;
    }

    private int listBottom() {
        return height - 64;
    }

    private int rightX() {
        return leftX() + leftW() + 16;
    }

    private int rightW() {
        return Math.max(120, width - rightX() - 16);
    }

    /** The rename row, at the top of the right-hand column. */
    private int nameRowY() {
        return 40;
    }

    /** The search row, under the name. */
    private int searchRowY() {
        return 62;
    }

    /** Where the "n entries" caption sits, between the search box and the list. */
    private int captionY() {
        return 84;
    }

    private int entriesTop() {
        return 94;
    }

    private int entriesBottom() {
        return height - 88;
    }

    private int visiblePlaylistRows() {
        return Math.max(1, (listBottom() - listTop()) / ROW_PL);
    }

    private int visibleEntryRows() {
        return Math.max(1, (entriesBottom() - entriesTop()) / ROW_EN);
    }

    // ------------------------------------------------------------------
    // Widgets
    // ------------------------------------------------------------------

    /**
     * Builds every widget the screen will ever have. Called once on open, and again by
     * vanilla only when the window is resized.
     */
    @Override
    protected void init() {
        selectionWidgets.clear();

        // Left: create a new playlist, or paste one in.
        newNameBox = new EditBox(font, leftX(), height - 58, leftW() - 50, 18, Component.translatable("gui.liasmediaplayer.playlists.new"));
        newNameBox.setMaxLength(64);
        newNameBox.setHint(Component.translatable("gui.liasmediaplayer.playlists.new_name"));
        addRenderableWidget(newNameBox);
        addRenderableWidget(Button.builder(Component.translatable("gui.liasmediaplayer.playlists.button.create"), b -> createPlaylist())
                .bounds(leftX() + leftW() - 46, height - 58, 20, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.liasmediaplayer.playlists.tooltip.create")))
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.liasmediaplayer.playlists.button.import"), b -> importClipboard())
                .bounds(leftX() + leftW() - 22, height - 58, 22, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.liasmediaplayer.playlists.tooltip.import")))
                .build());

        // Bottom: the library, then close.
        addRenderableWidget(Button.builder(Component.translatable("gui.liasmediaplayer.playlists.button.history"),
                        b -> Screens.open(new HistoryScreen(this)))
                .bounds(Math.max(4, width / 2 - 164), height - 26, 80, 20)
                .tooltip(Tooltip.create(
                        Component.translatable("gui.liasmediaplayer.playlists.tooltip.history")))
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.liasmediaplayer.playlists.button.done"), b -> onClose())
                .bounds(width / 2 - 80, height - 26, 160, 20).build());
        // Whatever addons registered, to the right of "Done" — see api.screen.MediaScreenTab.
        ScreenTabs.addTo(this, width / 2 + 84, height - 26, this::addRenderableWidget);

        buildSelectionWidgets();
        syncSelection();
    }

    /**
     * The right-hand column: everything that acts on the selected playlist. Built
     * whether or not one is selected, and hidden until one is — a hidden widget takes
     * neither clicks nor focus, so this is the whole of what used to be a rebuild.
     */
    private void buildSelectionWidgets() {
        int rx = rightX();
        int rw = rightW();

        nameBox = new EditBox(font, rx, nameRowY(), rw - 60, 18, Component.translatable("gui.liasmediaplayer.playlists.name"));
        nameBox.setMaxLength(64);
        addSelectionWidget(nameBox);
        addSelectionWidget(Button.builder(Component.translatable("gui.liasmediaplayer.playlists.button.rename"), b -> rename())
                .bounds(rx + rw - 56, nameRowY(), 56, 18).build());

        searchBox = new EditBox(font, rx, searchRowY(), rw, 18, Component.translatable("gui.liasmediaplayer.playlists.search"));
        searchBox.setHint(Component.translatable("gui.liasmediaplayer.playlists.search"));
        searchBox.setValue(query);
        // Filtering happens where the list is drawn, so the responder only notes what was
        // typed: no widget is rebuilt and the box never loses what is in it.
        searchBox.setResponder(value -> {
            query = value;
            entryScroll = 0;
            cancelDrag();
        });
        addSelectionWidget(searchBox);

        addBox = new EditBox(font, rx, height - 82, rw - 60, 18, Component.translatable("gui.liasmediaplayer.playlists.add"));
        addBox.setMaxLength(1024);
        addBox.setHint(Component.translatable("gui.liasmediaplayer.playlists.paste_link"));
        addSelectionWidget(addBox);
        addSelectionWidget(Button.builder(Component.translatable("gui.liasmediaplayer.playlists.button.add"), b -> addEntry())
                .bounds(rx + rw - 56, height - 82, 56, 18).build());

        int bw = (rw - 16) / 5;
        int by = height - 58;
        addSelectionWidget(Button.builder(Component.translatable("gui.liasmediaplayer.playlists.play"),
                        b -> play(false))
                .bounds(rx, by, bw, 20).build());
        addSelectionWidget(Button.builder(Component.translatable("gui.liasmediaplayer.playlists.shuffle"),
                        b -> play(true))
                .bounds(rx + bw + 4, by, bw, 20).build());
        addSelectionWidget(Button.builder(loopLabel(), b -> {
                    loopOnPlay = !loopOnPlay;
                    b.setMessage(loopLabel());
                })
                .bounds(rx + (bw + 4) * 2, by, bw, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.liasmediaplayer.playlists.tooltip.loop")))
                .build());
        addSelectionWidget(Button.builder(Component.translatable("gui.liasmediaplayer.playlists.button.export"),
                        b -> exportClipboard())
                .bounds(rx + (bw + 4) * 3, by, bw, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.liasmediaplayer.playlists.tooltip.export")))
                .build());
        addSelectionWidget(Button.builder(Component.translatable("gui.liasmediaplayer.playlists.delete"),
                        b -> deleteSelected())
                .bounds(rx + (bw + 4) * 4, by, rw - (bw + 4) * 4, 20).build());
    }

    private <T extends AbstractWidget> void addSelectionWidget(T widget) {
        selectionWidgets.add(widget);
        addRenderableWidget(widget);
    }

    /**
     * Shows or hides the right-hand column for the current selection, and puts the
     * selected playlist's name in the rename box. The only thing a selection change has
     * to do.
     */
    private void syncSelection() {
        boolean has = selected != null;
        for (AbstractWidget widget : selectionWidgets) {
            widget.visible = has;
        }
        if (has && nameBox != null) {
            nameBox.setValue(selected.name());
        } else if (!has) {
            // A hidden widget takes no clicks, but it can still be holding the focus it
            // had when the playlist under it was deleted — and then the next keystroke
            // goes into a box nobody can see.
            setFocused(null);
        }
        cancelDrag();
        clampScroll();
    }

    private void createPlaylist() {
        String name = newNameBox != null ? newNameBox.getValue() : "";
        selected = getContext().getPlaylistStore().create(name);
        if (newNameBox != null) {
            newNameBox.setValue("");
        }
        clearSearch();
        syncSelection();
    }

    private void rename() {
        if (selected == null || nameBox == null) {
            return;
        }
        String name = nameBox.getValue().strip();
        if (!name.isBlank()) {
            selected.setName(name);
            getContext().getPlaylistStore().save();
        }
    }

    private void addEntry() {
        if (selected == null || addBox == null) {
            return;
        }
        String url = addBox.getValue().strip();
        // A YouTube playlist link stands for all of its videos, so it is expanded into
        // the entries instead of being stored as one unplayable page link.
        if (YouTubePlaylistSource.isPlaylist(url)) {
            importYouTubePlaylist(selected, url, false);
            addBox.setValue("");
            return;
        }
        // Same rule as importClipboard: only real http(s) links get stored, so a playlist
        // can never feed something else to the player on a later session.
        if (Urls.isHttp(url)) {
            selected.add(url);
            getContext().getPlaylistStore().save();
            MediaPlayerContext.get().getTitleCache().getOrLoad(url); // warm the name for the list
            addBox.setValue("");
            clampScroll();
        }
    }

    /**
     * Appends every video of a YouTube playlist to {@code target}, in the background.
     * With {@code renameIfDefault} the freshly created playlist also takes the name it
     * has on YouTube, which is nicer than "Imported Playlist".
     */
    private void importYouTubePlaylist(Playlist target, String url, boolean renameIfDefault) {
        pendingImports++;
        String defaultName = target.name();
        YouTubePlaylistResolver.loadAsync(url, result -> {
            pendingImports--;
            if (result == null) {
                return; // the resolver has already said why in chat
            }
            for (String entry : result.urls()) {
                target.add(entry);
                MediaPlayerContext.get().getTitleCache().getOrLoad(entry); // warm the names for the list
            }
            if (renameIfDefault && !result.title().isBlank() && target.name().equals(defaultName)) {
                target.setName(result.title());
                if (target == selected && nameBox != null) {
                    nameBox.setValue(target.name());
                }
            }
            getContext().getPlaylistStore().save();
            if (Screens.current() == this) {
                // Only the scroll bounds moved: the rows are drawn from the playlist
                // every frame, so an import that lands mid-edit disturbs nothing else.
                clampScroll();
            }
        });
    }

    private Component loopLabel() {
        return Component.translatable(loopOnPlay
                ? "gui.liasmediaplayer.playlists.loop.on"
                : "gui.liasmediaplayer.playlists.loop.off");
    }

    private void play(boolean shuffle) {
        if (selected == null || selected.isEmpty()) {
            return;
        }
        // Through the request path, so a registered MediaInterceptor sees a playlist
        // being started the way it sees a chat click — with PLAYLIST as the origin, which
        // is exactly the distinction an addon that gates other people's links wants.
        MediaRequest request = MediaRequest.ofAll(selected.urls())
                .as(MediaKind.AUDIO)
                .newWindow(true)
                .shuffle(shuffle)
                .repeat(loopOnPlay ? RepeatMode.ALL : RepeatMode.OFF);
        if (MediaWindowOverlay.play(request, PlayOrigin.PLAYLIST) != null) {
            onClose();
        }
    }

    private void deleteSelected() {
        if (selected != null) {
            getContext().getPlaylistStore().delete(selected);
            selected = null;
            clearSearch();
            syncSelection();
        }
    }

    private void clearSearch() {
        query = "";
        if (searchBox != null) {
            searchBox.setValue("");
        }
    }

    private void exportClipboard() {
        if (selected != null && !selected.isEmpty()) {
            String out = String.join("\n", selected.urls());
            minecraft.keyboardHandler.setClipboard(out);
        }
    }

    private void importClipboard() {
        String in = minecraft.keyboardHandler.getClipboard();
        if (in == null || in.isBlank()) {
            return;
        }
        List<String> direct = new ArrayList<>();
        List<String> youtubePlaylists = new ArrayList<>();
        for (String line : in.split("\n")) {
            String url = line.strip();
            if (YouTubePlaylistSource.isPlaylist(url)) {
                youtubePlaylists.add(url);
            } else if (url.startsWith("http://") || url.startsWith("https://")) {
                direct.add(url);
            }
        }
        Playlist pl = getContext().getPlaylistStore().create(Component.translatable("gui.liasmediaplayer.playlists.imported").getString());
        if (direct.isEmpty() && youtubePlaylists.isEmpty()) {
            getContext().getPlaylistStore().delete(pl); // nothing usable on the clipboard
            return;
        }
        for (String url : direct) {
            pl.add(url);
        }
        getContext().getPlaylistStore().save();
        selected = pl;
        clearSearch();
        syncSelection();
        // A single pasted YouTube playlist also names the new playlist after it.
        boolean rename = direct.isEmpty() && youtubePlaylists.size() == 1;
        for (String url : youtubePlaylists) {
            importYouTubePlaylist(pl, url, rename);
        }
    }

    // ------------------------------------------------------------------
    // The entries being shown
    // ------------------------------------------------------------------

    /**
     * The positions in the selected playlist that the search box lets through, in play
     * order.
     *
     * <p>Positions rather than URLs: a row has to say where the track sits in the
     * playlist and its buttons have to act on that same place, neither of which survives
     * being turned into a filtered copy of the list.</p>
     */
    private List<Integer> shownEntries() {
        List<Integer> shown = new ArrayList<>();
        if (selected == null) {
            return shown;
        }
        List<String> urls = selected.urls();
        String needle = query.strip().toLowerCase(Locale.ROOT);
        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(i);
            if (needle.isEmpty()
                    || MediaPlayerContext.get().getTitleCache().getOrLoad(url).toLowerCase(Locale.ROOT).contains(needle)
                    || url.toLowerCase(Locale.ROOT).contains(needle)) {
                shown.add(i);
            }
        }
        return shown;
    }

    /** Whether the list on screen is the whole playlist, in order — see {@link DragTarget}. */
    private boolean isReorderable() {
        return query.strip().isEmpty();
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
        super.render(g, mouseX, mouseY, partialTick); // background + widgets
        draw(g, mouseX, mouseY);
    }
    //?} else {
    /*@Override
    public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick); // background + widgets
        draw(g, mouseX, mouseY);
    }
    *///?}

    private void draw(GuiGraphics g, int mouseX, int mouseY) {
        g.drawCenteredString(font, title, width / 2, 14, Theme.TEXT);

        renderPlaylistList(g, mouseX, mouseY);

        if (pendingImports > 0) {
            g.drawCenteredString(font, Component.translatable("gui.liasmediaplayer.playlists.importing"),
                    width / 2, height - 70, Theme.TEXT_SUBTLE);
        }

        if (selected != null) {
            renderEntries(g, mouseX, mouseY);
        } else {
            renderNoSelection(g);
        }
        // The rows are drawn by hand, not by widgets, so they have no setTooltip of their
        // own; the mod's seam collects what a row asked for and draws it here, last.
        Tooltips.renderPending(g, mouseX, mouseY);
    }

    /**
     * What the right-hand column says when there is nothing to edit: what to do, rather
     * than a bare instruction to select something that may not exist yet.
     */
    private void renderNoSelection(GuiGraphics g) {
        boolean any = !getContext().getPlaylistStore().all().isEmpty();
        int cx = rightX() + rightW() / 2;
        int cy = (entriesTop() + entriesBottom()) / 2;
        g.drawCenteredString(font, Component.translatable(any
                        ? "gui.liasmediaplayer.playlists.select"
                        : "gui.liasmediaplayer.playlists.none"),
                cx, cy - 10, Theme.TEXT_SUBTLE);
        g.drawCenteredString(font, Component.translatable(any
                        ? "gui.liasmediaplayer.playlists.select.hint"
                        : "gui.liasmediaplayer.playlists.none.hint"),
                cx, cy + 4, Theme.TEXT_DIM);
    }

    private void renderPlaylistList(GuiGraphics g, int mouseX, int mouseY) {
        List<Playlist> playlists = getContext().getPlaylistStore().all();
        int x = leftX();
        int w = leftW();
        int top = listTop();
        g.drawString(font, Component.translatable("gui.liasmediaplayer.playlists.count", playlists.size()), x, top - 12, Theme.TEXT_SUBTLE);
        g.fill(x, top, x + w, listBottom(), Theme.LIST_BG);

        int rows = visiblePlaylistRows();
        for (int i = 0; i < rows; i++) {
            int index = playlistScroll + i;
            if (index >= playlists.size()) {
                break;
            }
            int rowY = top + i * ROW_PL;
            Playlist playlist = playlists.get(index);
            boolean isSel = playlist == selected;
            boolean over = MediaWindow.inRect(mouseX, mouseY, x, rowY, w, ROW_PL - 1);
            int bg = isSel ? Theme.ROW_SELECTED_BG : (over ? Theme.ROW_HOVER_BG : Theme.ROW_BG);
            g.fill(x + 1, rowY, x + w - 1, rowY + ROW_PL - 1, bg);
            String label = playlist.name() + "  (" + playlist.size() + ")";
            g.drawString(font, Component.literal(Glyphs.fit(font, label, w - 8)), x + 4, rowY + 3, Theme.TEXT);
        }
    }

    private void renderEntries(GuiGraphics g, int mouseX, int mouseY) {
        if (selected == null) {
            return;
        }
        List<Integer> shown = shownEntries();
        int x = rightX();
        int w = rightW();
        int top = entriesTop();

        Component caption = query.strip().isEmpty()
                ? Component.translatable("gui.liasmediaplayer.playlists.entries", selected.size())
                : Component.translatable("gui.liasmediaplayer.playlists.entries.filtered",
                        shown.size(), selected.size());
        g.drawString(font, caption, x, captionY(), Theme.TEXT_SUBTLE);
        g.fill(x, top, x + w, entriesBottom(), Theme.LIST_BG);

        if (selected.isEmpty()) {
            drawEmptyList(g, "gui.liasmediaplayer.playlists.empty", "gui.liasmediaplayer.playlists.empty.hint");
            return;
        }
        if (shown.isEmpty()) {
            drawEmptyList(g, "gui.liasmediaplayer.playlists.no_match", "gui.liasmediaplayer.playlists.no_match.hint");
            return;
        }

        boolean reorderable = isReorderable();
        int rows = visibleEntryRows();
        for (int i = 0; i < rows; i++) {
            int position = entryScroll + i;
            if (position >= shown.size()) {
                break;
            }
            renderEntryRow(g, shown.get(position), top + i * ROW_EN, mouseX, mouseY, reorderable);
        }
        if (dragging) {
            renderDropLine(g);
        }
    }

    private void drawEmptyList(GuiGraphics g, String titleKey, String hintKey) {
        int cx = rightX() + rightW() / 2;
        int cy = (entriesTop() + entriesBottom()) / 2;
        g.drawCenteredString(font, Component.translatable(titleKey), cx, cy - 10, Theme.TEXT_SUBTLE);
        g.drawCenteredString(font, Component.translatable(hintKey), cx, cy + 4, Theme.TEXT_DIM);
    }

    /**
     * One track: its grip, its picture, its position and name, and the three buttons
     * that move or drop it.
     *
     * @param index the entry's position in the playlist, which is what the row shows and
     *              what its buttons act on — not its position on screen
     */
    private void renderEntryRow(GuiGraphics g, int index, int rowY, int mouseX, int mouseY, boolean reorderable) {
        if (selected == null) {
            return;
        }
        String url = selected.urls().get(index);
        int x = rightX();
        int w = rightW();
        int btnY = rowY + (ROW_EN - BUTTON) / 2;

        boolean overButtons = MediaWindow.inRect(mouseX, mouseY, upX() - 3, rowY, ROW_BUTTONS_W, ROW_EN - 1);
        boolean overRow = MediaWindow.inRect(mouseX, mouseY, x, rowY, w, ROW_EN - 1);
        boolean isSource = dragging && index == dragIndex;
        int bg = isSource ? Theme.ROW_SELECTED_BG
                : ((overRow && !overButtons) ? Theme.ROW_HOVER_BG : Theme.ROW_BG);
        g.fill(x + 1, rowY, x + w - 1, rowY + ROW_EN - 1, bg);

        // The grip says the row can be picked up; it greys out with the arrows when a
        // search has made "where you dropped it" meaningless.
        Glyphs.dragHandle(g, x + 3, btnY, reorderable
                ? (overRow && !overButtons ? Theme.ICON : Theme.ICON_INACTIVE)
                : Theme.ICON_DISABLED);

        int thumbX = x + 3 + BUTTON + 3;
        Thumbnail.draw(g, font, url, thumbX, rowY + (ROW_EN - Thumbnail.H) / 2);

        int labelX = thumbX + Thumbnail.W + 5;
        int labelMax = upX() - 6 - labelX;
        // Nothing rather than something spilling over the buttons: on a GUI scale narrow
        // enough for the grip, the picture and the three buttons to fill the row, the
        // name is what has to give — Glyphs.fit reads a width of zero as "no limit".
        if (labelMax >= 16) {
            String label = (index + 1) + ". " + MediaPlayerContext.get().getTitleCache().getOrLoad(url);
            g.drawString(font, Component.literal(Glyphs.fit(font, label, labelMax)),
                    labelX, rowY + (ROW_EN - font.lineHeight) / 2, Theme.TEXT);
        }

        boolean canUp = reorderable && index > 0;
        boolean canDown = reorderable && index < selected.size() - 1;
        boolean overUp = rowButton(g, upX(), btnY, mouseX, mouseY, canUp);
        Glyphs.arrow(g, upX(), btnY, true, canUp ? (overUp ? Theme.ICON_HOVER : Theme.ICON) : Theme.ICON_DISABLED);
        if (overUp) {
            Tooltips.request(Component.translatable("gui.liasmediaplayer.playlists.tooltip.up"));
        }
        boolean overDown = rowButton(g, downX(), btnY, mouseX, mouseY, canDown);
        Glyphs.arrow(g, downX(), btnY, false, canDown ? (overDown ? Theme.ICON_HOVER : Theme.ICON) : Theme.ICON_DISABLED);
        if (overDown) {
            Tooltips.request(Component.translatable("gui.liasmediaplayer.playlists.tooltip.down"));
        }
        boolean overRemove = rowButton(g, removeX(), btnY, mouseX, mouseY, true);
        Glyphs.close(g, removeX(), btnY, overRemove ? Theme.DANGER : Theme.ICON);
        if (overRemove) {
            Tooltips.request(Component.translatable("gui.liasmediaplayer.playlists.tooltip.remove"));
        }
    }

    /**
     * Draws the chip behind a hovered row button and reports whether it was hovered — the
     * same treatment the history screen gives its rows, so a button reads as a button in
     * both. A button that cannot act gets no chip.
     */
    private boolean rowButton(GuiGraphics g, int x, int y, int mouseX, int mouseY, boolean enabled) {
        boolean over = MediaWindow.inRect(mouseX, mouseY, x, y, BUTTON, BUTTON);
        if (over && enabled) {
            Panels.fill(g, x - 3, y - 2, x + BUTTON + 3, y + BUTTON + 2, Theme.CHIP_HOVER_BG);
        }
        return over && enabled;
    }

    /** The line showing where the dragged track would land. */
    private void renderDropLine(GuiGraphics g) {
        int gap = dropGap();
        int y = entriesTop() + (gap - entryScroll) * ROW_EN;
        y = Mth.clamp(y, entriesTop(), entriesBottom() - 1);
        int x = rightX();
        int w = rightW();
        g.fill(x + 2, y - 1, x + w - 2, y + 1, Theme.FILL);
        // Small caps at each end, so the line reads as an insertion point rather than as
        // a divider that was always there.
        g.fill(x + 2, y - 3, x + 4, y + 3, Theme.FILL);
        g.fill(x + w - 4, y - 3, x + w - 2, y + 3, Theme.FILL);
    }

    private int upX() {
        return rightX() + rightW() - ROW_BUTTONS_W;
    }

    private int downX() {
        return upX() + ROW_BUTTON_PITCH;
    }

    private int removeX() {
        return downX() + ROW_BUTTON_PITCH;
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    // The mouse callbacks were folded into input event records: 1.21.11 passes a
    // MouseButtonEvent (gui-scaled x/y plus the button and its modifiers) and a
    // double-click flag instead of three loose arguments. Only the override
    // wrapper differs, so the hit testing below stays in one place and each
    // version's signature just unpacks its own arguments into it. Threshold
    // unbisected: 1.21.9 and 1.21.10 are not targets.
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

        // Left list: select a playlist.
        List<Playlist> playlists = getContext().getPlaylistStore().all();
        int rows = visiblePlaylistRows();
        for (int i = 0; i < rows; i++) {
            int index = playlistScroll + i;
            if (index >= playlists.size()) {
                break;
            }
            int rowY = listTop() + i * ROW_PL;
            if (MediaWindow.inRect(mouseX, mouseY, leftX(), rowY, leftW(), ROW_PL - 1)) {
                selected = playlists.get(index);
                entryScroll = 0;
                clearSearch();
                syncSelection();
                return true;
            }
        }

        return selected != null && clickEntry(mouseX, mouseY);
    }

    /** A click in the entry list: a row button, or the start of a drag on the row. */
    private boolean clickEntry(double mouseX, double mouseY) {
        List<Integer> shown = shownEntries();
        int erows = visibleEntryRows();
        boolean reorderable = isReorderable();
        for (int i = 0; i < erows; i++) {
            int position = entryScroll + i;
            if (position >= shown.size()) {
                break;
            }
            int index = shown.get(position);
            int rowY = entriesTop() + i * ROW_EN;
            if (!MediaWindow.inRect(mouseX, mouseY, rightX(), rowY, rightW(), ROW_EN - 1)) {
                continue;
            }
            int btnY = rowY + (ROW_EN - BUTTON) / 2;
            if (MediaWindow.inRect(mouseX, mouseY, removeX(), btnY, BUTTON, BUTTON)) {
                selected.removeAt(index);
                getContext().getPlaylistStore().save();
                clampScroll();
            } else if (reorderable && MediaWindow.inRect(mouseX, mouseY, downX(), btnY, BUTTON, BUTTON)) {
                if (index < selected.size() - 1) {
                    selected.swap(index, index + 1);
                    getContext().getPlaylistStore().save();
                }
            } else if (reorderable && MediaWindow.inRect(mouseX, mouseY, upX(), btnY, BUTTON, BUTTON)) {
                if (index > 0) {
                    selected.swap(index, index - 1);
                    getContext().getPlaylistStore().save();
                }
            } else if (reorderable) {
                // Anywhere else on the row picks it up. Nothing has happened yet: a press
                // that never moves is not a drag, and this list has nothing a plain click
                // should do.
                dragIndex = index;
                dragStartY = mouseY;
                dragY = mouseY;
                dragging = false;
            }
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Dragging a track (see DragTarget)
    // ------------------------------------------------------------------

    @Override
    public boolean onDrag(double mouseX, double mouseY) {
        if (dragIndex < 0 || selected == null) {
            return false;
        }
        dragY = mouseY;
        if (!dragging && Math.abs(mouseY - dragStartY) > DRAG_SLOP) {
            dragging = true;
        }
        if (dragging) {
            scrollWhileDragging(mouseY);
        }
        return dragging;
    }

    @Override
    public boolean onRelease() {
        boolean wasDragging = dragging;
        if (dragging && selected != null) {
            selected.move(dragIndex, dropGap());
            getContext().getPlaylistStore().save();
        }
        cancelDrag();
        return wasDragging;
    }

    private void cancelDrag() {
        dragIndex = -1;
        dragging = false;
    }

    /**
     * Where the dragged track would be inserted: the gap between rows nearest the
     * cursor, as an index into the playlist.
     *
     * <p>Held to the rows actually on screen. Rounding a cursor far above the list
     * straight to "the very top" would move a track from the fortieth position to the
     * first because the pointer strayed onto the search box.</p>
     */
    private int dropGap() {
        if (selected == null) {
            return 0;
        }
        int gap = entryScroll + (int) Math.round((dragY - entriesTop()) / (double) ROW_EN);
        gap = Mth.clamp(gap, entryScroll, entryScroll + visibleEntryRows());
        return Mth.clamp(gap, 0, selected.size());
    }

    /**
     * Scrolls the list when a drag is held against its top or bottom edge, so a track can
     * be moved further than one screenful. Stepped on a timer rather than per frame,
     * which would fly past the whole list in a moment.
     */
    private void scrollWhileDragging(double mouseY) {
        int direction = 0;
        if (mouseY < entriesTop() + ROW_EN / 2) {
            direction = -1;
        } else if (mouseY > entriesBottom() - ROW_EN / 2) {
            direction = 1;
        }
        if (direction == 0 || Anim.progress(lastDragScrollAt, DRAG_SCROLL_MS) < 1.0) {
            return;
        }
        lastDragScrollAt = Anim.now();
        entryScroll += direction;
        clampScroll();
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
            if (selected != null
                    && MediaWindow.inRect(mouseX, mouseY, rightX(), entriesTop(), rightW(), entriesBottom() - entriesTop())) {
                entryScroll = Math.max(0, entryScroll + dir);
                clampScroll();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void clampScroll() {
        int playlists = getContext().getPlaylistStore().all().size();
        playlistScroll = Mth.clamp(playlistScroll, 0, Math.max(0, playlists - visiblePlaylistRows()));
        int entries = selected != null ? shownEntries().size() : 0;
        entryScroll = Mth.clamp(entryScroll, 0, Math.max(0, entries - visibleEntryRows()));
    }
}
