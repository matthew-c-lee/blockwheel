package dev.blockwheel.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import com.mojang.blaze3d.platform.InputConstants;
import dev.blockwheel.core.PressHoldAction;
import net.minecraft.util.Util;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Native button input, sound, focus and narration with a compact custom pixel-art face. */
public final class InventoryIconButton extends Button {
    public enum Kind { BUILDER, DEPOSIT }
    private final Kind kind;
    private final Runnable rightClick;
    private final BooleanSupplier pressed;
    private Component tooltipMessage;
    private final PressHoldAction hold = new PressHoldAction();
    private Consumer<Boolean> holdAction;
    private boolean mouseGesture, mouseDown;
    private int heldKey;
    public InventoryIconButton(Kind kind, Component message, Runnable leftClick, Runnable rightClick, BooleanSupplier pressed) {
        super(0, 0, BlockwheelStyle.BUTTON_SIZE, BlockwheelStyle.BUTTON_SIZE, message,
                button -> leftClick.run(), DEFAULT_NARRATION);
        this.kind = kind; this.rightClick = rightClick; this.pressed = pressed;
    }
    public Kind kind() { return kind; }
    public InventoryIconButton holdAction(Consumer<Boolean> action) { this.holdAction = action; return this; }
    public void cancelHold() { hold.cancel(); mouseDown = false; }
    public double holdProgress() { return hold.progress(Util.getMillis()); }
    private void dispatch(PressHoldAction.Action action) {
        if (action != PressHoldAction.Action.NONE) holdAction.accept(action == PressHoldAction.Action.INCLUDING_HOTBAR);
    }
    private boolean holdAllowed() {
        var client = Minecraft.getInstance();
        return active && visible && client.player != null && client.isWindowActive()
                && (mouseGesture ? isMouseOver(client.mouseHandler.getScaledXPos(client.getWindow()), client.mouseHandler.getScaledYPos(client.getWindow())) : isFocused());
    }
    public void tickHold() {
        if (!hold.pending()) return;
        if (!holdAllowed()) { cancelHold(); return; }
        var client = Minecraft.getInstance();
        boolean down = mouseGesture ? mouseDown : InputConstants.isKeyDown(client.getWindow(), heldKey);
        dispatch(hold.update(Util.getMillis(), down));
    }
    @Override public void playDownSound(SoundManager sounds) { /* Server feedback supplies mode/deposit sounds. */ }
    public void contextTooltip(Component message) {
        Component shown = InterfacePreferencesClient.settings().hideButtonTooltips() ? null : message;
        if (!java.util.Objects.equals(shown, tooltipMessage)) {
            tooltipMessage = shown; setTooltip(shown == null ? null : Tooltip.create(shown));
        }
    }
    @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        BlockwheelStyle.control(graphics, getX(), getY(), pressed.getAsBoolean(), active, isHoveredOrFocused(), kind == Kind.DEPOSIT);
        if (hold.pending()) {
            int left = getX() + 2, bottom = getY() + getHeight() - 4, width = getWidth() - 4;
            graphics.fill(left, bottom, left + width, bottom + 2, 0xff242424);
            graphics.fill(left, bottom, left + (int) Math.ceil(width * holdProgress()), bottom + 2, 0xff8ed4c6);
        }
    }
    @Override protected boolean isValidClickButton(MouseButtonInfo button) {
        return button.button() == 0 || button.button() == 1 && rightClick != null;
    }
    @Override public void onPress(InputWithModifiers input) {
        if (kind == Kind.DEPOSIT && holdAction != null) {
            if (!hold.pending()) {
                if (input instanceof MouseButtonEvent) { mouseGesture = true; mouseDown = true; }
                else if (input instanceof KeyEvent event) { mouseGesture = false; heldKey = event.key(); }
                else { holdAction.accept(false); return; }
                hold.begin(Util.getMillis());
            }
            return;
        }
        if (input instanceof MouseButtonEvent event && event.button() == 1 && rightClick != null) {
            super.playDownSound(net.minecraft.client.Minecraft.getInstance().getSoundManager()); rightClick.run();
        }
        else super.onPress(input);
    }
    public boolean releaseHold(MouseButtonEvent event) {
        if (event.button() != 0) return false;
        mouseDown = false;
        if (hold.pending() && mouseGesture) {
            if (holdAllowed()) dispatch(hold.update(Util.getMillis(), false)); else cancelHold();
            return true;
        }
        return false;
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        return releaseHold(event) || super.mouseReleased(event);
    }
    @Override public boolean keyReleased(KeyEvent event) {
        if (hold.pending() && !mouseGesture && event.key() == heldKey) {
            if (holdAllowed()) dispatch(hold.update(Util.getMillis(), false)); else cancelHold();
            return true;
        }
        return super.keyReleased(event);
    }
}
