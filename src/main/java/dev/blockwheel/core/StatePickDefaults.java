package dev.blockwheel.core;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Selection priorities only: this model never defines material equivalence or manufactures a state. */
public final class StatePickDefaults {
    private StatePickDefaults() {}
    public record Copper(String id, String subtype, int oxidation, boolean waxed) {
        public Copper { if (oxidation < 0 || oxidation > 3) throw new IllegalArgumentException("Invalid oxidation state"); }
    }
    public static Map<String, List<String>> anvils(Collection<String> registered) {
        var available = Set.copyOf(registered);
        var states = List.of("minecraft:anvil", "minecraft:chipped_anvil", "minecraft:damaged_anvil");
        Map<String, List<String>> result = new TreeMap<>();
        for (int index = 0; index < states.size(); index++) {
            int requested = index;
            if (!available.contains(states.get(index))) continue;
            var candidates = java.util.stream.IntStream.range(0, states.size()).boxed()
                    .filter(other -> other != requested && available.contains(states.get(other)))
                    .sorted(Comparator.<Integer>comparingInt(other -> Math.abs(other - requested)).thenComparing(Comparator.reverseOrder()))
                    .map(states::get).toList();
            if (!candidates.isEmpty()) result.put(states.get(index), candidates);
        }
        return Map.copyOf(result);
    }
    public static Map<String, List<String>> copper(Collection<Copper> registered) {
        Map<String, List<String>> result = new TreeMap<>();
        for (var requested : registered) {
            var alternatives = registered.stream().filter(other -> other.subtype().equals(requested.subtype()) && !other.id().equals(requested.id()))
                    .sorted(Comparator.<Copper>comparingInt(other -> other.oxidation() == requested.oxidation() ? 0
                            : other.waxed() == requested.waxed() ? 1 : 2)
                            .thenComparingInt(other -> Math.abs(other.oxidation() - requested.oxidation()))
                            .thenComparingInt(Copper::oxidation).thenComparing(Copper::id))
                    .map(Copper::id).distinct().toList();
            if (!alternatives.isEmpty()) result.put(requested.id(), alternatives);
        }
        return Map.copyOf(result);
    }
}
