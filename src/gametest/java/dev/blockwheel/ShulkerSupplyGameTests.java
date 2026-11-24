package dev.blockwheel;

import dev.blockwheel.core.BuilderSettings;
import dev.blockwheel.core.BuilderSettings.Feature;
import dev.blockwheel.network.Payloads;
import dev.blockwheel.server.BuilderServerConfig;
import dev.blockwheel.server.InventoryTransaction;
import dev.blockwheel.server.MaterialSupply;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.LockCode;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;

/** Real item components, nested transaction conflicts, source tiers and explicit hotbar picking. */
public final class ShulkerSupplyGameTests {
    private ServerPlayer player(GameTestHelper test) {
        var player = test.makeMockServerPlayerInLevel(); player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent(); player.getEnderChestInventory().clearContent(); player.getInventory().setSelectedSlot(0);
        Blockwheel.BUILDERS.configure(player, settings()); return player;
    }
    private BuilderSettings settings() {
        return BuilderSettings.defaults().withEnabled(true).withFeature(Feature.SHULKER_SUPPLY, true)
                // These source-order/reserve cases request a complete refill; defaults are covered separately.
                .withRestock(dev.blockwheel.core.RestockSettings.DEFAULT.withAmount(99))
                .withFeature(Feature.NORMALIZE, false).withFeature(Feature.PICKUP, false);
    }
    private static ItemStack stack(Item item, int count) { return new ItemStack(item, count); }
    public static ItemStack box(boolean named, ItemStack... contents) {
        var items = NonNullList.withSize(27, ItemStack.EMPTY);
        for (int i = 0; i < contents.length; i++) items.set(i, contents[i].copy());
        var box = new ItemStack(Items.DYED_SHULKER_BOX.pick(net.minecraft.world.item.DyeColor.BLUE));
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        if (named) box.set(DataComponents.CUSTOM_NAME, Component.literal("Stone Materials"));
        var tag = new CompoundTag(); tag.putString("marker", "preserve"); box.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return box;
    }
    public static NonNullList<ItemStack> contents(ItemStack box) {
        var items = NonNullList.withSize(27, ItemStack.EMPTY);
        box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items); return items;
    }
    private long units(ServerPlayer player, Item member) {
        return MaterialSupply.totalUnits(new InventoryTransaction(player.getInventory(), player.getEnderChestInventory()),
                Blockwheel.BUILDERS.materials(), Blockwheel.BUILDERS.materials().resolve(member).family(), false);
    }
    private int refill(GameTestHelper test, ServerPlayer player, Item target) {
        var policy = Blockwheel.BUILDERS.supplyPolicy(player, false, false);
        var plan = Blockwheel.BUILDERS.planner().restock(player.getInventory(), policy.ender() ? player.getEnderChestInventory() : null, policy, 0, target);
        if (plan == null) return 0;
        long before = units(player, target);
        test.assertTrue(plan.transaction().commit(), "Supply plan failed to commit");
        test.assertTrue(units(player, target) == before, "Supply transaction lost physical material"); return plan.count();
    }
    private void metadata(GameTestHelper test, ItemStack before, ItemStack after) {
        var left = before.copy(); var right = after.copy(); left.remove(DataComponents.CONTAINER); right.remove(DataComponents.CONTAINER);
        test.assertTrue(ItemStack.matches(left, right), "Outer shulker name/color/metadata changed");
    }
    private void fillMain(ServerPlayer player) { for (int slot = 9; slot < 36; slot++) player.getInventory().setItem(slot, stack(Items.DIRT, 64)); }

    @GameTest public void exactPickWorksWithCarriedLooseEnderAndEnderShulkerSources(GameTestHelper test) {
        var player = player(test);
        test.assertTrue(Blockwheel.BUILDERS.materials().resolve(Items.NETHERRACK) == null, "Fixture unexpectedly has a conversion family");
        for (int source = 0; source < 3; source++) {
            player.getInventory().clearContent(); player.getEnderChestInventory().clearContent();
            player.getInventory().setItem(0, stack(Items.DIRT, 1));
            var original = box(true, stack(Items.NETHERRACK, 64), stack(Items.NETHER_BRICKS, 32));
            if (source == 0) player.getInventory().setItem(9, original.copy());
            else player.getEnderChestInventory().setItem(0, source == 1 ? stack(Items.NETHERRACK, 64) : original.copy());
            test.assertTrue(Blockwheel.BUILDERS.pick(player, stack(Items.NETHERRACK, 1)) && player.getMainHandItem().is(Items.NETHERRACK)
                    && player.getMainHandItem().getCount() == (source == 1 ? 64 : 63)
                    && player.getInventory().countItem(Items.DIRT) == 1, "Exact pick failed or overwrote the old hand: " + source);
            if (source == 1) test.assertTrue(player.getEnderChestInventory().getItem(0).isEmpty(), "Loose ender source was not withdrawn");
            else {
                var outer = source == 0 ? player.getInventory().getItem(9) : player.getEnderChestInventory().getItem(0);
                test.assertTrue(contents(outer).get(0).getCount() == 1 && contents(outer).get(1).getCount() == 32, "Exact pick changed reserves or unrelated contents");
                metadata(test, original, outer);
            }
        }
        test.succeed();
    }

    @GameTest public void exactPickCombinesCarriedLooseEnderAndNamedEnderSupplyInOrder(GameTestHelper test) {
        var player = player(test); player.getInventory().setItem(9, box(false, stack(Items.NETHERRACK, 20)));
        player.getEnderChestInventory().setItem(0, stack(Items.NETHERRACK, 30));
        player.getEnderChestInventory().setItem(1, box(true, stack(Items.NETHERRACK, 20)));
        test.assertTrue(Blockwheel.BUILDERS.pick(player, stack(Items.NETHERRACK, 1)) && player.getMainHandItem().getCount() == 64
                && contents(player.getInventory().getItem(9)).get(0).isEmpty() && player.getEnderChestInventory().getItem(0).isEmpty()
                && contents(player.getEnderChestInventory().getItem(1)).get(0).getCount() == 6, "Exact pick did not combine 20 + 30 + 14 in source order");
        test.succeed();
    }

    @GameTest public void exactPickLeavesExistingInventoryMatchesToVanilla(GameTestHelper test) {
        var player = player(test); var remote = box(false, stack(Items.NETHERRACK, 64)); player.getEnderChestInventory().setItem(0, remote.copy());
        for (int slot : new int[]{5, 10}) {
            player.getInventory().clearContent(); player.getInventory().setItem(slot, stack(Items.NETHERRACK, 12));
            test.assertTrue(!Blockwheel.BUILDERS.pick(player, stack(Items.NETHERRACK, 1))
                    && player.getInventory().getItem(slot).getCount() == 12 && ItemStack.matches(remote, player.getEnderChestInventory().getItem(0)),
                    "Exact inventory match was intercepted instead of preserving vanilla");
        }
        test.succeed();
    }

    @GameTest public void exactPickRespectsAllRemoteAndFeatureGates(GameTestHelper test) {
        var player = player(test); var original = box(true, stack(Items.NETHERRACK, 32));
        player.getEnderChestInventory().setItem(0, stack(Items.NETHERRACK, 32)); player.getEnderChestInventory().setItem(1, original.copy());
        for (var option : java.util.List.of(settings().withEnabled(false), settings().withFeature(Feature.PICK_BLOCK, false),
                settings().withFeature(Feature.SHULKER_SUPPLY, false), settings().withFeature(Feature.SHULKER_PICK_BLOCK, false),
                settings().withFeature(Feature.ENDER_ACCESS, false))) {
            Blockwheel.BUILDERS.configure(player, option);
            test.assertTrue(!Blockwheel.BUILDERS.pick(player, stack(Items.NETHERRACK, 1)) && player.getMainHandItem().isEmpty()
                    && player.getEnderChestInventory().getItem(0).getCount() == 32 && ItemStack.matches(original, player.getEnderChestInventory().getItem(1)),
                    "Exact pick bypassed a feature or remote preference");
        }
        boolean allowed = Blockwheel.BUILDERS.enderAllowed();
        try {
            Blockwheel.BUILDERS.configure(player, settings()); Blockwheel.BUILDERS.setPolicy(new BuilderServerConfig(false));
            test.assertTrue(!Blockwheel.BUILDERS.pick(player, stack(Items.NETHERRACK, 1)) && player.getEnderChestInventory().getItem(0).getCount() == 32
                    && ItemStack.matches(original, player.getEnderChestInventory().getItem(1)), "Loose or boxed ender pick bypassed administrator policy");
        } finally { Blockwheel.BUILDERS.setPolicy(new BuilderServerConfig(allowed)); }
        test.succeed();
    }

    @GameTest public void exactPickWithNoDisplacementSpacePreservesEverySource(GameTestHelper test) {
        var player = player(test); for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, stack(Items.DIRT, 64));
        var original = box(false, stack(Items.NETHERRACK, 32)); player.getInventory().setItem(9, original.copy());
        player.getEnderChestInventory().setItem(0, stack(Items.NETHERRACK, 32));
        test.assertTrue(!Blockwheel.BUILDERS.pick(player, stack(Items.NETHERRACK, 1)) && player.getMainHandItem().is(Items.DIRT)
                && player.getInventory().countItem(Items.DIRT) == 35 * 64 && ItemStack.matches(original, player.getInventory().getItem(9))
                && player.getEnderChestInventory().getItem(0).getCount() == 32, "Failed exact pick consumed supply or overwrote unrelated items"); test.succeed();
    }

    @GameTest public void staleLooseEnderPickRejectsTheEntireMixedTransaction(GameTestHelper test) {
        var player = player(test); var original = box(true, stack(Items.NETHERRACK, 32));
        player.getEnderChestInventory().setItem(0, stack(Items.NETHERRACK, 40)); player.getEnderChestInventory().setItem(1, original.copy());
        var policy = Blockwheel.BUILDERS.supplyPolicy(player, true, false);
        var plan = Blockwheel.BUILDERS.planner().pick(player.getInventory(), player.getEnderChestInventory(), policy, 0, Items.NETHERRACK);
        player.getEnderChestInventory().setItem(0, stack(Items.NETHERRACK, 39));
        test.assertTrue(plan != null && !plan.transaction().commit() && player.getMainHandItem().isEmpty()
                && player.getEnderChestInventory().getItem(0).getCount() == 39 && ItemStack.matches(original, player.getEnderChestInventory().getItem(1)),
                "Stale ender pick partially committed its other source"); test.succeed();
    }

    @GameTest public void looseEnderFamilyPickConvertsButAutomaticRestockStillExcludesLooseEnder(GameTestHelper test) {
        var player = player(test); player.getEnderChestInventory().setItem(0, stack(Items.COBBLESTONE_SLAB, 32));
        test.assertTrue(Blockwheel.BUILDERS.pick(player, stack(Items.COBBLESTONE_STAIRS, 1)) && player.getMainHandItem().is(Items.COBBLESTONE_STAIRS)
                && player.getMainHandItem().getCount() == 16 && player.getEnderChestInventory().getItem(0).isEmpty(), "Family pick ignored loose ender material or its ratio");
        player.getInventory().clearContent(); player.getInventory().setSelectedSlot(0); player.getEnderChestInventory().setItem(0, stack(Items.COBBLESTONE_SLAB, 32));
        Blockwheel.BUILDERS.afterUse(player, 0, stack(Items.COBBLESTONE_STAIRS, 1));
        test.assertTrue(player.getMainHandItem().isEmpty() && player.getEnderChestInventory().getItem(0).getCount() == 32,
                "Pick-only loose ender policy leaked into automatic restocking"); test.succeed();
    }

    @GameTest public void netherrackRestocksWithoutAConversionFamilyFromCombinedSources(GameTestHelper test) {
        var player = player(test);
        test.assertTrue(Blockwheel.BUILDERS.materials().resolve(Items.NETHERRACK) == null, "Fixture unexpectedly has a conversion family");
        player.getInventory().setItem(0, stack(Items.NETHERRACK, 1));
        player.getInventory().setItem(10, stack(Items.NETHERRACK, 20));
        var carried = box(false, stack(Items.NETHERRACK, 30), stack(Items.NETHER_BRICKS, 64));
        player.getInventory().setItem(9, carried.copy());
        player.getEnderChestInventory().setItem(0, box(true, stack(Items.NETHERRACK, 64)));
        Blockwheel.BUILDERS.afterUse(player, 0, stack(Items.NETHERRACK, 2));
        test.assertTrue(player.getMainHandItem().is(Items.NETHERRACK) && player.getMainHandItem().getCount() == 64
                && player.getInventory().getItem(10).isEmpty() && contents(player.getInventory().getItem(9)).get(0).isEmpty()
                && contents(player.getInventory().getItem(9)).get(1).getCount() == 64
                && contents(player.getEnderChestInventory().getItem(0)).get(0).getCount() == 51,
                "Exact restock ignored source order, changed another item or lost supply");
        metadata(test, carried, player.getInventory().getItem(9)); test.succeed();
    }

    @GameTest public void exactRestockKeepsDuplicateNamedSlotReservesAndLayout(GameTestHelper test) {
        var player = player(test); var original = box(true, stack(Items.NETHERRACK, 64), ItemStack.EMPTY, stack(Items.NETHERRACK, 64));
        player.getEnderChestInventory().setItem(0, original.copy());
        Blockwheel.BUILDERS.afterUse(player, 0, stack(Items.NETHERRACK, 1));
        var first = contents(player.getEnderChestInventory().getItem(0));
        test.assertTrue(player.getMainHandItem().getCount() == 64 && first.get(0).getCount() == 1
                && first.get(1).isEmpty() && first.get(2).getCount() == 63, "Exact reserve was per item instead of per slot");
        player.getInventory().setItem(0, ItemStack.EMPTY);
        Blockwheel.BUILDERS.afterUse(player, 0, stack(Items.NETHERRACK, 1));
        var last = contents(player.getEnderChestInventory().getItem(0));
        test.assertTrue(player.getMainHandItem().getCount() == 62 && last.get(0).getCount() == 1
                && last.get(1).isEmpty() && last.get(2).getCount() == 1, "Exact restock consumed a template or lost items");
        metadata(test, original, player.getEnderChestInventory().getItem(0)); test.succeed();
    }

    @GameTest public void nativeThrowableUseRestocksNonBlockItemsToTheirActualStackLimit(GameTestHelper test) {
        var player = player(test); player.getInventory().setItem(0, stack(Items.SNOWBALL, 2));
        player.getEnderChestInventory().setItem(0, box(true, stack(Items.SNOWBALL, 16), stack(Items.ENDER_PEARL, 16)));
        var result = player.gameMode.useItem(player, player.level(), player.getMainHandItem(), net.minecraft.world.InteractionHand.MAIN_HAND);
        var after = contents(player.getEnderChestInventory().getItem(0));
        test.assertTrue(result.consumesAction() && player.getMainHandItem().is(Items.SNOWBALL) && player.getMainHandItem().getCount() == 16
                && after.get(0).getCount() == 1 && after.get(1).getCount() == 16, "Non-block native use lost exact identity or stack limits"); test.succeed();
    }

    @GameTest public void exactRestockMatchesAllComponentsWithoutLaunderingCustomizedItems(GameTestHelper test) {
        var player = player(test); var target = stack(Items.NETHERRACK, 1); target.set(DataComponents.CUSTOM_NAME, Component.literal("Target"));
        var other = target.copyWithCount(32); other.set(DataComponents.CUSTOM_NAME, Component.literal("Other"));
        player.getInventory().setItem(10, stack(Items.NETHERRACK, 64));
        var original = box(true, target.copyWithCount(17), other); player.getInventory().setItem(9, original.copy());
        Blockwheel.BUILDERS.afterUse(player, 0, target);
        test.assertTrue(ItemStack.matches(player.getMainHandItem(), target.copyWithCount(16))
                && player.getInventory().getItem(10).getCount() == 64 && contents(player.getInventory().getItem(9)).get(0).getCount() == 1
                && ItemStack.matches(contents(player.getInventory().getItem(9)).get(1), other), "Exact matching erased or mixed item components");
        metadata(test, original, player.getInventory().getItem(9)); test.succeed();
    }

    @GameTest public void exactRestockCannotUseOtherLooseHotbarSlotsOffhandOrDisabledEnderSources(GameTestHelper test) {
        var player = player(test); player.getInventory().setItem(1, stack(Items.NETHERRACK, 64));
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, stack(Items.NETHERRACK, 64));
        var remote = box(false, stack(Items.NETHERRACK, 64)); player.getEnderChestInventory().setItem(0, remote.copy());
        Blockwheel.BUILDERS.configure(player, settings().withFeature(Feature.ENDER_ACCESS, false));
        Blockwheel.BUILDERS.afterUse(player, 0, stack(Items.NETHERRACK, 1));
        test.assertTrue(player.getMainHandItem().isEmpty() && player.getInventory().getItem(1).getCount() == 64
                && player.getOffhandItem().getCount() == 64 && ItemStack.matches(remote, player.getEnderChestInventory().getItem(0)),
                "Exact restock bypassed player/source restrictions");
        boolean allowed = Blockwheel.BUILDERS.enderAllowed();
        try {
            Blockwheel.BUILDERS.configure(player, settings()); Blockwheel.BUILDERS.setPolicy(new BuilderServerConfig(false));
            Blockwheel.BUILDERS.afterUse(player, 0, stack(Items.NETHERRACK, 1));
            test.assertTrue(player.getMainHandItem().isEmpty() && ItemStack.matches(remote, player.getEnderChestInventory().getItem(0)), "Exact restock bypassed server policy");
        } finally { Blockwheel.BUILDERS.setPolicy(new BuilderServerConfig(allowed)); }
        test.succeed();
    }

    @GameTest public void exactRestockStopsWithSupplyBuilderOrRestockDisabled(GameTestHelper test) {
        var player = player(test); var original = box(false, stack(Items.NETHERRACK, 64)); player.getInventory().setItem(9, original.copy());
        player.getInventory().setItem(10, stack(Items.NETHERRACK, 64));
        for (var option : java.util.List.of(settings().withEnabled(false), settings().withFeature(Feature.RESTOCK, false),
                settings().withFeature(Feature.SHULKER_SUPPLY, false))) {
            Blockwheel.BUILDERS.configure(player, option); Blockwheel.BUILDERS.afterUse(player, 0, stack(Items.NETHERRACK, 1));
            test.assertTrue(player.getMainHandItem().isEmpty() && ItemStack.matches(original, player.getInventory().getItem(9))
                    && player.getInventory().getItem(10).getCount() == 64, "Disabled exact restock spent supply");
        }
        test.succeed();
    }

    @GameTest public void staleExactWithdrawalDoesNotPartiallyConsumeLooseOrBoxSources(GameTestHelper test) {
        var player = player(test); player.getInventory().setItem(10, stack(Items.NETHERRACK, 20));
        player.getEnderChestInventory().setItem(0, box(false, stack(Items.NETHERRACK, 64)));
        var policy = Blockwheel.BUILDERS.supplyPolicy(player, false, false);
        var plan = Blockwheel.BUILDERS.planner().restock(player.getInventory(), player.getEnderChestInventory(), policy, 0, Items.NETHERRACK);
        var changed = box(true, stack(Items.NETHERRACK, 64)); player.getEnderChestInventory().setItem(0, changed.copy());
        test.assertTrue(plan != null && !plan.transaction().commit() && player.getMainHandItem().isEmpty()
                && player.getInventory().getItem(10).getCount() == 20 && ItemStack.matches(changed, player.getEnderChestInventory().getItem(0)),
                "Stale exact transaction partially applied"); test.succeed();
    }

    @GameTest public void exactRestockWorksWithFullMainInventoryAndLeavesUnrelatedStacksIntact(GameTestHelper test) {
        var player = player(test); fillMain(player);
        for (int slot = 1; slot < 9; slot++) player.getInventory().setItem(slot, stack(Items.DIRT, 64));
        var original = box(false, stack(Items.BREAD, 40)); player.getInventory().setItem(9, original.copy());
        Blockwheel.BUILDERS.afterUse(player, 0, stack(Items.BREAD, 1));
        test.assertTrue(player.getMainHandItem().is(Items.BREAD) && player.getMainHandItem().getCount() == 40
                && contents(player.getInventory().getItem(9)).get(0).isEmpty() && player.getInventory().countItem(Items.DIRT) == 34 * 64,
                "Full-inventory exact refill overwrote items or skipped partial supply"); test.succeed();
    }

    @GameTest public void unnamedShulkersCanSupplyTheirLastItemsAndBecomeEmpty(GameTestHelper test) {
        var player = player(test); var box = box(false, stack(Items.COBBLESTONE_WALL, 64));
        player.getInventory().setItem(9, box.copy());
        test.assertTrue(refill(test, player, Items.COBBLESTONE_STAIRS) == 64, "Unnamed carried supply missing");
        var after = player.getInventory().getItem(9);
        test.assertTrue(contents(after).stream().allMatch(ItemStack::isEmpty), "Unnamed final items were protected");
        metadata(test, box, after); test.succeed();
    }

    @GameTest public void namedSlotsPreserveOneItemEachIncludingDuplicateTypes(GameTestHelper test) {
        var player = player(test); var box = box(true, stack(Items.COBBLESTONE, 64), ItemStack.EMPTY, ItemStack.EMPTY,
                ItemStack.EMPTY, ItemStack.EMPTY, stack(Items.COBBLESTONE, 64), stack(Items.DIAMOND, 3));
        player.getInventory().setItem(9, box.copy());
        test.assertTrue(refill(test, player, Items.COBBLESTONE_STAIRS) == 64, "First named withdrawal failed");
        var first = contents(player.getInventory().getItem(9));
        test.assertTrue(first.get(0).getCount() == 1 && first.get(5).getCount() == 63, "Reserve was not per source slot");
        player.getInventory().setItem(0, ItemStack.EMPTY);
        test.assertTrue(refill(test, player, Items.COBBLESTONE_STAIRS) == 62, "Remaining duplicate supply missing");
        var after = contents(player.getInventory().getItem(9));
        test.assertTrue(after.get(0).getCount() == 1 && after.get(5).getCount() == 1 && after.get(6).is(Items.DIAMOND)
                && after.get(6).getCount() == 3 && after.get(1).isEmpty(), "Named layout or unrelated contents changed");
        metadata(test, box, player.getInventory().getItem(9)); test.succeed();
    }

    @GameTest public void namedSingleItemSlotsAreNeverWithdrawn(GameTestHelper test) {
        var player = player(test); var box = box(true, stack(Items.COBBLESTONE, 1)); player.getInventory().setItem(9, box.copy());
        test.assertTrue(refill(test, player, Items.COBBLESTONE_STAIRS) == 0, "Protected final item was spent");
        test.assertTrue(ItemStack.matches(box, player.getInventory().getItem(9)) && player.getMainHandItem().isEmpty(), "Failed supply changed items"); test.succeed();
    }

    @GameTest public void mixedNamedTemplateCanDrainOnlyToItsOriginalPerSlotReserves(GameTestHelper test) {
        var player = player(test); var box = box(true, stack(Items.COBBLESTONE, 64), stack(Items.COBBLESTONE_STAIRS, 32),
                stack(Items.COBBLESTONE_WALL, 16), stack(Items.COBBLESTONE_SLAB, 48), stack(Items.COBBLESTONE, 64));
        player.getInventory().setItem(9, box.copy()); player.getInventory().setItem(10, stack(Items.COBBLESTONE_SLAB, 1));
        int supplied = 0;
        for (int i = 0; i < 4; i++) {
            player.getInventory().setItem(0, ItemStack.EMPTY); supplied += refill(test, player, Items.COBBLESTONE_STAIRS);
        }
        var after = contents(player.getInventory().getItem(9)); var original = contents(box);
        for (int slot = 0; slot < 5; slot++) test.assertTrue(after.get(slot).is(original.get(slot).getItem()) && after.get(slot).getCount() == 1, "Mixed template slot moved or drained: " + slot);
        test.assertTrue(supplied == 196, "Mixed source accounting was not exact: " + supplied);
        metadata(test, box, player.getInventory().getItem(9)); test.succeed();
    }

    @GameTest public void looseAndMultipleBoxesCombineWithoutCrossingNamedReserves(GameTestHelper test) {
        var player = player(test); player.getInventory().setItem(0, stack(Items.COBBLESTONE_STAIRS, 1));
        player.getInventory().setItem(9, stack(Items.COBBLESTONE_WALL, 20));
        player.getInventory().setItem(10, box(false, stack(Items.COBBLESTONE, 30)));
        player.getInventory().setItem(11, box(true, stack(Items.COBBLESTONE, 41)));
        test.assertTrue(refill(test, player, Items.COBBLESTONE_STAIRS) == 63 && player.getMainHandItem().getCount() == 64, "Sources did not combine 20 + 30 + 13");
        test.assertTrue(player.getInventory().getItem(9).isEmpty() && contents(player.getInventory().getItem(10)).get(0).isEmpty()
                && contents(player.getInventory().getItem(11)).get(0).getCount() == 28, "Source tiers or withdrawal amounts changed"); test.succeed();
    }

    @GameTest public void fiveSourceTiersKeepNamedEnderSupplyUntilOtherTiersAreUsed(GameTestHelper test) {
        var player = player(test); player.getInventory().setItem(0, stack(Items.COBBLESTONE_STAIRS, 60));
        player.getInventory().setItem(9, stack(Items.COBBLESTONE_WALL, 1));
        player.getInventory().setItem(10, box(false, stack(Items.COBBLESTONE_STAIRS, 1)));
        player.getInventory().setItem(11, box(true, stack(Items.COBBLESTONE_STAIRS, 2)));
        player.getEnderChestInventory().setItem(0, box(false, stack(Items.COBBLESTONE_STAIRS, 1)));
        player.getEnderChestInventory().setItem(1, box(true, stack(Items.COBBLESTONE_STAIRS, 20)));
        test.assertTrue(refill(test, player, Items.COBBLESTONE_STAIRS) == 4, "Four preferred tiers were not used");
        test.assertTrue(contents(player.getInventory().getItem(11)).get(0).getCount() == 1
                && contents(player.getEnderChestInventory().getItem(1)).get(0).getCount() == 20, "Named ender reserve was used too early");
        player.getInventory().setItem(0, stack(Items.COBBLESTONE_STAIRS, 63));
        test.assertTrue(refill(test, player, Items.COBBLESTONE_STAIRS) == 1
                && contents(player.getEnderChestInventory().getItem(1)).get(0).getCount() == 19, "Final source tier was inaccessible"); test.succeed();
    }

    @GameTest public void exactThenBaseThenOtherShapesArePreferredWithinEachTier(GameTestHelper test) {
        var player = player(test); player.getInventory().setItem(0, stack(Items.COBBLESTONE_STAIRS, 62));
        player.getInventory().setItem(9, box(false, stack(Items.COBBLESTONE_WALL, 64), stack(Items.COBBLESTONE, 64), stack(Items.COBBLESTONE_STAIRS, 1)));
        test.assertTrue(refill(test, player, Items.COBBLESTONE_STAIRS) == 2, "Refill failed"); var slots = contents(player.getInventory().getItem(9));
        test.assertTrue(slots.get(2).isEmpty() && slots.get(1).getCount() == 63 && slots.get(0).getCount() == 64, "Shape priority was not exact/base/other"); test.succeed();
    }

    @GameTest public void namedThreeSlabsSupplyOneStairAndKeepOneSlab(GameTestHelper test) {
        var player = player(test); player.getInventory().setItem(9, box(true, stack(Items.COBBLESTONE_SLAB, 3)));
        test.assertTrue(refill(test, player, Items.COBBLESTONE_STAIRS) == 1 && contents(player.getInventory().getItem(9)).get(0).getCount() == 1, "Named slab accounting lost the reserve"); test.succeed();
    }

    @GameTest public void twoAvailableFractionsCombineAcrossProtectedShulkerSlots(GameTestHelper test) {
        var player = player(test); var first = box(true, stack(Items.COBBLESTONE_SLAB, 2));
        player.getInventory().setItem(9, first.copy());
        test.assertTrue(refill(test, player, Items.COBBLESTONE_STAIRS) == 0 && ItemStack.matches(first, player.getInventory().getItem(9)), "One available slab was rounded up");
        player.getEnderChestInventory().setItem(0, box(true, stack(Items.COBBLESTONE_SLAB, 2)));
        test.assertTrue(refill(test, player, Items.COBBLESTONE_STAIRS) == 1 && contents(player.getInventory().getItem(9)).get(0).getCount() == 1
                && contents(player.getEnderChestInventory().getItem(0)).get(0).getCount() == 1, "Cross-source fractions consumed a protected slab"); test.succeed();
    }

    @GameTest public void enderToggleAndServerPolicyIndependentlyDenyRemoteSupply(GameTestHelper test) {
        var player = player(test); var original = box(false, stack(Items.COBBLESTONE, 64)); player.getEnderChestInventory().setItem(0, original.copy());
        Blockwheel.BUILDERS.configure(player, settings().withFeature(Feature.ENDER_ACCESS, false));
        test.assertTrue(refill(test, player, Items.COBBLESTONE_STAIRS) == 0, "Player ender toggle ignored");
        Blockwheel.BUILDERS.configure(player, settings()); var previous = Blockwheel.BUILDERS.enderAllowed();
        try {
            Blockwheel.BUILDERS.setPolicy(new BuilderServerConfig(false));
            test.assertTrue(refill(test, player, Items.COBBLESTONE_STAIRS) == 0 && ItemStack.matches(original, player.getEnderChestInventory().getItem(0)), "Server remote denial ignored");
        } finally { Blockwheel.BUILDERS.setPolicy(new BuilderServerConfig(previous)); }
        test.assertTrue(refill(test, player, Items.COBBLESTONE_STAIRS) == 64, "Permitted ender supply failed"); test.succeed();
    }

    @GameTest public void disabledSupplyAndDisabledBuilderModeLeaveAllBoxesUnchanged(GameTestHelper test) {
        var player = player(test); var original = box(false, stack(Items.COBBLESTONE, 64)); player.getInventory().setItem(9, original.copy());
        Blockwheel.BUILDERS.configure(player, settings().withFeature(Feature.SHULKER_SUPPLY, false));
        Blockwheel.BUILDERS.afterPlacement(player, 0, stack(Items.COBBLESTONE_STAIRS, 1));
        test.assertTrue(player.getMainHandItem().isEmpty() && ItemStack.matches(original, player.getInventory().getItem(9)), "Disabled supply withdrew material");
        Blockwheel.BUILDERS.configure(player, settings().withEnabled(false));
        Blockwheel.BUILDERS.afterPlacement(player, 0, stack(Items.COBBLESTONE_STAIRS, 1));
        test.assertTrue(player.getMainHandItem().isEmpty() && ItemStack.matches(original, player.getInventory().getItem(9)), "Disabled Builder mode withdrew material"); test.succeed();
    }

    @GameTest public void automaticRestockCannotConsumeLooseHotbarOrOffhandItems(GameTestHelper test) {
        var player = player(test); player.getInventory().setItem(1, stack(Items.COBBLESTONE, 64));
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, stack(Items.COBBLESTONE, 64));
        Blockwheel.BUILDERS.afterPlacement(player, 0, stack(Items.COBBLESTONE_STAIRS, 1));
        test.assertTrue(player.getMainHandItem().isEmpty() && player.getInventory().getItem(1).getCount() == 64
                && player.getOffhandItem().getCount() == 64, "Automatic supply consumed a protected inventory domain"); test.succeed();
    }

    @GameTest public void serverPlacementRestockerUsesShulkersOnTheLastItem(GameTestHelper test) {
        var player = player(test); player.getEnderChestInventory().setItem(0, box(true, stack(Items.COBBLESTONE, 64)));
        long before = units(player, Items.COBBLESTONE); Blockwheel.BUILDERS.afterPlacement(player, 0, stack(Items.COBBLESTONE_STAIRS, 1));
        test.assertTrue(player.getMainHandItem().is(Items.COBBLESTONE_STAIRS) && player.getMainHandItem().getCount() == 63
                && contents(player.getEnderChestInventory().getItem(0)).get(0).getCount() == 1 && units(player, Items.COBBLESTONE) == before, "Last-item controller failed to use remote supply"); test.succeed();
    }

    @GameTest public void hotbarOnlyPickCanTransformTheSelectedStackWithNoFreeInventorySpace(GameTestHelper test) {
        var player = player(test); fillMain(player);
        for (int slot = 0; slot < 9; slot++) player.getInventory().setItem(slot, stack(Items.DIRT, 64));
        player.getInventory().setItem(0, stack(Items.COBBLESTONE_STAIRS, 64)); long before = units(player, Items.COBBLESTONE);
        Blockwheel.BUILDERS.configure(player, settings().withFeature(Feature.SHULKER_SUPPLY, false));
        test.assertTrue(Blockwheel.BUILDERS.pick(player, stack(Items.COBBLESTONE_WALL, 1)) && player.getMainHandItem().is(Items.COBBLESTONE_WALL)
                && player.getMainHandItem().getCount() == 64 && units(player, Items.COBBLESTONE) == before, "Selected hotbar-only source was ignored"); test.succeed();
    }

    @GameTest public void hotbarOnlyPickFallsBackToItsCompatibleSourceSlotWithoutOverwritingDirt(GameTestHelper test) {
        var player = player(test); fillMain(player);
        for (int slot = 0; slot < 9; slot++) player.getInventory().setItem(slot, stack(Items.DIRT, 64));
        player.getInventory().setItem(5, stack(Items.COBBLESTONE_STAIRS, 64));
        test.assertTrue(Blockwheel.BUILDERS.pick(player, stack(Items.COBBLESTONE_WALL, 1)) && player.getInventory().getSelectedSlot() == 5
                && player.getMainHandItem().is(Items.COBBLESTONE_WALL), "Other hotbar source could not be selected/transformed");
        test.assertTrue(player.getInventory().countItem(Items.DIRT) == 35 * 64, "Pick destroyed or unnecessarily moved unrelated items"); test.succeed();
    }

    @GameTest public void pickCombinesMainAndHotbarAndKeepsOddSlabRemainders(GameTestHelper test) {
        var player = player(test); player.getInventory().setItem(0, stack(Items.COBBLESTONE_SLAB, 31));
        test.assertTrue(Blockwheel.BUILDERS.pick(player, stack(Items.COBBLESTONE_WALL, 1)) && player.getMainHandItem().is(Items.COBBLESTONE_WALL)
                && player.getMainHandItem().getCount() == 15 && player.getInventory().countItem(Items.COBBLESTONE_SLAB) == 1, "Odd hotbar slab pick lost its remainder");
        player.getInventory().clearContent(); player.getInventory().setSelectedSlot(0);
        player.getInventory().setItem(0, stack(Items.COBBLESTONE_STAIRS, 30)); player.getInventory().setItem(9, stack(Items.COBBLESTONE, 20));
        test.assertTrue(Blockwheel.BUILDERS.pick(player, stack(Items.COBBLESTONE_WALL, 1)) && player.getMainHandItem().getCount() == 50
                && player.getInventory().countItem(Items.COBBLESTONE_STAIRS) == 0 && player.getInventory().countItem(Items.COBBLESTONE) == 0, "Main and hotbar supplies were not combined"); test.succeed();
    }

    @GameTest public void insufficientHotbarSlabPickFailsWithoutMutation(GameTestHelper test) {
        var player = player(test); player.getInventory().setItem(0, stack(Items.COBBLESTONE_SLAB, 1));
        test.assertTrue(!Blockwheel.BUILDERS.pick(player, stack(Items.COBBLESTONE_WALL, 1)) && player.getMainHandItem().is(Items.COBBLESTONE_SLAB)
                && player.getMainHandItem().getCount() == 1, "One slab became a wall"); test.succeed();
    }

    @GameTest public void exactPickAndMainInventoryPriorityRemainIntact(GameTestHelper test) {
        var player = player(test); player.getInventory().setItem(3, stack(Items.COBBLESTONE_WALL, 7)); player.getInventory().setItem(9, stack(Items.COBBLESTONE, 64));
        test.assertTrue(!Blockwheel.BUILDERS.pick(player, stack(Items.COBBLESTONE_WALL, 1)) && player.getInventory().getItem(9).getCount() == 64, "Exact vanilla pick was intercepted");
        player.getInventory().setItem(3, stack(Items.COBBLESTONE_STAIRS, 64));
        test.assertTrue(Blockwheel.BUILDERS.pick(player, stack(Items.COBBLESTONE_WALL, 1)) && player.getInventory().getItem(3).getCount() == 64
                && player.getInventory().getItem(9).isEmpty(), "Main material did not precede loose hotbar donors"); test.succeed();
    }

    @GameTest public void shulkerPickSubtoggleAndRemoteNamedReservesAreEnforced(GameTestHelper test) {
        var player = player(test); var original = box(true, stack(Items.COBBLESTONE, 64)); player.getEnderChestInventory().setItem(0, original.copy());
        Blockwheel.BUILDERS.configure(player, settings().withFeature(Feature.SHULKER_PICK_BLOCK, false));
        test.assertTrue(!Blockwheel.BUILDERS.pick(player, stack(Items.COBBLESTONE_WALL, 1)) && ItemStack.matches(original, player.getEnderChestInventory().getItem(0)), "Disabled shulker pick accessed storage");
        Blockwheel.BUILDERS.configure(player, settings());
        test.assertTrue(Blockwheel.BUILDERS.pick(player, stack(Items.COBBLESTONE_WALL, 1)) && player.getMainHandItem().getCount() == 63
                && contents(player.getEnderChestInventory().getItem(0)).get(0).getCount() == 1, "Remote named pick failed");
        metadata(test, original, player.getEnderChestInventory().getItem(0)); test.succeed();
    }

    @GameTest public void modifiedOuterShulkersRejectStaleMultiSourcePlansAtomically(GameTestHelper test) {
        var player = player(test); player.getInventory().setItem(9, box(false, stack(Items.COBBLESTONE, 20)));
        player.getEnderChestInventory().setItem(0, box(false, stack(Items.COBBLESTONE, 44)));
        var policy = Blockwheel.BUILDERS.supplyPolicy(player, false, false);
        var plan = Blockwheel.BUILDERS.planner().restock(player.getInventory(), player.getEnderChestInventory(), policy, 0, Items.COBBLESTONE_STAIRS);
        var mainBefore = player.getInventory().getItem(9).copy(); var replacement = box(true, stack(Items.COBBLESTONE, 44));
        player.getEnderChestInventory().setItem(0, replacement.copy());
        test.assertTrue(plan != null && !plan.transaction().commit() && player.getMainHandItem().isEmpty()
                && ItemStack.matches(mainBefore, player.getInventory().getItem(9)) && ItemStack.matches(replacement, player.getEnderChestInventory().getItem(0)), "Stale nested source partially committed"); test.succeed();
    }

    @GameTest public void shulkerTooltipCountsExcludeReservesAndHonorRemotePermissions(GameTestHelper test) {
        var player = player(test); player.getInventory().setItem(0, stack(Items.COBBLESTONE_STAIRS, 1)); player.getInventory().setItem(10, stack(Items.COBBLESTONE, 10));
        player.getInventory().setItem(9, box(false, stack(Items.COBBLESTONE_WALL, 20)));
        player.getEnderChestInventory().setItem(0, box(true, stack(Items.COBBLESTONE, 64)));
        var amounts = Blockwheel.BUILDERS.supplyAmounts(player, Items.COBBLESTONE_STAIRS);
        test.assertTrue(amounts.loose() == 22 && amounts.carried() == 40 && amounts.ender() == 126 && amounts.protectedUnits() == 2
                && amounts.total() == 188, "Tooltip counted protected or unavailable material");
        Blockwheel.BUILDERS.configure(player, settings().withFeature(Feature.ENDER_ACCESS, false));
        test.assertTrue(Blockwheel.BUILDERS.supplyAmounts(player, Items.COBBLESTONE_STAIRS).ender() == 0, "Tooltip ignored ender toggle");
        Blockwheel.BUILDERS.configure(player, settings().withFeature(Feature.SHULKER_TOOLTIPS, false));
        amounts = Blockwheel.BUILDERS.supplyAmounts(player, Items.COBBLESTONE_STAIRS);
        test.assertTrue(amounts.carried() == 0 && amounts.ender() == 0 && amounts.loose() == 22, "Disabled shulker tooltip still included boxes"); test.succeed();
    }

    @GameTest public void moddedValuesAndSmallerStackLimitsApplyToShulkerSupply(GameTestHelper test) {
        var player = player(test); player.getInventory().setItem(9, box(true, stack(BuilderTestContent.BASE, 32)));
        test.assertTrue(refill(test, player, BuilderTestContent.STAIRS) == 16 && player.getMainHandItem().getCount() == 16, "Modded item limit ignored");
        player.getInventory().setItem(0, ItemStack.EMPTY);
        test.assertTrue(refill(test, player, BuilderTestContent.STAIRS) == 15 && contents(player.getInventory().getItem(9)).get(0).getCount() == 1, "Modded reserve or units ignored"); test.succeed();
    }

    @GameTest public void exactFallbackCanBypassUnstorableChangeWithoutSpendingTheWrongBox(GameTestHelper test) {
        var player = player(test); fillMain(player); player.getInventory().setItem(0, stack(BuilderTestContent.STAIRS, 15));
        var original = box(false, stack(BuilderTestContent.FLOOR, 1)); player.getInventory().setItem(9, original.copy());
        player.getEnderChestInventory().setItem(0, box(false, stack(BuilderTestContent.BASE, 1)));
        test.assertTrue(refill(test, player, BuilderTestContent.STAIRS) == 1 && ItemStack.matches(original, player.getInventory().getItem(9))
                && contents(player.getEnderChestInventory().getItem(0)).get(0).isEmpty(), "Safe exact source was blocked by higher-tier fractional change"); test.succeed();
    }

    @GameTest public void manualRadialCannotSpendShulkerContents(GameTestHelper test) {
        var player = player(test); var original = box(false, stack(Items.COBBLESTONE_SLAB, 1));
        player.getInventory().setItem(0, stack(Items.COBBLESTONE_SLAB, 1)); player.getInventory().setItem(9, original.copy());
        var wheel = Blockwheel.CONVERSIONS.prepare(player, 0); Blockwheel.CONVERSIONS.convert(player, new Payloads.Convert(wheel.token(), "blockwheel:block"));
        test.assertTrue(player.getMainHandItem().is(Items.COBBLESTONE_SLAB) && player.getMainHandItem().getCount() == 1
                && ItemStack.matches(original, player.getInventory().getItem(9)), "Manual radial silently used shulker supply"); test.succeed();
    }

    @GameTest public void pickCanMoveItsUpdatedSourceShulkerWithoutDuplicatingTheOriginalContents(GameTestHelper test) {
        var player = player(test);
        for (int slot = 1; slot < 9; slot++) player.getInventory().setItem(slot, stack(Items.DIRT, 64));
        var original = box(true, stack(Items.COBBLESTONE, 64)); player.getInventory().setItem(0, original.copy());
        long before = units(player, Items.COBBLESTONE);
        test.assertTrue(Blockwheel.BUILDERS.pick(player, stack(Items.COBBLESTONE_WALL, 1))
                && player.getMainHandItem().is(Items.COBBLESTONE_WALL) && player.getMainHandItem().getCount() == 63, "Carried hotbar box was not available to pick");
        var moved = player.getInventory().getItem(9);
        test.assertTrue(moved.is(original.getItem()) && contents(moved).get(0).getCount() == 1
                && units(player, Items.COBBLESTONE) == before, "Moving the outer source restored or lost its withdrawn material");
        metadata(test, original, moved); test.succeed();
    }

    @GameTest public void carriedHotbarBoxesSupplyContentsWithoutConsumingTheOuterBox(GameTestHelper test) {
        var player = player(test); var original = box(true, stack(Items.COBBLESTONE, 64)); player.getInventory().setItem(4, original.copy());
        Blockwheel.BUILDERS.afterPlacement(player, 0, stack(Items.COBBLESTONE_STAIRS, 1));
        test.assertTrue(player.getMainHandItem().getCount() == 63 && player.getInventory().getItem(4).getCount() == 1
                && contents(player.getInventory().getItem(4)).get(0).getCount() == 1, "Carried box sourcing consumed its hotbar slot");
        metadata(test, original, player.getInventory().getItem(4)); test.succeed();
    }

    @GameTest public void unstorableFractionalChangeLeavesTheOnlySourceUntouched(GameTestHelper test) {
        var player = player(test); fillMain(player); player.getInventory().setItem(0, stack(BuilderTestContent.STAIRS, 15));
        var original = box(false, stack(BuilderTestContent.FLOOR, 1)); player.getInventory().setItem(9, original.copy());
        test.assertTrue(refill(test, player, BuilderTestContent.STAIRS) == 0 && player.getMainHandItem().getCount() == 15
                && ItemStack.matches(original, player.getInventory().getItem(9)), "Failed fractional conversion partially withdrew from a box"); test.succeed();
    }

    @GameTest public void normalizationAndPickupNeverDepositIntoOrNormalizeBoxes(GameTestHelper test) {
        var player = player(test); var named = box(true, stack(Items.COBBLESTONE_STAIRS, 32)); var unnamed = box(false, stack(Items.COBBLESTONE_SLAB, 33));
        player.getInventory().setItem(9, named.copy()); player.getInventory().setItem(10, unnamed.copy());
        player.getInventory().setItem(11, stack(Items.COBBLESTONE_STAIRS, 5));
        Blockwheel.BUILDERS.configure(player, settings().withFeature(Feature.NORMALIZE, true).withFeature(Feature.PICKUP, true));
        test.assertTrue(ItemStack.matches(named, player.getInventory().getItem(9)) && ItemStack.matches(unnamed, player.getInventory().getItem(10))
                && player.getInventory().countItem(Items.COBBLESTONE) == 5, "Normalization altered nested contents");
        fillMain(player); player.getInventory().setItem(9, named.copy());
        var incoming = stack(Items.COBBLESTONE_WALL, 4);
        test.assertTrue(Boolean.FALSE.equals(Blockwheel.BUILDERS.pickup(player, incoming)) && incoming.getCount() == 4
                && ItemStack.matches(named, player.getInventory().getItem(9)), "Pickup deposited into a shulker"); test.succeed();
    }

    @GameTest public void unknownContainersNestedBoxesAndCustomizedContentsAreIgnored(GameTestHelper test) {
        var player = player(test); var custom = stack(Items.COBBLESTONE, 64); custom.set(DataComponents.CUSTOM_NAME, Component.literal("Keep"));
        var nested = box(false, stack(Items.COBBLESTONE, 64));
        var original = box(false, custom, nested, stack(Items.STONE, 64)); player.getInventory().setItem(9, original.copy());
        var locked = box(false, stack(Items.COBBLESTONE, 64)); locked.set(DataComponents.LOCK, LockCode.NO_LOCK); player.getInventory().setItem(10, locked.copy());
        var fake = new ItemStack(Items.CHEST); fake.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(java.util.List.of(stack(Items.COBBLESTONE, 64)))); player.getInventory().setItem(11, fake.copy());
        player.getEnderChestInventory().setItem(0, stack(Items.COBBLESTONE, 64));
        var expanded = box(false); var oversizedContents = NonNullList.withSize(28, ItemStack.EMPTY);
        oversizedContents.set(27, stack(Items.COBBLESTONE, 64)); expanded.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(oversizedContents));
        player.getInventory().setItem(12, expanded.copy());
        test.assertTrue(refill(test, player, Items.COBBLESTONE_STAIRS) == 0 && ItemStack.matches(original, player.getInventory().getItem(9))
                && ItemStack.matches(locked, player.getInventory().getItem(10)) && ItemStack.matches(fake, player.getInventory().getItem(11))
                && ItemStack.matches(expanded, player.getInventory().getItem(12)) && player.getEnderChestInventory().getItem(0).getCount() == 64, "Unsupported or customized storage was consumed"); test.succeed();
    }
}
