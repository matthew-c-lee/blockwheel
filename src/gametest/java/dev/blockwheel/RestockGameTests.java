package dev.blockwheel;

import dev.blockwheel.core.BuilderSettings;
import dev.blockwheel.core.BuilderSettings.Feature;
import dev.blockwheel.core.RestockSettings;
import dev.blockwheel.server.InventoryTransaction;
import dev.blockwheel.server.MaterialSupply;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
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

    @GameTest public void offhandFamilyRefillHonorsThresholdQuantityAndLeavesTheMainHandAlone(GameTestHelper test) {
        var player = player(test, RestockSettings.DEFAULT); player.getInventory().setSelectedSlot(4);
        set(player, 4, Items.DIAMOND_PICKAXE, 1); set(player, 9, Items.COBBLESTONE, 64);
        set(player, Inventory.SLOT_OFFHAND, Items.COBBLESTONE_STAIRS, 6);
        Blockwheel.BUILDERS.afterUse(player, Inventory.SLOT_OFFHAND, new ItemStack(Items.COBBLESTONE_STAIRS, 7));
        test.assertTrue(player.getOffhandItem().getCount() == 6, "Offhand refilled at the exclusive threshold");
        set(player, Inventory.SLOT_OFFHAND, Items.COBBLESTONE_STAIRS, 5);
        var materials = Blockwheel.BUILDERS.materials(player); var family = materials.resolve(Items.COBBLESTONE).family();
        long before = MaterialSupply.totalUnits(new InventoryTransaction(player.getInventory()), materials, family, false);
        Blockwheel.BUILDERS.afterUse(player, Inventory.SLOT_OFFHAND, new ItemStack(Items.COBBLESTONE_STAIRS, 6));
        test.assertTrue(player.getOffhandItem().is(Items.COBBLESTONE_STAIRS) && player.getOffhandItem().getCount() == 37
                && player.getInventory().getItem(9).getCount() == 32 && player.getMainHandItem().is(Items.DIAMOND_PICKAXE)
                && player.getInventory().getSelectedSlot() == 4
                && MaterialSupply.totalUnits(new InventoryTransaction(player.getInventory()), materials, family, false) == before,
                "Offhand family refill lost material, ignored the configured amount or changed the main hand"); test.succeed();
    }
    @GameTest public void exhaustedOffhandRefillsFromNamedEnderSupplyWithoutSpendingItsReserve(GameTestHelper test) {
        var player = player(test, new RestockSettings(1, 32, true));
        var original = ShulkerSupplyGameTests.box(true, new ItemStack(Items.COBBLESTONE, 33));
        player.getEnderChestInventory().setItem(0, original.copy());
        Blockwheel.BUILDERS.afterUse(player, Inventory.SLOT_OFFHAND, new ItemStack(Items.COBBLESTONE_WALL, 1));
        var updated = player.getEnderChestInventory().getItem(0);
        test.assertTrue(player.getOffhandItem().is(Items.COBBLESTONE_WALL) && player.getOffhandItem().getCount() == 32
                && inside(updated) == 1 && player.getMainHandItem().isEmpty(), "Last offhand item did not refill or spent a named reserve");
        var metadata = updated.copy(); metadata.set(net.minecraft.core.component.DataComponents.CONTAINER,
                original.get(net.minecraft.core.component.DataComponents.CONTAINER));
        test.assertTrue(ItemStack.matches(original, metadata), "Offhand refill changed outer shulker metadata"); test.succeed();
    }
    @GameTest public void offhandExactRefillRespectsSmallStackLimitsAndNonBlockSourcePolicy(GameTestHelper test) {
        var player = player(test, new RestockSettings(16, 32, false));
        set(player, Inventory.SLOT_OFFHAND, Items.SNOWBALL, 15);
        var box = ShulkerSupplyGameTests.box(false, new ItemStack(Items.SNOWBALL, 16));
        player.getInventory().setItem(9, box.copy()); player.getEnderChestInventory().setItem(0, box.copy());
        Blockwheel.BUILDERS.afterUse(player, Inventory.SLOT_OFFHAND, new ItemStack(Items.SNOWBALL, 16));
        test.assertTrue(player.getOffhandItem().getCount() == 15 && ItemStack.matches(box, player.getInventory().getItem(9))
                && ItemStack.matches(box, player.getEnderChestInventory().getItem(0)), "Offhand bypassed the disabled non-block shulker option");
        set(player, 10, Items.SNOWBALL, 16);
        Blockwheel.BUILDERS.afterUse(player, Inventory.SLOT_OFFHAND, new ItemStack(Items.SNOWBALL, 16));
        test.assertTrue(player.getOffhandItem().getCount() == 16 && player.getInventory().getItem(10).getCount() == 15,
                "Exact offhand refill overfilled the stack or ignored loose inventory"); test.succeed();
    }
    @GameTest public void offhandRestockPreservesSourceBoundariesAndRejectsInactiveDestinations(GameTestHelper test) {
        var player = player(test, RestockSettings.DEFAULT); set(player, 0, Items.SNOWBALL, 16); set(player, 5, Items.SNOWBALL, 16);
        Blockwheel.BUILDERS.afterUse(player, Inventory.SLOT_OFFHAND, new ItemStack(Items.SNOWBALL, 1));
        test.assertTrue(player.getOffhandItem().isEmpty() && player.getMainHandItem().getCount() == 16
                && player.getInventory().getItem(5).getCount() == 16, "Offhand restock spent another loose hotbar stack");
        set(player, 0, Items.SNOWBALL, 0); set(player, 5, Items.SNOWBALL, 0); set(player, Inventory.SLOT_OFFHAND, Items.SNOWBALL, 16);
        used(player, Items.SNOWBALL, 1);
        test.assertTrue(player.getMainHandItem().isEmpty() && player.getOffhandItem().getCount() == 16, "Main-hand restock spent offhand supplies");
        set(player, 9, Items.SNOWBALL, 16);
        for (int slot : new int[]{-1, 5, 9, 39, 41}) {
            Blockwheel.BUILDERS.afterUse(player, slot, new ItemStack(Items.SNOWBALL, 1));
            test.assertTrue(player.getInventory().getItem(9).getCount() == 16, "Restock accepted a non-hand destination");
        }
        test.succeed();
    }
    @GameTest public void offhandRestockRespectsFeatureGatesAndReturnedReplacementItems(GameTestHelper test) {
        var player = player(test, RestockSettings.DEFAULT);
        var enabled = Blockwheel.BUILDERS.settings(player);
        var original = ShulkerSupplyGameTests.box(false, new ItemStack(Items.BREAD, 20));
        player.getEnderChestInventory().setItem(0, original.copy()); set(player, Inventory.SLOT_OFFHAND, Items.BREAD, 1);
        for (var settings : java.util.List.of(enabled.withEnabled(false), enabled.withFeature(Feature.RESTOCK, false),
                enabled.withFeature(Feature.SHULKER_SUPPLY, false), enabled.withFeature(Feature.ENDER_ACCESS, false))) {
            Blockwheel.BUILDERS.configure(player, settings);
            Blockwheel.BUILDERS.afterUse(player, Inventory.SLOT_OFFHAND, new ItemStack(Items.BREAD, 2));
            test.assertTrue(player.getOffhandItem().getCount() == 1 && ItemStack.matches(original, player.getEnderChestInventory().getItem(0)),
                    "Offhand restock bypassed a feature or remote gate");
        }
        Blockwheel.BUILDERS.configure(player, enabled); set(player, Inventory.SLOT_OFFHAND, Items.GLASS_BOTTLE, 1);
        Blockwheel.BUILDERS.afterUse(player, Inventory.SLOT_OFFHAND, new ItemStack(Items.BREAD, 2));
        test.assertTrue(player.getOffhandItem().is(Items.GLASS_BOTTLE) && ItemStack.matches(original, player.getEnderChestInventory().getItem(0)),
                "Offhand restock overwrote a returned replacement item"); test.succeed();
    }
    @GameTest public void offhandFamilyRefillKeepsUnstorablePhysicalChangeAndStalePlansAtomic(GameTestHelper test) {
        var player = player(test, new RestockSettings(6, 1, true));
        Blockwheel.BUILDERS.configure(player, Blockwheel.BUILDERS.settings(player).withFeature(Feature.EXPERIMENTAL_WOOD, true));
        for (int slot = 9; slot < 36; slot++) set(player, slot, Items.DIRT, 64);
        set(player, Inventory.SLOT_OFFHAND, Items.OAK_STAIRS, 1);
        var original = ShulkerSupplyGameTests.box(true, new ItemStack(Items.OAK_LOG, 2));
        player.getEnderChestInventory().setItem(0, original.copy());
        Blockwheel.BUILDERS.afterUse(player, Inventory.SLOT_OFFHAND, new ItemStack(Items.OAK_STAIRS, 2));
        test.assertTrue(player.getOffhandItem().getCount() == 1 && ItemStack.matches(original, player.getEnderChestInventory().getItem(0)),
                "Offhand refill lost unstorable physical change");
        set(player, 9, Items.DIRT, 0);
        var sources = Blockwheel.BUILDERS.supplyPolicy(player, false, false);
        var plan = Blockwheel.BUILDERS.planner(player).restock(player.getInventory(), player.getEnderChestInventory(), sources,
                Inventory.SLOT_OFFHAND, new ItemStack(Items.OAK_STAIRS), 1);
        test.assertTrue(plan != null, "Offhand plan rejected available space for physical change");
        set(player, Inventory.SLOT_OFFHAND, Items.OAK_PLANKS, 1);
        test.assertTrue(!plan.transaction().commit() && player.getOffhandItem().is(Items.OAK_PLANKS)
                && player.getInventory().getItem(9).isEmpty() && ItemStack.matches(original, player.getEnderChestInventory().getItem(0)),
                "Stale offhand plan partially committed"); test.succeed();
    }

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
