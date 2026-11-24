package dev.blockwheel.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;

/** Exact physical change, preferring the canonical base and never storing virtual fractions. */
public final class MaterialComposition<T> {
    public record Part<T>(T form, int count) {}
    private final T base;
    private final int baseUnits;
    private final int maxUnits;
    private final T secondary;
    private final int secondaryUnits;
    private final List<T> forms;
    private final ToIntFunction<T> units;
    private final int[] cost;
    private final int[] last;

    public MaterialComposition(T base, List<T> forms, ToIntFunction<T> units) {
        this(base, null, forms, units);
    }

    /** Optional secondary storage form takes precedence over other physical change, after the base. */
    public MaterialComposition(T base, T secondary, List<T> forms, ToIntFunction<T> units) {
        this.base = base;
        this.secondary = secondary != null && !secondary.equals(base) ? secondary : null;
        this.forms = List.copyOf(forms);
        this.units = units;
        baseUnits = units.applyAsInt(base);
        secondaryUnits = this.secondary == null ? 0 : units.applyAsInt(this.secondary);
        maxUnits = forms.stream().mapToInt(units).max().orElseThrow();
        if (baseUnits < 1 || maxUnits > 64 || forms.stream().anyMatch(form -> units.applyAsInt(form) < 1)) {
            throw new IllegalArgumentException("Invalid material units");
        }
        if (this.secondary != null && !forms.contains(this.secondary)) throw new IllegalArgumentException("Unavailable secondary form");
        cost = new int[baseUnits * (maxUnits + 1) + 1];
        last = new int[cost.length];
        Arrays.fill(cost, Integer.MAX_VALUE);
        Arrays.fill(last, -1);
        cost[0] = 0;
        for (int sum = 1; sum < cost.length; sum++) {
            for (int i = 0; i < forms.size(); i++) {
                if (forms.get(i).equals(base) || forms.get(i).equals(this.secondary)) continue;
                int value = units.applyAsInt(forms.get(i));
                if (sum >= value && cost[sum - value] != Integer.MAX_VALUE && cost[sum - value] + 1 < cost[sum]) {
                    cost[sum] = cost[sum - value] + 1;
                    last[sum] = i;
                }
            }
        }
    }

    public Optional<List<Part<T>>> decompose(int value) {
        if (value < 0) throw new IllegalArgumentException("Negative material");
        int maximumBase = value / baseUnits;
        for (int removed = 0; removed <= Math.min(maximumBase, maxUnits); removed++) {
            int bases = maximumBase - removed;
            int change = value - bases * baseUnits;
            int maximumSecondary = secondary == null ? 0 : change / secondaryUnits;
            for (int seconds = maximumSecondary; seconds >= 0; seconds--) {
                int remainder = change - seconds * secondaryUnits;
                if (remainder >= cost.length || cost[remainder] == Integer.MAX_VALUE) continue;
                List<Part<T>> result = new ArrayList<>();
                if (bases > 0) result.add(new Part<>(base, bases));
                if (seconds > 0) result.add(new Part<>(secondary, seconds));
                int[] counts = new int[forms.size()];
                while (remainder > 0) {
                    int index = last[remainder];
                    counts[index]++;
                    remainder -= units.applyAsInt(forms.get(index));
                }
                for (int i = 0; i < counts.length; i++) if (counts[i] > 0) result.add(new Part<>(forms.get(i), counts[i]));
                return Optional.of(List.copyOf(result));
            }
        }
        return Optional.empty();
    }

    public static String format(long value, int perItem) {
        if (value < 0 || perItem < 1) throw new IllegalArgumentException("Invalid material amount");
        long whole = value / perItem;
        int remainder = (int) (value % perItem);
        if (remainder == 0) return Long.toString(whole);
        int divisor = perItem;
        for (int a = remainder; a != 0;) { int next = divisor % a; divisor = a; a = next; }
        String fraction = (remainder / divisor) + "/" + (perItem / divisor);
        return whole == 0 ? fraction : whole + " " + fraction;
    }
}
