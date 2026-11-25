package dev.blockwheel;

import com.mojang.blaze3d.platform.InputConstants;
import dev.blockwheel.client.BlockwheelClient;
import dev.blockwheel.client.BuilderClient;
import dev.blockwheel.client.BuilderSettingsScreen;
import dev.blockwheel.client.compat.TweakerooCompatibility;
import dev.blockwheel.core.BuilderSettings;
import dev.blockwheel.core.BuilderSettings.Feature;
import dev.blockwheel.core.RestockSettings;
import dev.blockwheel.server.BuilderController;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/** Native slider/keyboard input, unbound defaults, live refill policy and saved custom preferences. */
public final class RestockClientTests implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var toggle = context.computeOnClient(client -> KeyMappingHelper.getBoundKeyOf(BlockwheelClient.TOGGLE_BUILDER));
        var fill = context.computeOnClient(client -> KeyMappingHelper.getBoundKeyOf(BlockwheelClient.FILL_NAMED_SHULKERS));
        int scale = context.computeOnClient(client -> client.options.guiScale().get());
        try {
            context.runOnClient(client -> {
                if (!BlockwheelClient.TOGGLE_BUILDER.getDefaultKey().equals(InputConstants.UNKNOWN)
                        || !BlockwheelClient.FILL_NAMED_SHULKERS.getDefaultKey().equals(InputConstants.UNKNOWN)) {
                    throw new AssertionError("New actions were assigned default keys");
                }
                BlockwheelClient.TOGGLE_BUILDER.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_B));
                BlockwheelClient.FILL_NAMED_SHULKERS.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_N)); KeyMapping.resetMapping();
                client.options.guiScale().set(3); client.resizeGui();
            });
            TestWorldSave save; BuilderSettings persisted;
            try (var world = context.worldBuilder().create()) {
                var server = world.getServer(); var connection = world.getConnection(); connection.waitForChunksRender();
                context.waitFor(client -> BuilderClient.available());
                if (!server.computeOnServer(value -> Blockwheel.BUILDERS.settings(connection.getServerPlayer()).restock().equals(RestockSettings.DEFAULT))) {
                    throw new AssertionError("New player did not get below-six/add-thirty-two defaults");
                }
                context.getInput().holdKey(BlockwheelClient.TOGGLE_BUILDER);
                context.waitFor(client -> BuilderClient.settings().enabled()); context.waitTicks(5);
                if (!BuilderClient.settings().enabled()) throw new AssertionError("Held toggle repeated");
                context.getInput().releaseKey(BlockwheelClient.TOGGLE_BUILDER); context.waitTicks(2);
                context.getInput().pressKey(GLFW.GLFW_KEY_B); context.waitFor(client -> !BuilderClient.settings().enabled());
                context.getInput().pressKey(GLFW.GLFW_KEY_B); context.waitFor(client -> BuilderClient.settings().enabled());

                context.getInput().pressKey(GLFW.GLFW_KEY_E); context.waitForScreen(InventoryScreen.class);
                UiTestInput.settings(context); context.waitForScreen(BuilderSettingsScreen.class);
                context.clickScreenButton(net.minecraft.network.chat.Component.translatable("ui.blockwheel.restock_tab").getString()); context.waitTicks(2); capture(context, "blockwheel-restock-defaults");
                slider(context, "Restock below:", 9); context.waitFor(client -> BuilderClient.settings().restock().below() == 9);
                context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT); context.waitFor(client -> BuilderClient.settings().restock().below() == 10);
                slider(context, "Add up to:", 11); context.waitFor(client -> BuilderClient.settings().restock().amount() == 11);
                context.clickScreenButton("Supply"); context.clickScreenButton("Non-blocks from shulkers: ON"); context.waitFor(client -> !BuilderClient.settings().restock().nonBlockShulkers());
                context.clickScreenButton("Restock"); capture(context, "blockwheel-restock-custom");
                context.clickScreenButton("Done"); context.waitForScreen(InventoryScreen.class);
                context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); context.waitFor(client -> client.gui.screen() == null);

                seed(context, server, connection, player -> {
                    player.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE_STAIRS, 10));
                    player.getInventory().setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.COBBLESTONE_SLAB, 4));
                    player.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 20));
                    player.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(Items.COBBLESTONE, 1)));
                });
                context.getInput().holdKey(BlockwheelClient.FILL_NAMED_SHULKERS); context.waitTicks(2);
                if (!server.computeOnServer(value -> connection.getServerPlayer().getInventory().getItem(9).getCount() == 20
                        && connection.getServerPlayer().getMainHandItem().getCount() == 10)) throw new AssertionError("Dump fired on press");
                context.getInput().releaseKey(BlockwheelClient.FILL_NAMED_SHULKERS);
                context.waitFor(client -> client.player.getInventory().getItem(9).isEmpty());
                if (!server.computeOnServer(value -> inside(connection.getServerPlayer().getEnderChestInventory().getItem(0)) == 21
                        && connection.getServerPlayer().getMainHandItem().getCount() == 10
                        && connection.getServerPlayer().getOffhandItem().getCount() == 4)) throw new AssertionError("Quick dump consumed held items");
                server.runOnServer(value -> { connection.getServerPlayer().getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 20)); BuilderController.sync(connection.getServerPlayer()); });
                connection.waitForClientboundPackets(); context.waitTicks(2);
                context.getInput().holdKey(BlockwheelClient.FILL_NAMED_SHULKERS);
                context.waitFor(client -> client.player.getMainHandItem().isEmpty() && client.player.getOffhandItem().isEmpty()
                        && client.player.getInventory().getItem(9).isEmpty());
                if (!server.computeOnServer(value -> inside(connection.getServerPlayer().getEnderChestInventory().getItem(0)) == 53))
                    throw new AssertionError("Held dump hotkey did not include Builder hotbar and offhand coercion");
                server.runOnServer(value -> { connection.getServerPlayer().getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 4)); BuilderController.sync(connection.getServerPlayer()); });
                connection.waitForClientboundPackets(); context.waitTicks(5);
                context.getInput().releaseKey(BlockwheelClient.FILL_NAMED_SHULKERS); context.waitTicks(4);
                if (!server.computeOnServer(value -> connection.getServerPlayer().getInventory().getItem(9).getCount() == 4
                        && inside(connection.getServerPlayer().getEnderChestInventory().getItem(0)) == 53))
                    throw new AssertionError("Held dump repeated while held or fired again on release");
                context.getInput().pressKey(GLFW.GLFW_KEY_N); context.waitFor(client -> client.player.getInventory().getItem(9).isEmpty());
                if (!server.computeOnServer(value -> inside(connection.getServerPlayer().getEnderChestInventory().getItem(0)) == 57)) throw new AssertionError("Second short dump release failed");

                var supply = BuilderClient.settings().withFeature(Feature.SHULKER_SUPPLY, true);
                context.runOnClient(client -> BuilderClient.request(supply)); context.waitFor(client -> BuilderClient.settings().equals(supply));
                seed(context, server, connection, player -> {
                    player.getInventory().setItem(0, new ItemStack(Items.SNOWBALL, 2));
                    player.getInventory().setItem(9, ShulkerSupplyGameTests.box(false, new ItemStack(Items.SNOWBALL, 16)));
                    player.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(Items.SNOWBALL, 16)));
                });
                context.runOnClient(client -> client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND));
                context.waitFor(client -> client.player.getMainHandItem().getCount() == 1); context.waitTicks(4);
                if (!server.computeOnServer(value -> connection.getServerPlayer().getMainHandItem().getCount() == 1
                        && inside(connection.getServerPlayer().getInventory().getItem(9)) == 16
                        && inside(connection.getServerPlayer().getEnderChestInventory().getItem(0)) == 16)) {
                    throw new AssertionError("Native non-block use bypassed the disabled shulker option");
                }
                seed(context, server, connection, player -> {
                    player.getInventory().setItem(0, new ItemStack(Items.NETHERRACK, 7));
                    player.getInventory().setItem(9, ShulkerSupplyGameTests.box(false, new ItemStack(Items.NETHERRACK, 30)));
                });
                var floor = server.computeOnServer(value -> {
                    var position = connection.getServerPlayer().blockPosition().offset(2, -1, 0);
                    value.overworld().setBlockAndUpdate(position, Blocks.STONE.defaultBlockState());
                    value.overworld().setBlockAndUpdate(position.above(), Blocks.AIR.defaultBlockState()); return position;
                });
                context.waitFor(client -> client.level.getBlockState(floor).is(Blocks.STONE));
                context.runOnClient(client -> client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(floor).add(0, .5, 0), Direction.UP, floor, false)));
                context.waitFor(client -> client.level.getBlockState(floor.above()).is(Blocks.NETHERRACK) && client.player.getMainHandItem().getCount() == 17);
                if (!server.computeOnServer(value -> inside(connection.getServerPlayer().getInventory().getItem(9)) == 19)) {
                    throw new AssertionError("Native block use did not retain its eleven-item shulker refill");
                }
                offhandRestock(context, server, connection);
                persisted = BuilderClient.settings(); save = world.getWorldSave();
            }
            try (var reopened = save.open()) {
                reopened.getConnection().waitForChunksRender();
                context.waitFor(client -> BuilderClient.settings().equals(persisted));
                if (!reopened.getServer().computeOnServer(value -> Blockwheel.BUILDERS.settings(reopened.getConnection().getServerPlayer()).equals(persisted))) {
                    throw new AssertionError("Custom refill preferences did not persist in player data");
                }
            }
            Blockwheel.LOGGER.info("Restock client tests passed: unbound hotkeys, single-press toggle/dump, native sliders/arrows, additional amount, offhand placement/use/consumption, non-block guard and saved custom settings");
        } finally {
            context.getInput().releaseKey(BlockwheelClient.TOGGLE_BUILDER); context.getInput().releaseKey(BlockwheelClient.FILL_NAMED_SHULKERS);
            context.runOnClient(client -> {
                BlockwheelClient.TOGGLE_BUILDER.setKey(toggle); BlockwheelClient.FILL_NAMED_SHULKERS.setKey(fill); KeyMapping.resetMapping();
                client.options.guiScale().set(scale); client.resizeGui();
            });
        }
    }
    private void offhandRestock(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
        // Keep the configured below-ten/add-eleven policy, including disabled non-block shulker access.
        seed(context, server, connection, player -> {
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
            player.getInventory().setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.COBBLESTONE_STAIRS, 2));
            player.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(Items.COBBLESTONE, 12)));
        });
        context.runOnClient(client -> {
            if (!TweakerooCompatibility.ownsRestock(client.player, InteractionHand.OFF_HAND, client.player.getOffhandItem())
                    || TweakerooCompatibility.ownsRestock(client.player, InteractionHand.MAIN_HAND, client.player.getMainHandItem())) {
                throw new AssertionError("Restock compatibility did not own the supported offhand or captured tool handling");
            }
        });
        var floor = server.computeOnServer(value -> {
            var position = connection.getServerPlayer().blockPosition().offset(2, -1, 1);
            value.overworld().setBlockAndUpdate(position, Blocks.STONE.defaultBlockState());
            value.overworld().setBlockAndUpdate(position.above(), Blocks.AIR.defaultBlockState()); return position;
        });
        context.waitFor(client -> client.level.getBlockState(floor).is(Blocks.STONE));
        context.runOnClient(client -> client.gameMode.useItemOn(client.player, InteractionHand.OFF_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor).add(0, .5, 0), Direction.UP, floor, false)));
        context.waitFor(client -> client.level.getBlockState(floor.above()).is(Blocks.COBBLESTONE_STAIRS)
                && client.player.getOffhandItem().is(Items.COBBLESTONE_STAIRS) && client.player.getOffhandItem().getCount() == 12);
        if (!server.computeOnServer(value -> inside(connection.getServerPlayer().getEnderChestInventory().getItem(0)) == 1
                && connection.getServerPlayer().getMainHandItem().is(Items.DIAMOND_PICKAXE))) {
            throw new AssertionError("Offhand placement missed named supply or changed the main hand");
        }
        seed(context, server, connection, player -> {
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
            player.getInventory().setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.SNOWBALL, 1));
            player.getInventory().setItem(10, new ItemStack(Items.SNOWBALL, 16));
            player.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(false, new ItemStack(Items.SNOWBALL, 16)));
        });
        context.runOnClient(client -> client.gameMode.useItem(client.player, InteractionHand.OFF_HAND));
        context.waitFor(client -> client.player.getOffhandItem().is(Items.SNOWBALL) && client.player.getOffhandItem().getCount() == 11);
        if (!server.computeOnServer(value -> connection.getServerPlayer().getInventory().getItem(10).getCount() == 5
                && inside(connection.getServerPlayer().getEnderChestInventory().getItem(0)) == 16)) {
            throw new AssertionError("Last-item offhand use ignored the quantity or non-block source policy");
        }
        seed(context, server, connection, player -> {
            player.getFoodData().setFoodLevel(8);
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
            player.getInventory().setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.BREAD, 2));
            player.getInventory().setItem(10, new ItemStack(Items.BREAD, 20));
        });
        context.waitFor(client -> client.player.canEat(false)); context.getInput().lookAt(0, -75);
        context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        try {
            context.waitFor(client -> client.player.getOffhandItem().is(Items.BREAD) && client.player.getOffhandItem().getCount() == 12);
        } finally { context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT); }
        if (!server.computeOnServer(value -> connection.getServerPlayer().getInventory().getItem(10).getCount() == 9
                && connection.getServerPlayer().getMainHandItem().is(Items.DIAMOND_PICKAXE))) {
            throw new AssertionError("Completed offhand eating did not restock or changed the main hand");
        }
    }
    private void slider(ClientGameTestContext context, String prefix, int count) {
        var point = context.computeOnClient(client -> {
            var slider = client.gui.screen().children().stream().filter(child -> child instanceof AbstractSliderButton widget
                    && widget.getMessage().getString().startsWith(prefix)).map(child -> (AbstractSliderButton) child).findFirst().orElseThrow();
            var window = client.getWindow();
            return new double[]{(slider.getX() + 4 + (slider.getWidth() - 8) * (count - 1.0) / (RestockSettings.MAXIMUM - 1))
                    * window.getScreenWidth() / window.getGuiScaledWidth(),
                    (slider.getY() + 10.0) * window.getScreenHeight() / window.getGuiScaledHeight()};
        });
        context.getInput().setCursorPos(point[0], point[1]); context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }
    private void seed(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, Consumer<ServerPlayer> action) {
        server.runOnServer(value -> {
            var player = connection.getServerPlayer(); player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent(); player.getEnderChestInventory().clearContent(); player.getInventory().setSelectedSlot(0);
            action.accept(player); BuilderController.sync(player);
        });
        connection.waitForClientboundPackets(); context.waitTicks(2);
    }
    private static int inside(ItemStack box) { return ShulkerSupplyGameTests.contents(box).get(0).getCount(); }
    private void capture(ClientGameTestContext context, String name) {
        context.runOnClient(client -> { client.gui.toastManager().clear(); client.gui.hud.getChat().clearMessages(false); });
        context.waitTicks(1); context.takeScreenshot(name);
    }
}
