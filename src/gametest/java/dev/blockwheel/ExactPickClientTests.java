package dev.blockwheel;

import dev.blockwheel.client.BuilderClient;
import dev.blockwheel.core.BuilderSettings;
import dev.blockwheel.core.BuilderSettings.Feature;
import dev.blockwheel.server.BuilderController;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;

/** Focused native pick packets and inventory synchronization for non-family supplies. */
public final class ExactPickClientTests implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer(); var connection = world.getConnection();
            connection.waitForChunksRender();
            context.waitFor(client -> BuilderClient.material(new ItemStack(Items.COBBLESTONE_STAIRS)) != null);
            var settings = BuilderSettings.defaults().withEnabled(true).withFeature(Feature.SHULKER_SUPPLY, true)
                    .withFeature(Feature.PICKUP, false).withFeature(Feature.NORMALIZE, false);
            context.runOnClient(client -> BuilderClient.request(settings));
            context.waitFor(client -> BuilderClient.settings().equals(settings));
            var target = server.computeOnServer(value -> {
                var player = connection.getServerPlayer(); var position = player.blockPosition().offset(2, 0, 0);
                player.level().setBlockAndUpdate(position, Blocks.NETHERRACK.defaultBlockState()); return position;
            });
            context.waitFor(client -> client.level.getBlockState(target).is(Blocks.NETHERRACK));
            for (int source = 0; source < 5; source++) {
                int selected = source;
                server.runOnServer(value -> {
                    var player = connection.getServerPlayer(); player.setGameMode(GameType.SURVIVAL);
                    player.getInventory().clearContent(); player.getEnderChestInventory().clearContent();
                    player.getInventory().setSelectedSlot(0); player.inventoryMenu.setCarried(ItemStack.EMPTY);
                    player.getInventory().setItem(0, new ItemStack(Items.DIRT, 1));
                    var box = ShulkerSupplyGameTests.box(true, new ItemStack(Items.NETHERRACK, 64));
                    if (selected <= 1) {
                        player.getInventory().setItem(selected == 0 ? 5 : 10, new ItemStack(Items.NETHERRACK, 12));
                        player.getEnderChestInventory().setItem(0, box);
                    } else if (selected == 2) player.getInventory().setItem(9, box);
                    else player.getEnderChestInventory().setItem(0, selected == 3 ? new ItemStack(Items.NETHERRACK, 64) : box);
                    player.connection.send(new ClientboundSetHeldSlotPacket(0)); BuilderController.sync(player);
                });
                connection.waitForClientboundPackets(); context.waitTicks(2);
                context.runOnClient(client -> client.getConnection().send(new ServerboundPickItemFromBlockPacket(target, false)));
                int expected = source <= 1 ? 12 : source == 3 ? 64 : 63;
                context.waitFor(client -> client.player.getMainHandItem().is(Items.NETHERRACK)
                        && client.player.getMainHandItem().getCount() == expected);
                if (!server.computeOnServer(value -> {
                    var player = connection.getServerPlayer();
                    if (player.getInventory().countItem(Items.DIRT) != 1) return false;
                    if (selected <= 1) return ShulkerSupplyGameTests.contents(player.getEnderChestInventory().getItem(0)).get(0).getCount() == 64;
                    if (selected == 3) return player.getEnderChestInventory().getItem(0).isEmpty();
                    var outer = selected == 2 ? player.getInventory().getItem(9) : player.getEnderChestInventory().getItem(0);
                    return ShulkerSupplyGameTests.contents(outer).get(0).getCount() == 1;
                })) throw new AssertionError("Native exact pick lost items or ignored source/reserve rules: " + source);
                connection.waitForServerboundPackets(); connection.waitForClientboundPackets();
                var inventory = server.computeOnServer(value -> {
                    var player = connection.getServerPlayer(); var stacks = new ArrayList<ItemStack>();
                    for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) stacks.add(player.getInventory().getItem(slot).copy());
                    stacks.add(player.inventoryMenu.getCarried().copy()); return stacks;
                });
                context.runOnClient(client -> {
                    int size = client.player.getInventory().getContainerSize();
                    for (int slot = 0; slot < size; slot++) if (!ItemStack.matches(inventory.get(slot), client.player.getInventory().getItem(slot))) {
                        throw new AssertionError("Exact pick client/server mismatch in slot " + slot);
                    }
                    if (!ItemStack.matches(inventory.get(size), client.player.inventoryMenu.getCarried())) throw new AssertionError("Exact pick cursor mismatch");
                });
            }
        }
        Blockwheel.LOGGER.info("Exact pick client checks passed: native hotbar/main inventory, carried shulker, loose ender and protected ender shulker sources");
    }
}
