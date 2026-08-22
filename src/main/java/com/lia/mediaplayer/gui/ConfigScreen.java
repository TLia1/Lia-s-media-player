package com.lia.mediaplayer.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class ConfigScreen extends Screen {

    @Nullable
    private final Screen lastScreen;

    public ConfigScreen(@Nullable Screen lastScreen) {
        super(Component.translatable("gui.liasmediaplayer.config.title"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        int w = 200;
        int x = (this.width - w) / 2;
        int y = 40;
        int dy = 24;

        com.lia.mediaplayer.MediaPlayerContext ctx = (com.lia.mediaplayer.MediaPlayerContext) com.lia.mediaplayer.api.LiasMediaPlayerApi.getInstanceOrNull();
        if (ctx == null) return;

        Collection<String> groups = ctx.getConfigStore().getGroups();

        for (String group : groups) {
            this.addRenderableWidget(Button.builder(Component.translatable("gui.liasmediaplayer.config.button." + group), b -> {
                if (this.minecraft != null) {
                    Screens.open(new AddonConfigScreen(this, group));
                }
            }).bounds(x, y, w, 20).build());
            y += dy;
        }

        y += 12;

        // Done button
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
                .bounds((this.width - 200) / 2, y, 200, 20).build());
    }

    // 26.1 stopped drawing the GUI and started extracting it into a render state,
    // renaming Renderable.render to extractRenderState. Only the name changes —
    // same parameters, same meaning, same call order.
    //? if <26.1 {
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
    }
    //?} else {
    /*@Override
    public void extractRenderState(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
    }
    *///?}

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            Screens.open(this.lastScreen);
        }
    }
}
