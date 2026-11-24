package dev.blockwheel.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MaterialCompositionTest {
    record Form(String name, int units) {}
    @Test void secondaryPlanksWinOverSignsWithoutLosingCustomPhysicalRemainders() {
        var log = new Form("log", 8); var plank = new Form("plank", 2); var sign = new Form("sign", 4); var slab = new Form("slab", 1);
        var composition = new MaterialComposition<>(log, plank, List.of(log, plank, sign, slab), Form::units);
        assertEquals(List.of(new MaterialComposition.Part<>(log, 16), new MaterialComposition.Part<>(plank, 3)), composition.decompose(134).orElseThrow());
        var customLog = new Form("customLog", 24); var customPlank = new Form("customPlank", 6); var wood = new Form("wood", 32);
        var custom = new MaterialComposition<>(customLog, customPlank, List.of(customLog, customPlank, wood), Form::units);
        assertEquals(List.of(new MaterialComposition.Part<>(wood, 1)), custom.decompose(32).orElseThrow());
    }
    @Test void secondaryStoragePreservesExactIntegerValueAcrossRandomFormRatios() {
        var random = new java.util.Random(100);
        for (int trial = 0; trial < 4000; trial++) {
            var base = new Form("base", 1 + random.nextInt(64)); var secondary = new Form("secondary", 1 + random.nextInt(64));
            var exact = new Form("fraction", 1); var other = new Form("other", 1 + random.nextInt(64));
            var composition = new MaterialComposition<>(base, secondary, List.of(base, secondary, exact, other), Form::units);
            int value = random.nextInt(4097); var parts = composition.decompose(value).orElseThrow();
            assertEquals(value, parts.stream().mapToInt(part -> part.count() * part.form().units()).sum());
            assertEquals(parts.size(), parts.stream().map(MaterialComposition.Part::form).distinct().count());
            assertTrue(parts.stream().allMatch(part -> part.count() > 0));
        }
    }
    @Test void oddSlabsRemainPhysicalItemsAndBaseBlocksArePreferred() {
        var base = new Form("base", 2); var slab = new Form("slab", 1);
        var composition = new MaterialComposition<>(base, List.of(base, slab), Form::units);
        assertEquals(List.of(new MaterialComposition.Part<>(base, 16), new MaterialComposition.Part<>(slab, 1)), composition.decompose(33).orElseThrow());
        assertEquals(List.of(), composition.decompose(0).orElseThrow());
    }
    @Test void newShapeValuesAndUnavailableFractionsDoNotInventMaterial() {
        var base = new Form("base", 2); var floor = new Form("floor", 3);
        var composition = new MaterialComposition<>(base, List.of(base, floor), Form::units);
        assertTrue(composition.decompose(1).isEmpty());
        assertEquals(List.of(new MaterialComposition.Part<>(floor, 1)), composition.decompose(3).orElseThrow());
        assertEquals(List.of(new MaterialComposition.Part<>(base, 1), new MaterialComposition.Part<>(floor, 1)), composition.decompose(5).orElseThrow());
    }
    @Test void compositionsConserveEveryRepresentableInputAcrossDataDefinedRatios() {
        for (int baseUnits = 1; baseUnits <= 12; baseUnits++) for (int units = 1; units <= 16; units++) {
            var base = new Form("base", baseUnits); var variant = new Form("variant", units);
            var composition = new MaterialComposition<>(base, List.of(base, variant), Form::units);
            for (int a = 0; a <= 48; a++) for (int b = 0; b <= 12; b++) {
                int input = a * units + b * baseUnits;
                var result = composition.decompose(input).orElseThrow();
                assertEquals(input, result.stream().mapToInt(part -> part.count() * part.form().units()).sum());
                assertTrue(result.stream().allMatch(part -> part.count() > 0));
            }
        }
    }
    @Test void availabilityFormattingIsExactForArbitraryFractions() {
        assertEquals("416", MaterialComposition.format(832, 2));
        assertEquals("16 1/2", MaterialComposition.format(33, 2));
        assertEquals("1/3", MaterialComposition.format(1, 3));
        assertEquals("2 1/2", MaterialComposition.format(15, 6));
        assertThrows(IllegalArgumentException.class, () -> MaterialComposition.format(-1, 2));
        assertThrows(IllegalArgumentException.class, () -> MaterialComposition.format(1, 0));
    }
}
