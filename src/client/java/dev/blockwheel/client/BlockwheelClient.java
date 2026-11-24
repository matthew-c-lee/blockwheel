package dev.blockwheel.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.blockwheel.Blockwheel;
import dev.blockwheel.network.Payloads;
import dev.blockwheel.core.PressHoldAction;
import net.minecraft.util.Util;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class BlockwheelClient implements ClientModInitializer {
    public static KeyMapping OPEN;
    public static KeyMapping TOGGLE_BUILDER, FILL_NAMED_SHULKERS, OPEN_SETTINGS;
    private int pendingTicks;
    private long gesture;
    private long pendingGesture;
    private boolean wasHeld;
    private boolean wantsWheel;
    private boolean toggleHeld, fillHeld, settingsHeld;
    private final PressHoldAction fillGesture = new PressHoldAction();

    @Override public void onInitializeClient() {
        BuilderClient.initialize();
        InterfacePreferencesClient.initialize();
        PickRulesClient.initialize();
        var category = KeyMapping.Category.register(Blockwheel.id("controls"));
        OPEN = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.blockwheel.open", InputConstants.KEY_R, category));
        TOGGLE_BUILDER = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.blockwheel.toggle_builder", InputConstants.UNKNOWN.getValue(), category));
        FILL_NAMED_SHULKERS = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.blockwheel.fill_named_shulkers", InputConstants.UNKNOWN.getValue(), category));
        OPEN_SETTINGS = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.blockwheel.settings", InputConstants.UNKNOWN.getValue(), category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            handleBuilderKeys(client);
            if (pendingTicks > 0) pendingTicks--;
            while (OPEN.consumeClick()) { /* Physical edges below define one gesture per hold. */ }
            if (client.player == null || client.level == null) { reset(); return; }
            boolean held = isOpenHeld(client);
            if (held && !wasHeld) {
                gesture++;
                wantsWheel = client.gui.screen() == null && client.isWindowActive();
            }
            if (!held) wantsWheel = false;
            wasHeld = held;
            if (client.gui.screen() instanceof RadialScreen screen && !held) screen.releaseSelection();
            requestIfReady(client);
        });
        ClientPlayNetworking.registerGlobalReceiver(Payloads.Wheel.TYPE, (payload, context) -> {
            var client = context.client();
            if (pendingTicks == 0) return;
            boolean forCurrentHold = pendingGesture == gesture && wantsWheel && isOpenHeld(client);
            pendingTicks = 0;
            if (forCurrentHold && client.player != null && client.gui.screen() == null && client.isWindowActive()) {
                wantsWheel = false;
                client.gui.setScreen(new RadialScreen(payload));
            } else requestIfReady(client);
        });
        ClientPlayNetworking.registerGlobalReceiver(Payloads.Rejected.TYPE, (payload, context) -> {
            if (pendingTicks == 0) return;
            if (pendingGesture == gesture) wantsWheel = false;
            pendingTicks = 0;
            requestIfReady(context.client());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    private void handleBuilderKeys(Minecraft client) {
        boolean toggle = false, fill = false, settings = false;
        while (TOGGLE_BUILDER.consumeClick()) toggle = true;
        while (FILL_NAMED_SHULKERS.consumeClick()) fill = true;
        while (OPEN_SETTINGS.consumeClick()) settings = true;
        boolean ready = client.player != null && client.level != null && client.gui.screen() == null && client.isWindowActive();
        if (ready && settings && !settingsHeld) { client.gui.setScreen(new BuilderSettingsScreen(null)); ready = false; }
        if (ready && toggle && !toggleHeld && BuilderClient.available()) {
            BuilderClient.request(BuilderClient.settings().withEnabled(!BuilderClient.settings().enabled()));
        }
        boolean fillDown = FILL_NAMED_SHULKERS.isDown();
        if (!ready || !BuilderClient.available()) fillGesture.cancel();
        else {
            if (fill && !fillHeld) fillGesture.begin(Util.getMillis());
            var action = fillGesture.update(Util.getMillis(), fillDown);
            if (action != PressHoldAction.Action.NONE) BuilderClient.fillNamedShulkers(action == PressHoldAction.Action.INCLUDING_HOTBAR);
        }
        toggleHeld = TOGGLE_BUILDER.isDown(); fillHeld = FILL_NAMED_SHULKERS.isDown();
        settingsHeld = OPEN_SETTINGS.isDown();
    }

    private void requestIfReady(Minecraft client) {
        if (!wantsWheel || pendingTicks > 0 || client.player == null || client.gui.screen() != null
                || !isOpenHeld(client) || !client.isWindowActive()) return;
        if (!ClientPlayNetworking.canSend(Payloads.Open.TYPE)) {
            wantsWheel = false;
            client.player.sendOverlayMessage(Component.translatable("message.blockwheel.server_missing"));
            return;
        }
        // Keep one request in flight. A reply to a released hold is ignored; a
        // newer hold gets its own fresh snapshot after that reply arrives.
        pendingGesture = gesture;
        pendingTicks = 100;
        ClientPlayNetworking.send(new Payloads.Open(client.player.getInventory().getSelectedSlot()));
    }

    public static boolean isOpenHeld(Minecraft client) {
        var key = KeyMappingHelper.getBoundKeyOf(OPEN);
        if (key.getType() == InputConstants.Type.KEYSYM) {
            return key.getValue() != InputConstants.UNKNOWN.getValue() && InputConstants.isKeyDown(client.getWindow(), key.getValue());
        }
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(client.getWindow().handle(), key.getValue()) == GLFW.GLFW_PRESS;
        }
        // Scancode mappings receive a matching release event in the screen.
        return client.gui.screen() instanceof RadialScreen screen ? screen.isHoldingOpenKey() : OPEN.isDown();
    }

    private void reset() {
        pendingTicks = 0;
        wasHeld = false;
        wantsWheel = false;
        toggleHeld = false; fillHeld = false; settingsHeld = false;
        fillGesture.cancel();
    }
}
