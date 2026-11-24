package dev.blockwheel.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ManualConversionPlanTest {
    record Form(String name, int units) {}
    private final Form base = new Form("base", 2), stair = new Form("stair", 2), slab = new Form("slab", 1), floor = new Form("floor", 3);
    private final List<Form> forms = List.of(base, stair, slab, floor);
    @Test void cappedWoodChangeUsesSecondaryPlanksInsteadOfCreatingSigns() {
        var log = new Form("log", 8); var sign = new Form("sign", 4); var shapes = List.of(log, base, stair, slab, sign);
        var plan = ManualConversionPlan.calculate(63, stair, slab, log, shapes, Form::units, 64, 0, base);
        assertEquals(64, plan.produced());
        assertEquals(List.of(new MaterialComposition.Part<>(log, 7), new MaterialComposition.Part<>(base, 3)), plan.stored());
    }

    @Test void manualSelectionConsolidatesCompatibleFamilyShapesIntoOneStack() {
        var log = new Form("log", 8); var wood = new Form("wood", 8);
        var shapes = List.of(base, stair, slab, log, wood);
        var plan = ManualConversionPlan.calculate(64, base, wood, log, shapes, Form::units, 64,
                List.of(new MaterialWithdrawal.Supply<>(log, 10, 8)));
        assertEquals(26, plan.produced()); assertEquals(0, plan.remainder());
        assertEquals(74, plan.inputCount());
        assertEquals(List.of(new MaterialComposition.Part<>(log, 10)), plan.withdrawals());
        var mixed = ManualConversionPlan.calculate(1, base, log, log, shapes, Form::units, 64,
                List.of(new MaterialWithdrawal.Supply<>(stair, 3, 2)));
        assertEquals(1, mixed.produced()); assertEquals(1, mixed.consumed()); assertEquals(0, mixed.remainder());
        var capped = ManualConversionPlan.calculate(64, base, wood, log, shapes, Form::units, 64,
                List.of(new MaterialWithdrawal.Supply<>(log, 60, 8)));
        assertEquals(64, capped.produced()); assertEquals(48, capped.withdrawals().getFirst().count());
    }

    @Test void consolidationUsesExactTargetsFirstAndLeavesUnusableDonorFractionsIntact() {
        var log = new Form("log", 8); var wood = new Form("wood", 8);
        var shapes = List.of(base, stair, slab, log, wood);
        var plan = ManualConversionPlan.calculate(64, base, wood, log, shapes, Form::units, 64,
                List.of(new MaterialWithdrawal.Supply<>(log, 60, 8), new MaterialWithdrawal.Supply<>(wood, 48, 8)));
        assertEquals(List.of(new MaterialComposition.Part<>(wood, 48)), plan.withdrawals());
        var odd = ManualConversionPlan.calculate(4, stair, log, log, shapes, Form::units, 64,
                List.of(new MaterialWithdrawal.Supply<>(slab, 15, 1)));
        assertEquals(2, odd.produced()); assertEquals(8, odd.withdrawals().getFirst().count());
        var incomplete = ManualConversionPlan.calculate(1, stair, log, log, shapes, Form::units, 64,
                List.of(new MaterialWithdrawal.Supply<>(slab, 5, 1)));
        assertEquals(0, incomplete.produced()); assertEquals(1, incomplete.remainder()); assertTrue(incomplete.withdrawals().isEmpty());
    }

    @Test void mixedInventoryConsolidationConservesEveryUnitWithBoundedDonorWithdrawals() {
        var random = new java.util.Random(292);
        for (int iteration = 0; iteration < 4000; iteration++) {
            var shapes = List.of(slab, new Form("a", 1 + random.nextInt(64)), new Form("b", 1 + random.nextInt(64)), new Form("c", 1 + random.nextInt(64)));
            int index = random.nextInt(shapes.size()); var source = shapes.get(index);
            var target = shapes.get((index + 1 + random.nextInt(shapes.size() - 1)) % shapes.size());
            int count = 1 + random.nextInt(64), limit = random.nextBoolean() ? 16 : 64;
            var supplies = shapes.stream().map(form -> new MaterialWithdrawal.Supply<>(form, random.nextInt(65), form.units())).toList();
            var plan = ManualConversionPlan.calculate(count, source, target, shapes.getLast(), shapes, Form::units, limit, supplies);
            long input = (long) (count + plan.borrowed()) * source.units()
                    + plan.withdrawals().stream().mapToLong(part -> (long) part.count() * part.form().units()).sum();
            long output = (long) plan.produced() * target.units() + (long) plan.remainder() * source.units()
                    + plan.stored().stream().mapToLong(part -> (long) part.count() * part.form().units()).sum();
            assertEquals(input, output); assertTrue(plan.produced() <= limit);
            for (var supply : supplies) {
                int taken = plan.withdrawals().stream().filter(part -> part.form().equals(supply.source())).mapToInt(MaterialComposition.Part::count).sum()
                        + (source.equals(supply.source()) ? plan.borrowed() : 0);
                assertTrue(taken <= supply.count());
            }
        }
    }

    @Test void excessSlabsBecomePhysicalBaseMaterialAndTheWholeSourceIsConsumed() {
        var plan = ManualConversionPlan.calculate(64, stair, slab, base, forms, Form::units, 64);
        assertEquals(64, plan.consumed()); assertEquals(64, plan.produced()); assertEquals(0, plan.remainder());
        assertEquals(List.of(new MaterialComposition.Part<>(base, 32)), plan.stored());
        var odd = ManualConversionPlan.calculate(33, stair, slab, base, forms, Form::units, 64);
        assertEquals(List.of(new MaterialComposition.Part<>(base, 1)), odd.stored());
    }

    @Test void ordinaryConversionsAndOddReverseConversionsKeepExactBatching() {
        var small = ManualConversionPlan.calculate(31, base, slab, base, forms, Form::units, 64);
        assertEquals(62, small.produced()); assertTrue(small.stored().isEmpty());
        var reverse = ManualConversionPlan.calculate(63, slab, stair, base, forms, Form::units, Integer.MAX_VALUE);
        assertEquals(62, reverse.consumed()); assertEquals(31, reverse.produced()); assertEquals(1, reverse.remainder());
    }

    @Test void denseBaseToAnyRadialShapeMakesOnlyOneStackAndReturnsExcessBaseWithoutBorrowing() {
        var log = new Form("log", 24);
        for (String id : List.of("blockwheel:block", "blockwheel:stairs", "blockwheel:fence", "blockwheel:wall", "example:flooring")) {
            var target = new Form(id, 6);
            for (int itemLimit : new int[]{16, 64, 99}) {
                int limit = ManualConversionPlan.outputLimit(id, itemLimit);
                var plan = ManualConversionPlan.calculate(63, log, target, log, List.of(log, target), Form::units, limit, 64);
                int produced = itemLimit - itemLimit % 4;
                assertEquals(produced, plan.produced(), id);
                assertEquals(List.of(new MaterialComposition.Part<>(log, 63 - produced / 4)), plan.stored(), id);
                assertEquals(0, plan.borrowed(), id);
                assertEquals(63 * 24, plan.produced() * target.units() + plan.stored().getFirst().count() * log.units());
            }
        }
    }

    @Test void cappedReverseWoodToLogsKeepsOriginalWoodAndDoesNotStoreAdditionalTargetCopies() {
        var log = new Form("log", 24); var wood = new Form("wood", 32);
        var plan = ManualConversionPlan.calculate(63, wood, log, log, List.of(log, wood), Form::units,
                ManualConversionPlan.outputLimit("blockwheel:log", 64));
        assertEquals(64, plan.produced()); assertEquals(0, plan.remainder());
        assertEquals(List.of(new MaterialComposition.Part<>(wood, 15)), plan.stored());
        assertEquals(63 * 32, 64 * 24 + 15 * 32);
    }

    @Test void realSmallStackLimitsAndNewShapeRatiosPreserveEveryUnitWithoutExtraSlabs() {
        for (Form source : List.of(base, stair, floor)) for (int count = 1; count <= 99; count++) for (int limit : new int[]{16, 64}) {
            var plan = ManualConversionPlan.calculate(count, source, slab, base, forms, Form::units, limit);
            assertTrue(plan.produced() <= limit);
            long stored = plan.stored().stream().mapToLong(part -> (long) part.count() * part.form().units()).sum();
            assertEquals((long) count * source.units(), plan.produced() + (long) plan.remainder() * source.units() + stored);
            assertTrue(plan.stored().stream().noneMatch(part -> part.form().equals(slab)));
        }
        assertEquals(64, ManualConversionPlan.outputLimit("blockwheel:slab", 99));
        assertEquals(16, ManualConversionPlan.outputLimit("blockwheel:slab", 16));
    }

    @Test void baseLessFamiliesRetainPhysicalChangeOrSafelyRefuseAnImpossibleCap() {
        var plan = ManualConversionPlan.calculate(43, floor, slab, floor, List.of(floor, slab), Form::units, 64);
        assertEquals(63, plan.produced());
        assertEquals(List.of(new MaterialComposition.Part<>(floor, 22)), plan.stored());
        var large = new Form("large", 64);
        var impossible = ManualConversionPlan.calculate(1, large, slab, large, List.of(large, slab), Form::units, 16);
        assertEquals(0, impossible.produced()); assertEquals(1, impossible.remainder()); assertTrue(impossible.stored().isEmpty());
    }

    @Test void oneAndThirtyOneHeldSlabsUseOneMatchingInventorySlabToFinishTheirBatch() {
        for (int count : new int[]{1, 31}) {
            var plan = ManualConversionPlan.calculate(count, slab, base, base, forms, Form::units, Integer.MAX_VALUE, 1);
            assertEquals(1, plan.borrowed()); assertEquals(count + 1, plan.consumed());
            assertEquals((count + 1) / 2, plan.produced()); assertEquals(0, plan.remainder());
            assertTrue(plan.stored().isEmpty());
        }
    }

    @Test void borrowsOnlyTheMissingSourceItemsAndNeverBorrowsWhenTheOutputIsAlreadyCapped() {
        var plan = ManualConversionPlan.calculate(1, slab, stair, base, forms, Form::units, Integer.MAX_VALUE, 64);
        assertEquals(1, plan.borrowed()); assertEquals(1, plan.produced());
        var complete = ManualConversionPlan.calculate(32, slab, base, base, forms, Form::units, Integer.MAX_VALUE, 64);
        assertEquals(0, complete.borrowed()); assertEquals(16, complete.produced());
        var capped = ManualConversionPlan.calculate(15, floor, base, base, forms, Form::units, 16, 64);
        assertEquals(0, capped.borrowed()); assertEquals(16, capped.produced());
    }

    @Test void insufficientInventoryMaterialIsPreservedAndCustomRatiosUseTheSameBatchArithmetic() {
        var shortPlan = ManualConversionPlan.calculate(1, base, floor, base, forms, Form::units, Integer.MAX_VALUE, 1);
        assertEquals(0, shortPlan.borrowed()); assertEquals(0, shortPlan.produced()); assertEquals(1, shortPlan.remainder());
        var complete = ManualConversionPlan.calculate(1, base, floor, base, forms, Form::units, Integer.MAX_VALUE, 2);
        assertEquals(2, complete.borrowed()); assertEquals(3, complete.consumed()); assertEquals(2, complete.produced());
    }

    @Test void donorPlansConserveValueAcrossRatiosCountsAndAvailableSupply() {
        for (int input = 1; input <= 8; input++) for (int output = 1; output <= 8; output++) {
            var source = new Form("source", input); var target = new Form("target", output);
            var shapes = List.of(source, target, slab);
            for (int count = 1; count <= 32; count++) for (int available : new int[]{0, 1, 7}) {
                var plan = ManualConversionPlan.calculate(count, source, target, source, shapes, Form::units, 16, available);
                long stored = plan.stored().stream().mapToLong(part -> (long) part.count() * part.form().units()).sum();
                assertEquals((long) (count + plan.borrowed()) * input,
                        (long) plan.produced() * output + (long) plan.remainder() * input + stored);
                assertTrue(plan.borrowed() <= available);
                assertTrue(plan.borrowed() < ConversionPlan.minimumInput(input, output));
                assertTrue(plan.produced() <= 16);
            }
        }
    }
}
