package dev.blockwheel.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MaterialWithdrawalTest {
    @Test void exactFallbackCombinesFractionsWithoutOverdrawingAnySource() {
        var supplies = List.of(new MaterialWithdrawal.Supply<>("slabs", 31, 1), new MaterialWithdrawal.Supply<>("blocks", 64, 2));
        var path = new MaterialWithdrawal<>(supplies, 64).forUnits(64).orElseThrow();
        int slabs = path.stream().filter(part -> part.source().equals("slabs")).mapToInt(MaterialWithdrawal.Part::count).sum();
        int blocks = path.stream().filter(part -> part.source().equals("blocks")).mapToInt(MaterialWithdrawal.Part::count).sum();
        assertEquals(64, slabs + blocks * 2); assertTrue(slabs <= 31); assertTrue(blocks <= 64);
    }
    @Test void impossibleFractionsAndZeroWithdrawableReservesRemainUnavailable() {
        var plan = new MaterialWithdrawal<>(List.of(new MaterialWithdrawal.Supply<>("protected", 0, 1), new MaterialWithdrawal.Supply<>("floor", 1, 3)), 8);
        assertTrue(plan.forUnits(2).isEmpty()); assertTrue(plan.forUnits(4).isEmpty());
        assertEquals(List.of(new MaterialWithdrawal.Part<>("floor", 1)), plan.forUnits(3).orElseThrow());
        assertEquals(List.of(), plan.forUnits(0).orElseThrow());
    }
    @Test void boundedSelectionMatchesExhaustiveSmallInventoriesAndConservesEveryReturnedPlan() {
        for (int first = 1; first <= 7; first++) for (int second = 1; second <= 7; second++) {
            var plan = new MaterialWithdrawal<>(List.of(new MaterialWithdrawal.Supply<>(0, 3, first), new MaterialWithdrawal.Supply<>(1, 4, second)), 50);
            for (int units = 0; units <= 50; units++) {
                boolean reachable = false;
                for (int a = 0; a <= 3; a++) for (int b = 0; b <= 4; b++) if (a * first + b * second == units) reachable = true;
                var result = plan.forUnits(units); assertEquals(reachable, result.isPresent());
                if (result.isPresent()) {
                    int sum = 0;
                    for (var part : result.get()) {
                        assertTrue(part.count() > 0 && part.count() <= (part.source() == 0 ? 3 : 4));
                        sum += part.count() * (part.source() == 0 ? first : second);
                    }
                    assertEquals(units, sum);
                }
            }
        }
    }
}
