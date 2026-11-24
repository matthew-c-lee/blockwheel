package dev.blockwheel;

import dev.blockwheel.core.BuilderSettings;
import dev.blockwheel.core.BuilderSettings.Feature;
import dev.blockwheel.core.WoodFamilies;
import dev.blockwheel.network.Payloads;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntityTypes;

import java.util.List;

/** Native recipes, registry discovery and all material consumers share the new sign definition. */
public final class SignGameTests {
    private ServerPlayer player(GameTestHelper test, boolean wood, boolean builder) {
        var p = test.makeMockServerPlayerInLevel(); p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent(); p.getEnderChestInventory().clearContent(); p.getInventory().setSelectedSlot(0);
        Blockwheel.BUILDERS.configure(p, BuilderSettings.defaults().withEnabled(builder).withFeature(Feature.EXPERIMENTAL_WOOD, wood)
                .withFeature(Feature.PICKUP, false).withFeature(Feature.NORMALIZE, false));
        return p;
    }
    private void set(ServerPlayer p, int slot, Item item, int count) { p.getInventory().setItem(slot, new ItemStack(item, count)); }
    private void convert(ServerPlayer p, String shape) {
        var wheel = Blockwheel.CONVERSIONS.prepare(p, 0); if (wheel == null) throw new AssertionError("Missing sign wheel");
        Blockwheel.CONVERSIONS.convert(p, new Payloads.Convert(wheel.token(), shape));
    }
    private void normalize(ServerPlayer p) {
        var planner = Blockwheel.BUILDERS.planner(p); var plan = planner.normalize(p.getInventory(), planner.storageFamilies(p.getInventory()));
        if (!plan.transaction().commit()) throw new AssertionError("Sign normalization did not commit");
    }
    @GameTest public void allVanillaSignsHaveTheRealTwoPlankRecipeAndNativeStackLimit(GameTestHelper test) {
        int found = 0;
        for (var block : BuiltInRegistries.BLOCK) {
            if (block.builtInRegistryHolder().tags().noneMatch(tag -> tag.location().toString().equals("minecraft:standing_signs"))) continue;
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            var family = Blockwheel.CONVERSIONS.catalog().family(id).orElseThrow();
            var sign = family.shape("blockwheel:sign").orElseThrow(); var planks = family.shape("blockwheel:block").orElseThrow();
            test.assertTrue(sign.blockId().equals(id) && sign.shape().units() == 4 && planks.shape().units() == 2
                    && sign.shape().baseTags().equals(List.of("minecraft:planks")), "Sign discovered outside its plank family");
            var item = new ItemStack(BuiltInRegistries.BLOCK.getValue(Identifier.parse(planks.blockId())).asItem());
            var input = CraftingInput.of(3, 3, List.of(item.copy(), item.copy(), item.copy(), item.copy(), item.copy(), item.copy(), ItemStack.EMPTY, new ItemStack(Items.STICK), ItemStack.EMPTY));
            var result = test.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, test.getLevel())
                    .orElseThrow().value().assemble(input);
            test.assertTrue(result.is(block.asItem()) && result.getCount() == 3 && result.getMaxStackSize() == 16,
                    "Sign no longer matches six planks plus one stick for three native signs");
            found++;
        }
        test.assertTrue(found >= 12, "Missing native wooden sign types"); test.succeed();
    }
    @GameTest public void ordinarySignsAreAvailableWithBuilderAndExperimentalWoodOff(GameTestHelper test) {
        var p = player(test, false, false); set(p, 0, Items.OAK_PLANKS, 6); convert(p, "blockwheel:sign");
        test.assertTrue(p.getMainHandItem().is(Items.OAK_SIGN) && p.getMainHandItem().getCount() == 3, "Six planks did not give three signs");
        convert(p, "blockwheel:block");
        test.assertTrue(p.getMainHandItem().is(Items.OAK_PLANKS) && p.getMainHandItem().getCount() == 6
                && p.getInventory().countItem(Items.STICK) == 0, "Sign reverse conversion changed wood value or fabricated sticks"); test.succeed();
    }
    @GameTest public void signsUseSixteenItemCapsAndKeepUnconvertedRawMaterial(GameTestHelper test) {
        var p = player(test, false, false); set(p, 0, Items.OAK_PLANKS, 64); convert(p, "blockwheel:sign");
        test.assertTrue(p.getMainHandItem().is(Items.OAK_SIGN) && p.getMainHandItem().getCount() == 16
                && p.getInventory().countItem(Items.OAK_SIGN) == 16 && p.getInventory().countItem(Items.OAK_PLANKS) == 32,
                "Sign conversion ignored its real stack cap or lost planks");
        p.getInventory().clearContent(); set(p, 0, Items.OAK_SIGN, 16); convert(p, "blockwheel:slab");
        test.assertTrue(p.getMainHandItem().is(Items.OAK_SLAB) && p.getMainHandItem().getCount() == 64, "Sign/slab material ratio was not four to one"); test.succeed();
    }
    @GameTest public void newSignShapeDoesNotReplaceCanonicalPlankChange(GameTestHelper test) {
        var p = player(test, true, false); set(p, 0, Items.OAK_STAIRS, 63); convert(p, "blockwheel:slab");
        test.assertTrue(p.getMainHandItem().is(Items.OAK_SLAB) && p.getMainHandItem().getCount() == 64
                && p.getInventory().countItem(Items.OAK_LOG) == 7 && p.getInventory().countItem(Items.OAK_PLANKS) == 3
                && p.getInventory().countItem(Items.OAK_SIGN) == 0, "Capped change preferred signs over secondary planks"); test.succeed();
    }
    @GameTest public void incompleteSignBatchesUseOnlyTheMinimalMatchingDonorWithBuilderOff(GameTestHelper test) {
        var p = player(test, false, false); set(p, 0, Items.OAK_PLANKS, 1); set(p, 9, Items.OAK_PLANKS, 7); convert(p, "blockwheel:sign");
        test.assertTrue(p.getMainHandItem().is(Items.OAK_SIGN) && p.getMainHandItem().getCount() == 1 && p.getInventory().getItem(9).getCount() == 6,
                "Builder-off signs swept source material instead of completing one batch"); test.succeed();
    }
    @GameTest public void experimentalLogsAndWoodUseTheSameSignUnitsAndPreserveOddSigns(GameTestHelper test) {
        var p = player(test, true, false); set(p, 0, Items.OAK_LOG, 1); convert(p, "blockwheel:sign");
        test.assertTrue(p.getMainHandItem().is(Items.OAK_SIGN) && p.getMainHandItem().getCount() == 2, "One log did not give two signs");
        convert(p, WoodFamilies.WOOD); test.assertTrue(p.getMainHandItem().is(Items.OAK_WOOD) && p.getMainHandItem().getCount() == 1, "Two signs lost wood material");
        p.getInventory().clearContent(); set(p, 0, Items.OAK_SIGN, 3); convert(p, WoodFamilies.LOG);
        test.assertTrue(p.getMainHandItem().is(Items.OAK_LOG) && p.getMainHandItem().getCount() == 1 && p.getInventory().countItem(Items.OAK_SIGN) == 1,
                "An odd sign became a fractional log"); test.succeed();
    }
    @GameTest public void normalizationPicksPlanksOrCompleteLogsWithoutIncreasingSlots(GameTestHelper test) {
        var p = player(test, false, true); set(p, 9, Items.OAK_SIGN, 3); normalize(p);
        test.assertTrue(p.getInventory().getItem(9).is(Items.OAK_PLANKS) && p.getInventory().getItem(9).getCount() == 6, "Sign normalization did not use two planks");
        p.getInventory().clearContent(); Blockwheel.BUILDERS.configure(p, Blockwheel.BUILDERS.settings(p).withFeature(Feature.EXPERIMENTAL_WOOD, true));
        set(p, 9, Items.OAK_SIGN, 3); normalize(p);
        test.assertTrue(p.getInventory().getItem(9).is(Items.OAK_PLANKS) && p.getInventory().getItem(9).getCount() == 6, "Small signs split into log and remainder");
        p.getInventory().clearContent(); set(p, 9, Items.OAK_SIGN, 4); normalize(p);
        test.assertTrue(p.getInventory().getItem(9).is(Items.OAK_LOG) && p.getInventory().getItem(9).getCount() == 2, "Complete sign value did not compact to logs"); test.succeed();
    }
    @GameTest public void signRestockUsesMainPlanksButPreservesOtherSpeciesAndHotbar(GameTestHelper test) {
        var p = player(test, false, true); set(p, 0, Items.OAK_SIGN, 1); set(p, 9, Items.OAK_PLANKS, 32); set(p, 10, Items.SPRUCE_PLANKS, 20); set(p, 5, Items.OAK_PLANKS, 10);
        var plan = Blockwheel.BUILDERS.planner(p).restock(p.getInventory(), 0, Items.OAK_SIGN);
        test.assertTrue(plan != null && plan.transaction().commit() && p.getMainHandItem().getCount() == 16
                && p.getInventory().getItem(9).getCount() == 2 && p.getInventory().getItem(10).getCount() == 20 && p.getInventory().getItem(5).getCount() == 10,
                "Sign restock crossed its cap, species or hotbar source boundary"); test.succeed();
    }
    @GameTest public void explicitSignPickCanUseTheHeldUnstrippedLog(GameTestHelper test) {
        var p = player(test, true, true); set(p, 0, Items.OAK_LOG, 1);
        test.assertTrue(Blockwheel.BUILDERS.pick(p, new ItemStack(Items.OAK_SIGN)) && p.getMainHandItem().is(Items.OAK_SIGN)
                && p.getMainHandItem().getCount() == 2 && p.getInventory().countItem(Items.OAK_LOG) == 0, "Family-aware sign pick ignored its held source"); test.succeed();
    }
    @GameTest public void remoteSignSupplyKeepsTheNamedLogReserve(GameTestHelper test) {
        var p = player(test, true, true); Blockwheel.BUILDERS.configure(p, Blockwheel.BUILDERS.settings(p).withFeature(Feature.SHULKER_SUPPLY, true));
        set(p, 0, Items.OAK_SIGN, 1); p.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(Items.OAK_LOG, 4)));
        var plan = Blockwheel.BUILDERS.planner(p).restock(p.getInventory(), p.getEnderChestInventory(), Blockwheel.BUILDERS.supplyPolicy(p, false, false), 0, Items.OAK_SIGN);
        test.assertTrue(plan != null && plan.transaction().commit() && p.getMainHandItem().getCount() == 7
                && ShulkerSupplyGameTests.contents(p.getEnderChestInventory().getItem(0)).get(0).getCount() == 1, "Sign supply emptied a protected log template"); test.succeed();
    }
    @GameTest public void namedSignRefillRespectsMainOnlyScopeAndRealCapacity(GameTestHelper test) {
        var p = player(test, false, true); set(p, 0, Items.OAK_PLANKS, 6); set(p, 9, Items.OAK_PLANKS, 30);
        p.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(Items.OAK_SIGN, 1)));
        test.assertTrue(Blockwheel.BUILDERS.fillTemplates(p, p.containerMenu.containerId) && p.getMainHandItem().getCount() == 6 && p.getInventory().getItem(9).isEmpty()
                && ShulkerSupplyGameTests.contents(p.getEnderChestInventory().getItem(0)).get(0).getCount() == 16, "Sign refill overfilled its template or consumed the hotbar"); test.succeed();
    }
    @GameTest public void signBlockEntityDataAndHangingSignsStayOutsideConversion(GameTestHelper test) {
        var p = player(test, true, true); var sign = new ItemStack(Items.OAK_SIGN, 2); var data = new CompoundTag(); data.putString("marker", "Keep sign text");
        sign.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(BlockEntityTypes.SIGN, data)); p.getInventory().setItem(0, sign.copy());
        test.assertTrue(Blockwheel.CONVERSIONS.prepare(p, 0) == null, "Sign block-entity data could be converted away");
        p.getInventory().setItem(0, ItemStack.EMPTY); p.getInventory().setItem(9, sign.copy()); normalize(p);
        test.assertTrue(ItemStack.matches(sign, p.getInventory().getItem(9)), "Normalization discarded sign data");
        test.assertTrue(Blockwheel.BUILDERS.materials(p).resolve(Items.OAK_HANGING_SIGN) == null
                && Blockwheel.CONVERSIONS.catalog().family("minecraft:oak_wall_sign").isEmpty()
                && Blockwheel.BUILDERS.materials(p).resolve(Items.STRIPPED_OAK_LOG) == null, "Hanging, wall or stripped variants entered the sign family"); test.succeed();
    }
}
