package com.lia.mediaplayer.command;

import com.lia.mediaplayer.api.LiasMediaPlayerApi;
import com.lia.mediaplayer.api.MediaKind;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * The client-side {@code /show <type> <url> [newPlayer]} command, which opens a media
 * window without going through a chat link.
 *
 * <p>The command tree is generic in its source type on purpose. NeoForge hands out a
 * {@code CommandDispatcher<CommandSourceStack>} and Fabric a
 * {@code CommandDispatcher<FabricClientCommandSource>}; the two have no usable common
 * ancestor, and nothing this command does actually needs the source beyond reporting a
 * failure. So the source type stays a type variable, Brigadier's own generic builders
 * replace {@code Commands.literal}/{@code Commands.argument} (which are pinned to
 * {@code CommandSourceStack}), and each loader bridge passes the one thing that differs:
 * how its source reports an error.</p>
 */
public final class ShowCommand {

    private ShowCommand() {
    }

    /**
     * Builds the {@code /show} tree for a dispatcher of source type {@code S}.
     *
     * @param fail reports a failure message to the command source — {@code sendFailure}
     *             on NeoForge, {@code sendError} on Fabric
     */
    public static <S> LiteralArgumentBuilder<S> tree(BiConsumer<CommandContext<S>, Component> fail) {
        return LiteralArgumentBuilder.<S>literal("show")
                .then(RequiredArgumentBuilder.<S, String>argument("type", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            List<String> types = Arrays.stream(MediaKind.values())
                                    .map(kind -> kind.name().toLowerCase())
                                    .collect(Collectors.toList());
                            return SharedSuggestionProvider.suggest(types, builder);
                        })
                        .then(RequiredArgumentBuilder.<S, String>argument("url", StringArgumentType.string())
                                .executes(context -> executeShow(context, false, fail))
                                .then(RequiredArgumentBuilder.<S, Boolean>argument("newPlayer", BoolArgumentType.bool())
                                        .executes(context -> executeShow(context, true, fail))
                                )
                        )
                );
    }

    private static <S> int executeShow(CommandContext<S> context, boolean hasNewPlayerArg,
                                       BiConsumer<CommandContext<S>, Component> fail) {
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
            fail.accept(context, Component.translatable("command.liasmediaplayer.invalid_type", typeStr));
            return 0;
        }

        MediaKind actualKind = LiasMediaPlayerApi.getInstance().kindOf(url);
        if (actualKind == null) {
            fail.accept(context, Component.translatable("command.liasmediaplayer.unsupported_url"));
            return 0;
        }

        boolean valid = (kind == actualKind) || (actualKind == MediaKind.VIDEO && kind == MediaKind.AUDIO);
        if (!valid) {
            fail.accept(context, Component.translatable("command.liasmediaplayer.kind_mismatch",
                    actualKind.name().toLowerCase(), kind.name().toLowerCase()));
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
