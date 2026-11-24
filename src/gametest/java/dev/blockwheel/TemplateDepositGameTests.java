package dev.blockwheel;

import dev.blockwheel.core.BuilderSettings;
import dev.blockwheel.core.BuilderSettings.Feature;
import dev.blockwheel.server.BuilderServerConfig;
import dev.blockwheel.server.TemplateDeposit;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/** Exact template filling, metadata, capacity and server authorization with real container components. */
public final class TemplateDepositGameTests {
    private ServerPlayer player(GameTestHelper test) {
        var player = test.makeMockServerPlayerInLevel(); player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent(); player.getEnderChestInventory().clearContent(); player.getInventory().setSelectedSlot(0);
        Blockwheel.BUILDERS.configure(player, BuilderSettings.defaults().withEnabled(true).withFeature(Feature.SHULKER_SUPPLY, true)
                .withFeature(Feature.PICKUP, false).withFeature(Feature.NORMALIZE, false)); return player;
    }
    private boolean fill(ServerPlayer player) { return Blockwheel.BUILDERS.fillTemplates(player, player.containerMenu.containerId, true); }

    @GameTest public void shortDepositDefaultsToMainInventoryAndLeavesEveryHotbarSlot(GameTestHelper test) {
        var p = player(test); p.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        p.getInventory().setItem(8, new ItemStack(Items.COBBLESTONE, 12)); p.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 20));
        p.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(Items.COBBLESTONE, 1)));
        test.assertTrue(Blockwheel.BUILDERS.fillTemplates(p, p.containerMenu.containerId)
                && p.getInventory().getItem(9).isEmpty() && p.getMainHandItem().getCount() == 10 && p.getInventory().getItem(8).getCount() == 12
                && ShulkerSupplyGameTests.contents(p.getEnderChestInventory().getItem(0)).get(0).getCount() == 21, "Default dump consumed hotbar items");
        test.assertTrue(!Blockwheel.BUILDERS.fillTemplates(p, p.containerMenu.containerId), "Default dump fell through to a hotbar-only source");
        test.succeed();
    }
    @GameTest public void shortBuilderDepositCannotUseHotbarFamilyVariants(GameTestHelper test) {
        var p = player(test); p.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE_STAIRS, 10));
        p.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE_SLAB, 20));
        p.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(Items.COBBLESTONE, 1)));
        test.assertTrue(Blockwheel.BUILDERS.fillTemplates(p, p.containerMenu.containerId)
                && p.getMainHandItem().getCount() == 10 && p.getInventory().getItem(9).isEmpty()
                && ShulkerSupplyGameTests.contents(p.getEnderChestInventory().getItem(0)).get(0).getCount() == 11, "Short family dump borrowed from hotbar");
        test.assertTrue(!Blockwheel.BUILDERS.fillTemplates(p, p.containerMenu.containerId), "Short coercion consumed hotbar-only material");
        test.assertTrue(fill(p) && p.getMainHandItem().isEmpty()
                && ShulkerSupplyGameTests.contents(p.getEnderChestInventory().getItem(0)).get(0).getCount() == 21, "Completed hold did not include hotbar coercion");
        test.succeed();
    }
    @GameTest public void shortExperimentalLogPackingUsesOnlyMainSources(GameTestHelper test) {
        var p = player(test); Blockwheel.BUILDERS.configure(p, Blockwheel.BUILDERS.settings(p).withFeature(Feature.EXPERIMENTAL_WOOD, true));
        p.getInventory().setItem(0, new ItemStack(Items.OAK_STAIRS, 24)); p.getInventory().setItem(9, new ItemStack(Items.OAK_STAIRS, 25));
        p.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(Items.OAK_LOG, 12)));
        test.assertTrue(Blockwheel.BUILDERS.fillTemplates(p, p.containerMenu.containerId)
                && p.getMainHandItem().getCount() == 24 && p.getInventory().getItem(9).getCount() == 1
                && ShulkerSupplyGameTests.contents(p.getEnderChestInventory().getItem(0)).get(0).getCount() == 18, "Short log packing crossed the hotbar boundary");
        test.assertTrue(fill(p) && p.getInventory().countItem(Items.OAK_STAIRS) == 1
                && ShulkerSupplyGameTests.contents(p.getEnderChestInventory().getItem(0)).get(0).getCount() == 24, "Held log packing lost its incomplete main remainder");
        test.succeed();
    }
    private void metadata(GameTestHelper test, ItemStack before, ItemStack after) {
        var first = before.copy(); var second = after.copy(); first.remove(DataComponents.CONTAINER); second.remove(DataComponents.CONTAINER);
        test.assertTrue(ItemStack.matches(first, second), "Deposit changed outer name/color/metadata");
    }

    @GameTest public void builderModeConvertsHotbarStairsIntoExistingFullBlockTemplates(GameTestHelper test) {
        var player = player(test); var original = ShulkerSupplyGameTests.box(true, new ItemStack(Items.COBBLESTONE, 1), ItemStack.EMPTY);
        player.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE_STAIRS, 64)); player.getEnderChestInventory().setItem(0, original.copy());
        test.assertTrue(fill(player) && player.getMainHandItem().is(Items.COBBLESTONE_STAIRS) && player.getMainHandItem().getCount() == 1
                && ShulkerSupplyGameTests.contents(player.getEnderChestInventory().getItem(0)).get(0).getCount() == 64
                && ShulkerSupplyGameTests.contents(player.getEnderChestInventory().getItem(0)).get(1).isEmpty(), "Builder fill ignored a hotbar family variant or its capacity");
        metadata(test, original, player.getEnderChestInventory().getItem(0)); test.succeed();
    }

    @GameTest public void builderOffKeepsFamilyVariantsUnchangedDuringTemplateFill(GameTestHelper test) {
        var player = player(test); Blockwheel.BUILDERS.configure(player, BuilderSettings.defaults());
        var original = ShulkerSupplyGameTests.box(true, new ItemStack(Items.COBBLESTONE, 1));
        player.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE_STAIRS, 32)); player.getEnderChestInventory().setItem(0, original.copy());
        test.assertTrue(!fill(player) && player.getMainHandItem().is(Items.COBBLESTONE_STAIRS) && player.getMainHandItem().getCount() == 32
                && ItemStack.matches(original, player.getEnderChestInventory().getItem(0)), "Exact-only mode converted a family variant"); test.succeed();
    }

    @GameTest public void builderFillCombinesOddSlabSourcesWithoutLosingPhysicalRemainders(GameTestHelper test) {
        var player = player(test); player.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE_SLAB, 31));
        player.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE_SLAB, 2));
        player.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(Items.COBBLESTONE, 1)));
        test.assertTrue(fill(player) && ShulkerSupplyGameTests.contents(player.getEnderChestInventory().getItem(0)).get(0).getCount() == 17
                && player.getInventory().countItem(Items.COBBLESTONE_SLAB) == 1, "Odd slab conversion did not conserve exact units"); test.succeed();
    }

    @GameTest public void builderFillUsesExactTemplateItemsBeforeConvertingForOtherSlots(GameTestHelper test) {
        var player = player(test); player.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE_STAIRS, 64));
        player.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(Items.COBBLESTONE, 1), new ItemStack(Items.COBBLESTONE_STAIRS, 1)));
        test.assertTrue(fill(player), "Mixed templates did not accept supply");
        var contents = ShulkerSupplyGameTests.contents(player.getEnderChestInventory().getItem(0));
        test.assertTrue(contents.get(0).getCount() == 2 && contents.get(1).getCount() == 64 && player.getMainHandItem().isEmpty(), "Coercion stole exact supply from a matching template"); test.succeed();
    }

    @GameTest public void builderFillDoesNotConvertCustomizedInputsOrPopulateUnnamedOrEmptySlots(GameTestHelper test) {
        var player = player(test); var custom = new ItemStack(Items.COBBLESTONE_STAIRS, 10); custom.set(DataComponents.CUSTOM_NAME, Component.literal("Keep"));
        var original = ShulkerSupplyGameTests.box(true, new ItemStack(Items.COBBLESTONE, 1), ItemStack.EMPTY);
        var unnamed = ShulkerSupplyGameTests.box(false, new ItemStack(Items.COBBLESTONE, 1));
        player.getInventory().setItem(0, custom.copy()); player.getEnderChestInventory().setItem(0, original.copy()); player.getEnderChestInventory().setItem(1, unnamed.copy());
        test.assertTrue(!fill(player) && ItemStack.matches(custom, player.getMainHandItem())
                && ItemStack.matches(original, player.getEnderChestInventory().getItem(0)) && ItemStack.matches(unnamed, player.getEnderChestInventory().getItem(1)),
                "Coercion stripped components or widened destination eligibility"); test.succeed();
    }

    @GameTest public void builderFillReturnsPhysicalModdedChangeAndRejectsUnstorableChange(GameTestHelper test) {
        var player = player(test); player.getInventory().setItem(0, new ItemStack(BuilderTestContent.FLOOR, 1));
        var original = ShulkerSupplyGameTests.box(true, new ItemStack(BuilderTestContent.BASE, 1)); player.getEnderChestInventory().setItem(0, original.copy());
        test.assertTrue(fill(player) && ShulkerSupplyGameTests.contents(player.getEnderChestInventory().getItem(0)).get(0).getCount() == 2
                && player.getInventory().countItem(BuilderTestContent.SLAB) == 1, "Three-unit form did not return exact physical change");
        player.getInventory().clearContent(); player.getInventory().setItem(0, new ItemStack(BuilderTestContent.FLOOR, 1));
        for (int slot = 9; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
        player.getEnderChestInventory().setItem(0, original.copy());
        test.assertTrue(!fill(player) && player.getMainHandItem().is(BuilderTestContent.FLOOR)
                && ItemStack.matches(original, player.getEnderChestInventory().getItem(0)), "Unstorable fractional change partially withdrew a source"); test.succeed();
    }

    @GameTest public void fillsOccupiedNamedSlotsInPlaceIncludingDuplicateItemTypes(GameTestHelper test) {
        var player = player(test);
        var original = ShulkerSupplyGameTests.box(true, new ItemStack(Items.COBBLESTONE, 60), ItemStack.EMPTY,
                new ItemStack(Items.COBBLESTONE, 1), new ItemStack(Items.DIAMOND, 3));
        player.getEnderChestInventory().setItem(0, original.copy()); player.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 20));
        test.assertTrue(fill(player), "Named templates did not accept matching material");
        var contents = ShulkerSupplyGameTests.contents(player.getEnderChestInventory().getItem(0));
        test.assertTrue(contents.get(0).getCount() == 64 && contents.get(1).isEmpty() && contents.get(2).getCount() == 17
                && contents.get(3).getCount() == 3 && player.getInventory().getItem(9).isEmpty(), "Named layout was changed or material was lost");
        metadata(test, original, player.getEnderChestInventory().getItem(0)); test.succeed();
    }

    @GameTest public void ignoresUnnamedBoxesEmptySlotsLooseEnderItemsAndOtherShapes(GameTestHelper test) {
        var player = player(test); Blockwheel.BUILDERS.configure(player, BuilderSettings.defaults()); var unnamed = ShulkerSupplyGameTests.box(false, new ItemStack(Items.COBBLESTONE, 1));
        var named = ShulkerSupplyGameTests.box(true, new ItemStack(Items.COBBLESTONE_STAIRS, 1));
        player.getEnderChestInventory().setItem(0, unnamed.copy()); player.getEnderChestInventory().setItem(1, named.copy());
        player.getEnderChestInventory().setItem(2, new ItemStack(Items.COBBLESTONE, 1));
        player.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 64));
        test.assertTrue(!fill(player) && player.getInventory().getItem(9).getCount() == 64
                && ItemStack.matches(unnamed, player.getEnderChestInventory().getItem(0))
                && ItemStack.matches(named, player.getEnderChestInventory().getItem(1))
                && player.getEnderChestInventory().getItem(2).getCount() == 1, "Dump filled empty/unnamed storage or converted shapes"); test.succeed();
    }

    @GameTest public void exactComponentsAndActualSmallStackLimitsPreserveLeftovers(GameTestHelper test) {
        var player = player(test); var renamed = new ItemStack(Items.COBBLESTONE, 1); renamed.set(DataComponents.CUSTOM_NAME, Component.literal("Template"));
        var wrong = renamed.copyWithCount(5); wrong.set(DataComponents.CUSTOM_NAME, Component.literal("Other"));
        player.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, renamed, new ItemStack(Items.SNOWBALL, 14)));
        player.getInventory().setItem(9, renamed.copyWithCount(5)); player.getInventory().setItem(10, wrong.copy());
        player.getInventory().setItem(11, new ItemStack(Items.SNOWBALL, 10));
        test.assertTrue(fill(player), "Exact component template was not fillable");
        var contents = ShulkerSupplyGameTests.contents(player.getEnderChestInventory().getItem(0));
        test.assertTrue(ItemStack.matches(contents.get(0), renamed.copyWithCount(6)) && contents.get(1).getCount() == 16
                && ItemStack.matches(wrong, player.getInventory().getItem(10)) && player.getInventory().getItem(11).getCount() == 8,
                "Components, smaller stack limit or unmatched leftovers were lost"); test.succeed();
    }

    @GameTest public void mainInventoryPrecedesHotbarAndEquipmentAndCursorAreExcluded(GameTestHelper test) {
        var player = player(test); player.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(Items.COBBLESTONE, 60)));
        player.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 10)); player.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 2));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COBBLESTONE, 20));
        test.assertTrue(fill(player) && player.getInventory().getItem(9).isEmpty() && player.getMainHandItem().getCount() == 8
                && player.getOffhandItem().getCount() == 20, "Dump source domains or main-first priority changed");
        player.inventoryMenu.setCarried(new ItemStack(Items.DIAMOND));
        test.assertTrue(!fill(player) && player.inventoryMenu.getCarried().is(Items.DIAMOND), "Dump mutated a cursor transaction"); test.succeed();
    }

    @GameTest public void fullTemplatesLeaveAllRemainingInventoryUntouched(GameTestHelper test) {
        var player = player(test); var original = ShulkerSupplyGameTests.box(true, new ItemStack(Items.COBBLESTONE, 64));
        player.getEnderChestInventory().setItem(0, original.copy()); player.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 64));
        test.assertTrue(!fill(player) && player.getInventory().getItem(9).getCount() == 64
                && ItemStack.matches(original, player.getEnderChestInventory().getItem(0)), "Full template consumed input"); test.succeed();
    }

    @GameTest public void staleSourceOrTargetRejectsTheWholeDeposit(GameTestHelper test) {
        var player = player(test); var original = ShulkerSupplyGameTests.box(true, new ItemStack(Items.COBBLESTONE, 1));
        player.getEnderChestInventory().setItem(0, original.copy()); player.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 10));
        var plan = TemplateDeposit.plan(player.getInventory(), player.getEnderChestInventory());
        player.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 9));
        test.assertTrue(!plan.transaction().commit() && player.getInventory().getItem(9).getCount() == 9
                && ItemStack.matches(original, player.getEnderChestInventory().getItem(0)), "Stale input partially deposited");
        plan = TemplateDeposit.plan(player.getInventory(), player.getEnderChestInventory());
        var replacement = ShulkerSupplyGameTests.box(false, new ItemStack(Items.COBBLESTONE, 1)); player.getEnderChestInventory().setItem(0, replacement.copy());
        test.assertTrue(!plan.transaction().commit() && player.getInventory().getItem(9).getCount() == 9
                && ItemStack.matches(replacement, player.getEnderChestInventory().getItem(0)), "Stale destination partially deposited"); test.succeed();
    }

    @GameTest public void remoteDepositRespectsPreferencesPolicyAndCurrentMenuId(GameTestHelper test) {
        var player = player(test); var original = ShulkerSupplyGameTests.box(true, new ItemStack(Items.COBBLESTONE, 1));
        player.getEnderChestInventory().setItem(0, original.copy()); player.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 5));
        test.assertTrue(!Blockwheel.BUILDERS.fillTemplates(player, player.containerMenu.containerId + 1), "Stale screen ID was accepted");
        Blockwheel.BUILDERS.configure(player, Blockwheel.BUILDERS.settings(player).withFeature(Feature.ENDER_ACCESS, false));
        test.assertTrue(!fill(player), "Disabled player remote access was bypassed");
        Blockwheel.BUILDERS.configure(player, Blockwheel.BUILDERS.settings(player).withFeature(Feature.ENDER_ACCESS, true));
        boolean allowed = Blockwheel.BUILDERS.enderAllowed();
        try {
            Blockwheel.BUILDERS.setPolicy(new BuilderServerConfig(false));
            test.assertTrue(!fill(player) && ItemStack.matches(original, player.getEnderChestInventory().getItem(0))
                    && player.getInventory().getItem(9).getCount() == 5, "Remote administrator policy was bypassed");
        } finally { Blockwheel.BUILDERS.setPolicy(new BuilderServerConfig(allowed)); }
        test.succeed();
    }

    @GameTest public void openPersonalEnderChestCanFillWithBuilderAndRemoteAccessOff(GameTestHelper test) {
        var player = player(test); Blockwheel.BUILDERS.configure(player, BuilderSettings.defaults());
        player.getInventory().setItem(9, new ItemStack(Items.NETHERRACK, 12));
        player.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(Items.NETHERRACK, 1)));
        player.containerMenu = ChestMenu.threeRows(7, player.getInventory(), player.getEnderChestInventory());
        boolean allowed = Blockwheel.BUILDERS.enderAllowed();
        try {
            Blockwheel.BUILDERS.setPolicy(new BuilderServerConfig(false));
            test.assertTrue(fill(player) && player.getInventory().getItem(9).isEmpty()
                    && ShulkerSupplyGameTests.contents(player.getEnderChestInventory().getItem(0)).get(0).getCount() == 13,
                    "An already open personal ender chest incorrectly required remote/Builder access");
        } finally { Blockwheel.BUILDERS.setPolicy(new BuilderServerConfig(allowed)); }
        test.succeed();
    }

    @GameTest public void explicitRemoteDepositDoesNotRequireAutomaticBuilderOrSupplyEnabled(GameTestHelper test) {
        var player = player(test); Blockwheel.BUILDERS.configure(player, BuilderSettings.defaults());
        player.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 3));
        player.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(Items.COBBLESTONE, 1)));
        test.assertTrue(fill(player) && player.getInventory().getItem(9).isEmpty()
                && ShulkerSupplyGameTests.contents(player.getEnderChestInventory().getItem(0)).get(0).getCount() == 4,
                "Explicit storage action incorrectly depended on automatic Builder/Supply mode"); test.succeed();
    }
}
