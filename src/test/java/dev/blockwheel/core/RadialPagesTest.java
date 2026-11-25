package dev.blockwheel.core;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RadialPagesTest {
    private RadialPages pages(List<String> ids) { return new RadialPages(ids.size(), ids::get, RadialPages.STANDARD_POSITIONS); }

    @Test void fenceGateSharesFenceNodeWithoutAddingPagesOrMovingWoodForms() {
        var ids = List.of("blockwheel:block", "blockwheel:stairs", "blockwheel:slab", RadialPages.FENCE, RadialPages.FENCE_GATE,
                "blockwheel:sign", "blockwheel:trapdoor", WoodFamilies.LOG, WoodFamilies.WOOD);
        var pages = pages(ids);
        assertEquals(1, pages.size()); assertEquals(8, pages.slots(0));
        assertEquals(2, pages.variants(0, 4));
        assertEquals(RadialPages.FENCE, ids.get(pages.choice(0, 4, 0)));
        assertEquals(RadialPages.FENCE_GATE, ids.get(pages.choice(0, 4, 1)));
        assertEquals(RadialPages.FENCE, ids.get(pages.choice(0, 4, 2)));
        assertEquals(RadialPages.FENCE_GATE, ids.get(pages.choice(0, 4, -1)));
        assertEquals("blockwheel:sign", ids.get(pages.choice(0, 5, 1)));
        assertEquals("blockwheel:trapdoor", ids.get(pages.choice(0, 3, 1)));
    }
    @Test void fenceGateStaysWithFenceWhenWallOwnsDown() {
        var ids = List.of("blockwheel:block", "blockwheel:stairs", "blockwheel:slab", "blockwheel:wall", RadialPages.FENCE_GATE, RadialPages.FENCE);
        var pages = pages(ids);
        assertEquals("blockwheel:wall", ids.get(pages.choice(0, 4)));
        assertEquals(RadialPages.FENCE, ids.get(pages.choice(0, 5)));
        assertEquals(RadialPages.FENCE_GATE, ids.get(pages.choice(0, 5, 1)));
        assertEquals(1, pages.size());
    }
    @Test void gateOnlyFamiliesRemainReachableAndFenceOnlyFamiliesDoNotGainVariants() {
        var gateIds = List.of("blockwheel:block", RadialPages.FENCE_GATE);
        var gates = pages(gateIds);
        assertEquals(4, gates.slots(0)); assertEquals(RadialPages.FENCE_GATE, gateIds.get(gates.choice(0, 2)));
        assertEquals(1, gates.variants(0, 2));
        var fences = pages(List.of("blockwheel:block", RadialPages.FENCE));
        assertEquals(1, fences.variants(0, 2)); assertEquals(0, fences.variants(0, 1));
    }

    @Test void everySubsetRetainsItsCardinalPositionRegardlessOfInputOrder() {
        var all = List.of("blockwheel:slab", "blockwheel:wall", "blockwheel:stairs", "blockwheel:block");
        for (int mask = 1; mask < 16; mask++) {
            var present = new ArrayList<String>();
            for (int i = 0; i < 4; i++) if ((mask & (1 << i)) != 0) present.add(all.get(i));
            var pages = pages(present);
            assertEquals(1, pages.size()); assertEquals(4, pages.slots(0));
            var layout = RadialLayout.fit(640, 360, 4);
            for (int slot = 0; slot < 4; slot++) {
                int choice = pages.choice(0, slot);
                if (choice < 0) continue;
                assertEquals(slot, RadialPages.STANDARD_POSITIONS.get(present.get(choice)));
                var point = layout.node(slot);
                assertEquals(slot, layout.closestNode(point.x(), point.y(), index -> pages.choice(0, index) >= 0));
            }
            assertEquals(-1, layout.closestNode(layout.centerX(), layout.centerY(), index -> pages.choice(0, index) >= 0));
        }
    }

    @Test void fenceHasItsOwnBottomLeftPositionWhenWallIsPresentAndOtherwiseStaysDown() {
        var ids = List.of("blockwheel:fence", "blockwheel:wall", "blockwheel:slab");
        var pages = pages(ids);
        assertEquals(8, pages.slots(0));
        assertEquals("blockwheel:wall", ids.get(pages.choice(0, 4)));
        assertEquals("blockwheel:fence", ids.get(pages.choice(0, 5)));
        assertEquals("blockwheel:slab", ids.get(pages.choice(0, 6)));
        var fenceOnly = pages(List.of("blockwheel:fence", "blockwheel:block"));
        assertEquals(0, fenceOnly.choice(0, 2)); assertEquals(-1, fenceOnly.choice(0, 3));
    }

    @Test void alternateSubsetsPreserveEveryCardinalCoordinateAndSizeAcrossViewports() {
        var all = List.of("blockwheel:trapdoor", "blockwheel:fence", "blockwheel:slab", "blockwheel:wall", "blockwheel:stairs", "blockwheel:block");
        for (int mask = 1; mask < 64; mask++) {
            var ids = new ArrayList<String>();
            for (int i = 0; i < all.size(); i++) if ((mask & (1 << i)) != 0) ids.add(all.get(i));
            var pages = pages(ids);
            for (int[] viewport : new int[][]{{320, 240}, {640, 360}, {960, 540}}) {
                var standard = RadialLayout.fit(viewport[0], viewport[1], 4);
                var actual = RadialLayout.fit(viewport[0], viewport[1], pages.slots(0));
                assertEquals(standard.radius(), actual.radius()); assertEquals(standard.nodeSize(), actual.nodeSize());
                for (int slot = 0; slot < pages.slots(0); slot++) {
                    int choice = pages.choice(0, slot); if (choice < 0) continue;
                    String id = ids.get(choice);
                    int expected = id.equals("blockwheel:trapdoor") ? 3 : id.equals("blockwheel:fence") && ids.contains("blockwheel:wall")
                            ? 5 : RadialPages.STANDARD_POSITIONS.get(id) * 2;
                    var expectedPoint = RadialLayout.fit(viewport[0], viewport[1], 8).node(expected);
                    assertEquals(expectedPoint, actual.node(slot));
                    assertEquals(slot, actual.closestNode(expectedPoint.x(), expectedPoint.y(), index -> pages.choice(0, index) >= 0));
                }
            }
        }
    }

    @Test void extraFormsUseDiagonalsAndPagesWithoutMovingStandardDirectionsOrLosingChoices() {
        var ids = new ArrayList<>(List.of("blockwheel:slab", "blockwheel:stairs", "blockwheel:block", "blockwheel:wall"));
        for (int i = 0; i < 28; i++) ids.add("test:extra_" + i);
        var pages = pages(ids);
        assertEquals(4, pages.size()); assertEquals(8, pages.slots(0));
        assertEquals("blockwheel:block", ids.get(pages.choice(0, 0)));
        assertEquals("blockwheel:stairs", ids.get(pages.choice(0, 2)));
        assertEquals("blockwheel:wall", ids.get(pages.choice(0, 4)));
        assertEquals("blockwheel:slab", ids.get(pages.choice(0, 6)));
        var seen = new HashSet<Integer>();
        for (int page = 0; page < pages.size(); page++) for (int slot = 0; slot < pages.slots(page); slot++) {
            int choice = pages.choice(page, slot);
            if (choice >= 0) assertTrue(seen.add(choice));
        }
        assertEquals(32, seen.size());
    }

    @Test void missingPositionsAreNotSelectableAndAmbiguousAnchorsAreRejected() {
        var pages = pages(List.of("blockwheel:block", "blockwheel:slab"));
        var layout = RadialLayout.fit(640, 360, pages.slots(0));
        assertEquals(3, layout.closestNode(layout.centerX() - 24, layout.centerY(), slot -> pages.choice(0, slot) >= 0));
        assertNotEquals(1, layout.closestNode(layout.centerX() + 24, layout.centerY(), slot -> pages.choice(0, slot) >= 0));
        assertThrows(IllegalArgumentException.class, () -> pages(List.of("blockwheel:stairs", "blockwheel:stairs")));
    }
    @Test void logsAndWoodKeepTheirDiagonalPositionsWithoutMovingAnyExistingForm() {
        var ids = List.of("blockwheel:block", "blockwheel:stairs", "blockwheel:slab", "blockwheel:fence", WoodFamilies.LOG, WoodFamilies.WOOD, "blockwheel:trapdoor", "test:other");
        var pages = pages(ids); assertEquals(8, pages.slots(0));
        assertEquals(WoodFamilies.LOG, ids.get(pages.choice(0, 7))); assertEquals(WoodFamilies.WOOD, ids.get(pages.choice(0, 1)));
        assertEquals("blockwheel:trapdoor", ids.get(pages.choice(0, 3))); assertEquals("blockwheel:fence", ids.get(pages.choice(0, 4)));
        assertEquals("blockwheel:block", ids.get(pages.choice(0, 0))); assertEquals("blockwheel:stairs", ids.get(pages.choice(0, 2)));
        assertEquals("blockwheel:slab", ids.get(pages.choice(0, 6))); assertEquals("test:other", ids.get(pages.choice(0, 5)));
    }
    @Test void signsKeepBottomLeftInEveryWoodViewAndAllEightWoodShapesFitTogether() {
        var all = List.of("blockwheel:block", "blockwheel:stairs", "blockwheel:slab", "blockwheel:fence",
                "blockwheel:sign", "blockwheel:trapdoor", WoodFamilies.LOG, WoodFamilies.WOOD);
        for (int mask = 0; mask < 8; mask++) {
            var ids = new ArrayList<>(all.subList(0, 5));
            if ((mask & 1) != 0) ids.add("blockwheel:trapdoor");
            if ((mask & 2) != 0) ids.add(WoodFamilies.LOG);
            if ((mask & 4) != 0) ids.add(WoodFamilies.WOOD);
            java.util.Collections.reverse(ids); var pages = pages(ids);
            assertEquals(1, pages.size()); assertEquals("blockwheel:sign", ids.get(pages.choice(0, 5)));
            assertEquals("blockwheel:block", ids.get(pages.choice(0, 0))); assertEquals("blockwheel:stairs", ids.get(pages.choice(0, 2)));
            assertEquals("blockwheel:fence", ids.get(pages.choice(0, 4))); assertEquals("blockwheel:slab", ids.get(pages.choice(0, 6)));
        }
    }
    @Test void signsDoNotDisplaceAnExistingWallFenceAlternateInACustomFamily() {
        var ids = List.of("blockwheel:block", "blockwheel:stairs", "blockwheel:slab", "blockwheel:wall", "blockwheel:fence",
                "blockwheel:sign", "blockwheel:trapdoor", WoodFamilies.LOG, WoodFamilies.WOOD);
        var pages = pages(ids); assertEquals(2, pages.size());
        assertEquals("blockwheel:wall", ids.get(pages.choice(0, 4))); assertEquals("blockwheel:fence", ids.get(pages.choice(0, 5)));
        assertEquals("blockwheel:sign", ids.get(pages.choice(1, 0)));
    }
}
