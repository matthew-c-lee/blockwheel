package dev.blockwheel.core;

import org.junit.jupiter.api.Test;
import static dev.blockwheel.core.PressHoldAction.Action.*;
import static org.junit.jupiter.api.Assertions.*;

class PressHoldActionTest {
    @Test void shortPressWaitsForReleaseAndUsesMainInventory() {
        var gesture = new PressHoldAction(); gesture.begin(1000);
        assertEquals(NONE, gesture.update(1000, true));
        assertEquals(NONE, gesture.update(1500, true));
        assertEquals(MAIN_INVENTORY, gesture.update(1599, false));
        assertEquals(NONE, gesture.update(1600, false));
    }
    @Test void holdFiresOnceAtThresholdAndReleaseDoesNothing() {
        var gesture = new PressHoldAction(); gesture.begin(0);
        assertEquals(NONE, gesture.update(599, true));
        assertEquals(INCLUDING_HOTBAR, gesture.update(600, true));
        for (long time = 601; time < 3000; time++) assertEquals(NONE, gesture.update(time, true));
        assertEquals(NONE, gesture.update(3000, false));
        gesture.begin(3100); assertEquals(MAIN_INVENTORY, gesture.update(3101, false));
    }
    @Test void releaseAfterThresholdStillCountsAsAHoldWhenNoIntermediateTickRan() {
        var gesture = new PressHoldAction(); gesture.begin(0);
        assertEquals(INCLUDING_HOTBAR, gesture.update(600, false));
        assertEquals(NONE, gesture.update(601, false));
    }
    @Test void repeatedPressEventsCannotRestartOrRearmAHold() {
        var gesture = new PressHoldAction(); gesture.begin(0); gesture.begin(599);
        assertEquals(INCLUDING_HOTBAR, gesture.update(600, true));
        gesture.begin(900); assertEquals(NONE, gesture.update(1500, true));
        assertEquals(NONE, gesture.update(1600, false));
    }
    @Test void cancellationCannotTurnIntoAShortReleaseAction() {
        var gesture = new PressHoldAction(); gesture.begin(0); gesture.cancel();
        assertEquals(NONE, gesture.update(100, false));
        assertEquals(NONE, gesture.update(900, true));
    }
    @Test void progressIsBoundedAndClearsAfterRelease() {
        var gesture = new PressHoldAction(); assertEquals(0, gesture.progress(0)); gesture.begin(100);
        assertEquals(0, gesture.progress(50)); assertEquals(.5, gesture.progress(400));
        assertEquals(1, gesture.progress(900)); gesture.update(900, false); assertEquals(0, gesture.progress(901));
    }
    @Test void randomizedPressTracesAlwaysDispatchExactlyOnce() {
        var random = new java.util.Random(293);
        for (int trial = 0; trial < 2000; trial++) {
            var gesture = new PressHoldAction(); gesture.begin(0); int actions = 0; long time = 0;
            int duration = random.nextInt(2001);
            while (time < duration) {
                if (random.nextBoolean()) gesture.begin(time);
                if (gesture.update(time, true) != NONE) actions++;
                time += 1 + random.nextInt(300);
            }
            if (gesture.update(duration, false) != NONE) actions++;
            assertEquals(1, actions); assertEquals(NONE, gesture.update(duration + 1000, false));
        }
    }
}
