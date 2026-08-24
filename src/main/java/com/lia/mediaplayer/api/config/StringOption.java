package com.lia.mediaplayer.api.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A free-text option, rendered as a text field.
 *
 * <p>The type the sliders and toggles could not express: a list of hosts, a list of
 * player names — anything whose values are not known in advance. The stored value is a
 * plain string; {@link #entries()} reads it as the comma-separated list the
 * list-shaped options actually want, so every caller splits it the same way.</p>
 *
 * <p>The field shows the option's translated label as its hint, so an empty box still
 * says what it is: unlike a button, a text field has nowhere to put a label of its
 * own.</p>
 */
public class StringOption extends ConfigOption<String> {

    private final int maxLength;

    public StringOption(String id, String group, String translationKey, String defaultValue) {
        this(id, group, translationKey, defaultValue, 512);
    }

    public StringOption(String id, String group, String translationKey, String defaultValue, int maxLength) {
        super(id, group, translationKey, defaultValue == null ? "" : defaultValue);
        this.maxLength = maxLength;
    }

    /**
     * The value read as a comma-separated list: trimmed, lower-cased, blanks dropped.
     *
     * <p>Lower-casing here rather than at each comparison is deliberate — every list
     * this option holds (hosts, player names) is matched case-insensitively, and doing
     * it once at the source keeps the matchers from each having to remember.</p>
     */
    public List<String> entries() {
        List<String> entries = new ArrayList<>();
        for (String part : getValue().split(",")) {
            String entry = part.strip().toLowerCase(Locale.ROOT);
            if (!entry.isEmpty()) {
                entries.add(entry);
            }
        }
        return entries;
    }

    @Override
    public JsonElement serialize() {
        return new JsonPrimitive(getValue());
    }

    @Override
    public void deserialize(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            setValue(element.getAsString());
        }
    }

    @Override
    public AbstractWidget createWidget(int x, int y, int width, Runnable saveCallback) {
        Component label = Component.translatable(getTranslationKey());
        EditBox box = new EditBox(Minecraft.getInstance().font, x, y, width, 20, label);
        box.setMaxLength(maxLength);
        box.setHint(label);
        box.setValue(getValue());
        box.setResponder(value -> {
            if (!value.equals(getValue())) {
                setValue(value);
                saveCallback.run();
            }
        });
        return box;
    }
}
