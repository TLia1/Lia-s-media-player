package com.lia.mediaplayer.api.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;

public class EnumOption<E extends Enum<E>> extends ConfigOption<E> {
    private final List<E> values;

    public EnumOption(String id, String group, String translationKey, E defaultValue) {
        super(id, group, translationKey, defaultValue);
        this.values = Arrays.asList(defaultValue.getDeclaringClass().getEnumConstants());
    }

    @Override
    public JsonElement serialize() {
        return new JsonPrimitive(getValue().name());
    }

    @Override
    public void deserialize(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String name = element.getAsString();
            for (E val : values) {
                if (val.name().equals(name)) {
                    setValue(val);
                    break;
                }
            }
        }
    }

    @Override
    public AbstractWidget createWidget(int x, int y, int width, Runnable saveCallback) {
        return new Button.Builder(getButtonMessage(), (button) -> {
            cycleValue();
            saveCallback.run();
            button.setMessage(getButtonMessage());
        }).bounds(x, y, width, 20).build();
    }

    private void cycleValue() {
        int currentIndex = values.indexOf(getValue());
        int nextIndex = (currentIndex + 1) % values.size();
        setValue(values.get(nextIndex));
    }

    private Component getButtonMessage() {
        return Component.translatable(getTranslationKey()).append(Component.literal(": ")).append(Component.literal(getValue().name()));
    }
}
