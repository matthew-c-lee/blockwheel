package dev.blockwheel.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RadialLayoutTest {
    @Test void compactWheelKeepsIconsCloseToHotbarSizeAcrossGuiScales() {
        for (int count = 1; count <= 8; count++) {
            var small = RadialLayout.fit(320, 240, count);
            var large = RadialLayout.fit(960, 540, count);
            assertEquals(small.radius(), large.radius(), "GUI pixels should not grow with screen resolution");
            assertEquals(small.nodeSize(), large.nodeSize());
            assertTrue(16 * small.itemScale() > 16, "Icons should be larger than hotbar items");
            assertTrue(16 * small.itemScale() <= 24, "Icons should stay close to hotbar size");
            assertTrue(2 * (small.radius() + small.nodeSize() + 3) <= 200, "Keep the whole wheel compact");
        }
    }

    @Test void nearestSelectionWorksInsideAndFarOutsideIconsAcrossPageSizesAndGuiScales() {
        for (int[] resolution : new int[][]{{320, 240}, {480, 270}, {640, 360}, {960, 540}}) {
            for (int count = 1; count <= 8; count++) {
                var layout = RadialLayout.fit(resolution[0], resolution[1], count);
                for (int i = 0; i < count; i++) {
                    var p = layout.node(i);
                    assertEquals(i, layout.closestNode(p.x(), p.y()));
                    for (double distance : new double[]{0.25, 3}) {
                        double x = layout.centerX() + (p.x() - layout.centerX()) * distance;
                        double y = layout.centerY() + (p.y() - layout.centerY()) * distance;
                        assertTrue(Math.hypot(x - p.x(), y - p.y()) > layout.nodeSize() + 3);
                        assertEquals(i, layout.closestNode(x, y), "Selection should not require hovering an icon");
                    }
                    assertTrue(p.x() - layout.nodeSize() >= 0);
                    assertTrue(p.x() + layout.nodeSize() < resolution[0]);
                    assertTrue(p.y() - layout.nodeSize() >= 0);
                    assertTrue(p.y() + layout.nodeSize() + 3 < resolution[1] - 24);
                }
            }
        }
    }

    @Test void selectionDependsOnPositionAndReversingWithinTheSameSideKeepsItsOption() {
        var layout = RadialLayout.fit(640, 360, 4);
        double x = layout.centerX(), y = layout.centerY();
        assertEquals(3, layout.closestNode(x - 100, y));
        assertEquals(3, layout.closestNode(x - 76, y));
        assertEquals(1, layout.closestNode(x + 24, y));
        assertEquals(1, layout.closestNode(x + 22, y));
        assertEquals(0, layout.closestNode(x + 20, y - 40));
        assertEquals(2, layout.closestNode(x - 20, y + 40));
    }

    @Test void centerIsNeutralBeforeAndAfterChoosingAnOption() {
        var layout = RadialLayout.fit(640, 360, 4);
        double x = layout.centerX(), y = layout.centerY();
        assertEquals(-1, layout.closestNode(x, y));
        assertEquals(-1, layout.closestNode(x + 1, y - 1));
        assertEquals(1, layout.closestNode(x + 24, y));
        assertEquals(-1, layout.closestNode(x + RadialLayout.CENTER_DEAD_ZONE, y));
        assertEquals(-1, layout.closestNode(x, y));
    }

    @Test void invalidPointerPositionsAreNeutralAndPageSizesAreBounded() {
        var layout = RadialLayout.fit(640, 360, 4);
        assertEquals(-1, layout.closestNode(Double.NaN, 100));
        assertEquals(-1, layout.closestNode(100, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> RadialLayout.fit(640, 360, 0));
        assertThrows(IllegalArgumentException.class, () -> RadialLayout.fit(640, 360, 9));
    }
}
