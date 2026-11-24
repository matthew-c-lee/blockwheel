package dev.blockwheel;

import dev.blockwheel.client.*;
import dev.blockwheel.core.BuilderSettings.Feature;
import dev.blockwheel.core.RadialLayout;
import dev.blockwheel.server.BuilderController;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.util.function.Consumer;

/** Actual native hold-R/release input for default and expanded sign families. */
public final class SignClientTests implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        int scale = context.computeOnClient(client -> client.options.guiScale().get());
        try {
            context.runOnClient(client -> { client.options.guiScale().set(3); client.resizeGui(); });
            try (var world = context.worldBuilder().create()) {
                var server = world.getServer(); var connection = world.getConnection(); connection.waitForChunksRender();
                context.waitFor(client -> BuilderClient.available());
                seed(context, server, connection, p -> set(p, 0, Items.OAK_PLANKS, 6));
                open(context); point(context, 5); selected(context, "Sign. 6 -> 3"); capture(context, "blockwheel-sign-wheel");
                release(context); held(context, Items.OAK_SIGN, 3);
                open(context); point(context, 0); release(context); held(context, Items.OAK_PLANKS, 6);

                seed(context, server, connection, p -> set(p, 0, Items.OAK_PLANKS, 64));
                open(context); point(context, 5); release(context); held(context, Items.OAK_SIGN, 16);
                if (!server.computeOnServer(value -> connection.getServerPlayer().getInventory().countItem(Items.OAK_PLANKS) == 32
                        && connection.getServerPlayer().getInventory().countItem(Items.OAK_SIGN) == 16)) throw new AssertionError("Native sign cap lost raw material");
                capture(context, "blockwheel-sign-stack-limit");
                open(context); point(context, 0); release(context); held(context, Items.OAK_PLANKS, 32);
                if (!server.computeOnServer(value -> connection.getServerPlayer().getInventory().getItem(9).getCount() == 32))
                    throw new AssertionError("Builder-off sign conversion merged the stored planks");

                var expanded = BuilderClient.settings().withFeature(Feature.EXPERIMENTAL_WOOD, true).withFeature(Feature.WOOD_TRAPDOORS, true);
                context.runOnClient(client -> BuilderClient.request(expanded)); context.waitFor(client -> BuilderClient.settings().equals(expanded));
                seed(context, server, connection, p -> set(p, 0, Items.OAK_LOG, 2)); open(context);
                var labels = new String[]{"Full Block", "Wood", "Stairs", "Trapdoor", "Fence", "Sign", "Slab", "Log"};
                for (int slot = 0; slot < labels.length; slot++) {
                    point(context, slot); String name = labels[slot];
                    if (!context.computeOnClient(client -> client.gui.screen().getNarrationMessage().getString().startsWith(name + ".")))
                        throw new AssertionError("Sign displaced the fixed wood shape at " + slot);
                }
                point(context, 5); selected(context, "Sign. 2 -> 4"); capture(context, "blockwheel-sign-full-wood-wheel");
                release(context); held(context, Items.OAK_SIGN, 4);

                var builder = BuilderClient.settings().withEnabled(true).withFeature(Feature.NORMALIZE, false).withFeature(Feature.PICKUP, false);
                context.runOnClient(client -> BuilderClient.request(builder)); context.waitFor(client -> BuilderClient.settings().equals(builder));
                seed(context, server, connection, p -> { set(p, 0, Items.OAK_LOG, 1); set(p, 9, Items.OAK_LOG, 3); });
                open(context); point(context, 5); selected(context, "Sign. 4 -> 8"); release(context); held(context, Items.OAK_SIGN, 8);
                if (!server.computeOnServer(value -> connection.getServerPlayer().getInventory().getItem(9).isEmpty()))
                    throw new AssertionError("Builder-on sign conversion ignored compatible storage");
            }
            Blockwheel.LOGGER.info("Sign client tests passed: native R release, bottom-left sign, all eight fixed wood options, two-plank ratio, 16-sign cap, physical leftovers and Builder off/on sources");
        } finally {
            context.getInput().releaseKey(BlockwheelClient.OPEN);
            context.runOnClient(client -> {
                if (client.gui.screen() instanceof RadialScreen radial) radial.onClose();
                client.options.guiScale().set(scale); client.resizeGui();
            });
        }
    }
    private static void set(ServerPlayer p, int slot, Item item, int count) { p.getInventory().setItem(slot, new ItemStack(item, count)); }
    private void seed(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, Consumer<ServerPlayer> action) {
        server.runOnServer(value -> {
            var p = connection.getServerPlayer(); p.setGameMode(GameType.SURVIVAL); p.getInventory().clearContent(); p.getEnderChestInventory().clearContent();
            p.getInventory().setSelectedSlot(0); action.accept(p); p.connection.send(new ClientboundSetHeldSlotPacket(0)); BuilderController.sync(p);
        }); connection.waitForClientboundPackets(); context.waitTicks(2);
    }
    private void open(ClientGameTestContext context) { context.getInput().holdKey(BlockwheelClient.OPEN); context.waitForScreen(RadialScreen.class); context.waitTicks(2); }
    private void release(ClientGameTestContext context) { context.getInput().releaseKey(BlockwheelClient.OPEN); context.waitFor(client -> client.gui.screen() == null); context.waitTicks(2); }
    private void held(ClientGameTestContext context, Item item, int count) { context.waitFor(client -> client.player.getMainHandItem().is(item) && client.player.getMainHandItem().getCount() == count); }
    private void selected(ClientGameTestContext context, String narration) {
        if (!context.computeOnClient(client -> client.gui.screen().getNarrationMessage().getString().equals(narration))) throw new AssertionError("Wrong sign preview: " + narration);
    }
    private void point(ClientGameTestContext context, int slot) {
        var position = context.computeOnClient(client -> {
            var window = client.getWindow(); var layout = RadialLayout.fit(window.getGuiScaledWidth(), window.getGuiScaledHeight(), 8); var point = layout.node(slot);
            return new double[]{point.x() * window.getScreenWidth() / window.getGuiScaledWidth(), point.y() * window.getScreenHeight() / window.getGuiScaledHeight()};
        }); context.getInput().setCursorPos(position[0], position[1]); context.waitTicks(1);
    }
    private void capture(ClientGameTestContext context, String name) { context.runOnClient(client -> { client.gui.toastManager().clear(); client.gui.hud.getChat().clearMessages(false); }); context.waitTicks(1); context.takeScreenshot(name); }
}
