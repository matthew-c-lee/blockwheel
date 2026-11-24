package dev.blockwheel.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/** Fixed cardinal forms and reserved alternate diagonals; additional forms use gaps, then pages. */
public final class RadialPages {
    public static final Map<String, Integer> STANDARD_POSITIONS = Map.of(
            "blockwheel:block", 0, "blockwheel:stairs", 1,
            "blockwheel:wall", 2, "blockwheel:fence", 2, "blockwheel:slab", 3);
    private final List<int[]> pages;

    public RadialPages(int count, IntFunction<String> shapeId, Map<String, Integer> positions) {
        if (count < 1 || count > 32) throw new IllegalArgumentException("A wheel needs 1..32 choices");
        boolean wall = java.util.stream.IntStream.range(0, count).anyMatch(index -> shapeId.apply(index).equals("blockwheel:wall"));
        boolean fence = java.util.stream.IntStream.range(0, count).anyMatch(index -> shapeId.apply(index).equals("blockwheel:fence"));
        int[] first = new int[8];
        Arrays.fill(first, -1);
        List<Integer> extra = new ArrayList<>();
        for (int choice = 0; choice < count; choice++) {
            String id = shapeId.apply(choice);
            Integer cardinal = positions.get(id);
            int position;
            if (id.equals(WoodFamilies.LOG)) position = 7;
            else if (id.equals(WoodFamilies.WOOD)) position = 1;
            else if (id.equals("blockwheel:trapdoor")) position = 3; // Bottom-right, without moving stairs or walls.
            else if (id.equals("blockwheel:sign") && !(wall && fence)) position = 5; // Bottom-left in wooden families.
            else if (id.equals("blockwheel:fence") && wall) position = 5; // Bottom-left when walls own down.
            else if (cardinal == null) { extra.add(choice); continue; }
            else {
                if (cardinal < 0 || cardinal > 3) throw new IllegalArgumentException("Invalid cardinal position: " + cardinal);
                position = cardinal * 2;
            }
            if (first[position] != -1) throw new IllegalArgumentException("Ambiguous radial position: " + position);
            first[position] = choice;
        }
        List<int[]> result = new ArrayList<>();
        int used = 0;
        for (int diagonal : new int[]{1, 7, 3, 5}) {
            if (first[diagonal] < 0 && used < extra.size()) first[diagonal] = extra.get(used++);
        }
        if (first[1] < 0 && first[3] < 0 && first[5] < 0 && first[7] < 0) {
            result.add(new int[]{first[0], first[2], first[4], first[6]});
        } else result.add(first);
        for (int start = used; start < extra.size(); start += 8) {
            int[] next = new int[Math.max(4, Math.min(8, extra.size() - start))];
            Arrays.fill(next, -1);
            for (int i = 0; i < next.length && start + i < extra.size(); i++) next[i] = extra.get(start + i);
            result.add(next);
        }
        pages = List.copyOf(result);
    }

    public int size() { return pages.size(); }
    public int slots(int page) { return pages.get(page).length; }
    public int choice(int page, int slot) { return pages.get(page)[slot]; }
}
