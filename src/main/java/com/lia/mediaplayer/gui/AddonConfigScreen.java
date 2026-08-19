package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.MediaPlayerContext;
import com.lia.mediaplayer.api.LiasMediaPlayerApi;
import com.lia.mediaplayer.api.config.ConfigOption;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AddonConfigScreen extends Screen {

    @Nullable
    private final Screen lastScreen;
    private final String group;
    private OptionsList optionsList;

    public AddonConfigScreen(@Nullable Screen lastScreen, String group) {
        super(Component.translatable("gui.liasmediaplayer.config.title." + group));
        this.lastScreen = lastScreen;
        this.group = group;
    }

    @Override
    protected void init() {
        this.optionsList = new OptionsList(this, this.minecraft, this.width, this.height - 64, 32);
        this.addWidget(this.optionsList);

        MediaPlayerContext ctx = (MediaPlayerContext) LiasMediaPlayerApi.getInstanceOrNull();
        if (ctx == null) return;
        List<ConfigOption<?>> options = ctx.getConfigStore().getOptionsByGroup(group);
        this.optionsList.addOptions(options);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
                .bounds((this.width - 200) / 2, this.height - 28, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        super.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
        this.optionsList.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
        pGuiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
    }

    @Override
    public boolean mouseScrolled(double pMouseX, double pMouseY, double pScrollX, double pScrollY) {
        return this.optionsList.mouseScrolled(pMouseX, pMouseY, pScrollX, pScrollY);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.lastScreen);
        }
    }
}
