package dev.blockwheel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import java.util.function.BooleanSupplier;

/** A labeled switch with position and state text; feedback comes from the server-confirmed change. */
final class BuilderSwitchButton extends Button {
    private final BooleanSupplier enabled;
    BuilderSwitchButton(int x, int y, int width, Component label, Runnable press, BooleanSupplier enabled) {
        super(x, y, width, 20, label, button -> press.run(), DEFAULT_NARRATION); this.enabled = enabled;
    }
    @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.text(Minecraft.getInstance().font, getMessage(), getX() + 8, getY() + 6, active ? 0xffffffff : 0xffa0a0a0);
        BlockwheelStyle.switchTrack(graphics, getRight() - 40, getY() + 4, 32, 12, enabled.getAsBoolean(), active);
        if (active && isHoveredOrFocused()) graphics.outline(getRight() - 40, getY() + 4, 32, 12, 0xffdedede);
    }
    @Override public void playDownSound(SoundManager sounds) { /* The acknowledged change plays the requested sound. */ }
}
