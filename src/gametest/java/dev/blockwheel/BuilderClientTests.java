package dev.blockwheel;

import dev.blockwheel.client.BuilderClient;
import dev.blockwheel.client.BuilderSettingsScreen;
import dev.blockwheel.client.BlockwheelClient;
import dev.blockwheel.client.RadialScreen;
import dev.blockwheel.core.RadialLayout;
import dev.blockwheel.core.BuilderSettings;
import dev.blockwheel.core.BuilderSettings.Feature;
import dev.blockwheel.server.BuilderController;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

/** Real screen controls, inventory packets, saved player data and dedicated-server connections. */
public final class BuilderClientTests implements FabricClientGameTest {
    private Object handToggle, preToggle, threshold;
    private boolean oldHand, oldPre;
    private int oldThreshold;

    @Override public void runTest(ClientGameTestContext context) {
        try {
            TestWorldSave savedWorld;
            BuilderSettings saved = new BuilderSettings(true, BuilderSettings.ALL_FEATURES & ~Feature.NORMALIZE.bit() & ~Feature.PICKUP.bit())
                    .withRestock(new dev.blockwheel.core.RestockSettings(10, 11, false));
            try (var world = context.worldBuilder().create()) {
                var server = world.getServer(); var connection = world.getConnection();
                ready(context, connection);
                if (BuilderClient.settings().enabled()) throw new AssertionError("A new player's Builder Mode did not default off");
                seed(context, server, connection, player -> {
                    set(player, 0, Items.COBBLESTONE_STAIRS, 1);
                    set(player, 9, Items.COBBLESTONE_WALL, 33); set(player, 10, Items.COBBLESTONE_SLAB, 33);
                });
                context.getInput().pressKey(GLFW.GLFW_KEY_E); context.waitForScreen(InventoryScreen.class);
                context.clickScreenButton("Builder Mode: OFF");
                context.waitFor(client -> BuilderClient.settings().enabled());
                context.waitFor(client -> client.player.getInventory().countItem(Items.COBBLESTONE) == 49
                        && client.player.getInventory().countItem(Items.COBBLESTONE_SLAB) == 1);
                context.takeScreenshot("blockwheel-builder-inventory");
                UiTestInput.settings(context); context.waitForScreen(BuilderSettingsScreen.class); context.clickScreenButton("Builder");
                context.takeScreenshot("blockwheel-builder-settings");
                context.clickScreenButton("Compact main inventory: ON");
                context.waitFor(client -> !BuilderClient.settings().feature(Feature.NORMALIZE));
                if (!BuilderClient.settings().active(Feature.RESTOCK)) throw new AssertionError("Independent feature toggle disabled restocking");
                context.clickScreenButton("Done"); context.waitForScreen(InventoryScreen.class);
                context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); context.waitFor(client -> client.gui.screen() == null);

                exercisePackets(context, server, connection);
                exerciseTooltip(context, server, connection);
                if (handToggle != null) exerciseTweakeroo(context, server, connection);
                configure(context, BuilderSettings.defaults().withEnabled(true));
                server.runOnServer(value -> connection.getServerPlayer().setGameMode(GameType.CREATIVE));
                context.waitFor(client -> client.player.getAbilities().instabuild);
                context.getInput().pressKey(GLFW.GLFW_KEY_E); context.waitForScreen(CreativeModeInventoryScreen.class);
                UiTestInput.creativeInventory(context);
                context.takeScreenshot("blockwheel-builder-creative");
                context.clickScreenButton("Builder Mode: ON");
                context.waitFor(client -> !BuilderClient.settings().enabled());
                context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); context.waitFor(client -> client.gui.screen() == null);
                server.runOnServer(value -> connection.getServerPlayer().setGameMode(GameType.SURVIVAL));
                context.waitFor(client -> !client.player.getAbilities().instabuild);
                configure(context, saved);
                assertSynced(context, server, connection);
                savedWorld = world.getWorldSave();
            }
            try (var reopened = savedWorld.open()) {
                ready(context, reopened.getConnection());
                context.waitFor(client -> BuilderClient.settings().equals(saved));
                var actual = reopened.getServer().computeOnServer(server -> Blockwheel.BUILDERS.settings(reopened.getConnection().getServerPlayer()));
                if (!actual.equals(saved)) throw new AssertionError("Settings did not survive world close and reopen");
                assertSynced(context, reopened.getServer(), reopened.getConnection());
            }

            Properties properties = new Properties();
            properties.setProperty("online-mode", "false"); properties.setProperty("max-tick-time", "-1");
            properties.setProperty("level-name", "blockwheel-builder-test-" + System.nanoTime());
            try (var server = context.worldBuilder().createServer(properties)) {
                try (var connection = server.connect()) {
                    ready(context, connection);
                    if (BuilderClient.settings().enabled()) throw new AssertionError("New dedicated-server player inherited another world's setting");
                    configure(context, BuilderSettings.defaults().withEnabled(true));
                    exercisePackets(context, server, connection);
                    configure(context, saved);
                }
                try (var reconnected = server.connect()) {
                    ready(context, reconnected);
                    context.waitFor(client -> BuilderClient.settings().equals(saved));
                    if (!server.computeOnServer(value -> Blockwheel.BUILDERS.settings(reconnected.getServerPlayer()).equals(saved))) {
                        throw new AssertionError("Per-player settings did not survive dedicated-server reconnect");
                    }
                    assertSynced(context, server, reconnected);
                }
            }
            Blockwheel.LOGGER.info("Builder client tests passed: UI, singleplayer restart, dedicated reconnect, native packets, Tweakeroo={}", handToggle != null);
        } finally { restoreTweakeroo(context); }
    }

    private void ready(ClientGameTestContext context, TestServerConnection connection) {
        connection.waitForChunksRender();
        context.waitFor(client -> BuilderClient.material(new ItemStack(Items.COBBLESTONE_STAIRS)) != null);
        // MaLiLib can reload configuration during connection setup, so configure after joining.
        enableTweakeroo(context);
    }
    private void configure(ClientGameTestContext context, BuilderSettings settings) {
        context.runOnClient(client -> BuilderClient.request(settings));
        context.waitFor(client -> BuilderClient.settings().equals(settings));
    }
    private static void set(ServerPlayer player, int slot, Item item, int count) { player.getInventory().setItem(slot, new ItemStack(item, count)); }
    private void seed(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, Consumer<ServerPlayer> seeder) {
        server.runOnServer(value -> {
            var player = connection.getServerPlayer(); player.setGameMode(GameType.SURVIVAL);
            player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(16)).forEach(ItemEntity::discard);
            player.getInventory().clearContent(); player.getInventory().setSelectedSlot(0);
            player.inventoryMenu.setCarried(ItemStack.EMPTY); seeder.accept(player);
            player.connection.send(new ClientboundSetHeldSlotPacket(0)); BuilderController.sync(player);
        });
        connection.waitForClientboundPackets(); context.waitTicks(2);
    }
    private void exercisePackets(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
        configure(context, BuilderSettings.defaults().withEnabled(true));
        seed(context, server, connection, player -> set(player, 0, Items.COBBLESTONE_SLAB, 63));
        server.runOnServer(value -> {
            var player = connection.getServerPlayer();
            var entity = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), new ItemStack(Items.COBBLESTONE_WALL, 10));
            entity.setNoPickUpDelay(); player.level().addFreshEntity(entity); entity.playerTouch(player);
        });
        context.waitFor(client -> client.player.getMainHandItem().is(Items.COBBLESTONE_SLAB)
                && client.player.getMainHandItem().getCount() == 64 && client.player.getInventory().countItem(Items.COBBLESTONE) == 9
                && client.player.getInventory().countItem(Items.COBBLESTONE_SLAB) == 65);
        assertSynced(context, server, connection);

        seed(context, server, connection, player -> set(player, 0, Items.COBBLESTONE_STAIRS, 64));
        context.getInput().pressKey(GLFW.GLFW_KEY_E); context.waitForScreen(InventoryScreen.class);
        context.runOnClient(client -> client.gameMode.handleContainerInput(client.player.containerMenu.containerId, 36, 0, ContainerInput.QUICK_MOVE, client.player));
        context.waitFor(client -> client.player.getInventory().countItem(Items.COBBLESTONE) == 64
                && client.player.getInventory().countItem(Items.COBBLESTONE_STAIRS) == 0);
        assertSynced(context, server, connection);
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); context.waitFor(client -> client.gui.screen() == null);

        seed(context, server, connection, player -> {
            set(player, 0, Items.COBBLESTONE_STAIRS, 1); set(player, 1, Items.COBBLESTONE_STAIRS, 17);
            set(player, 9, Items.COBBLESTONE, 10); set(player, Inventory.SLOT_OFFHAND, Items.COBBLESTONE_STAIRS, 19);
        });
        BlockPos floor = server.computeOnServer(value -> {
            var player = connection.getServerPlayer(); var pos = player.blockPosition().offset(2, -1, 0);
            player.level().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            player.level().setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState()); return pos;
        });
        context.waitFor(client -> client.level.getBlockState(floor).is(Blocks.STONE));
        context.runOnClient(client -> client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor).add(0, .5, 0), Direction.UP, floor, false)));
        context.waitFor(client -> client.level.getBlockState(floor.above()).is(Blocks.COBBLESTONE_STAIRS)
                && client.player.getMainHandItem().is(Items.COBBLESTONE_STAIRS) && client.player.getMainHandItem().getCount() == 10);
        if (!server.computeOnServer(value -> connection.getServerPlayer().getInventory().getItem(1).getCount() == 17
                && connection.getServerPlayer().getOffhandItem().getCount() == 19
                && connection.getServerPlayer().getInventory().getItem(9).isEmpty())) throw new AssertionError("Restock used another inventory domain");
        assertSynced(context, server, connection);

        seed(context, server, connection, player -> { set(player, 0, Items.COBBLESTONE_STAIRS, 1); set(player, 9, Items.COBBLESTONE, 64); });
        context.getInput().pressKey(GLFW.GLFW_KEY_Q);
        context.waitFor(client -> client.player.getMainHandItem().isEmpty()); context.waitTicks(5);
        if (!server.computeOnServer(value -> connection.getServerPlayer().getInventory().getItem(0).isEmpty()
                && connection.getServerPlayer().getInventory().getItem(9).getCount() == 64)) throw new AssertionError("Dropping the held item triggered restocking");

        seed(context, server, connection, player -> { set(player, 0, Items.DIRT, 1); set(player, 9, Items.COBBLESTONE, 16); });
        BlockPos pick = server.computeOnServer(value -> {
            var player = connection.getServerPlayer(); var pos = player.blockPosition().offset(2, 0, 1);
            player.level().setBlockAndUpdate(pos, Blocks.COBBLESTONE_WALL.defaultBlockState()); return pos;
        });
        context.waitFor(client -> client.level.getBlockState(pick).is(Blocks.COBBLESTONE_WALL));
        context.runOnClient(client -> client.getConnection().send(new ServerboundPickItemFromBlockPacket(pick, false)));
        context.waitFor(client -> client.player.getMainHandItem().is(Items.COBBLESTONE_WALL) && client.player.getMainHandItem().getCount() == 16);
        assertSynced(context, server, connection);
        if (!server.computeOnServer(value -> connection.getServerPlayer().getInventory().countItem(Items.DIRT) == 1
                && connection.getServerPlayer().getInventory().countItem(Items.COBBLESTONE) == 0)) throw new AssertionError("Pick lost existing items or failed to consume material");

        seed(context, server, connection, player -> {
            set(player, 0, Items.DIRT, 1); set(player, 5, Items.COBBLESTONE_WALL, 7); set(player, 9, Items.COBBLESTONE, 64);
        });
        context.runOnClient(client -> client.getConnection().send(new ServerboundPickItemFromBlockPacket(pick, false)));
        context.waitFor(client -> client.player.getInventory().getSelectedSlot() == 5 && client.player.getMainHandItem().getCount() == 7);
        if (!server.computeOnServer(value -> connection.getServerPlayer().getInventory().getItem(9).getCount() == 64)) throw new AssertionError("Exact pick unnecessarily converted source material");
        assertSynced(context, server, connection);

        seed(context, server, connection, player -> {
            for (int i = 0; i < 9; i++) set(player, i, Items.DIRT, 64);
            set(player, 0, Items.COBBLESTONE_STAIRS, 5); set(player, 9, Items.COBBLESTONE, 16);
        });
        context.runOnClient(client -> client.getConnection().send(new ServerboundPickItemFromBlockPacket(pick, false)));
        context.waitFor(client -> client.player.getMainHandItem().is(Items.COBBLESTONE_WALL)
                && client.player.getMainHandItem().getCount() == 21 && client.player.getInventory().countItem(Items.COBBLESTONE) == 0);
        assertSynced(context, server, connection);

        seed(context, server, connection, player -> set(player, 0, Items.COBBLESTONE, 8));
        context.getInput().holdKey(BlockwheelClient.OPEN); context.waitForScreen(RadialScreen.class);
        double[] point = context.computeOnClient(client -> {
            var window = client.getWindow(); var layout = RadialLayout.fit(window.getGuiScaledWidth(), window.getGuiScaledHeight(), 4);
            return new double[]{(layout.centerX() - 24) * window.getScreenWidth() / window.getGuiScaledWidth(),
                    layout.centerY() * window.getScreenHeight() / window.getGuiScaledHeight()};
        });
        context.getInput().setCursorPos(point[0], point[1]); context.waitTicks(1);
        context.getInput().releaseKey(BlockwheelClient.OPEN);
        context.waitFor(client -> client.gui.screen() == null && client.player.getMainHandItem().is(Items.COBBLESTONE_SLAB)
                && client.player.getMainHandItem().getCount() == 16);
        assertSynced(context, server, connection);
        exerciseFractionalManual(context, server, connection);
    }

    private void exerciseFractionalManual(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
        for (int[] counts : new int[][]{{1, 1}, {31, 1}, {31, 0}, {1, 63}}) {
            int held = counts[0], stored = counts[1];
            int blocks = (held + stored) / 2;
            seed(context, server, connection, player -> {
                set(player, 0, Items.COBBLESTONE_SLAB, held);
                if (stored > 0) set(player, 9, Items.COBBLESTONE_SLAB, stored);
            });
            context.getInput().holdKey(BlockwheelClient.OPEN); context.waitForScreen(RadialScreen.class);
            pointAtFullBlock(context);
            String expected = (blocks * 2) + " -> " + blocks;
            context.waitFor(client -> client.gui.screen().getNarrationMessage().getString().equals("Full Block. " + expected));
            if (held == 31 && stored == 1) context.takeScreenshot("blockwheel-inventory-slab-preview");
            context.getInput().releaseKey(BlockwheelClient.OPEN);
            context.waitFor(client -> client.gui.screen() == null && client.player.getMainHandItem().is(Items.COBBLESTONE)
                    && client.player.getMainHandItem().getCount() == blocks);
            int remainingSlabs = held + stored - blocks * 2;
            context.waitFor(client -> client.player.getInventory().countItem(Items.COBBLESTONE_SLAB) == remainingSlabs);
            if (!server.computeOnServer(value -> {
                var inventory = connection.getServerPlayer().getInventory();
                for (int slot = 1; slot < 9; slot++) if (!inventory.getItem(slot).isEmpty()) return false;
                return remainingSlabs == 0 || inventory.getItem(9).is(Items.COBBLESTONE_SLAB)
                        && inventory.getItem(9).getCount() == remainingSlabs;
            })) throw new AssertionError("Manual remainder or result used the wrong inventory domain");
            assertSynced(context, server, connection);
        }

        // Availability can change while R is held; the preview and server use current main storage.
        seed(context, server, connection, player -> set(player, 0, Items.COBBLESTONE_SLAB, 1));
        context.getInput().holdKey(BlockwheelClient.OPEN); context.waitForScreen(RadialScreen.class); pointAtFullBlock(context);
        server.runOnServer(value -> { var player = connection.getServerPlayer(); set(player, 9, Items.COBBLESTONE_SLAB, 1); BuilderController.sync(player); });
        context.waitFor(client -> client.gui.screen().getNarrationMessage().getString().equals("Full Block. 2 -> 1"));
        context.getInput().releaseKey(BlockwheelClient.OPEN);
        context.waitFor(client -> client.gui.screen() == null && client.player.getMainHandItem().is(Items.COBBLESTONE)
                && client.player.getMainHandItem().getCount() == 1);
        assertSynced(context, server, connection);

        seed(context, server, connection, player -> { set(player, 0, Items.COBBLESTONE_SLAB, 31); set(player, 9, Items.COBBLESTONE_SLAB, 1); });
        context.getInput().holdKey(BlockwheelClient.OPEN); context.waitForScreen(RadialScreen.class); pointAtFullBlock(context);
        context.waitFor(client -> client.gui.screen().getNarrationMessage().getString().equals("Full Block. 32 -> 16"));
        server.runOnServer(value -> { var player = connection.getServerPlayer(); player.getInventory().setItem(9, ItemStack.EMPTY); BuilderController.sync(player); });
        context.waitFor(client -> client.gui.screen().getNarrationMessage().getString().equals("Full Block. 30 -> 15"));
        context.getInput().releaseKey(BlockwheelClient.OPEN);
        context.waitFor(client -> client.gui.screen() == null && client.player.getMainHandItem().is(Items.COBBLESTONE)
                && client.player.getMainHandItem().getCount() == 15 && client.player.getInventory().getItem(9).is(Items.COBBLESTONE_SLAB));
        assertSynced(context, server, connection);
    }

    private void pointAtFullBlock(ClientGameTestContext context) {
        double[] point = context.computeOnClient(client -> {
            var window = client.getWindow(); var layout = RadialLayout.fit(window.getGuiScaledWidth(), window.getGuiScaledHeight(), 4);
            return new double[]{layout.centerX() * window.getScreenWidth() / window.getGuiScaledWidth(),
                    (layout.centerY() - 24) * window.getScreenHeight() / window.getGuiScaledHeight()};
        });
        context.getInput().setCursorPos(point[0], point[1]); context.waitTicks(1);
    }

    private void exerciseTooltip(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
        seed(context, server, connection, player -> { set(player, 0, Items.COBBLESTONE_STAIRS, 1); set(player, 9, Items.COBBLESTONE, 32); set(player, 10, Items.COBBLESTONE_SLAB, 1); });
        var lines = tooltip(context);
        if (!lines.contains("Available material: 33 1/2 blocks") || !lines.contains("Equivalent slabs: 67")) throw new AssertionError("Tooltip fractions incorrect: " + lines);
        configure(context, BuilderClient.settings().withFeature(Feature.TOOLTIPS, false));
        if (tooltip(context).stream().anyMatch(line -> line.startsWith("Available material:"))) throw new AssertionError("Tooltip setting was ignored");
        configure(context, BuilderClient.settings().withFeature(Feature.TOOLTIPS, true));
    }
    private List<String> tooltip(ClientGameTestContext context) {
        return context.computeOnClient(client -> new ItemStack(Items.COBBLESTONE_STAIRS)
                .getTooltipLines(Item.TooltipContext.of(client.level), client.player, TooltipFlag.NORMAL).stream().map(Component::getString).toList());
    }

    private void assertSynced(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
        connection.waitForServerboundPackets(); connection.waitForClientboundPackets();
        List<ItemStack> authoritative = server.computeOnServer(value -> {
            var result = new ArrayList<ItemStack>(); var player = connection.getServerPlayer();
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) result.add(player.getInventory().getItem(i).copy());
            result.add(player.containerMenu.getCarried().copy()); return result;
        });
        context.runOnClient(client -> {
            int size = client.player.getInventory().getContainerSize();
            for (int i = 0; i < size; i++) if (!ItemStack.matches(authoritative.get(i), client.player.getInventory().getItem(i))) {
                throw new AssertionError("Client/server inventory mismatch at slot " + i);
            }
            if (!ItemStack.matches(authoritative.get(size), client.player.containerMenu.getCarried())) throw new AssertionError("Client/server carried stack mismatch");
        });
    }

    private void enableTweakeroo(ClientGameTestContext context) {
        if (!FabricLoader.getInstance().isModLoaded("tweakeroo")) return;
        context.runOnClient(client -> {
            try {
                if (handToggle == null) {
                    handToggle = Class.forName("fi.dy.masa.tweakeroo.config.FeatureToggle").getField("TWEAK_HAND_RESTOCK").get(null);
                    var generic = Class.forName("fi.dy.masa.tweakeroo.config.Configs$Generic");
                    preToggle = generic.getField("HAND_RESTOCK_PRE").get(null); threshold = generic.getField("HAND_RESTOCK_PRE_THRESHOLD").get(null);
                    oldHand = (boolean) handToggle.getClass().getMethod("getBooleanValue").invoke(handToggle);
                    oldPre = (boolean) preToggle.getClass().getMethod("getBooleanValue").invoke(preToggle);
                    oldThreshold = (int) threshold.getClass().getMethod("getIntegerValue").invoke(threshold);
                }
                handToggle.getClass().getMethod("setBooleanValue", boolean.class).invoke(handToggle, true);
                preToggle.getClass().getMethod("setBooleanValue", boolean.class).invoke(preToggle, true);
                threshold.getClass().getMethod("setIntegerValue", int.class).invoke(threshold, 16);
            } catch (ReflectiveOperationException error) { throw new AssertionError("Actual Tweakeroo restock configuration was unavailable", error); }
        });
    }
    private void exerciseTweakeroo(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
        configure(context, BuilderSettings.defaults().withEnabled(true).withFeature(Feature.NORMALIZE, false));
        seed(context, server, connection, player -> { set(player, 0, Items.COBBLESTONE_STAIRS, 1); set(player, 9, Items.COBBLESTONE_STAIRS, 10); });
        invokeTweakeroo(context, true); invokeTweakeroo(context, false); context.waitTicks(3);
        if (!server.computeOnServer(value -> connection.getServerPlayer().getMainHandItem().getCount() == 1
                && connection.getServerPlayer().getInventory().getItem(9).getCount() == 10)) throw new AssertionError("Tweakeroo issued a competing Builder restock");
        assertSynced(context, server, connection);
        configure(context, BuilderClient.settings().withEnabled(false));
        invokeTweakeroo(context, true);
        context.waitFor(client -> client.player.getMainHandItem().getCount() == 11 && client.player.containerMenu.getCarried().isEmpty());
        assertSynced(context, server, connection);
    }
    private void invokeTweakeroo(ClientGameTestContext context, boolean pre) {
        context.runOnClient(client -> {
            try {
                if (!(boolean) handToggle.getClass().getMethod("getBooleanValue").invoke(handToggle)
                        || !(boolean) preToggle.getClass().getMethod("getBooleanValue").invoke(preToggle)
                        || (int) threshold.getClass().getMethod("getIntegerValue").invoke(threshold) != 16) {
                    throw new AssertionError("Tweakeroo hand restock was not enabled for compatibility testing");
                }
                var type = Class.forName("fi.dy.masa.tweakeroo.util.InventoryUtils");
                if (pre) type.getMethod("preRestockHand", net.minecraft.world.entity.player.Player.class, InteractionHand.class, boolean.class)
                        .invoke(null, client.player, InteractionHand.MAIN_HAND, true);
                else type.getMethod("restockNewStackToHand", net.minecraft.world.entity.player.Player.class, InteractionHand.class, ItemStack.class, boolean.class)
                        .invoke(null, client.player, InteractionHand.MAIN_HAND, new ItemStack(Items.COBBLESTONE_STAIRS), true);
            } catch (ReflectiveOperationException error) { throw new AssertionError("Actual Tweakeroo 0.29.5 restock entrypoint failed", error); }
        });
    }
    private void restoreTweakeroo(ClientGameTestContext context) {
        if (handToggle == null) return;
        context.runOnClient(client -> {
            try {
                handToggle.getClass().getMethod("setBooleanValue", boolean.class).invoke(handToggle, oldHand);
                preToggle.getClass().getMethod("setBooleanValue", boolean.class).invoke(preToggle, oldPre);
                threshold.getClass().getMethod("setIntegerValue", int.class).invoke(threshold, oldThreshold);
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        });
    }
}
