package com.lia.mediaplayer.command;

import com.lia.mediaplayer.LiasMediaPlayer;
import com.lia.mediaplayer.api.LiasMediaPlayerApi;
import com.lia.mediaplayer.api.MediaKind;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@EventBusSubscriber(modid = LiasMediaPlayer.MODID, value = Dist.CLIENT)
public class ShowCommand {

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                net.minecraft.commands.Commands.literal("show")
                        .then(net.minecraft.commands.Commands.argument("type", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    List<String> types = Arrays.stream(MediaKind.values())
                                            .map(kind -> kind.name().toLowerCase())
                                            .collect(Collectors.toList());
                                    return SharedSuggestionProvider.suggest(types, builder);
                                })
                                .then(net.minecraft.commands.Commands.argument("url", StringArgumentType.string())
                                        .executes(context -> executeShow(context, false))
                                        .then(net.minecraft.commands.Commands.argument("newPlayer", BoolArgumentType.bool())
                                                .executes(context -> executeShow(context, true))
                                        )
                                )
                        )
        );
    }

    private static int executeShow(CommandContext<CommandSourceStack> context, boolean hasNewPlayerArg) {
        String typeStr = StringArgumentType.getString(context, "type");
        String url = StringArgumentType.getString(context, "url");
        boolean newPlayer = false;

        if (hasNewPlayerArg) {
            newPlayer = BoolArgumentType.getBool(context, "newPlayer");
        }

        MediaKind kind;
        try {
            kind = MediaKind.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Invalid media type: " + typeStr));
            return 0;
        }

        MediaKind actualKind = LiasMediaPlayerApi.getInstance().kindOf(url);
        if (actualKind == null) {
            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Unsupported media URL."));
            return 0;
        }

        boolean valid = (kind == actualKind) || (actualKind == MediaKind.VIDEO && kind == MediaKind.AUDIO);
        if (!valid) {
            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Cannot play a " + actualKind.name().toLowerCase() + " as " + kind.name().toLowerCase() + "."));
            return 0;
        }

        switch (kind) {
            case IMAGE -> LiasMediaPlayerApi.getInstance().showImage(url);
            case VIDEO -> {
                if (newPlayer) {
                    LiasMediaPlayerApi.getInstance().playVideoNewWindow(url);
                } else {
                    LiasMediaPlayerApi.getInstance().playVideo(url);
                }
            }
            case AUDIO -> {
                if (newPlayer) {
                    LiasMediaPlayerApi.getInstance().playAudioNewWindow(url);
                } else {
                    LiasMediaPlayerApi.getInstance().playAudio(url);
                }
            }
        }

        return 1;
    }
}
