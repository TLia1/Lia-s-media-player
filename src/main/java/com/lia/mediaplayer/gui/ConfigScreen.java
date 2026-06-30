package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.api.config.ConfigOption;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class ConfigScreen extends Screen {

    @Nullable
    private final Screen lastScreen;

    public ConfigScreen(@Nullable Screen lastScreen) {
        super(Component.translatable("gui.liasmediaplayer.config.title"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        int w = 300;
        int x = (this.width - w) / 2;
        int y = 40;
        int dy = 24;

        // Iterate over all registered options and create their widgets
        com.lia.mediaplayer.MediaPlayerContext ctx = (com.lia.mediaplayer.MediaPlayerContext) com.lia.mediaplayer.api.LiasMediaPlayerApi.getInstance();
        if (ctx == null) return;
        for (ConfigOption<?> option : ctx.getConfigStore().getAllOptions()) {
            AbstractWidget widget = option.createWidget(x, y, w, ctx.getConfigStore()::save);
            if (widget != null) {
                this.addRenderableWidget(widget);
                y += dy;
            }
        }

        y += 12;

        // Done button
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
                .bounds((this.width - 200) / 2, y, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.lastScreen);
        }
    }
}
