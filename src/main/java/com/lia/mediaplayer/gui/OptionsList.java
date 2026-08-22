package com.lia.mediaplayer.gui;

import com.google.common.collect.ImmutableList;
import com.lia.mediaplayer.MediaPlayerContext;
import com.lia.mediaplayer.api.LiasMediaPlayerApi;
import com.lia.mediaplayer.api.config.ConfigOption;
import com.lia.mediaplayer.api.config.OptionWidth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OptionsList extends ContainerObjectSelectionList<OptionsList.Entry> {

    public OptionsList(AddonConfigScreen screen, Minecraft minecraft, int width, int height, int y) {
        super(minecraft, width, height, y, 24);
        this.centerListVertically = false;
    }

    public void addOptions(List<ConfigOption<?>> options) {
        List<ConfigOption<?>> halfWidthOptions = new ArrayList<>();
        for (ConfigOption<?> option : options) {
            if (option.getWidth() == OptionWidth.HALF) {
                halfWidthOptions.add(option);
            } else {
                // process queued half-width options
                for (int i = 0; i < halfWidthOptions.size(); i += 2) {
                    if (i + 1 < halfWidthOptions.size()) {
                        addEntry(new DoubleEntry(halfWidthOptions.get(i), halfWidthOptions.get(i + 1)));
                    } else {
                        addEntry(new SingleEntry(halfWidthOptions.get(i)));
                    }
                }
                halfWidthOptions.clear();

                // add the full-width option
                addEntry(new SingleEntry(option));
            }
        }

        // process any remaining half-width options
        for (int i = 0; i < halfWidthOptions.size(); i += 2) {
            if (i + 1 < halfWidthOptions.size()) {
                addEntry(new DoubleEntry(halfWidthOptions.get(i), halfWidthOptions.get(i + 1)));
            } else {
                addEntry(new SingleEntry(halfWidthOptions.get(i)));
            }
        }
    }

    // The scrollbar accessor was renamed when the list widgets were rebuilt on
    // AbstractScrollArea. Confirmed renamed by 1.21.4; if 1.21.2 or 1.21.3 are
    // ever added as targets, re-check which of them first dropped
    // getScrollbarPosition.
    //? if <1.21.4 {
    @Override
    public int getScrollbarPosition() {
        return this.width / 2 + this.getRowWidth() / 2 + 4;
    }
    //?} else {
    /*@Override
    protected int scrollBarX() {
        return this.width / 2 + this.getRowWidth() / 2 + 4;
    }
    *///?}

    @Override
    public int getRowWidth() {
        return 310;
    }

    protected void renderBackground(GuiGraphics guiGraphics) {
        // We don't want to render the default background, so we override this method and do nothing.
    }

    public static class SingleEntry extends Entry {
        private final AbstractWidget widget;

        public SingleEntry(ConfigOption<?> option) {
            MediaPlayerContext ctx = (MediaPlayerContext) LiasMediaPlayerApi.getInstance();
            this.widget = option.createWidget(0, 0, 300, () -> ctx.getConfigStore().save());
            if (option.getWarningKey() != null) {
                this.widget.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable(option.getWarningKey())
                                .withStyle(net.minecraft.ChatFormatting.RED)
                ));
            }
        }

        // The list widgets were rebuilt somewhere in 1.21.9-1.21.11: entries now
        // carry their own position (Entry.getX()/getY(), which the list sets to
        // getRowLeft() and the row top — exactly the `left`/`top` the old
        // callback was handed), so the render callback lost every geometry
        // parameter and was renamed. Threshold unbisected: 1.21.9 and 1.21.10
        // are not targets, so this could equally be <1.21.9 or <1.21.10.
        //? if <1.21.11 {
        @Override
        public void render(@NotNull GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isMouseOver, float partialTick) {
            this.widget.setX(left);
            this.widget.setY(top);
            this.widget.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        //?} elif <26.1 {
        /*@Override
        public void renderContent(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, boolean isHovering, float partialTick) {
            this.widget.setX(this.getX());
            this.widget.setY(this.getY());
            this.widget.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        *///?} else {
        /*@Override
        public void extractContent(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, boolean isHovering, float partialTick) {
            this.widget.setX(this.getX());
            this.widget.setY(this.getY());
            this.widget.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        }
        *///?}

        @Override
        public @NotNull List<? extends GuiEventListener> children() {
            return ImmutableList.of(this.widget);
        }

        @Override
        public @NotNull List<? extends NarratableEntry> narratables() {
            return ImmutableList.of(this.widget);
        }
    }

    public static class DoubleEntry extends Entry {
        private final AbstractWidget widget1;
        private final AbstractWidget widget2;

        public DoubleEntry(ConfigOption<?> option1, ConfigOption<?> option2) {
            MediaPlayerContext ctx = (MediaPlayerContext) LiasMediaPlayerApi.getInstance();
            this.widget1 = option1.createWidget(0, 0, 148, () -> ctx.getConfigStore().save());
            this.widget2 = option2.createWidget(0, 0, 148, () -> ctx.getConfigStore().save());
            if (option1.getWarningKey() != null) {
                this.widget1.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable(option1.getWarningKey())
                                .withStyle(net.minecraft.ChatFormatting.RED)
                ));
            }
            if (option2.getWarningKey() != null) {
                this.widget2.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable(option2.getWarningKey())
                                .withStyle(net.minecraft.ChatFormatting.RED)
                ));
            }
        }

        // See the note on SingleEntry.
        //? if <1.21.11 {
        @Override
        public void render(@NotNull GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isMouseOver, float partialTick) {
            this.widget1.setX(left);
            this.widget1.setY(top);
            this.widget1.render(guiGraphics, mouseX, mouseY, partialTick);

            this.widget2.setX(left + 152);
            this.widget2.setY(top);
            this.widget2.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        //?} elif <26.1 {
        /*@Override
        public void renderContent(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, boolean isHovering, float partialTick) {
            this.widget1.setX(this.getX());
            this.widget1.setY(this.getY());
            this.widget1.render(guiGraphics, mouseX, mouseY, partialTick);

            this.widget2.setX(this.getX() + 152);
            this.widget2.setY(this.getY());
            this.widget2.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        *///?} else {
        /*@Override
        public void extractContent(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, boolean isHovering, float partialTick) {
            this.widget1.setX(this.getX());
            this.widget1.setY(this.getY());
            this.widget1.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);

            this.widget2.setX(this.getX() + 152);
            this.widget2.setY(this.getY());
            this.widget2.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        }
        *///?}

        @Override
        public @NotNull List<? extends GuiEventListener> children() {
            return ImmutableList.of(this.widget1, this.widget2);
        }

        @Override
        public @NotNull List<? extends NarratableEntry> narratables() {
            return ImmutableList.of(this.widget1, this.widget2);
        }
    }

    public abstract static class Entry extends ContainerObjectSelectionList.Entry<Entry> {
    }
}
