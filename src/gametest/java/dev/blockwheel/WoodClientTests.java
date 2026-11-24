package dev.blockwheel;

import com.mojang.blaze3d.platform.InputConstants;
import dev.blockwheel.client.*;
import dev.blockwheel.core.BuilderSettings.Feature;
import dev.blockwheel.core.InterfacePreferences;
import dev.blockwheel.core.PickRules;
import dev.blockwheel.core.RadialLayout;
import dev.blockwheel.server.BuilderController;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.glfw.GLFW;
import java.util.function.Consumer;

/** Real hidden-control settings, native input/pick packets, wood conversions and persisted remote packing. */
public final class WoodClientTests implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var oldPreferences = InterfacePreferencesClient.settings(); var oldRules = PickRulesClient.rules();
        var oldSettingsKey = context.computeOnClient(client -> KeyMappingHelper.getBoundKeyOf(BlockwheelClient.OPEN_SETTINGS));
        var oldFillKey = context.computeOnClient(client -> KeyMappingHelper.getBoundKeyOf(BlockwheelClient.FILL_NAMED_SHULKERS));
        int scale = context.computeOnClient(client -> client.options.guiScale().get());
        try {
            context.runOnClient(client -> {
                InterfacePreferencesClient.save(InterfacePreferences.DEFAULT); PickRulesClient.save(PickRules.DEFAULT);
                if (oldSettingsKey.getValue() != InputConstants.UNKNOWN.getValue()) throw new AssertionError("Settings key was bound by default");
                BlockwheelClient.OPEN_SETTINGS.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F9));
                BlockwheelClient.FILL_NAMED_SHULKERS.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F10)); KeyMapping.resetMapping();
                client.options.guiScale().set(3); client.resizeGui();
            });
            TestWorldSave saved; ItemStack remote, remainderRemote;
            try (var world = context.worldBuilder().create()) {
                var server = world.getServer(); var connection = world.getConnection(); connection.waitForChunksRender(); context.waitFor(client -> BuilderClient.available());
                if (context.computeOnClient(client -> BuilderClient.settings().feature(Feature.EXPERIMENTAL_WOOD))) throw new AssertionError("Wood system was enabled by default");
                seed(context, server, connection, p -> set(p, 0, Items.OAK_STAIRS, 5));
                context.getInput().pressKey(GLFW.GLFW_KEY_E); context.waitForScreen(InventoryScreen.class); controls(context, 2);
                UiTestInput.settings(context); context.waitForScreen(BuilderSettingsScreen.class);
                context.clickScreenButton("Logs and wood (experimental): OFF");
                context.waitFor(client -> BuilderClient.settings().feature(Feature.EXPERIMENTAL_WOOD) && BuilderClient.material(new ItemStack(Items.OAK_LOG)) != null);
                capture(context, "blockwheel-experimental-wood-settings"); context.clickScreenButton("Interface");
                context.clickScreenButton("Show Builder Mode Button: ON"); context.clickScreenButton("Show Shulker Refill Controls: ON");
                context.clickScreenButton("Block wheel"); context.clickScreenButton("Use only the block wheel"); context.waitFor(client -> !BuilderClient.settings().enabled());
                context.clickScreenButton("Interface"); capture(context, "blockwheel-radial-only-settings"); context.clickScreenButton("Done"); context.waitForScreen(InventoryScreen.class); context.waitTicks(2);
                controls(context, 0); context.runOnClient(client -> {
                    if (!InterfacePreferencesClient.reload() || InterfacePreferencesClient.settings().showBuilderButton()
                            || InterfacePreferencesClient.settings().showShulkerRefillControls()) throw new AssertionError("Hidden controls did not persist");
                });
                var oldPosition = context.computeOnClient(client -> {
                    var screen = (dev.blockwheel.client.mixin.ContainerScreenAccessor) client.gui.screen(); var window = client.getWindow();
                    return new double[]{(screen.blockwheel$getLeft() + screen.blockwheel$getImageWidth() - 15.0) * window.getScreenWidth() / window.getGuiScaledWidth(),
                            (screen.blockwheel$getTop() + 14.0) * window.getScreenHeight() / window.getGuiScaledHeight()};
                });
                context.getInput().setCursorPos(oldPosition[0], oldPosition[1]); context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT); context.waitTicks(2);
                if (context.computeOnClient(client -> BuilderClient.settings().enabled() || !client.player.containerMenu.getCarried().isEmpty())) throw new AssertionError("Hidden Builder button left a clickable target");
                capture(context, "blockwheel-radial-only-inventory"); close(context);

                open(context); point(context, -24, -24); selected(context, "Log"); capture(context, "blockwheel-log-wheel"); release(context);
                held(context, Items.OAK_LOG, 1);
                if (!server.computeOnServer(value -> connection.getServerPlayer().getInventory().getItem(9).is(Items.OAK_STAIRS)
                        && connection.getServerPlayer().getInventory().getItem(9).getCount() == 1)) throw new AssertionError("Manual log conversion lost the original stair remainder");
                seed(context, server, connection, p -> set(p, 0, Items.OAK_LOG, 1));
                open(context); point(context, 24, -24); selected(context, "Wood"); capture(context, "blockwheel-bark-wheel"); release(context); held(context, Items.OAK_WOOD, 1);
                seed(context, server, connection, p -> set(p, 0, Items.OAK_LOG, 63));
                open(context); point(context, 64, 0); selected(context, "Stairs");
                if (!context.computeOnClient(client -> client.gui.screen().getNarrationMessage().getString().contains("64")))
                    throw new AssertionError("Client preview did not cap logs-to-stairs at one stack");
                release(context); held(context, Items.OAK_STAIRS, 64);
                if (!server.computeOnServer(value -> connection.getServerPlayer().getInventory().countItem(Items.OAK_STAIRS) == 64
                        && connection.getServerPlayer().getInventory().getItem(9).is(Items.OAK_LOG)
                        && connection.getServerPlayer().getInventory().getItem(9).getCount() == 47))
                    throw new AssertionError("Native radial release produced extra stairs instead of returning 47 logs");
                capture(context, "blockwheel-one-stack-conversion");
                seed(context, server, connection, p -> { set(p, 0, Items.OAK_STAIRS, 64); set(p, 9, Items.OAK_LOG, 44); });
                open(context); point(context, -24, -24); selected(context, "Log"); release(context); held(context, Items.OAK_LOG, 16);
                if (!server.computeOnServer(value -> connection.getServerPlayer().getInventory().getItem(9).getCount() == 44
                        && connection.getServerPlayer().getInventory().countItem(Items.OAK_LOG) == 60))
                    throw new AssertionError("Builder-off radial consolidated the 44 stored target logs");
                context.getInput().pressKey(GLFW.GLFW_KEY_E); context.waitForScreen(InventoryScreen.class);
                capture(context, "blockwheel-radial-only-result"); close(context);
                seed(context, server, connection, p -> { set(p, 0, Items.OAK_PLANKS, 64); set(p, 9, Items.OAK_LOG, 10); });
                open(context); point(context, 24, -24); selected(context, "Wood");
                if (!context.computeOnClient(client -> client.gui.screen().getNarrationMessage().getString().equals("Wood. 64 -> 16")))
                    throw new AssertionError("Builder-off preview counted compatible stored logs");
                release(context); held(context, Items.OAK_WOOD, 16);
                if (!server.computeOnServer(value -> connection.getServerPlayer().getInventory().getItem(9).getCount() == 10))
                    throw new AssertionError("Builder-off radial spent stored compatible logs");
                seed(context, server, connection, p -> set(p, 0, Items.STRIPPED_OAK_LOG, 4));
                context.getInput().holdKey(BlockwheelClient.OPEN); context.waitTicks(8);
                if (context.computeOnClient(client -> client.gui.screen() != null || BuilderClient.material(new ItemStack(Items.STRIPPED_OAK_LOG)) != null)) throw new AssertionError("Stripped logs opened a conversion family");
                context.getInput().releaseKey(BlockwheelClient.OPEN);

                settings(context); context.clickScreenButton("Builder"); context.clickScreenButton("Builder Mode: OFF"); context.waitFor(client -> BuilderClient.settings().enabled());
                context.clickScreenButton("Done"); context.waitFor(client -> client.gui.screen() == null);
                var preferences = BuilderClient.settings().withFeature(Feature.NORMALIZE, true).withFeature(Feature.PICKUP, false).withFeature(Feature.SHULKER_SUPPLY, true);
                context.runOnClient(client -> BuilderClient.request(preferences)); context.waitFor(client -> BuilderClient.settings().equals(preferences));
                seed(context, server, connection, p -> {
                    set(p, 0, Items.OAK_PLANKS, 2); p.getEnderChestInventory().clearContent();
                    p.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(Items.OAK_LOG, 12)));
                });
                open(context); point(context, -24, -24); selected(context, "Log"); release(context); held(context, Items.OAK_LOG, 11);
                if (!server.computeOnServer(value -> connection.getServerPlayer().getInventory().getItem(9).is(Items.OAK_PLANKS)
                        && connection.getServerPlayer().getInventory().getItem(9).getCount() == 2
                        && ShulkerSupplyGameTests.contents(connection.getServerPlayer().getEnderChestInventory().getItem(0)).get(0).getCount() == 1))
                    throw new AssertionError("Zero-preview radial release failed to fetch stored logs and preserve planks");
                seed(context, server, connection, p -> { set(p, 0, Items.OAK_STAIRS, 64); set(p, 9, Items.OAK_LOG, 44); });
                open(context); point(context, -24, -24); selected(context, "Log"); release(context); held(context, Items.OAK_LOG, 60);
                if (!server.computeOnServer(value -> connection.getServerPlayer().getInventory().getItem(9).isEmpty()))
                    throw new AssertionError("Builder-on radial failed to consolidate stored target logs");
                capture(context, "blockwheel-combined-result");
                seed(context, server, connection, p -> { set(p, 0, Items.OAK_PLANKS, 64); set(p, 9, Items.OAK_LOG, 10); });
                open(context); point(context, 24, -24); selected(context, "Wood");
                if (!context.computeOnClient(client -> client.gui.screen().getNarrationMessage().getString().equals("Wood. 74 -> 26")))
                    throw new AssertionError("Builder-on preview missed compatible main material");
                release(context); held(context, Items.OAK_WOOD, 26);
                if (!server.computeOnServer(value -> connection.getServerPlayer().getInventory().getItem(9).isEmpty()))
                    throw new AssertionError("Builder-on radial left stored logs outside its wood result");
                capture(context, "blockwheel-family-consolidation");
                seed(context, server, connection, p -> {
                    set(p, 0, Items.STONE, 64); p.getEnderChestInventory().clearContent();
                    p.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(Items.STONE, 10)));
                });
                open(context); point(context, -64, 0); selected(context, "Slab"); release(context); held(context, Items.STONE_SLAB, 64);
                if (!server.computeOnServer(value -> connection.getServerPlayer().getInventory().countItem(Items.STONE) == 0
                        && ShulkerSupplyGameTests.contents(connection.getServerPlayer().getEnderChestInventory().getItem(0)).get(0).getCount() == 42))
                    throw new AssertionError("Native radial did not return 32 raw stone to the named ender shulker");
                remainderRemote = server.computeOnServer(value -> connection.getServerPlayer().getEnderChestInventory().getItem(0).copy());
                server.runOnServer(value -> {
                    var ender = connection.getServerPlayer().getEnderChestInventory();
                    ender.setItem(1, ender.getItem(0).copy()); ender.setItem(0, ItemStack.EMPTY);
                });
                seed(context, server, connection, p -> { set(p, 0, Items.OAK_STAIRS, 14); set(p, 10, Items.OAK_STAIRS, 14); });
                context.getInput().pressKey(GLFW.GLFW_KEY_E); context.waitForScreen(InventoryScreen.class);
                context.runOnClient(client -> {
                    int id = client.player.containerMenu.containerId;
                    client.gameMode.handleContainerInput(id, 36, 0, ContainerInput.PICKUP, client.player);
                    client.gameMode.handleContainerInput(id, 23, 0, ContainerInput.PICKUP, client.player);
                });
                context.waitFor(client -> client.player.getInventory().getItem(23).is(Items.OAK_LOG) && client.player.getInventory().getItem(23).getCount() == 7
                        && client.player.getInventory().getItem(10).isEmpty()); controls(context, 0); capture(context, "blockwheel-log-normalization"); close(context);
                seed(context, server, connection, p -> set(p, 0, Items.OAK_FENCE, 1));
                context.getInput().pressKey(GLFW.GLFW_KEY_E); context.waitForScreen(InventoryScreen.class);
                context.runOnClient(client -> {
                    int id = client.player.containerMenu.containerId;
                    client.gameMode.handleContainerInput(id, 36, 0, ContainerInput.PICKUP, client.player);
                    client.gameMode.handleContainerInput(id, 23, 0, ContainerInput.PICKUP, client.player);
                });
                context.waitFor(client -> client.player.getInventory().getItem(23).is(Items.OAK_PLANKS)
                        && client.player.getInventory().getItem(23).getCount() == 1 && client.player.containerMenu.getCarried().isEmpty());
                capture(context, "blockwheel-plank-fallback"); close(context);
                seed(context, server, connection, p -> { set(p, 9, Items.OAK_PLANKS, 1); set(p, 10, Items.OAK_STAIRS, 3); });
                context.getInput().pressKey(GLFW.GLFW_KEY_E); context.waitForScreen(InventoryScreen.class);
                context.runOnClient(client -> client.gameMode.handleContainerInput(client.player.containerMenu.containerId, 9, 0, ContainerInput.PICKUP, client.player));
                context.waitFor(client -> client.player.containerMenu.getCarried().is(Items.OAK_PLANKS)
                        && client.player.getInventory().getItem(10).is(Items.OAK_PLANKS) && client.player.getInventory().getItem(10).getCount() == 3);
                context.runOnClient(client -> client.gameMode.handleContainerInput(client.player.containerMenu.containerId, 23, 0, ContainerInput.PICKUP, client.player));
                context.waitFor(client -> client.player.getInventory().getItem(23).is(Items.OAK_LOG)
                        && client.player.getInventory().getItem(23).getCount() == 1 && client.player.getInventory().getItem(9).isEmpty()
                        && client.player.getInventory().getItem(10).isEmpty() && client.player.containerMenu.getCarried().isEmpty());
                capture(context, "blockwheel-mixed-wood-compaction"); close(context);
                seed(context, server, connection, p -> {
                    set(p, 0, Items.DIRT, 1); set(p, 9, Items.OAK_STAIRS, 25);
                    p.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(Items.OAK_LOG, 12)));
                });
                context.getInput().pressKey(GLFW.GLFW_KEY_F10);
                context.waitFor(client -> client.player.getInventory().getItem(9).is(Items.OAK_STAIRS) && client.player.getInventory().getItem(9).getCount() == 1);
                remote = server.computeOnServer(value -> connection.getServerPlayer().getEnderChestInventory().getItem(0).copy());
                if (ShulkerSupplyGameTests.contents(remote).get(0).getCount() != 18) throw new AssertionError("Native fill key did not pack six logs");
                server.runOnServer(value -> {
                    var p = connection.getServerPlayer();
                    p.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inventory, owner) ->
                            net.minecraft.world.inventory.ChestMenu.threeRows(id, inventory, p.getEnderChestInventory()),
                            net.minecraft.network.chat.Component.translatable("container.enderchest")));
                });
                context.waitForScreen(net.minecraft.client.gui.screens.inventory.ContainerScreen.class); controls(context, 0);
                capture(context, "blockwheel-hidden-ender-controls"); close(context); connection.waitForServerboundPackets();

                seed(context, server, connection, p -> { set(p, 0, Items.DIRT, 1); set(p, 10, Items.ANVIL, 3); set(p, 5, Items.DAMAGED_ANVIL, 4); });
                pick(context, target(context, server, connection, Blocks.CHIPPED_ANVIL)); held(context, Items.DAMAGED_ANVIL, 4);
                var exposed = BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:exposed_cut_copper_stairs"));
                seed(context, server, connection, p -> { set(p, 0, Items.DIRT, 1); set(p, 10, item("waxed_exposed_cut_copper_slab"), 10); });
                pick(context, target(context, server, connection, exposed)); held(context, item("waxed_exposed_cut_copper_stairs"), 5);
                seed(context, server, connection, p -> { set(p, 0, Items.DIRT, 1); set(p, 10, item("exposed_cut_copper_stairs"), 7); set(p, 5, item("waxed_exposed_cut_copper_stairs"), 12); });
                pick(context, target(context, server, connection, exposed)); held(context, item("exposed_cut_copper_stairs"), 7);
                saved = world.getWorldSave();
            }
            try (var reopened = saved.open()) {
                reopened.getConnection().waitForChunksRender(); context.waitFor(client -> BuilderClient.available() && BuilderClient.settings().feature(Feature.EXPERIMENTAL_WOOD));
                if (!reopened.getServer().computeOnServer(value -> ItemStack.matches(remote, reopened.getConnection().getServerPlayer().getEnderChestInventory().getItem(0))))
                    throw new AssertionError("Packed log contents/metadata were not persisted");
                if (!reopened.getServer().computeOnServer(value -> ItemStack.matches(remainderRemote, reopened.getConnection().getServerPlayer().getEnderChestInventory().getItem(1))))
                    throw new AssertionError("Radial raw-material remainder was not persisted in the ender shulker");
                context.getInput().pressKey(GLFW.GLFW_KEY_E); context.waitForScreen(InventoryScreen.class); controls(context, 0); close(context);
                settings(context); context.clickScreenButton("Interface"); context.clickScreenButton("Show Builder Mode Button: OFF"); context.clickScreenButton("Show Shulker Refill Controls: OFF");
                context.clickScreenButton("Done"); context.waitFor(client -> client.gui.screen() == null);
                context.getInput().pressKey(GLFW.GLFW_KEY_E); context.waitForScreen(InventoryScreen.class); controls(context, 2); close(context);
            }
            Blockwheel.LOGGER.info("Wood client tests passed: native opt-in, radial-only controls, one-to-one log/wood release, one-stack output, mixed-family consolidation and persisted remote raw change, fence-to-plank fallback, immediate mixed main-inventory compaction, stripped rejection, named log packing, persistence and state pick packets");
        } finally {
            context.getInput().releaseKey(BlockwheelClient.OPEN);
            context.runOnClient(client -> {
                if (client.gui.screen() instanceof RadialScreen radial) radial.onClose();
                InterfacePreferencesClient.save(oldPreferences); PickRulesClient.save(oldRules);
                BlockwheelClient.OPEN_SETTINGS.setKey(oldSettingsKey); BlockwheelClient.FILL_NAMED_SHULKERS.setKey(oldFillKey); KeyMapping.resetMapping();
                client.options.guiScale().set(scale); client.resizeGui();
            });
        }
    }
    private Item item(String id) { return BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:" + id)); }
    private static void set(ServerPlayer p, int slot, Item item, int count) { p.getInventory().setItem(slot, new ItemStack(item, count)); }
    private void seed(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, Consumer<ServerPlayer> action) {
        server.runOnServer(value -> {
            var p = connection.getServerPlayer(); p.setGameMode(GameType.SURVIVAL); p.getInventory().clearContent(); p.getInventory().setSelectedSlot(0);
            action.accept(p); p.connection.send(new ClientboundSetHeldSlotPacket(0)); BuilderController.sync(p);
        }); connection.waitForClientboundPackets(); context.waitTicks(2);
    }
    private void controls(ClientGameTestContext context, int expected) {
        context.waitTicks(2); context.runOnClient(client -> {
            long count = client.gui.screen().children().stream().filter(child -> child instanceof InventoryIconButton).count();
            if (count != expected) throw new AssertionError("Expected " + expected + " actual inventory controls, got " + count);
        });
    }
    private void settings(ClientGameTestContext context) { context.getInput().pressKey(GLFW.GLFW_KEY_F9); context.waitForScreen(BuilderSettingsScreen.class); }
    private void close(ClientGameTestContext context) { context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); context.waitFor(client -> client.gui.screen() == null); }
    private void open(ClientGameTestContext context) { context.getInput().holdKey(BlockwheelClient.OPEN); context.waitForScreen(RadialScreen.class); context.waitTicks(2); }
    private void release(ClientGameTestContext context) { context.getInput().releaseKey(BlockwheelClient.OPEN); context.waitFor(client -> client.gui.screen() == null); context.waitTicks(2); }
    private void held(ClientGameTestContext context, Item item, int count) { context.waitFor(client -> client.player.getMainHandItem().is(item) && client.player.getMainHandItem().getCount() == count); }
    private void selected(ClientGameTestContext context, String name) {
        if (!context.computeOnClient(client -> client.gui.screen().getNarrationMessage().getString().startsWith(name + "."))) throw new AssertionError("Wrong wood radial selection: " + name);
    }
    private void point(ClientGameTestContext context, int dx, int dy) {
        var position = context.computeOnClient(client -> { var window = client.getWindow(); var layout = RadialLayout.fit(window.getGuiScaledWidth(), window.getGuiScaledHeight(), 4);
            return new double[]{(layout.centerX() + dx) * window.getScreenWidth() / window.getGuiScaledWidth(), (layout.centerY() + dy) * window.getScreenHeight() / window.getGuiScaledHeight()}; });
        context.getInput().setCursorPos(position[0], position[1]); context.waitTicks(1);
    }
    private BlockPos target(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, Block block) {
        var pos = server.computeOnServer(value -> { var p = connection.getServerPlayer().blockPosition().offset(2, 0, 0);
            value.overworld().setBlockAndUpdate(p.below(), Blocks.STONE.defaultBlockState()); value.overworld().setBlockAndUpdate(p, block.defaultBlockState()); return p; });
        context.waitFor(client -> client.level.getBlockState(pos).is(block)); return pos;
    }
    private void pick(ClientGameTestContext context, BlockPos position) { context.runOnClient(client -> client.getConnection().send(new ServerboundPickItemFromBlockPacket(position, false))); }
    private void capture(ClientGameTestContext context, String name) { context.runOnClient(client -> { client.gui.toastManager().clear(); client.gui.hud.getChat().clearMessages(false); }); context.waitTicks(1); context.takeScreenshot(name); }
}
