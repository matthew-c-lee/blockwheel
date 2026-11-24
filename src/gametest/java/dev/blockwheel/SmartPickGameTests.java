package dev.blockwheel;

import dev.blockwheel.core.BuilderSettings;
import dev.blockwheel.core.BuilderSettings.Feature;
import dev.blockwheel.core.PickRules;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import java.util.List;

/** Real inventory picks: exact targets first, ordered alternatives, access gates and conservation. */
public final class SmartPickGameTests {
    private ServerPlayer player(GameTestHelper test, boolean supply) {
        var player = test.makeMockServerPlayerInLevel(); player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent(); player.getEnderChestInventory().clearContent(); player.getInventory().setSelectedSlot(0);
        Blockwheel.BUILDERS.configure(player, BuilderSettings.defaults().withEnabled(true).withFeature(Feature.SHULKER_SUPPLY, supply)
                .withFeature(Feature.NORMALIZE, false).withFeature(Feature.PICKUP, false));
        Blockwheel.BUILDERS.pickRules(player, PickRules.DEFAULT); set(player, 0, Items.DIRT, 1); return player;
    }
    private void set(ServerPlayer player, int slot, Item item, int count) { player.getInventory().setItem(slot, new ItemStack(item, count)); }
    private boolean pick(ServerPlayer player, Item item) { return Blockwheel.BUILDERS.pick(player, new ItemStack(item)); }
    private int inside(ItemStack box) { return ShulkerSupplyGameTests.contents(box).get(0).getCount(); }

    @GameTest public void exactLooseTargetRemainsVanillaAndBeatsAnAvailableAlternative(GameTestHelper test) {
        var player = player(test, false); set(player, 10, Items.STRIPPED_OAK_LOG, 5); set(player, 5, Items.OAK_LOG, 20);
        test.assertTrue(!pick(player, Items.STRIPPED_OAK_LOG) && player.getMainHandItem().is(Items.DIRT)
                && player.getInventory().getItem(10).getCount() == 5 && player.getInventory().getItem(5).getCount() == 20,
                "Fallback bypassed an exact loose vanilla pick"); test.succeed();
    }
    @GameTest public void exactRemoteTargetBeatsAHotbarAlternative(GameTestHelper test) {
        var player = player(test, true); set(player, 5, Items.OAK_LOG, 20);
        player.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(Items.STRIPPED_OAK_WOOD, 64)));
        test.assertTrue(pick(player, Items.STRIPPED_OAK_WOOD) && player.getMainHandItem().is(Items.STRIPPED_OAK_WOOD)
                && player.getMainHandItem().getCount() == 63 && player.getInventory().getItem(5).getCount() == 20
                && inside(player.getEnderChestInventory().getItem(0)) == 1, "Exact target did not precede fallbacks or lost its reserve"); test.succeed();
    }
    @GameTest public void strippedLogUsesAnActualSameSpeciesLogWithoutSupplyEnabled(GameTestHelper test) {
        var player = player(test, false); set(player, 10, Items.OAK_LOG, 7);
        test.assertTrue(pick(player, Items.STRIPPED_OAK_LOG) && player.getMainHandItem().is(Items.OAK_LOG) && player.getMainHandItem().getCount() == 7
                && player.getInventory().countItem(Items.DIRT) == 1 && player.getInventory().countItem(Items.STRIPPED_OAK_LOG) == 0,
                "Fallback crafted/stripped an item instead of picking the owned log"); test.succeed();
    }
    @GameTest public void woodUsesAnExistingHotbarLogWithoutMovingOtherStacks(GameTestHelper test) {
        var player = player(test, false); set(player, 5, Items.OAK_LOG, 12);
        test.assertTrue(pick(player, Items.OAK_WOOD) && player.getInventory().getSelectedSlot() == 5 && player.getMainHandItem().getCount() == 12
                && player.getInventory().getItem(0).is(Items.DIRT), "Hotbar fallback was not simply selected"); test.succeed();
    }
    @GameTest public void strippedWoodPrefersStrippedLogAcrossAllPermittedSources(GameTestHelper test) {
        var player = player(test, true); set(player, 9, Items.OAK_LOG, 20); set(player, 5, Items.OAK_WOOD, 11);
        player.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(false, new ItemStack(Items.STRIPPED_OAK_LOG, 8)));
        test.assertTrue(pick(player, Items.STRIPPED_OAK_WOOD) && player.getMainHandItem().is(Items.STRIPPED_OAK_LOG)
                && player.getMainHandItem().getCount() == 8 && player.getInventory().getItem(9).getCount() == 20
                && player.getInventory().getItem(5).getCount() == 11, "Source proximity overrode explicit fallback priority"); test.succeed();
    }
    @GameTest public void customPriorityOverridesDefaultsAndUnknownEntriesAreSkipped(GameTestHelper test) {
        var player = player(test, false); set(player, 5, Items.BIRCH_LOG, 6); set(player, 10, Items.OAK_LOG, 4);
        Blockwheel.BUILDERS.pickRules(player, PickRules.DEFAULT.put(new PickRules.Rule("minecraft:stripped_oak_log",
                List.of("example:missing_block", "minecraft:birch_log", "minecraft:oak_log"))));
        test.assertTrue(pick(player, Items.STRIPPED_OAK_LOG) && player.getMainHandItem().is(Items.BIRCH_LOG)
                && player.getInventory().getItem(10).getCount() == 4, "Custom order or missing-block handling failed"); test.succeed();
    }
    @GameTest public void togglesEmptyOverridesAndSpeciesBoundariesAreRespected(GameTestHelper test) {
        var player = player(test, false); set(player, 10, Items.BIRCH_LOG, 8);
        test.assertTrue(!pick(player, Items.STRIPPED_OAK_LOG), "Default crossed wood species");
        set(player, 10, Items.OAK_LOG, 8); Blockwheel.BUILDERS.pickRules(player, PickRules.DEFAULT.withEnabled(false));
        test.assertTrue(!pick(player, Items.STRIPPED_OAK_LOG), "Disabled fallback feature still picked a log");
        Blockwheel.BUILDERS.pickRules(player, PickRules.DEFAULT.put(new PickRules.Rule("minecraft:stripped_oak_log", List.of())));
        test.assertTrue(!pick(player, Items.STRIPPED_OAK_LOG) && player.getInventory().getItem(10).getCount() == 8, "Empty override did not disable a default"); test.succeed();
    }
    @GameTest public void fallbacksNeverChainAndCustomizedTargetsAreNotDiscarded(GameTestHelper test) {
        var player = player(test, false); set(player, 10, Items.OAK_LOG, 8);
        Blockwheel.BUILDERS.pickRules(player, new PickRules(true, false, List.of(
                new PickRules.Rule("minecraft:stone", List.of("minecraft:birch_log")),
                new PickRules.Rule("minecraft:birch_log", List.of("minecraft:oak_log")))));
        test.assertTrue(!pick(player, Items.STONE), "Fallbacks followed a recursive chain");
        Blockwheel.BUILDERS.pickRules(player, PickRules.DEFAULT);
        var custom = new ItemStack(Items.STRIPPED_OAK_LOG); custom.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Keep metadata"));
        test.assertTrue(!Blockwheel.BUILDERS.pick(player, custom) && player.getInventory().getItem(10).getCount() == 8, "Fallback ignored requested custom components"); test.succeed();
    }
    @GameTest public void supplyAndEnderGatesRemainRequiredForRemoteAlternatives(GameTestHelper test) {
        var player = player(test, false); var box = ShulkerSupplyGameTests.box(true, new ItemStack(Items.OAK_LOG, 64));
        player.getInventory().setItem(9, box.copy()); player.getEnderChestInventory().setItem(0, box.copy());
        test.assertTrue(!pick(player, Items.STRIPPED_OAK_LOG), "Fallback enabled shulker access by itself");
        player.getInventory().setItem(9, ItemStack.EMPTY);
        Blockwheel.BUILDERS.configure(player, Blockwheel.BUILDERS.settings(player).withFeature(Feature.SHULKER_SUPPLY, true).withFeature(Feature.ENDER_ACCESS, false));
        test.assertTrue(!pick(player, Items.STRIPPED_OAK_LOG) && ItemStack.matches(box, player.getEnderChestInventory().getItem(0)), "Fallback bypassed the ender preference"); test.succeed();
    }
    @GameTest public void fullInventoryFallbackUsesVanillaSwapAndPreservesEveryItem(GameTestHelper test) {
        var player = player(test, false);
        for (int slot = 0; slot < 36; slot++) set(player, slot, Items.DIRT, 64);
        set(player, 20, Items.OAK_LOG, 12);
        test.assertTrue(pick(player, Items.STRIPPED_OAK_LOG) && player.getMainHandItem().is(Items.OAK_LOG)
                && player.getInventory().countItem(Items.OAK_LOG) == 12 && player.getInventory().countItem(Items.DIRT) == 35 * 64,
                "A full-inventory fallback lost or created items"); test.succeed();
    }
}
