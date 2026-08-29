package com.lia.mediaplayer.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.lia.mediaplayer.api.config.ConfigOption;
import com.lia.mediaplayer.api.config.OptionWidth;
import com.lia.mediaplayer.api.theme.MediaTheme;
import com.lia.mediaplayer.api.theme.MediaThemes;
import com.lia.mediaplayer.gui.ThemeName;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The theme setting — the one option whose set of values is not known when it is
 * declared.
 *
 * <p>It used to be an {@code EnumOption<ThemeName>}, which was right while the only
 * palettes were the three the mod ships. API 3.2 lets an addon register one
 * ({@link MediaThemes}), and an enum cannot grow at runtime, so the stored value became a
 * <b>string id</b> and the choices are recomputed every time the button is clicked.</p>
 *
 * <p>The built-in ids are the {@link ThemeName} constants in lower case, which is what
 * makes this a compatible change and not a migration: {@link #deserialize} reads the old
 * {@code "DARK"} and the new {@code "dark"} as the same thing, and a config file written
 * by an older version needs no touching. An id that names an addon theme which is not
 * installed today is <em>kept</em> rather than reset — the addon may be back tomorrow,
 * and silently rewriting someone's setting because a mod was temporarily removed is the
 * kind of thing that is only noticed after it has happened three times. {@code gui.Theme}
 * falls back to the dark palette while it cannot resolve one.</p>
 */
public final class ThemeOption extends ConfigOption<String> {

    public ThemeOption(String id, String group, String translationKey) {
        super(id, group, translationKey, ThemeName.DARK.name().toLowerCase(Locale.ROOT));
    }

    /** The built-in palette ids, in the order the button cycles them. */
    public static List<String> builtInIds() {
        List<String> ids = new ArrayList<>(ThemeName.values().length);
        for (ThemeName name : ThemeName.values()) {
            ids.add(name.name().toLowerCase(Locale.ROOT));
        }
        return ids;
    }

    /** Every id the button can offer right now: the built-ins, then whatever is registered. */
    private static List<String> choices() {
        List<String> ids = builtInIds();
        for (MediaTheme theme : MediaThemes.all()) {
            ids.add(theme.id());
        }
        return ids;
    }

    @Override
    public JsonElement serialize() {
        return new JsonPrimitive(getValue());
    }

    @Override
    public void deserialize(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String stored = element.getAsString();
            if (stored != null && !stored.isBlank()) {
                // Lower-cased on the way in, which is what reads an older file's
                // "DARK" as today's "dark" without a migration step.
                setValue(stored.strip().toLowerCase(Locale.ROOT));
            }
        }
    }

    @Override
    public AbstractWidget createWidget(int x, int y, int width, Runnable saveCallback) {
        return new Button.Builder(buttonMessage(), button -> {
            cycle();
            saveCallback.run();
            button.setMessage(buttonMessage());
        }).bounds(x, y, width, 20).build();
    }

    /**
     * Steps to the next installed palette. A value that names nothing installed lands on
     * the first choice, which is how a user gets out of an id left behind by an addon
     * they removed.
     */
    private void cycle() {
        List<String> ids = choices();
        int index = ids.indexOf(getValue());
        setValue(ids.get((index + 1) % ids.size()));
    }

    private Component buttonMessage() {
        return Component.translatable(getTranslationKey())
                .append(Component.literal(": "))
                .append(label(getValue()));
    }

    /**
     * What one id is called: the mod's own key for a built-in, the theme's own name for a
     * registered one, and the bare id for one that is not installed — which is the honest
     * answer, and tells the user what to reinstall.
     */
    private static Component label(String id) {
        if (builtInIds().contains(id)) {
            return Component.translatable("config.liasmediaplayer.theme." + id);
        }
        MediaTheme theme = MediaThemes.byId(id);
        return theme != null ? theme.name() : Component.literal(id);
    }

    // ---- Covariant builder overrides (see ConfigOption's javadoc) ------------

    @Override
    public ThemeOption withDescription(String descriptionKey) {
        super.withDescription(descriptionKey);
        return this;
    }

    @Override
    public ThemeOption withWarning(String warningKey) {
        super.withWarning(warningKey);
        return this;
    }

    @Override
    public ThemeOption withWidth(OptionWidth width) {
        super.withWidth(width);
        return this;
    }
}
