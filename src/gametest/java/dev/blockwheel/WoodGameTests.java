package dev.blockwheel;

import dev.blockwheel.core.BuilderSettings;
import dev.blockwheel.core.BuilderSettings.Feature;
import dev.blockwheel.core.WoodFamilies;
import dev.blockwheel.mixin.AxeItemAccessor;
import dev.blockwheel.network.Payloads;
import dev.blockwheel.server.BlockMaterials;
import dev.blockwheel.server.InventoryConversion;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import java.util.ArrayList;
import java.util.Random;

/** Expanded material policies on actual native slots, block items, nested boxes and controller events. */
public final class WoodGameTests {
    private ServerPlayer player(GameTestHelper test, boolean experimental) {
        var p = test.makeMockServerPlayerInLevel(); p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent(); p.getEnderChestInventory().clearContent(); p.getInventory().setSelectedSlot(0);
        Blockwheel.BUILDERS.configure(p, BuilderSettings.defaults().withEnabled(true).withFeature(Feature.EXPERIMENTAL_WOOD, experimental)
                .withFeature(Feature.NORMALIZE, false).withFeature(Feature.PICKUP, false));
        return p;
    }
    private void set(ServerPlayer p, int slot, Item item, int count) { p.getInventory().setItem(slot, new ItemStack(item, count)); }
    private int count(ServerPlayer p, Item item) { return p.getInventory().countItem(item); }
    private int occupied(ServerPlayer p) { int n = 0; for (int i = 9; i < 36; i++) if (!p.getInventory().getItem(i).isEmpty()) n++; return n; }
    private long units(ServerPlayer p) {
        long value = 0;
        for (int slot = 0; slot < 36; slot++) {
            var stack = p.getInventory().getItem(slot); var material = Blockwheel.BUILDERS.materials(p).resolve(stack);
            if (material != null) value += (long) stack.getCount() * material.form().units();
        }
        return value;
    }
    private void normalize(ServerPlayer p) {
        var planner = Blockwheel.BUILDERS.planner(p);
        var plan = planner.normalize(p.getInventory(), planner.storageFamilies(p.getInventory()));
        if (!plan.transaction().commit()) throw new AssertionError("Normalization did not commit");
    }
    private void distribute(ServerPlayer p, Item item, int count) {
        p.getInventory().clearContent(); int slot = 9, limit = new ItemStack(item).getMaxStackSize();
        while (count > 0) { int take = Math.min(count, limit); set(p, slot++, item, take); count -= take; }
    }
    private void convert(ServerPlayer p, String shape) {
        var wheel = Blockwheel.CONVERSIONS.prepare(p, 0); if (wheel == null) throw new AssertionError("No wood wheel");
        Blockwheel.CONVERSIONS.convert(p, new Payloads.Convert(wheel.token(), shape));
    }
    @GameTest public void manualPlanksToWoodLeavesStoredLogsWithBuilderModeOff(GameTestHelper test) {
        var p = player(test, true); Blockwheel.BUILDERS.configure(p, Blockwheel.BUILDERS.settings(p).withEnabled(false));
        set(p, 0, Items.OAK_PLANKS, 64); set(p, 9, Items.OAK_LOG, 10); convert(p, WoodFamilies.WOOD);
        test.assertTrue(p.getMainHandItem().is(Items.OAK_WOOD) && p.getMainHandItem().getCount() == 16
                && p.getInventory().getItem(9).is(Items.OAK_LOG) && p.getInventory().getItem(9).getCount() == 10
                && units(p) == 26 * 8, "Radial-only conversion consolidated stored family material"); test.succeed();
    }
    @GameTest public void radialOnlyDoesNotMergeStoredTargetsButKeepsCapsAndMinimalSlabBatches(GameTestHelper test) {
        var p = player(test, true); Blockwheel.BUILDERS.configure(p, Blockwheel.BUILDERS.settings(p).withEnabled(false));
        set(p, 0, Items.OAK_SLAB, 64); set(p, 9, Items.OAK_LOG, 10); convert(p, WoodFamilies.LOG);
        test.assertTrue(p.getMainHandItem().is(Items.OAK_LOG) && p.getMainHandItem().getCount() == 8
                && p.getInventory().getItem(9).getCount() == 10, "Builder-off radial merged exact target logs");
        p.getInventory().clearContent(); set(p, 0, Items.OAK_LOG, 63); convert(p, "blockwheel:slab");
        test.assertTrue(p.getMainHandItem().is(Items.OAK_SLAB) && p.getMainHandItem().getCount() == 64 && count(p, Items.OAK_LOG) == 55,
                "Builder-off radial lost its one-stack cap or base leftovers");
        p.getInventory().clearContent(); set(p, 0, Items.OAK_SLAB, 1); set(p, 9, Items.OAK_SLAB, 63); convert(p, "blockwheel:block");
        test.assertTrue(p.getMainHandItem().is(Items.OAK_PLANKS) && p.getMainHandItem().getCount() == 1 && p.getInventory().getItem(9).getCount() == 62,
                "Builder-off radial swept extra source material instead of finishing only its missing slab batch"); test.succeed();
    }
    @GameTest public void manualFamilyConsolidationCombinesMixedShapesAndTheHeldRemainder(GameTestHelper test) {
        var p = player(test, true); set(p, 0, Items.OAK_PLANKS, 1); set(p, 9, Items.OAK_STAIRS, 3); convert(p, WoodFamilies.LOG);
        test.assertTrue(p.getMainHandItem().is(Items.OAK_LOG) && p.getMainHandItem().getCount() == 1 && occupied(p) == 0,
                "One plank and three stored stairs did not complete a log");
        p.getInventory().clearContent(); set(p, 0, Items.OAK_STAIRS, 4); set(p, 9, Items.OAK_PLANKS, 3);
        set(p, 10, Items.OAK_FENCE, 2); set(p, 11, Items.OAK_SLAB, 6); set(p, 12, Items.OAK_WOOD, 1); set(p, 13, Items.OAK_LOG, 2);
        convert(p, WoodFamilies.LOG);
        test.assertTrue(p.getMainHandItem().is(Items.OAK_LOG) && p.getMainHandItem().getCount() == 6 && occupied(p) == 0 && units(p) == 48,
                "Mixed compatible source shapes did not conserve a complete consolidated batch"); test.succeed();
    }
    @GameTest public void manualFamilyConsolidationStopsAtOneStackAndPrefersExistingTargets(GameTestHelper test) {
        var p = player(test, true); set(p, 0, Items.OAK_PLANKS, 64); set(p, 9, Items.OAK_LOG, 60); convert(p, WoodFamilies.WOOD);
        test.assertTrue(p.getMainHandItem().getCount() == 64 && p.getInventory().getItem(9).is(Items.OAK_LOG)
                && p.getInventory().getItem(9).getCount() == 12 && units(p) == 76 * 8, "Consolidation overfilled the hand or converted unaccepted logs");
        p.getInventory().clearContent(); set(p, 0, Items.OAK_PLANKS, 64); set(p, 9, Items.OAK_LOG, 60); set(p, 10, Items.OAK_WOOD, 48);
        convert(p, WoodFamilies.WOOD);
        test.assertTrue(p.getMainHandItem().getCount() == 64 && p.getInventory().getItem(9).getCount() == 60 && p.getInventory().getItem(10).isEmpty(),
                "Consolidation converted logs before merging existing target wood"); test.succeed();
    }
    @GameTest public void manualConsolidationPreservesIncompleteBatchesAndSourceBoundaries(GameTestHelper test) {
        var p = player(test, true); set(p, 0, Items.OAK_STAIRS, 4); set(p, 9, Items.OAK_SLAB, 15);
        set(p, 10, Items.SPRUCE_LOG, 8); set(p, 11, Items.STRIPPED_OAK_LOG, 8); set(p, 12, Items.STRIPPED_OAK_WOOD, 8);
        var custom = new ItemStack(Items.OAK_LOG, 8); custom.set(DataComponents.CUSTOM_NAME, Component.literal("Keep")); p.getInventory().setItem(13, custom.copy());
        var carried = ShulkerSupplyGameTests.box(true, new ItemStack(Items.OAK_LOG, 20)); p.getInventory().setItem(14, carried.copy());
        set(p, 5, Items.OAK_LOG, 8); set(p, 40, Items.OAK_LOG, 8); convert(p, WoodFamilies.LOG);
        test.assertTrue(p.getMainHandItem().is(Items.OAK_LOG) && p.getMainHandItem().getCount() == 2 && p.getInventory().getItem(9).getCount() == 7
                && p.getInventory().getItem(10).getCount() == 8 && p.getInventory().getItem(11).getCount() == 8 && p.getInventory().getItem(12).getCount() == 8
                && ItemStack.matches(custom, p.getInventory().getItem(13)) && ItemStack.matches(carried, p.getInventory().getItem(14))
                && p.getInventory().getItem(5).getCount() == 8 && p.getOffhandItem().getCount() == 8,
                "Family consolidation consumed an incomplete, stripped, customized, foreign or protected source"); test.succeed();
    }
    @GameTest public void staleAdditionalFamilyDonorCannotPartiallyCommit(GameTestHelper test) {
        var p = player(test, true); set(p, 0, Items.OAK_PLANKS, 64); set(p, 9, Items.OAK_LOG, 5); set(p, 10, Items.OAK_WOOD, 5);
        var family = Blockwheel.CONVERSIONS.experimentalCatalog().family("minecraft:oak_planks").orElseThrow();
        var plan = InventoryConversion.plan(p.getInventory(), 0, family, family.shape("blockwheel:block").orElseThrow(), family.shape(WoodFamilies.WOOD).orElseThrow());
        p.getInventory().setItem(9, ItemStack.EMPTY); boolean rejected = false;
        try { InventoryConversion.apply(p.getInventory(), stack -> { throw new AssertionError("Unexpected drop"); }, 0, Items.OAK_WOOD, plan); }
        catch (IllegalArgumentException expected) { rejected = true; }
        test.assertTrue(rejected && p.getMainHandItem().is(Items.OAK_PLANKS) && p.getMainHandItem().getCount() == 64
                && p.getInventory().getItem(10).getCount() == 5, "A stale family donor spent other staged sources"); test.succeed();
    }
    @GameTest public void randomizedManualFamilyConsolidationConservesNativeMaterial(GameTestHelper test) {
        var p = player(test, true); var random = new Random(292); var items = new Item[]{Items.OAK_PLANKS, Items.OAK_STAIRS, Items.OAK_SLAB, Items.OAK_FENCE, Items.OAK_LOG, Items.OAK_WOOD};
        var shapes = new String[]{"blockwheel:block", "blockwheel:stairs", "blockwheel:slab", "blockwheel:fence", WoodFamilies.LOG, WoodFamilies.WOOD};
        for (int iteration = 0; iteration < 200; iteration++) {
            p.getInventory().clearContent(); int source = random.nextInt(items.length), target = (source + 1 + random.nextInt(items.length - 1)) % items.length;
            set(p, 0, items[source], 1 + random.nextInt(64));
            for (int slot = 9; slot < 15; slot++) set(p, slot, items[random.nextInt(items.length)], 1 + random.nextInt(64));
            long before = units(p); convert(p, shapes[target]);
            test.assertTrue(before == units(p) && p.getMainHandItem().getCount() <= p.getMainHandItem().getMaxStackSize(), "Random manual consolidation lost material or overfilled the hand at " + iteration);
        }
        test.succeed();
    }
    @GameTest public void radialChangeRefillsOnlyMatchingNamedEnderTemplates(GameTestHelper test) {
        var p = player(test, false); set(p, 0, Items.STONE, 64); set(p, 9, Items.STONE, 5);
        Blockwheel.BUILDERS.configure(p, Blockwheel.BUILDERS.settings(p).withFeature(Feature.SHULKER_SUPPLY, true));
        var original = ShulkerSupplyGameTests.box(true, new ItemStack(Items.STONE, 10), ItemStack.EMPTY, new ItemStack(Items.STONE_STAIRS, 3));
        var carried = ShulkerSupplyGameTests.box(true, new ItemStack(Items.STONE, 10)); p.getInventory().setItem(10, carried.copy());
        p.getEnderChestInventory().setItem(0, original.copy()); convert(p, "blockwheel:slab");
        var contents = ShulkerSupplyGameTests.contents(p.getEnderChestInventory().getItem(0));
        test.assertTrue(p.getMainHandItem().is(Items.STONE_SLAB) && p.getMainHandItem().getCount() == 64
                && contents.get(0).getCount() == 42 && contents.get(1).isEmpty() && contents.get(2).getCount() == 3
                && p.getInventory().getItem(9).getCount() == 5 && ItemStack.matches(carried, p.getInventory().getItem(10))
                && original.getHoverName().equals(p.getEnderChestInventory().getItem(0).getHoverName()),
                "Radial physical change swept unrelated inventory, altered templates or missed remote storage"); test.succeed();
    }
    @GameTest public void radialChangeUsesAvailableRemoteCapacityThenKeepsTheRestInMain(GameTestHelper test) {
        var p = player(test, false); set(p, 0, Items.STONE, 64);
        Blockwheel.BUILDERS.configure(p, Blockwheel.BUILDERS.settings(p).withFeature(Feature.SHULKER_SUPPLY, true));
        p.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(Items.STONE, 60)));
        p.getEnderChestInventory().setItem(1, ShulkerSupplyGameTests.box(true, new ItemStack(Items.STONE, 50)));
        var unnamed = ShulkerSupplyGameTests.box(false, new ItemStack(Items.STONE, 1)); p.getEnderChestInventory().setItem(2, unnamed.copy());
        p.getEnderChestInventory().setItem(3, new ItemStack(Items.STONE, 1)); convert(p, "blockwheel:slab");
        test.assertTrue(p.getMainHandItem().getCount() == 64 && count(p, Items.STONE) == 14
                && ShulkerSupplyGameTests.contents(p.getEnderChestInventory().getItem(0)).get(0).getCount() == 64
                && ShulkerSupplyGameTests.contents(p.getEnderChestInventory().getItem(1)).get(0).getCount() == 64
                && ItemStack.matches(unnamed, p.getEnderChestInventory().getItem(2)) && p.getEnderChestInventory().getItem(3).getCount() == 1,
                "Remote remainder overfilled templates or lost unaccepted material"); test.succeed();
    }
    @GameTest public void radialChangeRespectsBuilderSupplyEnderAndServerGates(GameTestHelper test) {
        var p = player(test, false); var enabled = Blockwheel.BUILDERS.settings(p).withFeature(Feature.SHULKER_SUPPLY, true); boolean allowed = Blockwheel.BUILDERS.enderAllowed();
        try {
            for (int gate = 0; gate < 4; gate++) {
                p.getInventory().clearContent(); set(p, 0, Items.STONE, 64);
                var original = ShulkerSupplyGameTests.box(true, new ItemStack(Items.STONE, 1)); p.getEnderChestInventory().setItem(0, original.copy());
                var settings = gate == 0 ? enabled.withEnabled(false) : gate == 1 ? enabled.withFeature(Feature.SHULKER_SUPPLY, false)
                        : gate == 2 ? enabled.withFeature(Feature.ENDER_ACCESS, false) : enabled;
                Blockwheel.BUILDERS.configure(p, settings); Blockwheel.BUILDERS.setPolicy(new dev.blockwheel.server.BuilderServerConfig(gate != 3));
                convert(p, "blockwheel:slab");
                test.assertTrue(p.getMainHandItem().is(Items.STONE_SLAB) && p.getMainHandItem().getCount() == 64 && count(p, Items.STONE) == 32
                        && ItemStack.matches(original, p.getEnderChestInventory().getItem(0)), "Remote remainder bypassed gate " + gate);
            }
        } finally { Blockwheel.BUILDERS.setPolicy(new dev.blockwheel.server.BuilderServerConfig(allowed)); }
        test.succeed();
    }
    @GameTest public void enderRemainderAndHandConversionCommitOrRejectTogether(GameTestHelper test) {
        var p = player(test, false); set(p, 0, Items.STONE, 64);
        var original = ShulkerSupplyGameTests.box(true, new ItemStack(Items.STONE, 1)); p.getEnderChestInventory().setItem(0, original.copy());
        var transaction = new dev.blockwheel.server.InventoryTransaction(p.getInventory(), p.getEnderChestInventory());
        transaction.set(0, new ItemStack(Items.STONE_SLAB, 64));
        test.assertTrue(dev.blockwheel.server.TemplateDeposit.fillRemainder(transaction, new ItemStack(Items.STONE, 32)).isEmpty(), "Remote remainder was not staged");
        p.getEnderChestInventory().setItem(1, new ItemStack(Items.DIAMOND));
        test.assertTrue(!transaction.commit() && p.getMainHandItem().is(Items.STONE) && p.getMainHandItem().getCount() == 64
                && ItemStack.matches(original, p.getEnderChestInventory().getItem(0)), "A stale ender snapshot partially committed the hand or shulker"); test.succeed();
    }
    @GameTest public void experimentalViewDiscoversSpeciesAndStemRatiosButNotBamboo(GameTestHelper test) {
        var p = player(test, true);
        for (String stem : new String[]{"oak", "spruce", "cherry", "crimson", "warped"}) {
            String log = "minecraft:" + stem + (stem.equals("crimson") || stem.equals("warped") ? "_stem" : "_log");
            var family = Blockwheel.CONVERSIONS.experimentalCatalog().family(log).orElseThrow();
            test.assertTrue(family.shape(WoodFamilies.LOG).orElseThrow().shape().units() == 8
                    && family.shape(WoodFamilies.WOOD).orElseThrow().shape().units() == 8
                    && family.shape("blockwheel:block").orElseThrow().shape().units() == 2, "Wood relationship did not use verified same-species ratios");
        }
        test.assertTrue(Blockwheel.BUILDERS.materials(p).resolve(Items.BAMBOO_BLOCK) == null, "Bamboo was forced into the ordinary log relationship"); test.succeed();
    }
    @GameTest public void automaticCompactionMatchesTheRequestedQuantityTable(GameTestHelper test) {
        var p = player(test, true);
        for (int amount : new int[]{4, 5, 17, 24, 25, 28, 63, 64, 65, 67, 68, 128, 129}) {
            distribute(p, Items.OAK_STAIRS, amount); int before = occupied(p); normalize(p);
            boolean compact = amount % 4 == 0 || amount > 64;
            test.assertTrue(count(p, Items.OAK_LOG) == (compact ? amount / 4 : 0)
                    && count(p, Items.OAK_PLANKS) == (compact ? amount % 4 : amount)
                    && count(p, Items.OAK_STAIRS) == 0
                    && occupied(p) <= before && units(p) == (long) amount * 2, "Compaction table failed for " + amount);
        }
        test.succeed();
    }
    private void enableNormalization(ServerPlayer p) {
        Blockwheel.BUILDERS.configure(p, Blockwheel.BUILDERS.settings(p).withFeature(Feature.NORMALIZE, true));
    }
    @GameTest public void smallWoodenVariantsUseSecondaryPlanksWithoutAddingSlots(GameTestHelper test) {
        var p = player(test, true);
        for (var item : new Item[]{Items.OAK_FENCE, Items.OAK_STAIRS}) for (int amount : new int[]{1, 2, 3, 5, 17, 25, 63}) {
            distribute(p, item, amount); normalize(p);
            test.assertTrue(count(p, Items.OAK_PLANKS) == amount && count(p, Items.OAK_LOG) == 0 && occupied(p) == 1
                    && units(p) == amount * 2, "Small variant did not become one secondary plank stack");
        }
        test.succeed();
    }
    @GameTest public void nativeFencePlacementsUsePlanksThenCombineIntoALogImmediately(GameTestHelper test) {
        var p = player(test, true); enableNormalization(p);
        for (int amount = 1; amount <= 4; amount++) {
            set(p, 0, Items.OAK_FENCE, 1);
            p.containerMenu.clicked(36, 0, ContainerInput.PICKUP, p);
            test.assertTrue(p.containerMenu.getCarried().is(Items.OAK_FENCE), "Cursor fence was coerced before placement");
            p.containerMenu.clicked(20 + amount, 0, ContainerInput.PICKUP, p);
            test.assertTrue(p.containerMenu.getCarried().isEmpty() && occupied(p) == 1 && units(p) == amount * 2
                    && (amount < 4 ? count(p, Items.OAK_PLANKS) == amount : count(p, Items.OAK_LOG) == 1),
                    "Native fence placement did not apply primary/secondary normalization at " + amount);
        }
        test.succeed();
    }
    @GameTest public void nativeMainInventoryMovesCombineMixedShapesWithoutRetoggling(GameTestHelper test) {
        var p = player(test, true); enableNormalization(p); set(p, 9, Items.OAK_PLANKS, 1); set(p, 10, Items.OAK_STAIRS, 3);
        p.containerMenu.clicked(9, 0, ContainerInput.PICKUP, p);
        test.assertTrue(p.containerMenu.getCarried().is(Items.OAK_PLANKS) && p.containerMenu.getCarried().getCount() == 1
                && p.getInventory().getItem(10).is(Items.OAK_PLANKS) && p.getInventory().getItem(10).getCount() == 3,
                "Main move missed secondary normalization or altered the cursor");
        p.containerMenu.clicked(23, 0, ContainerInput.PICKUP, p);
        test.assertTrue(p.containerMenu.getCarried().isEmpty() && p.getInventory().getItem(23).is(Items.OAK_LOG)
                && p.getInventory().getItem(23).getCount() == 1 && occupied(p) == 1 && units(p) == 8,
                "One plank plus three stairs did not compact immediately after a main-to-main move"); test.succeed();
    }
    @GameTest public void nativeContainerArrivalNormalizesOnlyPlayerMainStorage(GameTestHelper test) {
        var p = player(test, true); enableNormalization(p); set(p, 9, Items.OAK_PLANKS, 3); set(p, 5, Items.OAK_STAIRS, 4); set(p, 40, Items.OAK_FENCE, 4);
        p.containerMenu = net.minecraft.world.inventory.ChestMenu.oneRow(7, p.getInventory());
        p.containerMenu.getSlot(0).set(new ItemStack(Items.OAK_FENCE)); p.containerMenu.getSlot(1).set(new ItemStack(Items.OAK_FENCE, 4));
        p.containerMenu.clicked(0, 0, ContainerInput.PICKUP, p); p.containerMenu.clicked(23, 0, ContainerInput.PICKUP, p);
        test.assertTrue(count(p, Items.OAK_LOG) == 1 && p.getInventory().getItem(5).is(Items.OAK_STAIRS) && p.getInventory().getItem(5).getCount() == 4
                && p.getOffhandItem().getCount() == 4 && p.containerMenu.getSlot(1).getItem().getCount() == 4
                && p.containerMenu.getCarried().isEmpty() && units(p) == 16, "Inbound storage normalization crossed an excluded domain");
        p.containerMenu = p.inventoryMenu; test.succeed();
    }
    @GameTest public void secondaryNormalizationKeepsStrippedAndCustomizedStacksSeparate(GameTestHelper test) {
        var p = player(test, true); enableNormalization(p);
        var named = new ItemStack(Items.OAK_FENCE, 4); named.set(DataComponents.CUSTOM_NAME, Component.literal("Keep")); p.getInventory().setItem(9, named.copy());
        set(p, 10, Items.STRIPPED_OAK_LOG, 4); set(p, 11, Items.OAK_FENCE, 1); set(p, 0, Items.OAK_FENCE, 1);
        p.containerMenu.clicked(36, 0, ContainerInput.PICKUP, p); p.containerMenu.clicked(23, 0, ContainerInput.PICKUP, p);
        test.assertTrue(count(p, Items.OAK_PLANKS) == 2 && count(p, Items.OAK_LOG) == 0
                && ItemStack.matches(named, p.getInventory().getItem(9)) && count(p, Items.STRIPPED_OAK_LOG) == 4,
                "Secondary normalization consumed a stripped or customized stack"); test.succeed();
    }
    @GameTest public void nativeWoodNormalizationStillRequiresTheMasterAndFeature(GameTestHelper test) {
        var p = player(test, true);
        for (boolean master : new boolean[]{false, true}) {
            p.getInventory().clearContent(); Blockwheel.BUILDERS.configure(p, Blockwheel.BUILDERS.settings(p)
                    .withEnabled(master).withFeature(Feature.NORMALIZE, !master));
            set(p, 0, Items.OAK_FENCE, 1); p.containerMenu.clicked(36, 0, ContainerInput.PICKUP, p); p.containerMenu.clicked(23, 0, ContainerInput.PICKUP, p);
            test.assertTrue(p.getInventory().getItem(23).is(Items.OAK_FENCE) && count(p, Items.OAK_PLANKS) == 0,
                    "Inactive normalization modified a fence");
        }
        test.succeed();
    }
    private ItemStack craft(GameTestHelper test, int width, int height, java.util.List<ItemStack> items) {
        var input = net.minecraft.world.item.crafting.CraftingInput.of(width, height, items);
        return test.getLevel().getServer().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, test.getLevel())
                .orElseThrow().value().assemble(input);
    }
    @GameTest public void vanillaCraftingAndOneToOneWoodConversionsDoNotAmplifyPlanks(GameTestHelper test) {
        var p = player(test, true);
        for (var item : new Item[]{Items.OAK_LOG, Items.OAK_WOOD}) {
            var planks = craft(test, 1, 1, java.util.List.of(new ItemStack(item)));
            test.assertTrue(planks.is(Items.OAK_PLANKS) && planks.getCount() == 4, "Vanilla log/wood plank yields differ");
        }
        set(p, 0, Items.OAK_LOG, 1);
        for (int cycle = 0; cycle < 10; cycle++) {
            convert(p, WoodFamilies.WOOD);
            test.assertTrue(p.getMainHandItem().is(Items.OAK_WOOD) && p.getMainHandItem().getCount() == 1, "One log did not produce one wood");
            var planks = craft(test, 1, 1, java.util.List.of(p.getMainHandItem().copy()));
            p.getInventory().setItem(0, planks); convert(p, WoodFamilies.LOG);
            test.assertTrue(p.getMainHandItem().is(Items.OAK_LOG) && p.getMainHandItem().getCount() == 1 && units(p) == 8,
                    "Mod/vanilla crafting cycle amplified material");
        }
        var wood = craft(test, 2, 2, java.util.List.of(new ItemStack(Items.OAK_LOG), new ItemStack(Items.OAK_LOG), new ItemStack(Items.OAK_LOG), new ItemStack(Items.OAK_LOG)));
        test.assertTrue(wood.is(Items.OAK_WOOD) && wood.getCount() == 3, "Vanilla four-log recipe no longer returns three wood");
        p.getInventory().setItem(0, wood); convert(p, WoodFamilies.LOG);
        test.assertTrue(p.getMainHandItem().getCount() == 3 && units(p) == 3 * 8, "Vanilla wood crafting plus mod conversion gained logs"); test.succeed();
    }
    @GameTest public void manualDenseMaterialsMakeOneHeldStackAndKeepExcessBaseInMainStorage(GameTestHelper test) {
        var p = player(test, true);
        Blockwheel.BUILDERS.configure(p, Blockwheel.BUILDERS.settings(p).withFeature(Feature.WOOD_TRAPDOORS, true));
        for (boolean enabled : new boolean[]{false, true}) for (var mode : new GameType[]{GameType.SURVIVAL, GameType.CREATIVE}) {
            Blockwheel.BUILDERS.configure(p, Blockwheel.BUILDERS.settings(p).withEnabled(enabled)); p.setGameMode(mode);
            for (String shape : new String[]{"blockwheel:block", "blockwheel:stairs", "blockwheel:slab", "blockwheel:fence", "blockwheel:trapdoor"}) {
                p.getInventory().clearContent(); set(p, 0, Items.OAK_LOG, 63);
                var family = Blockwheel.CONVERSIONS.experimentalCatalog().family("minecraft:oak_log").orElseThrow();
                var target = family.shape(shape).orElseThrow();
                var item = BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse(target.blockId())).asItem();
                convert(p, shape);
                int logs = (63 * 8 - 64 * target.shape().units()) / 8;
                test.assertTrue(p.getMainHandItem().is(item) && p.getMainHandItem().getCount() == 64 && count(p, item) == 64
                        && count(p, Items.OAK_LOG) == logs && p.getInventory().getItem(9).is(Items.OAK_LOG)
                        && units(p) == 63 * 8 && occupied(p) == 1, "Manual dense conversion filled extra target stacks for " + shape);
                for (int slot = 1; slot < 9; slot++) test.assertTrue(p.getInventory().getItem(slot).isEmpty(), "Base remainder used another hotbar slot");
                test.assertTrue(p.getOffhandItem().isEmpty(), "Base remainder used offhand");
            }
        }
        test.succeed();
    }
    @GameTest public void manualCapUsesRealTargetLimitsAndPreservesIncompleteWoodMaterial(GameTestHelper test) {
        var p = player(test, true); set(p, 0, BuilderTestContent.FOREST_LOG, 63); convert(p, "blockwheel:stairs");
        test.assertTrue(p.getMainHandItem().is(BuilderTestContent.FOREST_STAIRS) && p.getMainHandItem().getCount() == 16
                && count(p, BuilderTestContent.FOREST_STAIRS) == 16 && count(p, BuilderTestContent.FOREST_LOG) == 59
                && units(p) == 63 * 24, "Manual cap ignored the real 16-item target limit");
        p.getInventory().clearContent(); set(p, 0, BuilderTestContent.FOREST_WOOD, 63); convert(p, WoodFamilies.LOG);
        test.assertTrue(p.getMainHandItem().is(BuilderTestContent.FOREST_LOG) && p.getMainHandItem().getCount() == 64
                && count(p, BuilderTestContent.FOREST_LOG) == 64 && count(p, BuilderTestContent.FOREST_WOOD) == 15 && units(p) == 63 * 32,
                "Reverse dense conversion spilled extra logs or lost original wood"); test.succeed();
    }
    @GameTest public void manualCapDropsOnlyExcessBaseWhenMainStorageIsFull(GameTestHelper test) {
        var p = player(test, true); for (int slot = 9; slot < 36; slot++) set(p, slot, Items.DIRT, 64); set(p, 0, Items.OAK_LOG, 63);
        var family = Blockwheel.CONVERSIONS.experimentalCatalog().family("minecraft:oak_log").orElseThrow();
        var plan = InventoryConversion.plan(p.getInventory(), 0, family, family.shape(WoodFamilies.LOG).orElseThrow(), family.shape("blockwheel:stairs").orElseThrow());
        var drops = new ArrayList<ItemStack>(); InventoryConversion.apply(p.getInventory(), drops::add, 0, Items.OAK_STAIRS, plan);
        test.assertTrue(p.getMainHandItem().is(Items.OAK_STAIRS) && count(p, Items.OAK_STAIRS) == 64 && count(p, Items.DIRT) == 27 * 64
                && drops.size() == 1 && drops.getFirst().is(Items.OAK_LOG) && drops.getFirst().getCount() == 47,
                "Full storage produced target overflow instead of safe base overflow");
        for (int slot = 1; slot < 9; slot++) test.assertTrue(p.getInventory().getItem(slot).isEmpty(), "Overflow filled another hotbar slot");
        test.assertTrue(p.getOffhandItem().isEmpty(), "Overflow filled offhand"); test.succeed();
    }
    @GameTest public void manualResultConsolidatesMatchingStoredTargetsIntoTheSelectedHand(GameTestHelper test) {
        var p = player(test, true); set(p, 0, Items.OAK_STAIRS, 64); set(p, 9, Items.OAK_LOG, 44);
        convert(p, WoodFamilies.LOG);
        test.assertTrue(p.getMainHandItem().is(Items.OAK_LOG) && p.getMainHandItem().getCount() == 60
                && p.getInventory().getItem(9).isEmpty() && occupied(p) == 0 && units(p) == 60 * 8,
                "Converted 16 logs did not combine with the stored 44 logs"); test.succeed();
    }
    @GameTest public void manualTargetConsolidationHonorsCapacityComponentsAndStorageBoundaries(GameTestHelper test) {
        var p = player(test, true); set(p, 0, Items.OAK_STAIRS, 64); set(p, 10, Items.OAK_LOG, 35); set(p, 11, Items.OAK_LOG, 39);
        set(p, 5, Items.OAK_LOG, 7); set(p, 40, Items.OAK_LOG, 8);
        var named = new ItemStack(Items.OAK_LOG, 4); named.set(DataComponents.CUSTOM_NAME, Component.literal("Keep logs")); p.getInventory().setItem(9, named.copy());
        convert(p, WoodFamilies.LOG);
        test.assertTrue(p.getMainHandItem().getCount() == 64 && p.getInventory().getItem(10).isEmpty()
                && p.getInventory().getItem(11).getCount() == 26 && ItemStack.matches(named, p.getInventory().getItem(9))
                && p.getInventory().getItem(5).getCount() == 7 && p.getOffhandItem().getCount() == 8,
                "Consolidation exceeded a stack, consumed customized logs or crossed hotbar/offhand boundaries"); test.succeed();
    }
    @GameTest public void manualTargetConsolidationFreesStorageForIncompleteSourceRemainders(GameTestHelper test) {
        var p = player(test, true); for (int slot = 9; slot < 36; slot++) set(p, slot, Items.DIRT, 64);
        set(p, 0, Items.OAK_STAIRS, 63); set(p, 9, Items.OAK_LOG, 44); convert(p, WoodFamilies.LOG);
        test.assertTrue(p.getMainHandItem().is(Items.OAK_LOG) && p.getMainHandItem().getCount() == 59
                && p.getInventory().getItem(9).is(Items.OAK_STAIRS) && p.getInventory().getItem(9).getCount() == 3
                && count(p, Items.DIRT) == 26 * 64, "Consolidation did not free the destination for the original incomplete remainder"); test.succeed();
    }
    @GameTest public void manualTargetConsolidationUsesSmallerRealStackLimits(GameTestHelper test) {
        var p = player(test, true); set(p, 0, BuilderTestContent.FOREST_LOG, 1); set(p, 9, BuilderTestContent.FOREST_STAIRS, 14);
        convert(p, "blockwheel:stairs");
        test.assertTrue(p.getMainHandItem().is(BuilderTestContent.FOREST_STAIRS) && p.getMainHandItem().getCount() == 16
                && p.getInventory().getItem(9).getCount() == 2 && units(p) == 18 * 6,
                "Consolidation ignored the target's real 16-item limit"); test.succeed();
    }
    @GameTest public void compatiblePartialStacksAggregateWithoutMixingSpeciesOrCustomizedInputs(GameTestHelper test) {
        var p = player(test, true); set(p, 9, Items.OAK_STAIRS, 14); set(p, 10, Items.OAK_STAIRS, 14); set(p, 11, Items.SPRUCE_STAIRS, 14);
        var customized = new ItemStack(Items.OAK_STAIRS, 4); customized.set(DataComponents.CUSTOM_NAME, Component.literal("Keep")); p.getInventory().setItem(12, customized.copy());
        set(p, 5, Items.OAK_STAIRS, 60); normalize(p);
        test.assertTrue(count(p, Items.OAK_LOG) == 7 && count(p, Items.SPRUCE_LOG) == 0 && count(p, Items.SPRUCE_PLANKS) == 14
                && ItemStack.matches(customized, p.getInventory().getItem(12)) && p.getInventory().getItem(5).getCount() == 60
                && occupied(p) == 3, "Aggregation crossed protected slots, species or hotbar boundaries"); test.succeed();
    }
    @GameTest public void compactionUsesRealNonstandardStackLimits(GameTestHelper test) {
        var p = player(test, true); distribute(p, BuilderTestContent.FOREST_STAIRS, 17); normalize(p);
        test.assertTrue(count(p, BuilderTestContent.FOREST_LOG) == 0 && count(p, BuilderTestContent.FOREST_PLANKS) == 17
                && occupied(p) == 1 && units(p) == 102, "Real stack limits did not favor one plank stack over two log/change stacks");
        distribute(p, BuilderTestContent.FOREST_STAIRS, 15); normalize(p);
        test.assertTrue(count(p, BuilderTestContent.FOREST_PLANKS) == 15 && count(p, BuilderTestContent.FOREST_LOG) == 0 && occupied(p) == 1, "Small nonstandard input did not normalize to one plank stack"); test.succeed();
    }
    @GameTest public void existingLogCapacityIsUsedOnlyWhenTheQuantityRuleAllowsIt(GameTestHelper test) {
        var p = player(test, true); set(p, 9, Items.OAK_LOG, 60); set(p, 10, Items.OAK_STAIRS, 16); normalize(p);
        test.assertTrue(occupied(p) == 1 && count(p, Items.OAK_LOG) == 64, "Exact compaction did not merge into a partly filled log stack");
        p.getInventory().clearContent(); set(p, 9, Items.OAK_LOG, 10); set(p, 10, Items.OAK_STAIRS, 25); normalize(p);
        test.assertTrue(count(p, Items.OAK_LOG) == 10 && count(p, Items.OAK_STAIRS) == 0 && count(p, Items.OAK_PLANKS) == 25, "Secondary planks bypassed the conservative log rule"); test.succeed();
    }
    @GameTest public void woodNormalizationCannotIncreaseSlotsEvenForAnExactBatch(GameTestHelper test) {
        var p = player(test, true); distribute(p, BuilderTestContent.FOREST_WOOD, 63); int before = occupied(p); normalize(p);
        test.assertTrue(count(p, BuilderTestContent.FOREST_WOOD) == 63 && count(p, BuilderTestContent.FOREST_LOG) == 0 && occupied(p) == before, "Custom wood became more occupied log stacks");
        distribute(p, BuilderTestContent.FOREST_WOOD, 3); normalize(p);
        test.assertTrue(count(p, BuilderTestContent.FOREST_LOG) == 4 && occupied(p) == 1, "Explicit 4:3 override lost its three-wood batch");
        distribute(p, BuilderTestContent.FOREST_WOOD, 4); normalize(p);
        test.assertTrue(count(p, BuilderTestContent.FOREST_WOOD) == 4 && count(p, BuilderTestContent.FOREST_LOG) == 0, "Incomplete custom wood batch was split automatically");
        distribute(p, Items.OAK_WOOD, 63); normalize(p);
        test.assertTrue(count(p, Items.OAK_LOG) == 63 && occupied(p) == 1, "Default wood did not normalize one-for-one into logs"); test.succeed();
    }

    @GameTest public void aFullInventorySupportsSafeReplacementButNeverTemporaryOverflow(GameTestHelper test) {
        var p = player(test, true); for (int slot = 9; slot < 36; slot++) set(p, slot, Items.DIRT, 64);
        set(p, 23, Items.OAK_STAIRS, 24); normalize(p);
        test.assertTrue(p.getInventory().getItem(23).is(Items.OAK_LOG) && p.getInventory().getItem(23).getCount() == 6
                && count(p, Items.DIRT) == 26 * 64, "Full inventory did not replace a complete same-slot batch");
        set(p, 23, BuilderTestContent.FOREST_WOOD, 63); normalize(p);
        test.assertTrue(p.getInventory().getItem(23).is(BuilderTestContent.FOREST_WOOD) && p.getInventory().getItem(23).getCount() == 63, "Unstorable log output changed full storage"); test.succeed();
    }
    @GameTest public void oddSlabCompactionPreservesEveryPhysicalSlab(GameTestHelper test) {
        var p = player(test, true); distribute(p, Items.OAK_SLAB, 7); normalize(p);
        test.assertTrue(count(p, Items.OAK_SLAB) == 7, "Small odd slabs were split");
        distribute(p, Items.OAK_SLAB, 65); normalize(p);
        test.assertTrue(count(p, Items.OAK_LOG) == 8 && count(p, Items.OAK_SLAB) == 1 && occupied(p) == 2 && units(p) == 65, "Over-stack slabs lost their physical remainder"); test.succeed();
    }
    @GameTest public void mergingDenseWoodWinsWhenItUsesFewerSlotsThanLogs(GameTestHelper test) {
        var p = player(test, true); set(p, 9, BuilderTestContent.FOREST_WOOD, 60); set(p, 10, BuilderTestContent.FOREST_WOOD, 3); normalize(p);
        test.assertTrue(occupied(p) == 1 && count(p, BuilderTestContent.FOREST_WOOD) == 63 && count(p, BuilderTestContent.FOREST_LOG) == 0,
                "Canonical logs took priority over a denser unchanged wood stack"); test.succeed();
    }
    @GameTest public void denserCompatibleStorageWinsWhenLogsWouldOccupyMoreSlots(GameTestHelper test) {
        var p = player(test, true); set(p, 9, BuilderTestContent.FOREST_WOOD, 1);
        for (int slot = 10; slot < 14; slot++) set(p, slot, BuilderTestContent.FOREST_STAIRS, 16);
        normalize(p);
        test.assertTrue(occupied(p) == 1 && count(p, BuilderTestContent.FOREST_WOOD) == 13 && units(p) == 416,
                "Log preference prevented a lower-slot custom wood storage plan"); test.succeed();
    }

    @GameTest public void manualLogWoodConversionsKeepIncompleteSourceItems(GameTestHelper test) {
        var p = player(test, true); set(p, 0, BuilderTestContent.FOREST_LOG, 5); convert(p, WoodFamilies.WOOD);
        test.assertTrue(p.getMainHandItem().is(BuilderTestContent.FOREST_WOOD) && p.getMainHandItem().getCount() == 3 && count(p, BuilderTestContent.FOREST_LOG) == 1, "Five logs did not become three wood and one original log");
        p.getInventory().clearContent(); set(p, 0, BuilderTestContent.FOREST_WOOD, 4); convert(p, WoodFamilies.LOG);
        test.assertTrue(p.getMainHandItem().is(BuilderTestContent.FOREST_LOG) && p.getMainHandItem().getCount() == 4 && count(p, BuilderTestContent.FOREST_WOOD) == 1, "Four wood did not preserve its incomplete source");
        p.getInventory().clearContent(); set(p, 0, BuilderTestContent.FOREST_WOOD, 1); convert(p, WoodFamilies.LOG);
        test.assertTrue(p.getMainHandItem().is(BuilderTestContent.FOREST_WOOD) && p.getMainHandItem().getCount() == 1, "One wood created a fractional log"); test.succeed();
    }
    @GameTest public void experimentalOffRetainsLegacyNormalizationAndManualAvailability(GameTestHelper test) {
        var p = player(test, false); set(p, 9, Items.OAK_STAIRS, 25); normalize(p);
        test.assertTrue(count(p, Items.OAK_PLANKS) == 25 && count(p, Items.OAK_LOG) == 0, "Experimental-off changed ordinary normalization");
        set(p, 0, Items.OAK_LOG, 4); test.assertTrue(Blockwheel.CONVERSIONS.prepare(p, 0) == null, "Log conversion was available without opt-in"); test.succeed();
    }
    @GameTest public void restockAndExplicitHotbarPickUseTheRequestedShapeRatherThanStorageCompaction(GameTestHelper test) {
        var p = player(test, true); set(p, 0, Items.OAK_STAIRS, 4); set(p, 9, Items.OAK_LOG, 2); set(p, 5, Items.OAK_LOG, 8);
        Blockwheel.BUILDERS.afterUse(p, 0, new ItemStack(Items.OAK_STAIRS, 5));
        test.assertTrue(p.getMainHandItem().is(Items.OAK_STAIRS) && p.getMainHandItem().getCount() == 12 && p.getInventory().getItem(5).getCount() == 8, "Restock compacted instead of supplying stairs or consumed another hotbar slot");
        p.getInventory().clearContent(); set(p, 0, Items.DIRT, 1); set(p, 5, Items.OAK_LOG, 4);
        test.assertTrue(Blockwheel.BUILDERS.pick(p, new ItemStack(Items.OAK_STAIRS)) && p.getMainHandItem().is(Items.OAK_STAIRS)
                && p.getMainHandItem().getCount() == 16 && count(p, Items.OAK_LOG) == 0, "Explicit pick did not use eligible hotbar log material"); test.succeed();
    }
    @GameTest public void namedCarriedAndEnderLogWithdrawalsPreserveEveryTemplateReserve(GameTestHelper test) {
        var p = player(test, true); Blockwheel.BUILDERS.configure(p, Blockwheel.BUILDERS.settings(p).withFeature(Feature.SHULKER_SUPPLY, true));
        set(p, 0, Items.OAK_STAIRS, 4); p.getInventory().setItem(9, ShulkerSupplyGameTests.box(true, new ItemStack(Items.OAK_LOG, 2)));
        p.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(Items.OAK_LOG, 2), new ItemStack(Items.SPRUCE_LOG, 4)));
        Blockwheel.BUILDERS.afterUse(p, 0, new ItemStack(Items.OAK_STAIRS, 5));
        var carried = ShulkerSupplyGameTests.contents(p.getInventory().getItem(9)); var remote = ShulkerSupplyGameTests.contents(p.getEnderChestInventory().getItem(0));
        test.assertTrue(p.getMainHandItem().getCount() == 12 && carried.get(0).getCount() == 1 && remote.get(0).getCount() == 1
                && remote.get(1).is(Items.SPRUCE_LOG) && remote.get(1).getCount() == 4, "Log withdrawals changed template identities/reserves or species"); test.succeed();
    }
    @GameTest public void opportunisticLogPackingLeavesTheOriginalStairRemainder(GameTestHelper test) {
        var p = player(test, true); set(p, 9, Items.OAK_STAIRS, 25);
        p.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(Items.OAK_LOG, 12), ItemStack.EMPTY));
        test.assertTrue(Blockwheel.BUILDERS.fillTemplates(p, p.containerMenu.containerId, true), "Log template rejected an eligible partial batch");
        var items = ShulkerSupplyGameTests.contents(p.getEnderChestInventory().getItem(0));
        test.assertTrue(items.get(0).is(Items.OAK_LOG) && items.get(0).getCount() == 18 && items.get(1).isEmpty()
                && p.getInventory().getItem(9).is(Items.OAK_STAIRS) && p.getInventory().getItem(9).getCount() == 1, "Packing created a remainder template or changed the unaccepted stair"); test.succeed();
    }
    @GameTest public void logTemplatesTakePriorityAndOnlyThenFillExistingSecondarySlots(GameTestHelper test) {
        var p = player(test, true); set(p, 9, Items.OAK_STAIRS, 25);
        p.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(Items.OAK_STAIRS, 12), new ItemStack(Items.OAK_LOG, 12)));
        Blockwheel.BUILDERS.fillTemplates(p, p.containerMenu.containerId, true); var items = ShulkerSupplyGameTests.contents(p.getEnderChestInventory().getItem(0));
        test.assertTrue(items.get(0).is(Items.OAK_STAIRS) && items.get(0).getCount() == 13 && items.get(1).is(Items.OAK_LOG)
                && items.get(1).getCount() == 18 && p.getInventory().getItem(9).isEmpty(), "An exact stair template took priority over log packing"); test.succeed();
    }
    @GameTest public void partialLogCapacityLeavesUnacceptedMaterialSafeAcrossMultipleBoxes(GameTestHelper test) {
        var p = player(test, true); set(p, 9, Items.OAK_STAIRS, 25);
        p.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(Items.OAK_LOG, 63)));
        p.getEnderChestInventory().setItem(1, ShulkerSupplyGameTests.box(true, new ItemStack(Items.OAK_LOG, 62)));
        Blockwheel.BUILDERS.fillTemplates(p, p.containerMenu.containerId, true);
        test.assertTrue(p.getInventory().getItem(9).is(Items.OAK_STAIRS) && p.getInventory().getItem(9).getCount() == 13
                && ShulkerSupplyGameTests.contents(p.getEnderChestInventory().getItem(0)).get(0).getCount() == 64
                && ShulkerSupplyGameTests.contents(p.getEnderChestInventory().getItem(1)).get(0).getCount() == 64, "Partial multi-box packing overfilled a log slot or lost the original remainder"); test.succeed();
    }
    @GameTest public void woodPackingRequiresEnoughRoomForItsWholeLosslessOutputBatch(GameTestHelper test) {
        var p = player(test, true); set(p, 9, BuilderTestContent.FOREST_WOOD, 3);
        p.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(BuilderTestContent.FOREST_LOG, 61)));
        test.assertTrue(!Blockwheel.BUILDERS.fillTemplates(p, p.containerMenu.containerId, true) && count(p, BuilderTestContent.FOREST_WOOD) == 3, "Three wood were split to fit only three log spaces");
        p.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(BuilderTestContent.FOREST_LOG, 60)));
        test.assertTrue(Blockwheel.BUILDERS.fillTemplates(p, p.containerMenu.containerId, true) && count(p, BuilderTestContent.FOREST_WOOD) == 0
                && ShulkerSupplyGameTests.contents(p.getEnderChestInventory().getItem(0)).get(0).getCount() == 64, "Four available log spaces did not accept the exact wood batch"); test.succeed();
    }
    @GameTest public void carriedLogRefillDoesNotGrantRemoteEnderPermission(GameTestHelper test) {
        var p = player(test, true); Blockwheel.BUILDERS.configure(p, Blockwheel.BUILDERS.settings(p).withFeature(Feature.ENDER_ACCESS, false));
        set(p, 9, Items.OAK_STAIRS, 25); p.getInventory().setItem(15, ShulkerSupplyGameTests.box(true, new ItemStack(Items.OAK_LOG, 12)));
        var remote = ShulkerSupplyGameTests.box(true, new ItemStack(Items.OAK_LOG, 12)); p.getEnderChestInventory().setItem(0, remote.copy());
        test.assertTrue(Blockwheel.BUILDERS.fillTemplates(p, p.containerMenu.containerId, true)
                && ShulkerSupplyGameTests.contents(p.getInventory().getItem(15)).get(0).getCount() == 18
                && ItemStack.matches(remote, p.getEnderChestInventory().getItem(0)) && count(p, Items.OAK_STAIRS) == 1, "Carried refill bypassed ender permission or failed to pack logs"); test.succeed();
    }
    @GameTest public void strippedBlocksAndShelvesHaveNoFamilyUnderEitherPolicy(GameTestHelper test) {
        var p = player(test, true);
        for (var block : AxeItemAccessor.blockwheel$strippables().values()) {
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            test.assertTrue(Blockwheel.CONVERSIONS.catalog().family(id).isEmpty() && Blockwheel.CONVERSIONS.experimentalCatalog().family(id).isEmpty(), "Stripped block acquired a transformation family");
            set(p, 0, block.asItem(), 4); test.assertTrue(Blockwheel.CONVERSIONS.prepare(p, 0) == null, "Stripped block opened a radial");
        }
        for (String id : new String[]{"minecraft:oak_shelf", "minecraft:crimson_shelf", "minecraft:stripped_bamboo_block"})
            test.assertTrue(Blockwheel.CONVERSIONS.experimentalCatalog().family(id).isEmpty(), "Shelf or stripped special case entered an experimental family");
        p.getInventory().clearContent(); set(p, 9, Items.STRIPPED_OAK_WOOD, 4); normalize(p);
        test.assertTrue(count(p, Items.STRIPPED_OAK_WOOD) == 4 && count(p, Items.OAK_LOG) == 0, "Stripped storage was normalized");
        test.succeed();
    }
    @GameTest public void strippedSuppliesCannotFeedOtherShapesButExactStrippedPicksRemainValid(GameTestHelper test) {
        var p = player(test, true); Blockwheel.BUILDERS.configure(p, Blockwheel.BUILDERS.settings(p).withFeature(Feature.SHULKER_SUPPLY, true));
        set(p, 0, Items.OAK_STAIRS, 4); set(p, 10, Items.STRIPPED_OAK_LOG, 64);
        p.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(Items.STRIPPED_OAK_LOG, 10)));
        Blockwheel.BUILDERS.afterUse(p, 0, new ItemStack(Items.OAK_STAIRS, 5));
        test.assertTrue(p.getMainHandItem().getCount() == 4 && count(p, Items.STRIPPED_OAK_LOG) == 64
                && ShulkerSupplyGameTests.contents(p.getEnderChestInventory().getItem(0)).get(0).getCount() == 10, "Stripped material fed family-aware restocking");
        p.getInventory().clearContent(); set(p, 0, Items.DIRT, 1);
        test.assertTrue(Blockwheel.BUILDERS.pick(p, new ItemStack(Items.STRIPPED_OAK_LOG)) && p.getMainHandItem().is(Items.STRIPPED_OAK_LOG)
                && p.getMainHandItem().getCount() == 9, "Standalone exact stripped supply was broken"); test.succeed();
    }
    @GameTest public void pickupUsesSecondaryPlanksAndCanCompactWithoutAnEmptySourceSlot(GameTestHelper test) {
        var p = player(test, true); Blockwheel.BUILDERS.configure(p, Blockwheel.BUILDERS.settings(p).withFeature(Feature.PICKUP, true));
        var incoming = new ItemStack(Items.OAK_STAIRS, 25);
        test.assertTrue(Boolean.TRUE.equals(Blockwheel.BUILDERS.pickup(p, incoming)) && incoming.isEmpty() && count(p, Items.OAK_PLANKS) == 25
                && count(p, Items.OAK_LOG) == 0 && occupied(p) == 1, "Pickup failed to use a single secondary plank stack");
        p.getInventory().clearContent(); for (int slot = 9; slot < 36; slot++) set(p, slot, Items.DIRT, 64); set(p, 23, Items.OAK_LOG, 63);
        incoming = new ItemStack(Items.OAK_STAIRS, 4);
        test.assertTrue(Boolean.TRUE.equals(Blockwheel.BUILDERS.pickup(p, incoming)) && incoming.isEmpty() && p.getInventory().getItem(23).getCount() == 64
                && count(p, Items.DIRT) == 26 * 64, "Pickup needed a temporary empty source slot to fill an existing log"); test.succeed();
    }
    @GameTest public void experimentalDestinationMergingChoosesAnActualSlotReduction(GameTestHelper test) {
        var p = player(test, true); set(p, 0, Items.OAK_STAIRS, 24); set(p, 14, Items.OAK_LOG, 10);
        Blockwheel.BUILDERS.configure(p, Blockwheel.BUILDERS.settings(p).withFeature(Feature.NORMALIZE, true));
        p.containerMenu.clicked(36, 0, ContainerInput.PICKUP, p); p.containerMenu.clicked(23, 0, ContainerInput.PICKUP, p);
        test.assertTrue(p.getInventory().getItem(14).is(Items.OAK_LOG) && p.getInventory().getItem(14).getCount() == 16
                && p.getInventory().getItem(23).isEmpty(), "Compaction kept an unnecessary destination instead of freeing its slot"); test.succeed();
    }
    @GameTest public void randomizedNativeNormalizationConservesValueAndNeverIncreasesOccupiedSlots(GameTestHelper test) {
        var p = player(test, true); var random = new Random(280); var shapes = new Item[]{Items.OAK_STAIRS, Items.OAK_SLAB, Items.OAK_PLANKS, Items.OAK_LOG, Items.OAK_WOOD};
        for (int sample = 0; sample < 350; sample++) {
            p.getInventory().clearContent();
            for (int slot = 9; slot < 36; slot++) if (random.nextInt(3) != 0) set(p, slot, shapes[random.nextInt(shapes.length)], random.nextInt(64) + 1);
            long before = units(p); int slots = occupied(p); normalize(p);
            test.assertTrue(units(p) == before && occupied(p) <= slots, "Randomized compaction violated value/slot invariant");
            for (int slot = 9; slot < 36; slot++) test.assertTrue(p.getInventory().getItem(slot).getCount() <= p.getInventory().getItem(slot).getMaxStackSize(), "Compaction created an overstack");
        }
        test.succeed();
    }
    @GameTest public void randomizedPackingPreservesValueAndEveryOriginalTemplateIdentity(GameTestHelper test) {
        var p = player(test, true); var random = new Random(281);
        var sources = new Item[]{Items.OAK_STAIRS, Items.OAK_SLAB, Items.OAK_PLANKS, Items.OAK_LOG, Items.OAK_WOOD};
        for (int sample = 0; sample < 120; sample++) {
            p.getInventory().clearContent(); p.getEnderChestInventory().clearContent();
            for (int slot = 9; slot < 15; slot++) set(p, slot, sources[random.nextInt(sources.length)], random.nextInt(64) + 1);
            var before = ShulkerSupplyGameTests.box(true, new ItemStack(Items.OAK_LOG, random.nextInt(64) + 1),
                    new ItemStack(Items.OAK_STAIRS, random.nextInt(64) + 1), new ItemStack(Items.SPRUCE_LOG, 11), ItemStack.EMPTY);
            p.getEnderChestInventory().setItem(0, before.copy());
            long total = units(p) + ShulkerSupplyGameTests.contents(before).stream().mapToLong(stack -> {
                var material = Blockwheel.BUILDERS.materials(p).resolve(stack); return material == null ? 0 : (long) stack.getCount() * material.form().units();
            }).sum();
            Blockwheel.BUILDERS.fillTemplates(p, p.containerMenu.containerId, true);
            var after = p.getEnderChestInventory().getItem(0); var oldItems = ShulkerSupplyGameTests.contents(before); var items = ShulkerSupplyGameTests.contents(after);
            long finalTotal = units(p);
            for (int inner = 0; inner < 27; inner++) {
                var old = oldItems.get(inner); var current = items.get(inner);
                test.assertTrue(old.isEmpty() ? current.isEmpty() : ItemStack.isSameItemSameComponents(old, current)
                        && current.getCount() >= old.getCount() && current.getCount() <= current.getMaxStackSize(), "Packing changed a template identity/capacity");
                var material = Blockwheel.BUILDERS.materials(p).resolve(current);
                if (material != null) finalTotal += (long) current.getCount() * material.form().units();
            }
            test.assertTrue(total == finalTotal && items.get(2).getCount() == 11, "Randomized packing lost material or mixed species");
        }
        test.succeed();
    }
    @GameTest public void materialProjectionRejectsForbiddenMembersEvenInAnExplicitCatalog(GameTestHelper test) {
        var shape = new dev.blockwheel.core.Shape("blockwheel:block", "base", 2, 0, true, java.util.List.of(), java.util.List.of(), java.util.List.of());
        var other = new dev.blockwheel.core.Shape("test:other", "other", 2, 1, false, java.util.List.of("_other"), java.util.List.of(), java.util.List.of());
        var unsafe = dev.blockwheel.core.FamilyCatalog.of(java.util.List.of(new dev.blockwheel.core.FamilyCatalog.Family("test:unsafe", java.util.List.of(
                new dev.blockwheel.core.FamilyCatalog.Member(shape, "minecraft:oak_planks"), new dev.blockwheel.core.FamilyCatalog.Member(other, "minecraft:stripped_oak_log")))));
        test.assertTrue(new BlockMaterials(unsafe).resolve(Items.STRIPPED_OAK_LOG) == null, "Explicit catalog bypassed stripped-source protection"); test.succeed();
    }
}
