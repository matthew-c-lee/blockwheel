package dev.blockwheel.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Derive conservative log/wood and stem/hyphae defaults from existing same-namespace blocks. */
public final class LogPickDefaults {
    private LogPickDefaults() {}
    public static Map<String, List<String>> create(Collection<String> registered) {
        Set<String> ids = Set.copyOf(registered.stream().filter(id -> id.length() <= PickRules.MAX_ID_LENGTH).toList());
        Map<String, List<String>> result = new TreeMap<>();
        for (var id : ids) {
            int separator = id.indexOf(':'); if (separator < 0) continue;
            String namespace = id.substring(0, separator + 1), path = id.substring(separator + 1);
            if (path.startsWith("stripped_")) continue;
            if (path.endsWith("_log")) family(ids, result, namespace, path.substring(0, path.length() - 4), "_log", "_wood");
            if (path.endsWith("_stem")) family(ids, result, namespace, path.substring(0, path.length() - 5), "_stem", "_hyphae");
            if (path.equals("bamboo_block")) put(ids, result, namespace + "stripped_bamboo_block", List.of(id));
        }
        return java.util.Collections.unmodifiableMap(result);
    }
    private static void family(Set<String> ids, Map<String, List<String>> result, String namespace, String name, String log, String wood) {
        String rawLog = namespace + name + log, rawWood = namespace + name + wood;
        String strippedLog = namespace + "stripped_" + name + log, strippedWood = namespace + "stripped_" + name + wood;
        put(ids, result, strippedLog, List.of(rawLog)); put(ids, result, rawWood, List.of(rawLog));
        put(ids, result, strippedWood, List.of(strippedLog, rawWood, rawLog));
    }
    private static void put(Set<String> ids, Map<String, List<String>> result, String target, List<String> candidates) {
        if (!ids.contains(target)) return;
        var present = new ArrayList<String>(); for (var candidate : candidates) if (ids.contains(candidate)) present.add(candidate);
        if (!present.isEmpty()) result.put(target, List.copyOf(present));
    }
}
