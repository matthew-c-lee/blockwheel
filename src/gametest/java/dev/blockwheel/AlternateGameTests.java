package dev.blockwheel;

import dev.blockwheel.core.BuilderSettings;
import dev.blockwheel.core.BuilderSettings.Feature;
import dev.blockwheel.network.Payloads;
import dev.blockwheel.server.InventoryConversion;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import java.util.ArrayList;

/** Real registry/recipe discovery, optional-shape authorization and integer material conservation. */
public final class AlternateGameTests {
    private ServerPlayer player(GameTestHelper test, boolean trapdoors) {
        var player = test.makeMockServerPlayerInLevel(); player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent(); player.getEnderChestInventory().clearContent(); player.getInventory().setSelectedSlot(0);
        Blockwheel.BUILDERS.configure(player, BuilderSettings.defaults().withFeature(Feature.WOOD_TRAPDOORS, trapdoors));
        return player;
    }
    private void held(ServerPlayer player, Item item, int count) { player.getInventory().setItem(0, new ItemStack(item, count)); }
    private void convert(ServerPlayer player, String shape) {
        var wheel = Blockwheel.CONVERSIONS.prepare(player, 0);
        if (wheel == null) throw new AssertionError("Could not prepare alternate conversion");
        Blockwheel.CONVERSIONS.convert(player, new Payloads.Convert(wheel.token(), shape));
    }
    @GameTest public void discoveryIncludesOnlyWoodenTrapdoorsAtThreePlanksEach(GameTestHelper test) {
        int found = 0;
        for (var block : BuiltInRegistries.BLOCK) {
            boolean wooden = block.builtInRegistryHolder().tags().anyMatch(tag -> tag.location().toString().equals("minecraft:wooden_trapdoors"));
            var family = Blockwheel.CONVERSIONS.catalog().family(BuiltInRegistries.BLOCK.getKey(block).toString()).orElse(null);
            var member = family == null ? null : family.member(BuiltInRegistries.BLOCK.getKey(block).toString()).orElse(null);
            if (member == null || !member.shape().id().equals("blockwheel:trapdoor")) continue;
            found++;
            test.assertTrue(wooden && member.shape().units() == 6 && family.shape("blockwheel:block").orElseThrow().shape().units() == 2,
                    "Nonwooden trapdoor or wrong native recipe ratio");
        }
        test.assertTrue(found >= 10, "Wood trapdoor discovery missed registered wood families");
        test.assertTrue(Blockwheel.CONVERSIONS.catalog().family("minecraft:iron_trapdoor").isEmpty()
                && Blockwheel.CONVERSIONS.catalog().family("minecraft:copper_trapdoor").isEmpty(), "Metal trapdoor entered a wood family");
        test.succeed();
    }
    @GameTest public void trapdoorsAreHiddenAndUnspendableByDefault(GameTestHelper test) {
        var player = player(test, false); held(player, Items.OAK_PLANKS, 8);
        var wheel = Blockwheel.CONVERSIONS.prepare(player, 0);
        test.assertTrue(wheel != null && wheel.options().stream().noneMatch(option -> option.shapeId().equals("blockwheel:trapdoor")), "Off-by-default trapdoor was offered");
        Blockwheel.CONVERSIONS.convert(player, new Payloads.Convert(wheel.token(), "blockwheel:trapdoor"));
        test.assertTrue(player.getMainHandItem().is(Items.OAK_PLANKS) && player.getMainHandItem().getCount() == 8, "Forged disabled alternate spent material");
        held(player, Items.OAK_TRAPDOOR, 2);
        test.assertTrue(Blockwheel.CONVERSIONS.prepare(player, 0) == null, "Disabled trapdoor was treated as family input");
        test.succeed();
    }
    @GameTest public void manualTrapdoorRatioWorksInBothDirectionsWithoutBuilderMode(GameTestHelper test) {
        var player = player(test, true); held(player, Items.OAK_PLANKS, 8); convert(player, "blockwheel:trapdoor");
        test.assertTrue(!Blockwheel.BUILDERS.settings(player).enabled() && player.getMainHandItem().is(Items.OAK_TRAPDOOR)
                && player.getMainHandItem().getCount() == 2 && player.getInventory().countItem(Items.OAK_PLANKS) == 2
                && player.getInventory().getItem(9).is(Items.OAK_PLANKS), "Three-to-one output or physical main-inventory change was incorrect");
        player.getInventory().clearContent(); held(player, Items.OAK_TRAPDOOR, 2); convert(player, "blockwheel:block");
        test.assertTrue(player.getMainHandItem().is(Items.OAK_PLANKS) && player.getMainHandItem().getCount() == 6, "Reverse trapdoor ratio was not three planks");
        test.succeed();
    }
    @GameTest public void slabAndPlankDonorsCompleteTrapdoorBatchesWithoutHotbarChange(GameTestHelper test) {
        var player = player(test, true);
        for (Item source : new Item[]{Items.OAK_SLAB, Items.OAK_PLANKS}) {
            player.getInventory().clearContent(); int needed = source == Items.OAK_SLAB ? 6 : 3;
            held(player, source, needed - 1); player.getInventory().setItem(20, new ItemStack(source)); convert(player, "blockwheel:trapdoor");
            test.assertTrue(player.getMainHandItem().is(Items.OAK_TRAPDOOR) && player.getMainHandItem().getCount() == 1
                    && player.getInventory().countItem(source) == 0, "Main-inventory donor did not complete the exact trapdoor batch");
            for (int slot = 1; slot < 9; slot++) test.assertTrue(player.getInventory().getItem(slot).isEmpty(), "Trapdoor change entered another hotbar slot");
        }
        test.succeed();
    }
    @GameTest public void disablingTrapdoorsRevokesAnAlreadyOpenConversion(GameTestHelper test) {
        var player = player(test, true); held(player, Items.OAK_PLANKS, 6);
        var wheel = Blockwheel.CONVERSIONS.prepare(player, 0);
        Blockwheel.BUILDERS.configure(player, Blockwheel.BUILDERS.settings(player).withFeature(Feature.WOOD_TRAPDOORS, false));
        Blockwheel.CONVERSIONS.convert(player, new Payloads.Convert(wheel.token(), "blockwheel:trapdoor"));
        test.assertTrue(player.getMainHandItem().is(Items.OAK_PLANKS) && player.getMainHandItem().getCount() == 6, "Disabled target spent an earlier session");
        Blockwheel.BUILDERS.configure(player, Blockwheel.BUILDERS.settings(player).withFeature(Feature.WOOD_TRAPDOORS, true)); held(player, Items.OAK_TRAPDOOR, 2);
        wheel = Blockwheel.CONVERSIONS.prepare(player, 0);
        Blockwheel.BUILDERS.configure(player, Blockwheel.BUILDERS.settings(player).withFeature(Feature.WOOD_TRAPDOORS, false));
        Blockwheel.CONVERSIONS.convert(player, new Payloads.Convert(wheel.token(), "blockwheel:block"));
        test.assertTrue(player.getMainHandItem().is(Items.OAK_TRAPDOOR) && player.getMainHandItem().getCount() == 2, "Disabled source spent an earlier session");
        test.succeed();
    }
    @GameTest public void cappedSlabsFromTrapdoorsKeepBaseChangeAndDropOnlyOverflow(GameTestHelper test) {
        var player = player(test, true); for (int slot = 1; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
        held(player, Items.OAK_TRAPDOOR, 64);
        var family = Blockwheel.CONVERSIONS.catalog().family("minecraft:oak_trapdoor").orElseThrow();
        var plan = InventoryConversion.plan(player.getInventory(), 0, family, family.shape("blockwheel:trapdoor").orElseThrow(), family.shape("blockwheel:slab").orElseThrow());
        var drops = new ArrayList<ItemStack>(); InventoryConversion.apply(player.getInventory(), drops::add, 0, Items.OAK_SLAB, plan);
        test.assertTrue(player.getMainHandItem().is(Items.OAK_SLAB) && player.getMainHandItem().getCount() == 64
                && drops.stream().allMatch(stack -> stack.is(Items.OAK_PLANKS))
                && drops.stream().mapToInt(ItemStack::getCount).sum() == 160 && player.getInventory().countItem(Items.DIRT) == 2240,
                "Trapdoor-to-slab cap lost value, displaced inventory or dropped the wrong change");
        test.succeed();
    }
    @GameTest public void builderConversionOfTrapdoorsIsAlsoOptIn(GameTestHelper test) {
        var player = player(test, false);
        var settings = Blockwheel.BUILDERS.settings(player).withEnabled(true).withFeature(Feature.NORMALIZE, false);
        Blockwheel.BUILDERS.configure(player, settings);
        var incoming = new ItemStack(Items.OAK_TRAPDOOR, 2);
        test.assertTrue(Blockwheel.BUILDERS.pickup(player, incoming) == null && incoming.getCount() == 2, "Disabled alternate changed a pickup");
        player.getInventory().setItem(23, new ItemStack(Items.OAK_TRAPDOOR, 7));
        Blockwheel.BUILDERS.configure(player, settings.withFeature(Feature.NORMALIZE, true));
        test.assertTrue(player.getInventory().getItem(23).is(Items.OAK_TRAPDOOR), "Disabled alternate normalized in storage");
        Blockwheel.BUILDERS.configure(player, settings.withFeature(Feature.NORMALIZE, true).withFeature(Feature.WOOD_TRAPDOORS, true));
        test.assertTrue(player.getInventory().countItem(Items.OAK_TRAPDOOR) == 0 && player.getInventory().countItem(Items.OAK_PLANKS) == 21,
                "Opted-in trapdoor normalization did not preserve the three-plank value");
        test.succeed();
    }
    @GameTest public void optedInTrapdoorRestockUsesThreePlanksPerAdditionalItem(GameTestHelper test) {
        var player = player(test, true); Blockwheel.BUILDERS.configure(player, Blockwheel.BUILDERS.settings(player).withEnabled(true).withFeature(Feature.NORMALIZE, false));
        held(player, Items.OAK_TRAPDOOR, 4); player.getInventory().setItem(9, new ItemStack(Items.OAK_PLANKS, 10));
        Blockwheel.BUILDERS.afterUse(player, 0, new ItemStack(Items.OAK_TRAPDOOR, 5));
        test.assertTrue(player.getMainHandItem().is(Items.OAK_TRAPDOOR) && player.getMainHandItem().getCount() == 7
                && player.getInventory().countItem(Items.OAK_PLANKS) == 1, "Restock ignored the optional trapdoor's three-to-one value");
        test.succeed();
    }
    @GameTest public void templateCoercionHonorsTheTrapdoorOptInAndRatio(GameTestHelper test) {
        var player = player(test, false); var settings = Blockwheel.BUILDERS.settings(player).withEnabled(true).withFeature(Feature.NORMALIZE, false);
        Blockwheel.BUILDERS.configure(player, settings); held(player, Items.OAK_PLANKS, 9);
        player.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(Items.OAK_TRAPDOOR)));
        test.assertTrue(!Blockwheel.BUILDERS.fillTemplates(player, player.containerMenu.containerId, true) && player.getMainHandItem().getCount() == 9,
                "Disabled trapdoor template coerced planks");
        Blockwheel.BUILDERS.configure(player, settings.withFeature(Feature.WOOD_TRAPDOORS, true));
        test.assertTrue(Blockwheel.BUILDERS.fillTemplates(player, player.containerMenu.containerId, true) && player.getMainHandItem().isEmpty()
                && ShulkerSupplyGameTests.contents(player.getEnderChestInventory().getItem(0)).get(0).getCount() == 4, "Trapdoor template ignored its configured three-plank value");
        test.succeed();
    }
}
