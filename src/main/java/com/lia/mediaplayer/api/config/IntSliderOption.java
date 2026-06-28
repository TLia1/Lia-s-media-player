package com.lia.mediaplayer.api.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

public class IntSliderOption extends ConfigOption<Integer> {
    private final int min;
    private final int max;

    public IntSliderOption(String id, String translationKey, int defaultValue, int min, int max) {
        super(id, translationKey, defaultValue);
        this.min = min;
        this.max = max;
    }

    @Override
    public JsonElement serialize() {
        return new JsonPrimitive(getValue());
    }

    @Override
    public void deserialize(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            int val = element.getAsInt();
            setValue(Math.max(min, Math.min(max, val)));
        }
    }

    @Override
    public AbstractWidget createWidget(int x, int y, int width, Runnable saveCallback) {
        return new AbstractSliderButton(x, y, width, 20, Component.empty(), getSliderValue()) {
            {
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                this.setMessage(Component.translatable(getTranslationKey()).append(Component.literal(": " + getIntValue())));
            }

            @Override
            protected void applyValue() {
                setValue(getIntValue());
                saveCallback.run();
            }

            private int getIntValue() {
                return (int) Math.round(this.value * (max - min) + min);
            }
        };
    }

    private double getSliderValue() {
        return (double) (getValue() - min) / (max - min);
    }
}
