package dev.blockwheel.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class WoodFamiliesTest {
    private Shape shape(String id, int value, boolean base) {
        return new Shape("blockwheel:" + id, "shape." + id, value, 0, base, base ? List.of() : List.of("_" + id), List.of(), List.of());
    }
    private FamilyCatalog original() {
        return FamilyCatalog.of(List.of(new FamilyCatalog.Family("test:oak_planks", List.of(
                new FamilyCatalog.Member(shape("block", 2, true), "test:oak_planks"),
                new FamilyCatalog.Member(shape("stairs", 2, false), "test:oak_stairs"),
                new FamilyCatalog.Member(shape("slab", 1, false), "test:oak_slab"),
                new FamilyCatalog.Member(shape("fence", 2, false), "test:oak_fence"),
                new FamilyCatalog.Member(shape("trapdoor", 6, false), "test:oak_trapdoor")))));
    }
    private FamilyCatalog expanded() {
        return WoodFamilies.expand(original(), List.of(new WoodFamilies.Relation("test:oak_planks", "test:oak_log", "test:oak_wood", 4, 4, 3)));
    }
    @Test void expandedViewUsesExactValuesAndLeavesTheOrdinaryCatalogUntouched() {
        var ordinary = original(); var expanded = WoodFamilies.expand(ordinary, List.of(new WoodFamilies.Relation("test:oak_planks", "test:oak_log", "test:oak_wood", 4, 4, 3)));
        var family = expanded.family("test:oak_log").orElseThrow();
        assertEquals(List.of(24, 6, 6, 3, 6, 18, 32).stream().sorted().toList(), family.members().stream().map(member -> member.shape().units()).sorted().toList());
        assertEquals(1, family.members().stream().filter(member -> member.shape().base()).count());
        assertTrue(family.shape(WoodFamilies.LOG).orElseThrow().shape().base());
        assertEquals(2, ordinary.family("test:oak_planks").orElseThrow().shape("blockwheel:block").orElseThrow().shape().units());
        assertTrue(ordinary.family("test:oak_log").isEmpty());
    }
    @Test void manualBatchesPreserveOriginalRemaindersAndRespectBothLogWoodDirections() {
        var family = expanded().family("test:oak_log").orElseThrow();
        for (var example : List.of(new int[]{6, 24, 5, 1, 1}, new int[]{24, 32, 5, 3, 1}, new int[]{32, 24, 4, 4, 1}, new int[]{32, 24, 1, 0, 1}, new int[]{32, 6, 3, 16, 0})) {
            var plan = ConversionPlan.calculate(example[2], example[0], example[1]); assertEquals(example[3], plan.produced()); assertEquals(example[4], plan.remainder());
        }
        var wood = family.shape(WoodFamilies.WOOD).orElseThrow(); var logs = family.shape(WoodFamilies.LOG).orElseThrow();
        assertEquals(4, ManualConversionPlan.calculate(4, wood, logs, logs, family.members(), member -> member.shape().units(), Integer.MAX_VALUE).produced());
    }
    @Test void randomizedManualConversionsConserveAllPhysicalMaterialIncludingCappedSlabsAndDonors() {
        var family = expanded().family("test:oak_log").orElseThrow(); var base = family.shape(WoodFamilies.LOG).orElseThrow();
        var random = new java.util.Random(280);
        for (int iteration = 0; iteration < 6000; iteration++) {
            var source = family.members().get(random.nextInt(family.members().size())); var target = family.members().get(random.nextInt(family.members().size()));
            int count = random.nextInt(257), donors = random.nextInt(8), limit = ManualConversionPlan.outputLimit(target.shape().id(), random.nextBoolean() ? 16 : 64);
            var plan = ManualConversionPlan.calculate(count, source, target, base, family.members(), member -> member.shape().units(), limit, donors);
            long after = (long) plan.produced() * target.shape().units() + (long) plan.remainder() * source.shape().units()
                    + plan.stored().stream().mapToLong(part -> (long) part.count() * part.form().shape().units()).sum();
            assertEquals((long) (count + plan.borrowed()) * source.shape().units(), after);
            if (!source.equals(target)) assertTrue(plan.produced() <= limit);
        }
    }
    @Test void oneToOneWoodUsesTheSamePlankValueAsLogsAndDoesNotAmplifyCraftingCycles() {
        var catalog = WoodFamilies.expand(original(), List.of(new WoodFamilies.Relation("test:oak_planks", "test:oak_log", "test:oak_wood", 4, 1, 1)));
        var family = catalog.family("test:oak_log").orElseThrow();
        var log = family.shape(WoodFamilies.LOG).orElseThrow(); var wood = family.shape(WoodFamilies.WOOD).orElseThrow();
        var planks = family.shape(WoodFamilies.PLANKS).orElseThrow();
        assertEquals(8, log.shape().units()); assertEquals(log.shape().units(), wood.shape().units()); assertEquals(2, planks.shape().units());
        for (int count = 1; count <= 64; count++) {
            var intoWood = ConversionPlan.calculate(count, log.shape().units(), wood.shape().units());
            assertEquals(count, intoWood.produced()); assertEquals(0, intoWood.remainder());
            var intoPlanks = ConversionPlan.calculate(intoWood.produced(), wood.shape().units(), planks.shape().units());
            assertEquals(count * 4, intoPlanks.produced());
            assertEquals(count, ConversionPlan.calculate(intoPlanks.produced(), planks.shape().units(), log.shape().units()).produced());
            assertEquals(count / 4 * 3, ConversionPlan.calculate(count / 4 * 3, wood.shape().units(), log.shape().units()).produced());
        }
    }
    @Test void independentOptionalViewsKeepTheirOwnCanonicalUnitsAndExcludeStrippedRelations() {
        var views = new FamilyViews(original(), expanded());
        assertTrue(views.get(BuilderSettings.defaults()).family("test:oak_log").isEmpty());
        var enabled = BuilderSettings.defaults().withFeature(BuilderSettings.Feature.EXPERIMENTAL_WOOD, true);
        assertTrue(views.get(enabled).family("test:oak_log").orElseThrow().shape("blockwheel:trapdoor").isEmpty());
        assertTrue(views.get(enabled.withFeature(BuilderSettings.Feature.WOOD_TRAPDOORS, true)).family("test:oak_trapdoor").isPresent());
        assertTrue(WoodFamilies.expand(original(), List.of(new WoodFamilies.Relation("test:oak_planks", "test:stripped_oak_log", null, 4, 4, 3))).family("test:stripped_oak_log").isEmpty());
        assertTrue(WoodFamilies.stripped("example:cedar_log_stripped"));
    }
    @Test void compactionEligibilityUsesActualStackLimitsAndAppropriateLosslessBatchSizes() {
        for (int count : new int[]{4, 24, 28, 64, 65, 68, 128}) assertTrue(StorageCompaction.eligible(List.of(new StorageCompaction.Stack(count, 6, 64)), 24));
        for (int count : new int[]{5, 17, 25, 63}) assertFalse(StorageCompaction.eligible(List.of(new StorageCompaction.Stack(count, 6, 64)), 24));
        assertTrue(StorageCompaction.eligible(List.of(new StorageCompaction.Stack(14, 6, 64), new StorageCompaction.Stack(14, 6, 64)), 24));
        assertTrue(StorageCompaction.eligible(List.of(new StorageCompaction.Stack(17, 6, 16)), 24));
        assertFalse(StorageCompaction.eligible(List.of(new StorageCompaction.Stack(15, 6, 16)), 24));
        assertTrue(StorageCompaction.eligible(List.of(new StorageCompaction.Stack(3, 32, 64)), 24));
        assertFalse(StorageCompaction.eligible(List.of(new StorageCompaction.Stack(4, 32, 64)), 24));
        assertFalse(StorageCompaction.eligible(List.of(new StorageCompaction.Stack(7, 3, 64)), 24));
        assertTrue(StorageCompaction.eligible(List.of(new StorageCompaction.Stack(65, 3, 64)), 24));
    }
}
