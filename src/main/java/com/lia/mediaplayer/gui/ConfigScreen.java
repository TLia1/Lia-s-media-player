package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.MediaPlayerContext;
import com.lia.mediaplayer.api.LiasMediaPlayerApi;
import com.lia.mediaplayer.api.config.ConfigOption;
import com.lia.mediaplayer.tools.MediaBinaries;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The mod's settings screen: the groups down the left, the selected group's options on
 * the right.
 *
 * <p>It used to be a stack of buttons that opened a second screen per group — one click
 * and a screen change to reach a slider, and no way back to the list without closing.
 * With the options beside the groups, switching between them is a single click and the
 * options never leave the screen.</p>
 *
 * <p>The search box filters the selected group by translated label, so it finds an
 * option by the words the user actually sees rather than by its id.</p>
 */
public class ConfigScreen extends Screen {

    /** Widest the group column gets; it shrinks with the screen. */
    private static final int GROUP_W_MAX = 110;
    private static final int GROUP_W_MIN = 70;
    private static final int GROUP_H = 20;
    private static final int GROUP_GAP = 4;
    private static final int COLUMN_GAP = 12;
    private static final int MARGIN = 6;
    /** Where the two columns start, below the title and the search box. */
    private static final int TOP = 56;
    private static final int SEARCH_H = 18;
    private static final int BOTTOM_BAR = 34;

    // Resolved per layout, because the two columns have to fit a 320-wide GUI (the
    // narrowest Minecraft ever scales to) as well as a full-width one.
    private int groupW;
    private int rowW;
    private int groupX;
    private int optionsX;

    @Nullable
    private final Screen lastScreen;

    /** The group whose options are shown; {@code null} until the first layout. */
    @Nullable
    private String selectedGroup;
    @Nullable
    private EditBox searchBox;
    @Nullable
    private OptionsList optionsList;
    /** Kept across rebuilds, which is the point of holding it here. */
    private String query = "";
    /** Greyed out while an update runs, so a second click cannot start another. */
    @Nullable
    private Button updateButton;
    /**
     * The installed yt-dlp version, read on a side thread: asking costs a process
     * launch, which is not something a screen may do while it is being drawn.
     */
    @Nullable
    private static volatile String ytDlpVersion;

    public ConfigScreen(@Nullable Screen lastScreen) {
        super(Component.translatable("gui.liasmediaplayer.config.title"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        MediaPlayerContext ctx = (MediaPlayerContext) LiasMediaPlayerApi.getInstanceOrNull();
        if (ctx == null) {
            return;
        }
        List<String> groups = ctx.getConfigStore().getGroups();
        if (selectedGroup == null || !groups.contains(selectedGroup)) {
            selectedGroup = groups.isEmpty() ? null : groups.getFirst();
        }

        measureColumns();

        int y = TOP;
        for (String group : groups) {
            boolean current = group.equals(selectedGroup);
            Button button = Button.builder(
                    Component.translatable("gui.liasmediaplayer.config.button." + group),
                    b -> selectGroup(group)
            ).bounds(groupX, y, groupW, GROUP_H).build();
            // The open group is the one you cannot click: greying it out is how vanilla
            // marks "you are already here", and it needs no custom widget.
            button.active = !current;
            this.addRenderableWidget(button);
            y += GROUP_H + GROUP_GAP;
        }

        this.searchBox = new EditBox(this.font, optionsX, TOP - SEARCH_H - 6, rowW, SEARCH_H,
                Component.translatable("gui.liasmediaplayer.config.search"));
        this.searchBox.setHint(Component.translatable("gui.liasmediaplayer.config.search"));
        this.searchBox.setValue(query);
        this.searchBox.setResponder(value -> {
            query = value;
            refreshOptions();
        });
        this.addRenderableWidget(this.searchBox);

        // The list is moved as a whole and keeps its rows centred inside itself, rather
        // than being told where to put its rows — see OptionsList for why that
        // distinction decides whether the widgets are clickable.
        int listW = rowW + OptionsList.SIDE_GUTTER * 2;
        this.optionsList = new OptionsList(this.minecraft, listW,
                Math.max(GROUP_H, this.height - TOP - BOTTOM_BAR), TOP);
        this.optionsList.setX(optionsX - OptionsList.SIDE_GUTTER);
        this.optionsList.setRowWidth(rowW);
        this.addWidget(this.optionsList);
        fillOptions(ctx);

        // The tools row: "update the tools" beside Done, because a broken yt-dlp is the
        // single most common reason a link stops playing and the settings screen is
        // where a player goes looking when it does.
        int updateW = 120;
        int doneW = 140;
        int rowX = (this.width - (updateW + 4 + doneW)) / 2;
        this.updateButton = Button.builder(
                        Component.translatable("gui.liasmediaplayer.config.update_tools"),
                        b -> MediaBinaries.updateToolsAsync())
                .bounds(rowX, this.height - 28, updateW, 20).build();
        this.addRenderableWidget(this.updateButton);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
                .bounds(rowX + updateW + 4, this.height - 28, doneW, 20).build());

        readVersionAsync();
    }

    /**
     * Asks yt-dlp for its version once per game session, off the render thread.
     */
    private void readVersionAsync() {
        if (ytDlpVersion != null) {
            return;
        }
        Thread thread = new Thread(() -> {
            String version = MediaBinaries.ytDlpVersion();
            ytDlpVersion = version == null ? "" : version;
        }, "liasmediaplayer-ytdlp-version");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Sizes and places the two columns for the current screen width, as one centred
     * block. Both widths give way on a narrow screen — a fixed 310-wide options column
     * plus a fixed group column does not fit the 320-wide GUI Minecraft scales down to,
     * and the overflow lands on the reset buttons at the right edge of every row.
     */
    private void measureColumns() {
        int available = this.width - MARGIN * 2;
        groupW = Mth.clamp(available / 4, GROUP_W_MIN, GROUP_W_MAX);
        rowW = Math.min(OptionsList.MAX_ROW_W,
                available - groupW - COLUMN_GAP - OptionsList.SIDE_GUTTER * 2);
        int blockW = groupW + COLUMN_GAP + rowW + OptionsList.SIDE_GUTTER * 2;
        groupX = Math.max(MARGIN, (this.width - blockW) / 2);
        optionsX = groupX + groupW + COLUMN_GAP + OptionsList.SIDE_GUTTER;
    }

    private void selectGroup(String group) {
        this.selectedGroup = group;
        this.rebuildWidgets();
    }

    /**
     * Rebuilds only the option rows, leaving the search box (and what is being typed
     * into it) alone — the responder that calls this fires on every keystroke.
     */
    private void refreshOptions() {
        MediaPlayerContext ctx = (MediaPlayerContext) LiasMediaPlayerApi.getInstanceOrNull();
        if (ctx == null || this.optionsList == null) {
            return;
        }
        this.optionsList.clearOptions();
        fillOptions(ctx);
    }

    private void fillOptions(MediaPlayerContext ctx) {
        if (this.optionsList == null || this.selectedGroup == null) {
            return;
        }
        this.optionsList.addOptions(matchingOptions(ctx), this::refreshOptions);
    }

    /**
     * The selected group's options, narrowed to those whose translated label contains
     * the search text.
     */
    private List<ConfigOption<?>> matchingOptions(MediaPlayerContext ctx) {
        List<ConfigOption<?>> options = ctx.getConfigStore().getOptionsByGroup(this.selectedGroup);
        String needle = this.query.strip().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return options;
        }
        List<ConfigOption<?>> matches = new ArrayList<>();
        for (ConfigOption<?> option : options) {
            String label = Component.translatable(option.getTranslationKey()).getString().toLowerCase(Locale.ROOT);
            if (label.contains(needle)) {
                matches.add(option);
            }
        }
        return matches;
    }

    // 26.1 stopped drawing the GUI and started extracting it into a render state,
    // renaming Renderable.render to extractRenderState. Only the name changes —
    // same parameters, same meaning, same call order. The options list is drawn by
    // hand because it is registered with addWidget (it handles input) rather than
    // addRenderableWidget, so that it is drawn under the Done button.
    //? if <26.1 {
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        if (this.optionsList != null) {
            this.optionsList.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        drawLabels(guiGraphics);
    }
    //?} else {
    /*@Override
    public void extractRenderState(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        if (this.optionsList != null) {
            this.optionsList.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        }
        drawLabels(guiGraphics);
    }
    *///?}

    private void drawLabels(GuiGraphics g) {
        g.drawCenteredString(this.font, this.title, this.width / 2, 20, Theme.TEXT);
        if (this.updateButton != null) {
            this.updateButton.active = !MediaBinaries.isUpdating();
        }
        String version = ytDlpVersion;
        Component tools = MediaBinaries.isUpdating()
                ? Component.translatable("gui.liasmediaplayer.config.updating")
                : (version == null || version.isEmpty()
                        ? null
                        : Component.translatable("gui.liasmediaplayer.config.ytdlp_version", version));
        if (tools != null) {
            g.drawCenteredString(this.font, tools, this.width / 2, this.height - 40, Theme.TEXT_SUBTLE);
        }
        if (this.optionsList != null && this.optionsList.children().isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("gui.liasmediaplayer.config.no_match"),
                    optionsX + rowW / 2, TOP + 20, Theme.TEXT_SUBTLE);
        }
    }

    @Override
    public boolean mouseScrolled(double pMouseX, double pMouseY, double pScrollX, double pScrollY) {
        if (this.optionsList != null && this.optionsList.mouseScrolled(pMouseX, pMouseY, pScrollX, pScrollY)) {
            return true;
        }
        return super.mouseScrolled(pMouseX, pMouseY, pScrollX, pScrollY);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            Screens.open(this.lastScreen);
        }
    }
}
