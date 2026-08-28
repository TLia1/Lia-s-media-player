package com.lia.mediaplayer.gui;

import com.google.common.collect.ImmutableList;
import com.lia.mediaplayer.MediaPlayerContext;
import com.lia.mediaplayer.api.config.ConfigOption;
import com.lia.mediaplayer.api.config.OptionWidth;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The scrolling list of option widgets in {@link ConfigScreen}'s right-hand column.
 *
 * <p>Every row carries a reset button beside its widget, and every widget carries the
 * option's description (and its warning, if it has one) as a tooltip — the two pieces
 * of an option that used to exist in the model and never reach the screen:
 * {@code getDefaultValue()} was declared and never read, and only warnings were shown.</p>
 *
 * <p>To sit beside the group column the <em>list</em> is moved ({@code setX}) and its
 * rows stay centred within it. Overriding {@link #getRowLeft()} to move the rows on
 * their own looks equivalent and is not: before 1.21.11 the list finds the entry under
 * the cursor from its own centre and {@link #getRowWidth()}, never from
 * {@code getRowLeft()}, so the rows would draw in one place and be clickable in
 * another. Moving the list keeps drawing and hit-testing derived from the same
 * geometry on every version — which is also why the scrollbar position is left alone:
 * every supported version already derives it from the row's right edge.</p>
 */
public class OptionsList extends ContainerObjectSelectionList<OptionsList.Entry> {

    /** Widest a row of options gets, on a screen with room for it. */
    public static final int MAX_ROW_W = 310;
    /** Gutter kept on each side of the rows, for the scrollbar and breathing room. */
    public static final int SIDE_GUTTER = 20;
    /** The reset button squared off at the end of a widget. */
    private static final int RESET_W = 20;
    private static final int GAP = 4;
    /** Narrowest a row may get before its widgets stop being usable. */
    private static final int MIN_ROW_W = 120;

    private int rowWidth = MAX_ROW_W;

    public OptionsList(Minecraft minecraft, int width, int height, int y) {
        super(minecraft, width, height, y, 24);
        this.centerListVertically = false;
        this.rowWidth = Math.max(MIN_ROW_W, width - SIDE_GUTTER * 2);
    }

    /**
     * Narrows the rows to {@code width}, for a screen too small for {@link #MAX_ROW_W}.
     * Call before {@link #addOptions}: the row width decides how wide each option widget
     * is built.
     */
    public void setRowWidth(int width) {
        this.rowWidth = Mth.clamp(width, MIN_ROW_W, MAX_ROW_W);
    }

    /** The width an option widget gets when it has a row to itself. */
    private int singleWidth() {
        return rowWidth - GAP - RESET_W;
    }

    /** One of the two halves a row splits into, the gap between them included. */
    private int halfSlotWidth() {
        return (rowWidth - GAP) / 2;
    }

    /** The width of a half-width option widget, its own reset button's room taken out. */
    private int halfWidth() {
        return halfSlotWidth() - GAP - RESET_W;
    }

    /**
     * Empties the list, for a rebuild after the group or the search text changed.
     * {@code clearEntries} is protected on the vanilla list, so the screen cannot reach
     * it without this.
     */
    public void clearOptions() {
        this.clearEntries();
    }

    /**
     * Builds a row per option, pairing consecutive {@linkplain OptionWidth#HALF
     * half-width} ones.
     *
     * @param onChanged run when a reset changes a value; the screen rebuilds the list,
     *                  because an option widget renders the value it was built with
     */
    public void addOptions(List<ConfigOption<?>> options, Runnable onChanged) {
        List<ConfigOption<?>> halfWidthOptions = new ArrayList<>();
        for (ConfigOption<?> option : options) {
            if (option.getWidth() == OptionWidth.HALF) {
                halfWidthOptions.add(option);
            } else {
                // process queued half-width options
                flushHalfWidth(halfWidthOptions, onChanged);

                // add the full-width option
                addEntry(new SingleEntry(option, singleWidth(), onChanged));
            }
        }

        // process any remaining half-width options
        flushHalfWidth(halfWidthOptions, onChanged);
    }

    private void flushHalfWidth(List<ConfigOption<?>> pending, Runnable onChanged) {
        for (int i = 0; i < pending.size(); i += 2) {
            if (i + 1 < pending.size()) {
                addEntry(new DoubleEntry(pending.get(i), pending.get(i + 1),
                        halfWidth(), halfSlotWidth() + GAP, onChanged));
            } else {
                addEntry(new SingleEntry(pending.get(i), singleWidth(), onChanged));
            }
        }
        pending.clear();
    }

    @Override
    public int getRowWidth() {
        return this.rowWidth;
    }

    protected void renderBackground(GuiGraphics guiGraphics) {
        // We don't want to render the default background, so we override this method and do nothing.
    }

    /**
     * Attaches the option's description and warning to its widget as one tooltip. An
     * option with neither keeps no tooltip at all rather than an empty one.
     */
    private static void applyTooltip(AbstractWidget widget, ConfigOption<?> option) {
        MutableComponent text = null;
        if (option.getDescriptionKey() != null) {
            text = Component.translatable(option.getDescriptionKey());
        }
        if (option.getWarningKey() != null) {
            MutableComponent warning = Component.translatable(option.getWarningKey())
                    .withStyle(ChatFormatting.RED);
            // A newline is how a vanilla tooltip is split into lines; the two parts are
            // separate sentences and should not run together.
            text = text == null ? warning : text.append(Component.literal("\n")).append(warning);
        }
        if (text != null) {
            widget.setTooltip(Tooltip.create(text));
        }
    }

    /**
     * The small button that puts one option back to the value it was declared with.
     */
    private static Button resetButton(ConfigOption<?> option, Runnable onChanged) {
        MediaPlayerContext ctx = MediaPlayerContext.get();
        Button button = Button.builder(Component.translatable("gui.liasmediaplayer.config.reset"), b -> {
            option.resetToDefault();
            ctx.getConfigStore().save();
            onChanged.run();
        }).bounds(0, 0, RESET_W, 20).build();
        button.setTooltip(Tooltip.create(Component.translatable("gui.liasmediaplayer.config.reset.tooltip")));
        return button;
    }

    public static class SingleEntry extends Entry {
        private final ConfigOption<?> option;
        private final AbstractWidget widget;
        private final Button reset;
        private final int widgetWidth;

        public SingleEntry(ConfigOption<?> option, int widgetWidth, Runnable onChanged) {
            MediaPlayerContext ctx = MediaPlayerContext.get();
            this.option = option;
            this.widgetWidth = widgetWidth;
            this.widget = option.createWidget(0, 0, widgetWidth, () -> ctx.getConfigStore().save());
            applyTooltip(this.widget, option);
            this.reset = resetButton(option, onChanged);
        }

        /**
         * Places both widgets for this frame. Greying the reset button out while the
         * option is already at its default is decided here rather than once at build
         * time, because the user changes the value with the widget next to it.
         */
        private void place(int left, int top) {
            this.widget.setX(left);
            this.widget.setY(top);
            this.reset.setX(left + this.widgetWidth + GAP);
            this.reset.setY(top);
            this.reset.active = !this.option.isDefault();
        }

        // The list widgets were rebuilt somewhere in 1.21.9-1.21.11: entries now
        // carry their own position (Entry.getX()/getY(), which the list sets to
        // getRowLeft() and the row top — exactly the `left`/`top` the old
        // callback was handed), so the render callback lost every geometry
        // parameter and was renamed. Threshold unbisected: 1.21.9 and 1.21.10
        // are not targets, so this could equally be <1.21.9 or <1.21.10.
        //? if <1.21.11 {
        @Override
        public void render(@NotNull GuiGraphics guiGraphics, int index, int top, int left,
                           int width, int height, int mouseX, int mouseY,
                           boolean isMouseOver, float partialTick) {
            place(left, top);
            this.widget.render(guiGraphics, mouseX, mouseY, partialTick);
            this.reset.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        //?} elif <26.1 {
        /*@Override
        public void renderContent(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, boolean isHovering, float partialTick) {
            place(this.getX(), this.getY());
            this.widget.render(guiGraphics, mouseX, mouseY, partialTick);
            this.reset.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        *///?} else {
        /*@Override
        public void extractContent(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, boolean isHovering, float partialTick) {
            place(this.getX(), this.getY());
            this.widget.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
            this.reset.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        }
        *///?}

        @Override
        public @NotNull List<? extends GuiEventListener> children() {
            return ImmutableList.of(this.widget, this.reset);
        }

        @Override
        public @NotNull List<? extends NarratableEntry> narratables() {
            return ImmutableList.of(this.widget, this.reset);
        }
    }

    public static class DoubleEntry extends Entry {
        private final ConfigOption<?> option1;
        private final ConfigOption<?> option2;
        private final AbstractWidget widget1;
        private final AbstractWidget widget2;
        private final Button reset1;
        private final Button reset2;
        private final int widgetWidth;
        /** Where the second half starts, relative to the row's left edge. */
        private final int secondColumnX;

        public DoubleEntry(ConfigOption<?> option1, ConfigOption<?> option2,
                           int widgetWidth, int secondColumnX, Runnable onChanged) {
            MediaPlayerContext ctx = MediaPlayerContext.get();
            this.option1 = option1;
            this.option2 = option2;
            this.widgetWidth = widgetWidth;
            this.secondColumnX = secondColumnX;
            this.widget1 = option1.createWidget(0, 0, widgetWidth, () -> ctx.getConfigStore().save());
            this.widget2 = option2.createWidget(0, 0, widgetWidth, () -> ctx.getConfigStore().save());
            applyTooltip(this.widget1, option1);
            applyTooltip(this.widget2, option2);
            this.reset1 = resetButton(option1, onChanged);
            this.reset2 = resetButton(option2, onChanged);
        }

        /** See {@link SingleEntry#place}. */
        private void place(int left, int top) {
            this.widget1.setX(left);
            this.widget1.setY(top);
            this.reset1.setX(left + this.widgetWidth + GAP);
            this.reset1.setY(top);
            this.reset1.active = !this.option1.isDefault();

            this.widget2.setX(left + this.secondColumnX);
            this.widget2.setY(top);
            this.reset2.setX(left + this.secondColumnX + this.widgetWidth + GAP);
            this.reset2.setY(top);
            this.reset2.active = !this.option2.isDefault();
        }

        // See the note on SingleEntry.
        //? if <1.21.11 {
        @Override
        public void render(@NotNull GuiGraphics guiGraphics, int index, int top, int left,
                           int width, int height, int mouseX, int mouseY,
                           boolean isMouseOver, float partialTick) {
            place(left, top);
            this.widget1.render(guiGraphics, mouseX, mouseY, partialTick);
            this.reset1.render(guiGraphics, mouseX, mouseY, partialTick);
            this.widget2.render(guiGraphics, mouseX, mouseY, partialTick);
            this.reset2.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        //?} elif <26.1 {
        /*@Override
        public void renderContent(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, boolean isHovering, float partialTick) {
            place(this.getX(), this.getY());
            this.widget1.render(guiGraphics, mouseX, mouseY, partialTick);
            this.reset1.render(guiGraphics, mouseX, mouseY, partialTick);
            this.widget2.render(guiGraphics, mouseX, mouseY, partialTick);
            this.reset2.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        *///?} else {
        /*@Override
        public void extractContent(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, boolean isHovering, float partialTick) {
            place(this.getX(), this.getY());
            this.widget1.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
            this.reset1.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
            this.widget2.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
            this.reset2.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        }
        *///?}

        @Override
        public @NotNull List<? extends GuiEventListener> children() {
            return ImmutableList.of(this.widget1, this.reset1, this.widget2, this.reset2);
        }

        @Override
        public @NotNull List<? extends NarratableEntry> narratables() {
            return ImmutableList.of(this.widget1, this.reset1, this.widget2, this.reset2);
        }
    }

    public abstract static class Entry extends ContainerObjectSelectionList.Entry<Entry> {
    }
}
