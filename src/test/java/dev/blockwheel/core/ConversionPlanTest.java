package dev.blockwheel.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConversionPlanTest {
    @Test void doublesSlabsAndReversesThem() {
        assertEquals(new ConversionPlan(64, 128, 0), ConversionPlan.calculate(64, 2, 1));
        assertEquals(new ConversionPlan(64, 32, 0), ConversionPlan.calculate(64, 1, 2));
        assertEquals(new ConversionPlan(37, 37, 0), ConversionPlan.calculate(37, 2, 2));
    }
    @Test void retainsOddSlabsAndNeverRoundsUp() {
        assertEquals(new ConversionPlan(2, 1, 1), ConversionPlan.calculate(3, 1, 2));
        assertEquals(new ConversionPlan(0, 0, 1), ConversionPlan.calculate(1, 1, 2));
    }
    @Test void alwaysUsesEveryCompleteBatchIncludingCustomRatios() {
        assertEquals(new ConversionPlan(64, 96, 0), ConversionPlan.calculate(64, 3, 2));
        assertEquals(new ConversionPlan(63, 42, 2), ConversionPlan.calculate(65, 2, 3));
        assertEquals(new ConversionPlan(62, 31, 1), ConversionPlan.calculate(63, 1, 2));
    }
    @Test void conservesMaterialAndLeavesLessThanOneBatchForEverySupportedRatio() {
        for (int source = 1; source <= 64; source++) for (int target = 1; target <= 64; target++) {
            for (int count = 0; count <= 128; count++) {
                var plan = ConversionPlan.calculate(count, source, target);
                assertEquals(count, plan.consumed() + plan.remainder());
                assertEquals((long) count * source, (long) plan.produced() * target + (long) plan.remainder() * source);
                assertTrue(plan.consumed() >= 0 && plan.remainder() >= 0 && plan.produced() >= 0);
                assertTrue(plan.remainder() < ConversionPlan.minimumInput(source, target));
            }
        }
    }
    @Test void rejectsInvalidQuantities() {
        assertThrows(IllegalArgumentException.class, () -> ConversionPlan.calculate(-1, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> ConversionPlan.calculate(2, 0, 1));
    }
}
