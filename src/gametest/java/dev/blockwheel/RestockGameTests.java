package dev.blockwheel;

import dev.blockwheel.core.BuilderSettings;
import dev.blockwheel.core.BuilderSettings.Feature;
import dev.blockwheel.core.RestockSettings;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/** Event-triggered refill boundaries, additional-quantity limits and block/non-block supply policy. */
public final class RestockGameTests {
    private ServerPlayer player(GameTestHelper test, RestockSettings restock) {
        var player = test.makeMockServerPlayerInLevel(); player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent(); player.getInventory().setSelectedSlot(0);
        Blockwheel.BUILDERS.configure(player, BuilderSettings.defaults().withEnabled(true)
                .withFeature(Feature.SHULKER_SUPPLY, true).withRestock(restock));
        return player;
    }
    private void set(ServerPlayer player, int slot, Item item, int count) { player.getInventory().setItem(slot, new ItemStack(item, count)); }
    private void used(ServerPlayer player, Item item, int before) { Blockwheel.BUILDERS.afterUse(player, 0, new ItemStack(item, before)); }
    private int inside(ItemStack box) { return ShulkerSupplyGameTests.contents(box).get(0).getCount(); }

    @GameTest public void defaultRefillStartsBelowSixAndAddsThirtyTwoInsteadOfToppingUp(GameTestHelper test) {
        var player = player(test, RestockSettings.DEFAULT); set(player, 9, Items.COBBLESTONE, 64);
        set(player, 0, Items.COBBLESTONE_STAIRS, 6); used(player, Items.COBBLESTONE_STAIRS, 7);
        test.assertTrue(player.getMainHandItem().getCount() == 6 && player.getInventory().getItem(9).getCount() == 64, "Refilled at the exclusive threshold");
        set(player, 0, Items.COBBLESTONE_STAIRS, 5); used(player, Items.COBBLESTONE_STAIRS, 6);
        test.assertTrue(player.getMainHandItem().getCount() == 37 && player.getInventory().getItem(9).getCount() == 32,
                "Default refill did not add exactly 32 to the five held items"); test.succeed();
    }
    @GameTest public void emptyOnlyThresholdAndConfiguredAdditionalAmountAreHonored(GameTestHelper test) {
        var player = player(test, new RestockSettings(1, 7, true)); set(player, 9, Items.COBBLESTONE, 20);
        set(player, 0, Items.COBBLESTONE_WALL, 1); used(player, Items.COBBLESTONE_WALL, 2);
        test.assertTrue(player.getMainHandItem().getCount() == 1, "Empty-only configuration refilled early");
        player.getInventory().setItem(0, ItemStack.EMPTY); used(player, Items.COBBLESTONE_WALL, 1);
        test.assertTrue(player.getMainHandItem().is(Items.COBBLESTONE_WALL) && player.getMainHandItem().getCount() == 7
                && player.getInventory().getItem(9).getCount() == 13, "Configured quantity was treated as a full-stack refill"); test.succeed();
    }
    @GameTest public void configuredQuantityStillRespectsTheActualItemStackLimit(GameTestHelper test) {
        var player = player(test, new RestockSettings(16, 32, true)); set(player, 0, Items.ENDER_PEARL, 15);
        set(player, 9, Items.ENDER_PEARL, 16); used(player, Items.ENDER_PEARL, 16);
        test.assertTrue(player.getMainHandItem().getCount() == 16 && player.getInventory().getItem(9).getCount() == 15,
                "Quantity setting overfilled a sixteen-item stack"); test.succeed();
    }
    @GameTest public void partialSuppliesAddOnlyAvailableMaterial(GameTestHelper test) {
        var player = player(test, RestockSettings.DEFAULT); set(player, 0, Items.COBBLESTONE_STAIRS, 5); set(player, 9, Items.COBBLESTONE, 9);
        used(player, Items.COBBLESTONE_STAIRS, 6);
        test.assertTrue(player.getMainHandItem().getCount() == 14 && player.getInventory().getItem(9).isEmpty(),
                "Partial material was rejected or extra material was invented"); test.succeed();
    }
    @GameTest public void nonBlockSwitchRejectsCarriedAndRemoteShulkersWithoutChangingTheirContents(GameTestHelper test) {
        var player = player(test, RestockSettings.DEFAULT.withNonBlockShulkers(false)); set(player, 0, Items.SNOWBALL, 1);
        var box = ShulkerSupplyGameTests.box(true, new ItemStack(Items.SNOWBALL, 16));
        player.getInventory().setItem(9, box.copy()); player.getEnderChestInventory().setItem(0, box.copy()); used(player, Items.SNOWBALL, 2);
        test.assertTrue(player.getMainHandItem().getCount() == 1 && ItemStack.matches(box, player.getInventory().getItem(9))
                && ItemStack.matches(box, player.getEnderChestInventory().getItem(0)), "Disabled non-block shulker refill withdrew items"); test.succeed();
    }
    @GameTest public void disablingNonBlockShulkersKeepsOrdinaryInventoryRefills(GameTestHelper test) {
        var player = player(test, RestockSettings.DEFAULT.withNonBlockShulkers(false)); set(player, 0, Items.BREAD, 1); set(player, 10, Items.BREAD, 10);
        var box = ShulkerSupplyGameTests.box(false, new ItemStack(Items.BREAD, 64)); player.getInventory().setItem(9, box.copy());
        used(player, Items.BREAD, 2);
        test.assertTrue(player.getMainHandItem().getCount() == 11 && player.getInventory().getItem(10).isEmpty()
                && ItemStack.matches(box, player.getInventory().getItem(9)), "Non-block switch disabled loose refills or leaked into shulkers"); test.succeed();
    }
    @GameTest public void blockItemsStillUseShulkersEvenWithoutAConversionFamily(GameTestHelper test) {
        var player = player(test, RestockSettings.DEFAULT.withNonBlockShulkers(false));
        player.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(Items.NETHERRACK, 64)));
        used(player, Items.NETHERRACK, 1);
        test.assertTrue(player.getMainHandItem().is(Items.NETHERRACK) && player.getMainHandItem().getCount() == 32
                && inside(player.getEnderChestInventory().getItem(0)) == 32, "Non-block switch incorrectly blocked netherrack supplies"); test.succeed();
    }
    @GameTest public void nonBlockSwitchDoesNotRestrictExplicitPickBlock(GameTestHelper test) {
        var player = player(test, RestockSettings.DEFAULT.withNonBlockShulkers(false)); set(player, 0, Items.DIRT, 1);
        player.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(Items.ENDER_PEARL, 16)));
        test.assertTrue(Blockwheel.BUILDERS.pick(player, new ItemStack(Items.ENDER_PEARL)) && player.getMainHandItem().is(Items.ENDER_PEARL)
                && player.getMainHandItem().getCount() == 15 && inside(player.getEnderChestInventory().getItem(0)) == 1,
                "A hand-restock preference leaked into pick block"); test.succeed();
    }
}
