package dev.blockwheel.core;

import java.util.List;
import java.util.Map;

/** Generated groups stay independently toggleable; explicit user overrides always win. */
public record PickDefaults(Map<String, List<String>> logs, Map<String, List<String>> anvils, Map<String, List<String>> copper) {
    public static final PickDefaults EMPTY = new PickDefaults(Map.of(), Map.of(), Map.of());
    public PickDefaults { logs = freeze(logs); anvils = freeze(anvils); copper = freeze(copper); }
    private static Map<String, List<String>> freeze(Map<String, List<String>> source) {
        return source.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }
    public boolean containsKey(String target) { return logs.containsKey(target) || anvils.containsKey(target) || copper.containsKey(target); }
}
