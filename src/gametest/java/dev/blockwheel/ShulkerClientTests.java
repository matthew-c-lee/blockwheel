package dev.blockwheel;

import dev.blockwheel.client.BuilderClient;
import dev.blockwheel.client.BuilderSettingsScreen;
import dev.blockwheel.client.mixin.ContainerScreenAccessor;
import dev.blockwheel.core.BuilderSettings;
import dev.blockwheel.core.BuilderSettings.Feature;
import dev.blockwheel.server.BuilderController;
import dev.blockwheel.server.BuilderServerConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

/** Release-JAR UI, actual placement/pick packets, remote tooltip queries and saved EnderItems. */
public final class ShulkerClientTests implements FabricClientGameTest {
    private Object tweak, pre, threshold;
    private boolean oldTweak, oldPre;
    private int oldThreshold;
    private BuilderSettings settings() {
        return BuilderSettings.defaults().withEnabled(true).withFeature(Feature.SHULKER_SUPPLY, true)
                .withRestock(dev.blockwheel.core.RestockSettings.DEFAULT.withAmount(99))
                .withFeature(Feature.NORMALIZE, false).withFeature(Feature.PICKUP, false);
    }
    @Override public void runTest(ClientGameTestContext context) {
        try {
            TestWorldSave saved; ItemStack persisted;
            try (var world = context.worldBuilder().create()) {
                var server = world.getServer(); var connection = world.getConnection(); ready(context, connection);
                if (BuilderClient.settings().shulkerSupply()) throw new AssertionError("New player was opted into shulker supply");
                context.getInput().pressKey(GLFW.GLFW_KEY_E); context.waitForScreen(InventoryScreen.class);
                context.clickScreenButton("Builder Mode: OFF"); context.waitFor(client -> BuilderClient.settings().enabled());
                UiTestInput.settings(context); context.waitForScreen(BuilderSettingsScreen.class);
                context.clickScreenButton(Component.translatable("ui.blockwheel.shulker_tab").getString()); context.clickScreenButton("Shulker Supply: OFF");
                context.waitFor(client -> BuilderClient.settings().shulkerSupply());
                context.takeScreenshot("blockwheel-shulker-settings");
                context.clickScreenButton("Done"); context.waitForScreen(InventoryScreen.class);
                context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); context.waitFor(client -> client.gui.screen() == null);
                configure(context, settings()); exercise(context, server, connection);
                persisted = server.computeOnServer(value -> connection.getServerPlayer().getEnderChestInventory().getItem(0).copy());
                saved = world.getWorldSave();
            }
            try (var reopened = saved.open()) {
                ready(context, reopened.getConnection());
                context.waitFor(client -> BuilderClient.settings().equals(settings()));
                if (!reopened.getServer().computeOnServer(value -> ItemStack.matches(persisted,
                        reopened.getConnection().getServerPlayer().getEnderChestInventory().getItem(0)))) {
                    throw new AssertionError("Remote shulker contents/metadata did not survive world close and reopen");
                }
                synced(context, reopened.getServer(), reopened.getConnection());
            }
            Properties properties = new Properties(); properties.setProperty("online-mode", "false"); properties.setProperty("max-tick-time", "-1");
            properties.setProperty("level-name", "blockwheel-shulker-test-" + System.nanoTime());
            try (var server = context.worldBuilder().createServer(properties)) {
                ItemStack remote;
                try (var connection = server.connect()) {
                    ready(context, connection); configure(context, settings()); exercise(context, server, connection);
                    remote = server.computeOnServer(value -> connection.getServerPlayer().getEnderChestInventory().getItem(0).copy());
                }
                try (var connection = server.connect()) {
                    ready(context, connection); context.waitFor(client -> BuilderClient.settings().equals(settings()));
                    if (!server.computeOnServer(value -> ItemStack.matches(remote, connection.getServerPlayer().getEnderChestInventory().getItem(0)))) {
                        throw new AssertionError("Remote withdrawal did not persist after dedicated-server reconnect");
                    }
                    synced(context, server, connection);
                }
            }
            Blockwheel.LOGGER.info("Shulker client tests passed: family/exact placement, throwables, food, stable tooltip refresh, hotbar pick, remote pick/tooltips, policy, world restart and dedicated reconnect; Tweakeroo={}", tweak != null);
        } finally { restoreTweak(context); }
    }
    private void ready(ClientGameTestContext context, TestServerConnection connection) {
        connection.waitForChunksRender(); context.waitFor(client -> BuilderClient.material(new ItemStack(Items.COBBLESTONE_STAIRS)) != null);
        enableTweak(context);
    }
    private void configure(ClientGameTestContext context, BuilderSettings settings) {
        context.runOnClient(client -> BuilderClient.request(settings)); context.waitFor(client -> BuilderClient.settings().equals(settings));
    }
    private void seed(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, Consumer<ServerPlayer> seed) {
        server.runOnServer(value -> {
            var player = connection.getServerPlayer(); player.setGameMode(GameType.SURVIVAL);
            player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(16)).forEach(ItemEntity::discard);
            player.getInventory().clearContent(); player.getEnderChestInventory().clearContent(); player.getInventory().setSelectedSlot(0);
            player.inventoryMenu.setCarried(ItemStack.EMPTY); seed.accept(player);
            player.connection.send(new ClientboundSetHeldSlotPacket(0)); BuilderController.sync(player);
        });
        connection.waitForClientboundPackets(); context.waitTicks(2);
    }
    private static ItemStack stack(Item item, int count) { return new ItemStack(item, count); }
    private static ItemStack box(boolean named, Item item, int count) { return ShulkerSupplyGameTests.box(named, stack(item, count)); }
    private static int inside(ItemStack box) { return ShulkerSupplyGameTests.contents(box).get(0).getCount(); }

    private void exercise(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
        configure(context, settings());
        seed(context, server, connection, player -> {
            player.getInventory().setItem(0, stack(Items.COBBLESTONE_STAIRS, 2));
            player.getInventory().setItem(9, box(false, Items.COBBLESTONE_WALL, 20));
            player.getEnderChestInventory().setItem(0, box(false, Items.COBBLESTONE, 30));
            player.getEnderChestInventory().setItem(1, box(true, Items.COBBLESTONE, 41));
        });
        var floor = server.computeOnServer(value -> {
            var player = connection.getServerPlayer(); var position = player.blockPosition().offset(2, -1, 0);
            player.level().setBlockAndUpdate(position, Blocks.STONE.defaultBlockState());
            player.level().setBlockAndUpdate(position.above(), Blocks.AIR.defaultBlockState()); return position;
        });
        context.waitFor(client -> client.level.getBlockState(floor).is(Blocks.STONE));
        context.runOnClient(client -> client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor).add(0, .5, 0), Direction.UP, floor, false)));
        context.waitFor(client -> client.level.getBlockState(floor.above()).is(Blocks.COBBLESTONE_STAIRS)
                && client.player.getMainHandItem().is(Items.COBBLESTONE_STAIRS) && client.player.getMainHandItem().getCount() == 64);
        if (!server.computeOnServer(value -> {
            var player = connection.getServerPlayer(); return inside(player.getInventory().getItem(9)) == 0
                    && inside(player.getEnderChestInventory().getItem(0)) == 0 && inside(player.getEnderChestInventory().getItem(1)) == 28;
        })) throw new AssertionError("Native placement ignored source priority or named reserves");
        synced(context, server, connection);

        BlockPos target = server.computeOnServer(value -> {
            var player = connection.getServerPlayer(); var position = player.blockPosition().offset(2, 0, 1);
            player.level().setBlockAndUpdate(position, Blocks.COBBLESTONE_WALL.defaultBlockState()); return position;
        });
        context.waitFor(client -> client.level.getBlockState(target).is(Blocks.COBBLESTONE_WALL));
        for (int source : new int[]{0, 5}) {
            seed(context, server, connection, player -> {
                for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, stack(Items.DIRT, 64));
                player.getInventory().setItem(source, stack(Items.COBBLESTONE_STAIRS, 64));
            });
            pick(context, target);
            context.waitFor(client -> client.player.getInventory().getSelectedSlot() == source
                    && client.player.getMainHandItem().is(Items.COBBLESTONE_WALL) && client.player.getMainHandItem().getCount() == 64);
            if (!server.computeOnServer(value -> connection.getServerPlayer().getInventory().countItem(Items.DIRT) == 35 * 64)) {
                throw new AssertionError("Full-inventory hotbar pick overwrote displaced items");
            }
            synced(context, server, connection);
        }

        exerciseExactRestock(context, server, connection);
        exerciseTooltips(context, server, connection);

        seed(context, server, connection, player -> {
            player.getInventory().setItem(0, stack(Items.DIRT, 1)); player.getInventory().setItem(9, ShulkerSupplyGameTests.box(false));
            player.getEnderChestInventory().setItem(0, box(true, Items.COBBLESTONE, 64));
        });
        configure(context, settings().withFeature(Feature.SHULKER_PICK_BLOCK, false)); pick(context, target);
        connection.waitForServerboundPackets(); context.waitTicks(3);
        if (!server.computeOnServer(value -> connection.getServerPlayer().getMainHandItem().is(Items.DIRT)
                && inside(connection.getServerPlayer().getEnderChestInventory().getItem(0)) == 64)) throw new AssertionError("Disabled shulker pick withdrew material");
        configure(context, settings()); pick(context, target);
        context.waitFor(client -> client.player.getMainHandItem().is(Items.COBBLESTONE_WALL) && client.player.getMainHandItem().getCount() == 63);
        if (!server.computeOnServer(value -> {
            var box = connection.getServerPlayer().getEnderChestInventory().getItem(0);
            return inside(box) == 1 && box.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)
                    && box.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA) && box.is(Items.DYED_SHULKER_BOX.blue());
        })) throw new AssertionError("Native remote pick lost the protected slot or shulker metadata");
        synced(context, server, connection);
    }

    private void exerciseExactRestock(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
        seed(context, server, connection, player -> {
            player.getInventory().setItem(0, stack(Items.NETHERRACK, 2)); player.getInventory().setItem(10, stack(Items.NETHERRACK, 20));
            player.getInventory().setItem(9, box(false, Items.NETHERRACK, 30)); player.getEnderChestInventory().setItem(0, box(true, Items.NETHERRACK, 64));
        });
        if (tweak != null) {
            context.runOnClient(client -> {
                try {
                    var type = Class.forName("fi.dy.masa.tweakeroo.util.InventoryUtils");
                    type.getMethod("preRestockHand", net.minecraft.world.entity.player.Player.class, InteractionHand.class, boolean.class)
                            .invoke(null, client.player, InteractionHand.MAIN_HAND, true);
                    type.getMethod("restockNewStackToHand", net.minecraft.world.entity.player.Player.class, InteractionHand.class, ItemStack.class, boolean.class)
                            .invoke(null, client.player, InteractionHand.MAIN_HAND, stack(Items.NETHERRACK, 2), true);
                } catch (ReflectiveOperationException error) { throw new AssertionError("Exact-item Tweakeroo guard failed", error); }
            });
            connection.waitForServerboundPackets(); context.waitTicks(3);
            if (!server.computeOnServer(value -> connection.getServerPlayer().getMainHandItem().getCount() == 2
                    && connection.getServerPlayer().getInventory().getItem(10).getCount() == 20)) {
                throw new AssertionError("Tweakeroo competed with exact server restocking");
            }
            synced(context, server, connection);
        }
        var floor = server.computeOnServer(value -> {
            var player = connection.getServerPlayer(); var position = player.blockPosition().offset(2, -1, -1);
            player.level().setBlockAndUpdate(position, Blocks.STONE.defaultBlockState());
            player.level().setBlockAndUpdate(position.above(), Blocks.AIR.defaultBlockState()); return position;
        });
        context.waitFor(client -> client.level.getBlockState(floor).is(Blocks.STONE));
        context.runOnClient(client -> client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor).add(0, .5, 0), Direction.UP, floor, false)));
        context.waitFor(client -> client.level.getBlockState(floor.above()).is(Blocks.NETHERRACK)
                && client.player.getMainHandItem().is(Items.NETHERRACK) && client.player.getMainHandItem().getCount() == 64);
        if (!server.computeOnServer(value -> {
            var player = connection.getServerPlayer(); return player.getInventory().getItem(10).isEmpty()
                    && inside(player.getInventory().getItem(9)) == 0 && inside(player.getEnderChestInventory().getItem(0)) == 51;
        })) throw new AssertionError("Netherrack placement did not use exact main/carried/remote sources");
        synced(context, server, connection);

        seed(context, server, connection, player -> {
            player.getInventory().setItem(0, stack(Items.NETHERRACK, 1)); player.getEnderChestInventory().setItem(0, box(false, Items.NETHERRACK, 64));
        });
        context.getInput().pressKey(GLFW.GLFW_KEY_Q); context.waitFor(client -> client.player.getMainHandItem().isEmpty()); context.waitTicks(3);
        if (!server.computeOnServer(value -> connection.getServerPlayer().getMainHandItem().isEmpty()
                && inside(connection.getServerPlayer().getEnderChestInventory().getItem(0)) == 64)) throw new AssertionError("Intentional drop triggered an exact refill");
        synced(context, server, connection);

        seed(context, server, connection, player -> {
            player.getInventory().setItem(0, stack(Items.SNOWBALL, 2)); player.getEnderChestInventory().setItem(0, box(true, Items.SNOWBALL, 16));
        });
        context.runOnClient(client -> client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND));
        context.waitFor(client -> client.player.getMainHandItem().is(Items.SNOWBALL) && client.player.getMainHandItem().getCount() == 16);
        if (!server.computeOnServer(value -> inside(connection.getServerPlayer().getEnderChestInventory().getItem(0)) == 1)) {
            throw new AssertionError("Native non-block use did not preserve exact source counts");
        }
        synced(context, server, connection);

        seed(context, server, connection, player -> {
            player.getFoodData().setFoodLevel(8); player.getInventory().setItem(0, stack(Items.BREAD, 2));
            player.getEnderChestInventory().setItem(0, box(true, Items.BREAD, 64));
        });
        context.waitFor(client -> client.player.canEat(false));
        context.getInput().lookAt(0, -75);
        context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        try {
            context.waitFor(client -> client.player.getMainHandItem().is(Items.BREAD) && client.player.getMainHandItem().getCount() == 64);
        } finally { context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT); }
        if (!server.computeOnServer(value -> inside(connection.getServerPlayer().getEnderChestInventory().getItem(0)) == 1)) {
            throw new AssertionError("Completed food consumption did not restock or spent the named reserve");
        }
        synced(context, server, connection);
    }

    private void exerciseTooltips(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
        seed(context, server, connection, player -> {
            player.getInventory().setItem(0, stack(Items.COBBLESTONE_STAIRS, 1)); player.getInventory().setItem(10, stack(Items.COBBLESTONE, 10));
            player.getInventory().setItem(9, box(false, Items.COBBLESTONE_WALL, 20)); player.getEnderChestInventory().setItem(0, box(true, Items.COBBLESTONE, 64));
        });
        context.waitFor(client -> tooltip(client).contains("Available material: 94 blocks") && tooltip(client).contains("Protected templates: 1 blocks")
                && tooltip(client).contains("Ender chest shulkers: 63 blocks"));
        context.runOnClient(client -> {
            var previous = tooltip(client); BuilderClient.invalidateSupply();
            if (!tooltip(client).equals(previous) || !tooltip(client).equals(previous)) {
                throw new AssertionError("Inventory invalidation or pending refresh replaced the last complete tooltip");
            }
        });
        for (int tick = 0; tick < 26; tick++) {
            context.waitTicks(1);
            context.runOnClient(client -> {
                var lines = tooltip(client);
                if (!lines.contains("Available material: 94 blocks") || lines.contains("Checking shulker supply...")) {
                    throw new AssertionError("Periodic supply refresh flickered to loading text");
                }
            });
        }
        server.runOnServer(value -> {
            var player = connection.getServerPlayer(); player.getInventory().setItem(10, stack(Items.COBBLESTONE, 9)); BuilderController.sync(player);
        });
        connection.waitForClientboundPackets();
        context.runOnClient(client -> {
            if (tooltip(client).contains("Checking shulker supply...")) throw new AssertionError("Inventory sync hid the previous supply breakdown");
        });
        context.waitFor(client -> tooltip(client).contains("Available material: 93 blocks") && tooltip(client).contains("Inventory + hotbar: 10 blocks"));
        server.runOnServer(value -> {
            var player = connection.getServerPlayer(); player.getInventory().setItem(10, stack(Items.COBBLESTONE, 10)); BuilderController.sync(player);
        });
        context.waitFor(client -> tooltip(client).contains("Available material: 94 blocks"));
        context.getInput().pressKey(GLFW.GLFW_KEY_E); context.waitForScreen(InventoryScreen.class);
        double[] point = context.computeOnClient(client -> {
            var position = (ContainerScreenAccessor) client.gui.screen(); var window = client.getWindow();
            return new double[]{(position.blockwheel$getLeft() + 16) * window.getScreenWidth() / window.getGuiScaledWidth(),
                    (position.blockwheel$getTop() + 150) * window.getScreenHeight() / window.getGuiScaledHeight()};
        });
        context.getInput().setCursorPos(point[0], point[1]); context.waitTicks(2); context.takeScreenshot("blockwheel-shulker-tooltip");
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); context.waitFor(client -> client.gui.screen() == null);
        boolean allowed = server.computeOnServer(value -> Blockwheel.BUILDERS.enderAllowed());
        try {
            server.runOnServer(value -> { Blockwheel.BUILDERS.setPolicy(new BuilderServerConfig(false)); Blockwheel.BUILDERS.join(connection.getServerPlayer()); });
            context.waitFor(client -> !BuilderClient.enderAllowed());
            context.waitFor(client -> tooltip(client).contains("Available material: 31 blocks") && tooltip(client).contains("Ender chest shulkers: 0 blocks"));
            context.getInput().pressKey(GLFW.GLFW_KEY_E); context.waitForScreen(InventoryScreen.class);
            UiTestInput.settings(context); context.waitForScreen(BuilderSettingsScreen.class); context.clickScreenButton(Component.translatable("ui.blockwheel.shulker_tab").getString());
            boolean disabled = context.computeOnClient(client -> client.gui.screen().children().stream().filter(child -> child instanceof Button button
                    && button.getMessage().getString().equals("Ender chest access: Server disabled")).map(child -> (Button) child).anyMatch(button -> !button.active));
            if (!disabled) throw new AssertionError("Server remote denial was not visible in settings");
            context.takeScreenshot("blockwheel-shulker-policy");
            context.clickScreenButton("Done"); context.waitForScreen(InventoryScreen.class);
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); context.waitFor(client -> client.gui.screen() == null);
        } finally {
            server.runOnServer(value -> { Blockwheel.BUILDERS.setPolicy(new BuilderServerConfig(allowed)); Blockwheel.BUILDERS.join(connection.getServerPlayer()); });
        }
        context.waitFor(client -> BuilderClient.enderAllowed() == allowed);
        seed(context, server, connection, player -> player.getEnderChestInventory().setItem(0, box(true, Items.COBBLESTONE_SLAB, 2)));
        context.waitFor(client -> tooltip(client).contains("Available material: 1/2 blocks") && tooltip(client).contains("Whole shape equivalents: 0"));
    }
    private List<String> tooltip(Minecraft client) {
        return new ItemStack(Items.COBBLESTONE_STAIRS).getTooltipLines(Item.TooltipContext.of(client.level), client.player, TooltipFlag.NORMAL)
                .stream().map(net.minecraft.network.chat.Component::getString).toList();
    }
    private void pick(ClientGameTestContext context, BlockPos target) {
        context.runOnClient(client -> client.getConnection().send(new ServerboundPickItemFromBlockPacket(target, false)));
    }
    private void synced(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
        connection.waitForServerboundPackets(); connection.waitForClientboundPackets();
        var actual = server.computeOnServer(value -> {
            var player = connection.getServerPlayer(); var stacks = new ArrayList<ItemStack>();
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) stacks.add(player.getInventory().getItem(slot).copy());
            stacks.add(player.containerMenu.getCarried().copy()); return stacks;
        });
        context.runOnClient(client -> {
            int size = client.player.getInventory().getContainerSize();
            for (int slot = 0; slot < size; slot++) if (!ItemStack.matches(actual.get(slot), client.player.getInventory().getItem(slot))) {
                throw new AssertionError("Shulker inventory desync at regular slot " + slot);
            }
            if (!ItemStack.matches(actual.get(size), client.player.containerMenu.getCarried())) throw new AssertionError("Shulker carried-stack desync");
        });
    }
    private void enableTweak(ClientGameTestContext context) {
        if (!FabricLoader.getInstance().isModLoaded("tweakeroo")) return;
        context.runOnClient(client -> {
            try {
                if (tweak == null) {
                    tweak = Class.forName("fi.dy.masa.tweakeroo.config.FeatureToggle").getField("TWEAK_HAND_RESTOCK").get(null);
                    var generic = Class.forName("fi.dy.masa.tweakeroo.config.Configs$Generic"); pre = generic.getField("HAND_RESTOCK_PRE").get(null);
                    threshold = generic.getField("HAND_RESTOCK_PRE_THRESHOLD").get(null);
                    oldTweak = (boolean) tweak.getClass().getMethod("getBooleanValue").invoke(tweak);
                    oldPre = (boolean) pre.getClass().getMethod("getBooleanValue").invoke(pre);
                    oldThreshold = (int) threshold.getClass().getMethod("getIntegerValue").invoke(threshold);
                }
                tweak.getClass().getMethod("setBooleanValue", boolean.class).invoke(tweak, true);
                pre.getClass().getMethod("setBooleanValue", boolean.class).invoke(pre, true);
                threshold.getClass().getMethod("setIntegerValue", int.class).invoke(threshold, 16);
            } catch (ReflectiveOperationException error) { throw new AssertionError("Actual Tweakeroo shulker compatibility setup failed", error); }
        });
    }
    private void restoreTweak(ClientGameTestContext context) {
        if (tweak == null) return;
        context.runOnClient(client -> {
            try {
                tweak.getClass().getMethod("setBooleanValue", boolean.class).invoke(tweak, oldTweak);
                pre.getClass().getMethod("setBooleanValue", boolean.class).invoke(pre, oldPre);
                threshold.getClass().getMethod("setIntegerValue", int.class).invoke(threshold, oldThreshold);
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        });
    }
}
