package com.lia.mediaplayer.api.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.function.Function;

public class StepSliderOption<T> extends ConfigOption<Integer> {
    private final T[] steps;
    private final Function<T, String> displayFormatter;

    public StepSliderOption(String id, String translationKey, int defaultIndex, T[] steps, Function<T, String> displayFormatter) {
        super(id, translationKey, defaultIndex);
        this.steps = steps;
        this.displayFormatter = displayFormatter;
    }

    public T getSelectedStep() {
        return steps[getValue()];
    }

    @Override
    public JsonElement serialize() {
        return new JsonPrimitive(getValue());
    }

    @Override
    public void deserialize(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            int val = element.getAsInt();
            setValue(Math.max(0, Math.min(steps.length - 1, val)));
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
                this.setMessage(Component.translatable(getTranslationKey()).append(Component.literal(": " + displayFormatter.apply(steps[getIntValue()]))));
            }

            @Override
            protected void applyValue() {
                setValue(getIntValue());
                saveCallback.run();
            }

            private int getIntValue() {
                return (int) Math.round(this.value * (steps.length - 1));
            }
        };
    }

    private double getSliderValue() {
        return (double) getValue() / (steps.length - 1);
    }
}
