package dev.blockwheel;

import dev.blockwheel.client.*;
import dev.blockwheel.core.BuilderSettings.Feature;
import dev.blockwheel.core.InterfacePreferences;
import dev.blockwheel.core.RadialLayout;
import dev.blockwheel.gametest.FeedbackProbe;
import dev.blockwheel.server.BuilderController;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.WidgetTooltipHolder;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import org.lwjgl.glfw.GLFW;
import java.util.List;

/** Native fixed alternate selection, server-backed opt-in, local visibility preferences and feedback. */
public final class AlternateClientTests implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var old = InterfacePreferencesClient.settings();
        int scale = context.computeOnClient(client -> client.options.guiScale().get());
        try {
            context.runOnClient(client -> { InterfacePreferencesClient.save(InterfacePreferences.DEFAULT); client.options.guiScale().set(3); client.resizeGui(); });
            try (var world = context.worldBuilder().create()) {
                world.getConnection().waitForChunksRender(); context.waitFor(client -> BuilderClient.available());
                if (context.computeOnClient(client -> BuilderClient.settings().feature(Feature.WOOD_TRAPDOORS))) throw new AssertionError("Trapdoor alternate was enabled by default");
                seed(context, world, Items.OAK_PLANKS, 8);
                open(context); point(context, 0, 24); selected(context, "Fence"); capture(context, "blockwheel-wood-without-trapdoor"); cancel(context);
                context.getInput().pressKey(GLFW.GLFW_KEY_E); context.waitForScreen(InventoryScreen.class); UiTestInput.settings(context);
                context.waitForScreen(BuilderSettingsScreen.class); context.clickScreenButton("Wooden trapdoor shapes: OFF");
                context.waitFor(client -> BuilderClient.settings().feature(Feature.WOOD_TRAPDOORS));
                capture(context, "blockwheel-trapdoor-options"); context.clickScreenButton("Done"); context.waitForScreen(InventoryScreen.class);
                context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); context.waitFor(client -> client.gui.screen() == null);
                context.waitFor(client -> BuilderClient.material(new ItemStack(Items.OAK_TRAPDOOR)) != null);
                open(context); point(context, 0, 24); selected(context, "Fence");
                point(context, 24, 0); selected(context, "Stairs"); point(context, -24, 0); selected(context, "Slab");
                point(context, 24, 24); selected(context, "Trapdoor"); capture(context, "blockwheel-wood-trapdoor-alternate");
                context.getInput().scroll(1); context.waitTicks(1); selected(context, "Trapdoor"); release(context);
                context.waitFor(client -> client.player.getMainHandItem().is(Items.OAK_TRAPDOOR) && client.player.getMainHandItem().getCount() == 2
                        && client.player.getInventory().getItem(9).is(Items.OAK_PLANKS) && client.player.getInventory().getItem(9).getCount() == 2);
                open(context); point(context, 0, -24); selected(context, "Full Block"); release(context);
                context.waitFor(client -> client.player.getMainHandItem().is(Items.OAK_PLANKS) && client.player.getMainHandItem().getCount() == 6
                        && client.player.getInventory().getItem(9).is(Items.OAK_PLANKS) && client.player.getInventory().getItem(9).getCount() == 2);
                context.waitTicks(65); // Keep the preceding wood conversion status out of the nether preview.

                seed(context, world, Items.NETHER_BRICKS, 8); open(context); point(context, 0, 24); selected(context, "Wall");
                point(context, -24, 24); selected(context, "Fence"); capture(context, "blockwheel-nether-fence-alternate"); release(context);
                context.waitFor(client -> client.player.getMainHandItem().is(Items.NETHER_BRICK_FENCE) && client.player.getMainHandItem().getCount() == 8);
                context.waitTicks(65); // Let ordinary conversion/item-name overlays expire before interface captures.

                context.getInput().pressKey(GLFW.GLFW_KEY_E); context.waitForScreen(InventoryScreen.class); context.waitTicks(2); tooltips(context, false);
                UiTestInput.settings(context); context.waitForScreen(BuilderSettingsScreen.class); context.clickScreenButton("Interface");
                context.clickScreenButton("Hide button tooltips: OFF"); context.clickScreenButton("Hide Builder status text: OFF");
                context.runOnClient(client -> {
                    if (!InterfacePreferencesClient.reload() || !InterfacePreferencesClient.settings().equals(new InterfacePreferences(true, true))) {
                        throw new AssertionError("Native interface toggles did not persist independently in JSON");
                    }
                });
                capture(context, "blockwheel-interface-options"); context.clickScreenButton("Done"); context.waitForScreen(InventoryScreen.class);
                context.waitTicks(2); tooltips(context, true);
                toggle(context, true, true); capture(context, "blockwheel-switch-without-tooltip"); toggle(context, false, true);
                UiTestInput.settings(context); context.waitForScreen(BuilderSettingsScreen.class); context.clickScreenButton("Interface");
                context.clickScreenButton("Hide button tooltips: ON"); context.clickScreenButton("Hide Builder status text: ON");
                context.clickScreenButton("Done"); context.waitForScreen(InventoryScreen.class); context.waitTicks(2); tooltips(context, false);
                toggle(context, true, false);
                context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); context.waitFor(client -> client.gui.screen() == null);
            }
            Blockwheel.LOGGER.info("Alternate client tests passed: fixed fence/trapdoor slots, no scroll variants, native opt-in and ratio, optional tooltips/status text, JSON persistence and retained toggle sounds");
        } finally {
            context.getInput().releaseKey(BlockwheelClient.OPEN);
            context.runOnClient(client -> { if (client.gui.screen() instanceof RadialScreen radial) radial.onClose(); InterfacePreferencesClient.save(old); client.options.guiScale().set(scale); client.resizeGui(); });
        }
    }
    private void toggle(ClientGameTestContext context, boolean enabled, boolean hidden) {
        context.runOnClient(client -> FeedbackProbe.reset()); UiTestInput.click(context, InventoryIconButton.Kind.BUILDER, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitFor(client -> BuilderClient.settings().enabled() == enabled && FeedbackProbe.sounds().size() == 1);
        context.runOnClient(client -> {
            var sound = FeedbackProbe.sounds().getFirst();
            String expected = (enabled ? SoundEvents.ENCHANTMENT_TABLE_USE : SoundEvents.GRINDSTONE_USE).location().toString();
            if (!sound.id().equals(expected) || sound.volume() != .65f || !sound.relative()) throw new AssertionError("Hiding text changed toggle sound");
            if (!FeedbackProbe.messages().equals(hidden ? List.of() : List.of(enabled ? "Builder Mode: ON" : "Builder Mode: OFF"))) {
                throw new AssertionError("Builder status text preference was ignored");
            }
        });
    }
    private void tooltips(ClientGameTestContext context, boolean hidden) {
        context.runOnClient(client -> {
            try {
                var field = AbstractWidget.class.getDeclaredField("tooltip"); field.setAccessible(true); int checked = 0;
                for (var child : client.gui.screen().children()) if (child instanceof InventoryIconButton button) {
                    var holder = (WidgetTooltipHolder) field.get(button); checked++;
                    if ((holder.get() == null) != hidden || button.getMessage().getString().isEmpty()) throw new AssertionError("Button tooltip visibility or narration was incorrect");
                }
                if (checked != 2) throw new AssertionError("Both inventory controls must honor tooltip preferences");
            } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
        });
    }
    private void seed(ClientGameTestContext context, TestSingleplayerContext world, Item item, int count) {
        world.getServer().runOnServer(server -> {
            var player = world.getConnection().getServerPlayer(); player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent();
            player.getInventory().setSelectedSlot(0); player.getInventory().setItem(0, new ItemStack(item, count)); BuilderController.sync(player);
        });
        world.getConnection().waitForClientboundPackets(); context.waitFor(client -> client.player.getMainHandItem().is(item));
    }
    private void open(ClientGameTestContext context) { context.getInput().holdKey(BlockwheelClient.OPEN); context.waitForScreen(RadialScreen.class); context.waitTicks(2); }
    private void release(ClientGameTestContext context) { context.getInput().releaseKey(BlockwheelClient.OPEN); context.waitFor(client -> client.gui.screen() == null); context.waitTicks(2); }
    private void cancel(ClientGameTestContext context) { context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); context.getInput().releaseKey(BlockwheelClient.OPEN); context.waitFor(client -> client.gui.screen() == null); }
    private void selected(ClientGameTestContext context, String name) {
        if (!context.computeOnClient(client -> client.gui.screen().getNarrationMessage().getString().startsWith(name + "."))) throw new AssertionError("Wrong radial alternate: " + name);
    }
    private void point(ClientGameTestContext context, int dx, int dy) {
        var position = context.computeOnClient(client -> {
            var window = client.getWindow(); var layout = RadialLayout.fit(window.getGuiScaledWidth(), window.getGuiScaledHeight(), 4);
            return new double[]{(layout.centerX() + dx) * window.getScreenWidth() / window.getGuiScaledWidth(), (layout.centerY() + dy) * window.getScreenHeight() / window.getGuiScaledHeight()};
        });
        context.getInput().setCursorPos(position[0], position[1]); context.waitTicks(1);
    }
    private void capture(ClientGameTestContext context, String name) {
        context.runOnClient(client -> { client.gui.toastManager().clear(); client.gui.hud.getChat().clearMessages(false); }); context.waitTicks(1); context.takeScreenshot(name);
    }
}
