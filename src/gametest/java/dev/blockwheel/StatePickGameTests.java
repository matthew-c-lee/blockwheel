package dev.blockwheel;

import dev.blockwheel.core.BuilderSettings;
import dev.blockwheel.core.BuilderSettings.Feature;
import dev.blockwheel.core.PickRules;
import dev.blockwheel.data.RegistryPickDefaults;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/** Controller-level picks use native registry state graphs and actual inventory/shulker transactions. */
public final class StatePickGameTests {
    private ServerPlayer player(GameTestHelper test) {
        var player = test.makeMockServerPlayerInLevel(); player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent(); player.getEnderChestInventory().clearContent(); player.getInventory().setSelectedSlot(0);
        Blockwheel.BUILDERS.configure(player, BuilderSettings.defaults().withEnabled(true).withFeature(Feature.SHULKER_SUPPLY, true)
                .withFeature(Feature.NORMALIZE, false).withFeature(Feature.PICKUP, false));
        Blockwheel.BUILDERS.pickRules(player, PickRules.DEFAULT); set(player, 0, Items.DIRT, 1); return player;
    }
    private Item item(String id) { return BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:" + id)); }
    private void set(ServerPlayer player, int slot, Item item, int count) { player.getInventory().setItem(slot, new ItemStack(item, count)); }
    private boolean pick(ServerPlayer player, Item item) { return Blockwheel.BUILDERS.pick(player, new ItemStack(item)); }
    @GameTest public void exactAnvilIsLeftToVanillaBeforeAnyDamagedFallback(GameTestHelper test) {
        var p = player(test); set(p, 10, Items.CHIPPED_ANVIL, 3); set(p, 5, Items.DAMAGED_ANVIL, 4);
        test.assertTrue(!pick(p, Items.CHIPPED_ANVIL) && p.getMainHandItem().is(Items.DIRT) && p.getInventory().countItem(Items.CHIPPED_ANVIL) == 3, "Fallback displaced a vanilla exact anvil pick"); test.succeed();
    }
    @GameTest public void equidistantAnvilFallbackPrefersDamagedWithoutRepairingIt(GameTestHelper test) {
        var p = player(test); set(p, 10, Items.ANVIL, 3); set(p, 5, Items.DAMAGED_ANVIL, 4);
        test.assertTrue(pick(p, Items.CHIPPED_ANVIL) && p.getMainHandItem().is(Items.DAMAGED_ANVIL)
                && p.getMainHandItem().getCount() == 4 && p.getInventory().countItem(Items.ANVIL) == 3 && p.getInventory().countItem(Items.CHIPPED_ANVIL) == 0, "Anvil fallback repaired or chose the healthier tie"); test.succeed();
    }
    @GameTest public void loneHealthierOrMoreDamagedAnvilCanBeSelectedFromSupplies(GameTestHelper test) {
        var p = player(test);
        for (var pair : new Item[][]{{Items.CHIPPED_ANVIL, Items.ANVIL}, {Items.ANVIL, Items.DAMAGED_ANVIL}}) {
            p.getInventory().clearContent(); set(p, 0, Items.DIRT, 1);
            p.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(pair[1], 5)));
            test.assertTrue(pick(p, pair[0]) && p.getMainHandItem().is(pair[1]) && p.getMainHandItem().getCount() == 4
                    && ShulkerSupplyGameTests.contents(p.getEnderChestInventory().getItem(0)).get(0).getCount() == 1, "Anvil state or named reserve changed");
        }
        test.succeed();
    }
    @GameTest public void exactCopperVariantBeatsAllStateFallbacks(GameTestHelper test) {
        var p = player(test); set(p, 5, item("cut_copper"), 20);
        p.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(item("waxed_weathered_cut_copper"), 8)));
        test.assertTrue(pick(p, item("waxed_weathered_cut_copper")) && p.getMainHandItem().is(item("waxed_weathered_cut_copper"))
                && p.getMainHandItem().getCount() == 7 && p.getInventory().getItem(5).getCount() == 20, "Exact copper finish did not win"); test.succeed();
    }
    @GameTest public void exactCopperFinishShapeConversionPrecedesWaxSwap(GameTestHelper test) {
        var p = player(test); set(p, 10, item("waxed_weathered_cut_copper_slab"), 8); set(p, 5, item("weathered_cut_copper_stairs"), 12);
        test.assertTrue(pick(p, item("waxed_weathered_cut_copper_stairs")) && p.getMainHandItem().is(item("waxed_weathered_cut_copper_stairs"))
                && p.getMainHandItem().getCount() == 4 && p.getInventory().getItem(5).getCount() == 12, "Exact finish conversion lost to a wax fallback"); test.succeed();
    }
    @GameTest public void copperSameOxidationOppositeWaxWinsOverOtherAges(GameTestHelper test) {
        var p = player(test); set(p, 5, item("cut_copper"), 20); set(p, 10, item("weathered_cut_copper"), 8); set(p, 11, item("waxed_exposed_cut_copper"), 7);
        test.assertTrue(pick(p, item("exposed_cut_copper")) && p.getMainHandItem().is(item("waxed_exposed_cut_copper"))
                && p.getMainHandItem().getCount() == 7 && p.getInventory().countItem(item("exposed_cut_copper")) == 0, "Copper fallback fabricated wax state or chose another oxidation first"); test.succeed();
    }
    @GameTest public void copperSameWaxNearestAgeUsesLessOxidizedTie(GameTestHelper test) {
        var p = player(test); set(p, 10, item("cut_copper"), 9); set(p, 5, item("weathered_cut_copper"), 7); set(p, 12, item("waxed_cut_copper"), 11);
        test.assertTrue(pick(p, item("exposed_cut_copper")) && p.getMainHandItem().is(item("cut_copper"))
                && p.getInventory().countItem(item("weathered_cut_copper")) == 7, "Copper nearest-age tie or waxing priority was wrong"); test.succeed();
    }
    @GameTest public void copperAlternativeShapeConversionKeepsActualFallbackFinish(GameTestHelper test) {
        var p = player(test); set(p, 10, item("waxed_exposed_cut_copper_slab"), 10);
        test.assertTrue(pick(p, item("exposed_cut_copper_stairs")) && p.getMainHandItem().is(item("waxed_exposed_cut_copper_stairs"))
                && p.getMainHandItem().getCount() == 5 && p.getInventory().countItem(item("exposed_cut_copper_stairs")) == 0, "Copper fallback removed wax instead of preserving its actual finish"); test.succeed();
    }
    @GameTest public void unrelatedCopperSubtypesAreNotFallbackMaterials(GameTestHelper test) {
        var p = player(test); set(p, 10, item("copper_block"), 64); set(p, 11, item("copper_grate"), 64);
        test.assertTrue(!pick(p, item("exposed_cut_copper_stairs")) && p.getInventory().getItem(10).getCount() == 64
                && p.getInventory().getItem(11).getCount() == 64, "Unrelated copper subtypes were conflated"); test.succeed();
    }
    @GameTest public void anvilAndCopperDefaultsCanBeDisabledWithoutChangingSupplies(GameTestHelper test) {
        var p = player(test); set(p, 5, Items.DAMAGED_ANVIL, 4); set(p, 10, item("waxed_exposed_cut_copper"), 8);
        Blockwheel.BUILDERS.pickRules(p, PickRules.DEFAULT.withAnvilDefaults(false).withCopperDefaults(false));
        test.assertTrue(!pick(p, Items.CHIPPED_ANVIL) && !pick(p, item("exposed_cut_copper")), "Disabled state defaults were still active"); test.succeed();
    }
    @GameTest public void everyNativeCopperFamilyPreservesFinishAndAnvilsHaveNoFamily(GameTestHelper test) {
        var states = RegistryPickDefaults.copperStates();
        for (var family : Blockwheel.CONVERSIONS.experimentalCatalog().families()) {
            var finish = family.members().stream().filter(member -> states.containsKey(member.blockId())).map(member -> {
                var state = states.get(member.blockId()); return state.oxidation() + ":" + state.waxed();
            }).distinct().toList();
            test.assertTrue(finish.size() <= 1, "Family merges copper oxidation/wax finishes");
        }
        for (String id : new String[]{"minecraft:anvil", "minecraft:chipped_anvil", "minecraft:damaged_anvil"})
            test.assertTrue(Blockwheel.CONVERSIONS.experimentalCatalog().family(id).isEmpty(), "Anvil entered a conversion family");
        test.succeed();
    }
}
