package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.media.MediaTitleCache;
import com.lia.mediaplayer.playlist.Playlist;
import com.lia.mediaplayer.playlist.PlaylistStore;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The playlist manager, opened from the chat "Playlists" button or its keybind. The
 * left column lists saved playlists (click to select, plus a box to create one); the
 * right column edits the selected playlist — rename it, add links, remove entries, and
 * play it in order or shuffled (both hand the URLs to {@link AudioPlayerManager}).
 *
 * <p>Playlists are stored by {@link PlaylistStore}, which saves to disk after every
 * change, so edits made here persist between sessions.</p>
 */
public final class PlaylistScreen extends Screen {

    private static final int ROW_PL = 14;
    private static final int ROW_EN = 16;
    private static final int PANEL_BG = 0xC0101010;
    private static final int ROW_BG = 0xFF202020;
    private static final int ROW_HOVER_BG = 0xFF2E2E38;
    private static final int ROW_SELECTED_BG = 0xFF394A6B;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int SUBTLE = 0xFFAAAAAA;

    @Nullable
    private Playlist selected;
    private int playlistScroll;
    private int entryScroll;

    @Nullable
    private EditBox newNameBox;
    @Nullable
    private EditBox nameBox;
    @Nullable
    private EditBox addBox;

    public PlaylistScreen() {
        super(Component.translatable("gui.liasmediaplayer.playlists.title"));
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

    private int entriesTop() {
        return 76;
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

    @Override
    protected void init() {
        rebuild();
    }

    /** Rebuilds the widgets for the current selection (called on open and after edits). */
    private void rebuild() {
        clearWidgets();

        // Left: create a new playlist.
        newNameBox = new EditBox(font, leftX(), height - 58, leftW() - 50, 18, Component.translatable("gui.liasmediaplayer.playlists.new"));
        newNameBox.setMaxLength(64);
        newNameBox.setHint(Component.translatable("gui.liasmediaplayer.playlists.new_name"));
        addRenderableWidget(newNameBox);
        addRenderableWidget(Button.builder(Component.literal("+"), b -> createPlaylist())
                .bounds(leftX() + leftW() - 46, height - 58, 20, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.liasmediaplayer.playlists.button.import"), b -> importClipboard())
                .bounds(leftX() + leftW() - 22, height - 58, 22, 18)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.liasmediaplayer.playlists.tooltip.import")))
                .build());

        // Bottom: close and options.
        addRenderableWidget(Button.builder(Component.translatable("gui.liasmediaplayer.playlists.button.done"), b -> onClose())
                .bounds(width / 2 - 82, height - 26, 80, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.options"), b -> this.minecraft.setScreen(new com.lia.mediaplayer.gui.ConfigScreen(this)))
                .bounds(width / 2 + 2, height - 26, 80, 20).build());

        if (selected != null) {
            int rx = rightX();
            int rw = rightW();

            nameBox = new EditBox(font, rx, 40, rw - 60, 18, Component.translatable("gui.liasmediaplayer.playlists.name"));
            nameBox.setMaxLength(64);
            nameBox.setValue(selected.name());
            addRenderableWidget(nameBox);
            addRenderableWidget(Button.builder(Component.translatable("gui.liasmediaplayer.playlists.button.rename"), b -> rename())
                    .bounds(rx + rw - 56, 40, 56, 18).build());

            addBox = new EditBox(font, rx, height - 82, rw - 60, 18, Component.translatable("gui.liasmediaplayer.playlists.add"));
            addBox.setMaxLength(1024);
            addBox.setHint(Component.translatable("gui.liasmediaplayer.playlists.paste_link"));
            addRenderableWidget(addBox);
            addRenderableWidget(Button.builder(Component.translatable("gui.liasmediaplayer.playlists.button.add"), b -> addEntry())
                    .bounds(rx + rw - 56, height - 82, 56, 18).build());

            int bw = (rw - 12) / 4;
            int by = height - 58;
            addRenderableWidget(Button.builder(Component.translatable("gui.liasmediaplayer.playlists.play"),
                            b -> play(false))
                    .bounds(rx, by, bw, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.liasmediaplayer.playlists.shuffle"),
                            b -> play(true))
                    .bounds(rx + bw + 4, by, bw, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.liasmediaplayer.playlists.button.export"),
                            b -> exportClipboard())
                    .bounds(rx + (bw + 4) * 2, by, bw, 20)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.liasmediaplayer.playlists.tooltip.export")))
                    .build());
            addRenderableWidget(Button.builder(Component.translatable("gui.liasmediaplayer.playlists.delete"),
                            b -> deleteSelected())
                    .bounds(rx + (bw + 4) * 3, by, rw - (bw + 4) * 3, 20).build());
        } else {
            nameBox = null;
            addBox = null;
        }
        clampScroll();
    }

    private void createPlaylist() {
        String name = newNameBox != null ? newNameBox.getValue() : "";
        selected = PlaylistStore.create(name);
        if (newNameBox != null) {
            newNameBox.setValue("");
        }
        rebuild();
    }

    private void rename() {
        if (selected == null || nameBox == null) {
            return;
        }
        String name = nameBox.getValue().strip();
        if (!name.isBlank()) {
            selected.setName(name);
            PlaylistStore.save();
        }
    }

    private void addEntry() {
        if (selected == null || addBox == null) {
            return;
        }
        String url = addBox.getValue().strip();
        if (!url.isBlank()) {
            selected.add(url);
            PlaylistStore.save();
            MediaTitleCache.getOrLoad(url); // warm the name for the list
            addBox.setValue("");
            rebuild();
        }
    }

    private void play(boolean shuffle) {
        if (selected != null && !selected.isEmpty()) {
            AudioPlayerManager.playAll(selected.urls(), shuffle);
            onClose();
        }
    }

    private void deleteSelected() {
        if (selected != null) {
            PlaylistStore.delete(selected);
            selected = null;
            rebuild();
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
        if (in != null && !in.isBlank()) {
            Playlist pl = PlaylistStore.create(Component.translatable("gui.liasmediaplayer.playlists.imported").getString());
            for (String line : in.split("\n")) {
                String url = line.strip();
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    pl.add(url);
                }
            }
            if (pl.isEmpty()) {
                PlaylistStore.delete(pl);
            } else {
                PlaylistStore.save();
                selected = pl;
                rebuild();
            }
        }
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick); // background + widgets

        g.drawCenteredString(font, title, width / 2, 14, TEXT);

        renderPlaylistList(g, mouseX, mouseY);

        if (selected != null) {
            g.drawString(font, Component.translatable("gui.liasmediaplayer.playlists.entries", selected.size()),
                    rightX(), entriesTop() - 12, SUBTLE);
            renderEntries(g, mouseX, mouseY);
        } else {
            g.drawCenteredString(font, Component.translatable("gui.liasmediaplayer.playlists.select"),
                    rightX() + rightW() / 2, height / 2, SUBTLE);
        }
    }

    private void renderPlaylistList(GuiGraphics g, int mouseX, int mouseY) {
        List<Playlist> playlists = PlaylistStore.all();
        int x = leftX();
        int w = leftW();
        int top = listTop();
        g.drawString(font, Component.translatable("gui.liasmediaplayer.playlists.count", playlists.size()), x, top - 12, SUBTLE);
        g.fill(x, top, x + w, listBottom(), PANEL_BG);

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
            int bg = isSel ? ROW_SELECTED_BG : (over ? ROW_HOVER_BG : ROW_BG);
            g.fill(x + 1, rowY, x + w - 1, rowY + ROW_PL - 1, bg);
            String label = playlist.name() + "  (" + playlist.size() + ")";
            g.drawString(font, Component.literal(Glyphs.fit(font, label, w - 8)), x + 4, rowY + 3, TEXT);
        }
    }

    private void renderEntries(GuiGraphics g, int mouseX, int mouseY) {
        if (selected == null) {
            return;
        }
        List<String> urls = selected.urls();
        int x = rightX();
        int w = rightW();
        int top = entriesTop();
        g.fill(x, top, x + w, entriesBottom(), PANEL_BG);

        int rows = visibleEntryRows();
        for (int i = 0; i < rows; i++) {
            int index = entryScroll + i;
            if (index >= urls.size()) {
                break;
            }
            int rowY = top + i * ROW_EN;
            String url = urls.get(index);
            int removeX = x + w - ROW_EN;
            int downX = removeX - 16;
            int upX = downX - 16;

            boolean overUp = MediaWindow.inRect(mouseX, mouseY, upX, rowY, ROW_EN, ROW_EN - 1);
            boolean overDown = MediaWindow.inRect(mouseX, mouseY, downX, rowY, ROW_EN, ROW_EN - 1);
            boolean overRemove = MediaWindow.inRect(mouseX, mouseY, removeX, rowY, ROW_EN, ROW_EN - 1);
            boolean overRow = MediaWindow.inRect(mouseX, mouseY, x, rowY, w, ROW_EN - 1);
            g.fill(x + 1, rowY, x + w - 1, rowY + ROW_EN - 1, (overRow && !overRemove && !overDown && !overUp) ? ROW_HOVER_BG : ROW_BG);

            String label = (index + 1) + ". " + MediaTitleCache.getOrLoad(url);
            g.drawString(font, Component.literal(Glyphs.fit(font, label, w - (ROW_EN * 3) - 8)),
                    x + 4, rowY + 4, TEXT);
            
            boolean canUp = index > 0;
            boolean canDown = index < urls.size() - 1;
            Glyphs.arrow(g, upX + 2, rowY + 2, true, canUp ? (overUp ? TEXT : SUBTLE) : 0xFF555555);
            Glyphs.arrow(g, downX + 2, rowY + 2, false, canDown ? (overDown ? TEXT : SUBTLE) : 0xFF555555);
            g.drawString(font, Component.literal("x"), removeX + 5, rowY + 4, overRemove ? 0xFFFF6B6B : SUBTLE);
        }
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }

        // Left list: select a playlist.
        List<Playlist> playlists = PlaylistStore.all();
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
                rebuild();
                return true;
            }
        }

        // Right list: remove an entry.
        if (selected != null) {
            List<String> urls = selected.urls();
            int erows = visibleEntryRows();
            for (int i = 0; i < erows; i++) {
                int index = entryScroll + i;
                if (index >= urls.size()) {
                    break;
                }
                int rowY = entriesTop() + i * ROW_EN;
                int removeX = rightX() + rightW() - ROW_EN;
                int downX = removeX - 16;
                int upX = downX - 16;

                if (MediaWindow.inRect(mouseX, mouseY, removeX, rowY, ROW_EN, ROW_EN - 1)) {
                    selected.removeAt(index);
                    PlaylistStore.save();
                    rebuild();
                    return true;
                } else if (MediaWindow.inRect(mouseX, mouseY, downX, rowY, ROW_EN, ROW_EN - 1)) {
                    if (index < urls.size() - 1) {
                        selected.swap(index, index + 1);
                        PlaylistStore.save();
                        rebuild();
                        return true;
                    }
                } else if (MediaWindow.inRect(mouseX, mouseY, upX, rowY, ROW_EN, ROW_EN - 1)) {
                    if (index > 0) {
                        selected.swap(index, index - 1);
                        PlaylistStore.save();
                        rebuild();
                        return true;
                    }
                }
            }
        }
        return false;
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
        int playlists = PlaylistStore.all().size();
        playlistScroll = Mth.clamp(playlistScroll, 0, Math.max(0, playlists - visiblePlaylistRows()));
        int entries = selected != null ? selected.size() : 0;
        entryScroll = Mth.clamp(entryScroll, 0, Math.max(0, entries - visibleEntryRows()));
    }

}
