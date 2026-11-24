package dev.blockwheel.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Bounded physical source selection, used when greedy conversion cannot store its change. */
public final class MaterialWithdrawal<T> {
    public record Supply<T>(T source, int count, int units) {}
    public record Part<T>(T source, int count) {}
    private record Step(int source, Step previous) {}
    private final List<Supply<T>> sources;
    private final Step[] paths;
    private static final Step ROOT = new Step(-1, null);

    public MaterialWithdrawal(List<Supply<T>> supplies, int maximumUnits) {
        if (maximumUnits < 0 || maximumUnits > 65_536) throw new IllegalArgumentException("Withdrawal bound");
        sources = List.copyOf(supplies); paths = new Step[maximumUnits + 1]; paths[0] = ROOT;
        int[] used = new int[paths.length];
        for (int index = 0; index < sources.size(); index++) {
            var supply = sources.get(index);
            if (supply.count() < 0 || supply.units() < 1 || supply.units() > 64) throw new IllegalArgumentException("Invalid source");
            Arrays.fill(used, -1);
            for (int sum = 0; sum < paths.length; sum++) {
                if (paths[sum] != null) { used[sum] = 0; continue; }
                int previous = sum - supply.units();
                if (previous >= 0 && used[previous] >= 0 && used[previous] < supply.count()) {
                    used[sum] = used[previous] + 1; paths[sum] = new Step(index, paths[previous]);
                }
            }
        }
    }

    public Optional<List<Part<T>>> forUnits(int units) {
        if (units < 0 || units >= paths.length || paths[units] == null) return Optional.empty();
        int[] counts = new int[sources.size()];
        for (Step step = paths[units]; step != ROOT; step = step.previous()) counts[step.source()]++;
        List<Part<T>> result = new ArrayList<>();
        for (int i = 0; i < counts.length; i++) if (counts[i] > 0) result.add(new Part<>(sources.get(i).source(), counts[i]));
        return Optional.of(List.copyOf(result));
    }
}
