package dev.blockwheel.client;

import dev.blockwheel.core.RestockSettings;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import java.util.function.IntConsumer;

/** Native mouse/keyboard slider with one-item steps and bounded network values. */
final class RestockSlider extends AbstractSliderButton {
    private final String label;
    private final IntConsumer change;
    private int sent;

    RestockSlider(int x, int y, String label, int initial, IntConsumer change) {
        super(x, y, 250, 20, Component.empty(), (initial - 1.0) / (RestockSettings.MAXIMUM - 1));
        this.label = label; this.change = change; sent = initial; updateMessage();
    }
    private int count() { return 1 + (int) Math.round(value * (RestockSettings.MAXIMUM - 1)); }
    @Override public boolean keyPressed(KeyEvent event) {
        if (active && isFocused() && (event.key() == GLFW.GLFW_KEY_LEFT || event.key() == GLFW.GLFW_KEY_RIGHT)) {
            int next = Math.clamp(count() + (event.key() == GLFW.GLFW_KEY_RIGHT ? 1 : -1), 1, RestockSettings.MAXIMUM);
            setValue((next - 1.0) / (RestockSettings.MAXIMUM - 1)); return true;
        }
        return super.keyPressed(event);
    }
    @Override protected void updateMessage() { setMessage(Component.translatable(label, count())); }
    @Override protected void applyValue() {
        int count = count();
        if (count != sent) { sent = count; change.accept(count); }
    }
}
