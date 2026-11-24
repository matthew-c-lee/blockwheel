package dev.blockwheel;

import dev.blockwheel.client.BlockwheelClient;
import dev.blockwheel.client.BuilderClient;
import dev.blockwheel.client.BuilderSettingsScreen;
import dev.blockwheel.client.InventoryIconButton;
import dev.blockwheel.client.RadialScreen;
import dev.blockwheel.client.mixin.ContainerScreenAccessor;
import dev.blockwheel.core.BuilderSettings;
import dev.blockwheel.core.BuilderSettings.Feature;
import dev.blockwheel.server.BuilderController;
import dev.blockwheel.server.BuilderServerConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/** Actual UI clicks, GUI scales, floating cube frames and server-authoritative template deposits. */
public final class UiClientTests implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        int scale = context.computeOnClient(client -> client.options.guiScale().get());
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer(); var connection = world.getConnection(); connection.waitForChunksRender();
            context.waitFor(client -> BuilderClient.material(new ItemStack(Items.COBBLESTONE)) != null);
            context.getInput().pressKey(GLFW.GLFW_KEY_E); context.waitForScreen(InventoryScreen.class); context.waitTicks(2);
            context.runOnClient(client -> {
                var position = (ContainerScreenAccessor) client.gui.screen();
                long controls = client.gui.screen().children().stream().filter(child -> child instanceof InventoryIconButton icon
                        && icon.kind() == InventoryIconButton.Kind.BUILDER).count();
                if (controls != 1) throw new AssertionError("Inventory did not have exactly one Builder icon");
                var button = client.gui.screen().children().stream().filter(child -> child instanceof InventoryIconButton icon
                        && icon.kind() == InventoryIconButton.Kind.BUILDER).map(child -> (InventoryIconButton) child).findFirst().orElseThrow();
                if (button.getWidth() != 20 || button.getX() != position.blockwheel$getLeft() + position.blockwheel$getImageWidth() - 25
                        || button.getY() != position.blockwheel$getTop() + 4) throw new AssertionError("Builder icon was not anchored inside the top-right corner");
                for (var slot : client.player.inventoryMenu.slots) {
                    int x = position.blockwheel$getLeft() + slot.x, y = position.blockwheel$getTop() + slot.y;
                    if (button.getX() < x + 16 && button.getRight() > x && button.getY() < y + 16 && button.getBottom() > y) {
                        throw new AssertionError("Builder control overlaps an inventory/crafting slot");
                    }
                }
            });
            capture(context, "blockwheel-inventory-icon-off");
            UiTestInput.click(context, InventoryIconButton.Kind.BUILDER, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitFor(client -> BuilderClient.settings().enabled()); context.waitTicks(8);
            capture(context, "blockwheel-inventory-icon-on");
            UiTestInput.settings(context); context.waitForScreen(BuilderSettingsScreen.class);
            context.clickScreenButton("Done"); context.waitForScreen(InventoryScreen.class);
            server.runOnServer(value -> {
                var player = connection.getServerPlayer(); player.getInventory().clearContent();
                player.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE_STAIRS, 17));
                player.getInventory().setItem(10, new ItemStack(Items.COBBLESTONE, 5)); BuilderController.sync(player);
            });
            connection.waitForClientboundPackets(); context.waitTicks(2);
            context.runOnClient(client -> {
                int menu = client.player.containerMenu.containerId;
                client.gameMode.handleContainerInput(menu, 36, 0, ContainerInput.PICKUP, client.player);
                client.gameMode.handleContainerInput(menu, 23, 0, ContainerInput.PICKUP, client.player);
            });
            context.waitFor(client -> client.player.getInventory().getItem(23).is(Items.COBBLESTONE)
                    && client.player.getInventory().getItem(23).getCount() == 17 && client.player.getInventory().getItem(9).isEmpty()
                    && client.player.getInventory().getItem(10).getCount() == 5);
            if (!server.computeOnServer(value -> connection.getServerPlayer().getInventory().getItem(23).getCount() == 17
                    && connection.getServerPlayer().getInventory().getItem(10).getCount() == 5)) throw new AssertionError("Normalized output did not stay in the clicked slot");
            capture(context, "blockwheel-normalization-destination");
            server.runOnServer(value -> {
                var player = connection.getServerPlayer(); player.getInventory().clearContent();
                player.getInventory().setItem(0, new ItemStack(Items.STONE_BRICK_STAIRS, 17));
                player.getInventory().setItem(14, new ItemStack(Items.STONE_BRICKS, 10)); BuilderController.sync(player);
            });
            connection.waitForClientboundPackets(); context.waitTicks(2);
            context.runOnClient(client -> client.gameMode.handleContainerInput(client.player.containerMenu.containerId,
                    36, 0, ContainerInput.QUICK_MOVE, client.player));
            context.waitFor(client -> client.player.getInventory().getItem(14).is(Items.STONE_BRICKS)
                    && client.player.getInventory().getItem(14).getCount() == 27 && client.player.getInventory().getItem(9).isEmpty()
                    && client.player.getInventory().getItem(0).isEmpty());
            if (!server.computeOnServer(value -> connection.getServerPlayer().getInventory().getItem(14).getCount() == 27)) {
                throw new AssertionError("Shift-click output did not merge into the existing block stack");
            }
            capture(context, "blockwheel-shift-click-merge");
            verifyEnderPanelAnchor(context);
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); context.waitFor(client -> client.gui.screen() == null);

            var sources = new net.minecraft.world.item.Item[]{Items.COBBLESTONE, Items.POLISHED_GRANITE, Items.OAK_PLANKS};
            for (int i = 0; i < sources.length; i++) {
                int guiScale = i + 2; var item = sources[i];
                context.runOnClient(client -> { client.options.guiScale().set(guiScale); client.resizeGui(); });
                server.runOnServer(value -> {
                    var player = connection.getServerPlayer(); player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent();
                    player.getInventory().setSelectedSlot(0); player.getInventory().setItem(0, new ItemStack(item, 32)); BuilderController.sync(player);
                });
                connection.waitForClientboundPackets(); context.waitTicks(45);
                context.getInput().holdKey(BlockwheelClient.OPEN); context.waitForScreen(RadialScreen.class);
                capture(context, "blockwheel-cube-wheel-scale-" + guiScale + "-neutral");
                pointRight(context); context.waitTicks(2); capture(context, "blockwheel-cube-wheel-scale-" + guiScale + "-selected");
                context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); context.getInput().releaseKey(BlockwheelClient.OPEN);
                context.waitFor(client -> client.gui.screen() == null);
            }
            context.runOnClient(client -> { client.options.guiScale().set(3); client.resizeGui(); });
            server.runOnServer(value -> {
                value.getCommands().performPrefixedCommand(value.createCommandSourceStack(), "time set midnight");
                var player = connection.getServerPlayer(); player.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 32)); BuilderController.sync(player);
            });
            connection.waitForClientboundPackets(); context.waitTicks(45);
            context.getInput().holdKey(BlockwheelClient.OPEN); context.waitForScreen(RadialScreen.class);
            pointRight(context); context.waitTicks(2); capture(context, "blockwheel-cube-wheel-night");
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); context.getInput().releaseKey(BlockwheelClient.OPEN);
            context.waitFor(client -> client.gui.screen() == null);
            server.runOnServer(value -> value.getCommands().performPrefixedCommand(value.createCommandSourceStack(), "time set day"));
            var settings = BuilderSettings.defaults().withEnabled(false).withFeature(Feature.SHULKER_SUPPLY, false)
                    .withFeature(Feature.NORMALIZE, false).withFeature(Feature.PICKUP, false);
            context.runOnClient(client -> BuilderClient.request(settings)); context.waitFor(client -> BuilderClient.settings().equals(settings));
            server.runOnServer(value -> {
                var player = connection.getServerPlayer(); player.getInventory().clearContent(); player.getEnderChestInventory().clearContent();
                player.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 31)); player.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 40));
                player.getInventory().setItem(10, new ItemStack(Items.STICK, 5));
                player.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(Items.COBBLESTONE, 1), ItemStack.EMPTY, new ItemStack(Items.DIAMOND, 1)));
                player.getEnderChestInventory().setItem(1, ShulkerSupplyGameTests.box(false, new ItemStack(Items.COBBLESTONE, 1))); BuilderController.sync(player);
            });
            connection.waitForClientboundPackets(); context.getInput().pressKey(GLFW.GLFW_KEY_E); context.waitForScreen(InventoryScreen.class);
            context.waitTicks(2); capture(context, "blockwheel-inventory-deposit");
            UiTestInput.click(context, InventoryIconButton.Kind.DEPOSIT, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitFor(client -> client.player.getMainHandItem().getCount() == 31 && client.player.getInventory().getItem(9).isEmpty());
            if (!server.computeOnServer(value -> {
                var player = connection.getServerPlayer(); var contents = ShulkerSupplyGameTests.contents(player.getEnderChestInventory().getItem(0));
                return contents.get(0).getCount() == 41 && contents.get(1).isEmpty() && contents.get(2).getCount() == 1
                        && ShulkerSupplyGameTests.contents(player.getEnderChestInventory().getItem(1)).get(0).getCount() == 1 && player.getInventory().getItem(10).getCount() == 5;
            })) throw new AssertionError("Inventory deposit button changed template layout or ignored unnamed exclusion");
            context.runOnClient(client -> BuilderClient.request(settings.withEnabled(true)));
            context.waitFor(client -> BuilderClient.settings().enabled());
            server.runOnServer(value -> {
                var player = connection.getServerPlayer(); player.getInventory().clearContent(); player.getEnderChestInventory().clearContent();
                player.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE_STAIRS, 64));
                player.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(Items.COBBLESTONE, 1), ItemStack.EMPTY));
                BuilderController.sync(player);
            });
            connection.waitForClientboundPackets(); context.waitTicks(2);
            UiTestInput.hold(context, InventoryIconButton.Kind.DEPOSIT);
            context.waitFor(client -> client.player.getMainHandItem().is(Items.COBBLESTONE_STAIRS) && client.player.getMainHandItem().getCount() == 1);
            context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT); context.waitTicks(2);
            if (!server.computeOnServer(value -> {
                var contents = ShulkerSupplyGameTests.contents(connection.getServerPlayer().getEnderChestInventory().getItem(0));
                return contents.get(0).getCount() == 64 && contents.get(1).isEmpty();
            })) throw new AssertionError("Native fill button did not coerce hotbar stairs into the existing block template");
            capture(context, "blockwheel-coerced-template-fill");
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); context.waitFor(client -> client.gui.screen() == null);

            context.runOnClient(client -> BuilderClient.request(BuilderSettings.defaults())); context.waitFor(client -> !BuilderClient.settings().enabled());
            var chest = server.computeOnServer(value -> {
                var player = connection.getServerPlayer(); player.getInventory().clearContent(); player.getEnderChestInventory().clearContent();
                player.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 3));
                player.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(Items.COBBLESTONE, 1)));
                var position = player.blockPosition().offset(2, 0, 0); player.level().setBlockAndUpdate(position.below(), Blocks.STONE.defaultBlockState());
                player.level().setBlockAndUpdate(position.above(), Blocks.AIR.defaultBlockState());
                player.level().setBlockAndUpdate(position, Blocks.ENDER_CHEST.defaultBlockState()); BuilderController.sync(player); return position;
            });
            context.waitFor(client -> client.level.getBlockState(chest).is(Blocks.ENDER_CHEST));
            context.runOnClient(client -> client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(chest), Direction.UP, chest, false)));
            context.waitForScreen(ContainerScreen.class); context.waitTicks(2);
            context.runOnClient(client -> {
                var position = (ContainerScreenAccessor) client.gui.screen();
                var button = client.gui.screen().children().stream().filter(child -> child instanceof InventoryIconButton icon
                        && icon.kind() == InventoryIconButton.Kind.DEPOSIT).map(child -> (InventoryIconButton) child).findFirst().orElseThrow();
                if (button.getX() != position.blockwheel$getLeft() + position.blockwheel$getImageWidth() + 4) {
                    throw new AssertionError("Deposit button was not to the right of the ender chest panel");
                }
            });
            capture(context, "blockwheel-ender-deposit-button");
            UiTestInput.click(context, InventoryIconButton.Kind.DEPOSIT, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitFor(client -> client.player.getInventory().getItem(9).isEmpty());
            if (!server.computeOnServer(value -> ShulkerSupplyGameTests.contents(connection.getServerPlayer().getEnderChestInventory().getItem(0)).get(0).getCount() == 4)) {
                throw new AssertionError("Native ender chest dump did not work with Builder Mode disabled");
            }
            server.runOnServer(value -> {
                var p = connection.getServerPlayer(); p.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 5));
                p.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 7)); BuilderController.sync(p);
            }); connection.waitForClientboundPackets(); context.waitTicks(2);
            UiTestInput.hold(context, InventoryIconButton.Kind.DEPOSIT);
            context.waitFor(client -> client.gui.screen().children().stream().filter(child -> child instanceof InventoryIconButton icon && icon.kind() == InventoryIconButton.Kind.DEPOSIT)
                    .map(child -> (InventoryIconButton) child).anyMatch(icon -> icon.holdProgress() > .1 && icon.holdProgress() < .8));
            if (!server.computeOnServer(value -> connection.getServerPlayer().getInventory().getItem(9).getCount() == 7
                    && connection.getServerPlayer().getMainHandItem().getCount() == 5)) throw new AssertionError("Deposit fired before the hold threshold");
            context.takeScreenshot("blockwheel-deposit-hold-progress");
            context.waitFor(client -> client.player.getMainHandItem().isEmpty() && client.player.getInventory().getItem(9).isEmpty());
            server.runOnServer(value -> { connection.getServerPlayer().getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 3)); BuilderController.sync(connection.getServerPlayer()); });
            connection.waitForClientboundPackets(); context.waitTicks(4);
            context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT); context.waitTicks(4);
            if (!server.computeOnServer(value -> connection.getServerPlayer().getInventory().getItem(9).getCount() == 3
                    && ShulkerSupplyGameTests.contents(connection.getServerPlayer().getEnderChestInventory().getItem(0)).get(0).getCount() == 16))
                throw new AssertionError("Held deposit repeated while held or dispatched a second short action on release");
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); context.waitFor(client -> client.gui.screen() == null);
            Blockwheel.LOGGER.info("UI client tests passed: native-hotbar polygon frames at GUI scales 2/3/4, direct destination and shift-click merging, Builder family template filling, compact controls and ender-side deposits");
        } finally {
            context.getInput().releaseKey(BlockwheelClient.OPEN);
            context.runOnClient(client -> { client.options.guiScale().set(scale); client.resizeGui(); });
        }
    }
    private void pointRight(ClientGameTestContext context) {
        var point = context.computeOnClient(client -> {
            var window = client.getWindow();
            return new double[]{(window.getGuiScaledWidth() / 2.0 + 24) * window.getScreenWidth() / window.getGuiScaledWidth(),
                    (window.getGuiScaledHeight() / 2.0 - 3) * window.getScreenHeight() / window.getGuiScaledHeight()};
        });
        context.getInput().setCursorPos(point[0], point[1]);
    }
    private void verifyEnderPanelAnchor(ClientGameTestContext context) {
        int originalScale = context.computeOnClient(client -> client.options.guiScale().get());
        context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
        int originalSlots = context.computeOnClient(client -> client.player.inventoryMenu.slots.size());
        try {
            context.runOnClient(client -> {
                for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
                    client.player.inventoryMenu.slots.add(new Slot(client.player.getEnderChestInventory(), row * 9 + column,
                            192 + column * 18, 18 + row * 18));
                }
            });
            context.waitTicks(2);
            context.runOnClient(client -> {
                var position = (ContainerScreenAccessor) client.gui.screen();
                var button = client.gui.screen().children().stream().filter(child -> child instanceof InventoryIconButton icon
                        && icon.kind() == InventoryIconButton.Kind.DEPOSIT).map(child -> (InventoryIconButton) child).findFirst().orElseThrow();
                if (button.getX() != position.blockwheel$getLeft() + 192 + 8 * 18 + 28) {
                    throw new AssertionError("Deposit icon did not follow the right edge of exposed ender slots");
                }
            });
        } finally {
            context.runOnClient(client -> {
                var slots = client.player.inventoryMenu.slots;
                while (slots.size() > originalSlots) slots.remove(slots.size() - 1);
                client.options.guiScale().set(originalScale); client.resizeGui();
            });
        }
    }
    private void capture(ClientGameTestContext context, String name) {
        context.runOnClient(client -> { client.gui.toastManager().clear(); client.gui.hud.getChat().clearMessages(false); });
        context.waitTicks(1); context.takeScreenshot(name);
    }
}
