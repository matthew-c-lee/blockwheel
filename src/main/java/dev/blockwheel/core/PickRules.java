package dev.blockwheel.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Ordered one-hop alternatives. Rules select a target; only its own permitted family can supply its shape. */
public record PickRules(boolean enabled, boolean includeLogDefaults, boolean includeAnvilDefaults, boolean includeCopperDefaults, List<Rule> rules) {
    public static final int MAX_RULES = 256, MAX_FALLBACKS = 16, MAX_ID_LENGTH = 256, MAX_WIRE_BYTES = 28_000;
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    public static final PickRules DEFAULT = new PickRules(true, true, List.of());
    public static final Codec<PickRules> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(1, 1).optionalFieldOf("version", 1).forGetter(value -> 1),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(PickRules::enabled),
            Codec.BOOL.optionalFieldOf("include_log_defaults", true).forGetter(PickRules::includeLogDefaults),
            Codec.BOOL.optionalFieldOf("include_anvil_defaults", true).forGetter(PickRules::includeAnvilDefaults),
            Codec.BOOL.optionalFieldOf("include_copper_defaults", true).forGetter(PickRules::includeCopperDefaults),
            Rule.CODEC.listOf(0, MAX_RULES).optionalFieldOf("rules", List.of()).forGetter(PickRules::rules)
    ).apply(instance, (version, enabled, logs, anvils, copper, rules) -> new PickRules(enabled, logs, anvils, copper, rules)));
    public PickRules(boolean enabled, boolean logs, List<Rule> rules) { this(enabled, logs, true, true, rules); }

    public record Rule(String target, List<String> fallbacks) {
        public static final Codec<Rule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("target").forGetter(Rule::target),
                Codec.STRING.listOf(0, MAX_FALLBACKS).fieldOf("fallbacks").forGetter(Rule::fallbacks)
        ).apply(instance, Rule::new));
        public Rule {
            identifier(target); fallbacks = List.copyOf(fallbacks);
            if (fallbacks.size() > MAX_FALLBACKS) throw new IllegalArgumentException("Too many fallbacks");
            var seen = new HashSet<String>(); seen.add(target);
            for (var fallback : fallbacks) {
                identifier(fallback);
                if (!seen.add(fallback)) throw new IllegalArgumentException("Duplicate or self-referencing fallback");
            }
        }
    }
    public PickRules {
        rules = List.copyOf(rules);
        if (rules.size() > MAX_RULES) throw new IllegalArgumentException("Too many pick rules");
        var targets = new HashSet<String>(); int bytes = 8;
        for (var rule : rules) {
            if (!targets.add(rule.target())) throw new IllegalArgumentException("Duplicate pick target");
            bytes += rule.target().length() + 3;
            for (var fallback : rule.fallbacks()) bytes += fallback.length() + 2;
        }
        if (bytes > MAX_WIRE_BYTES) throw new IllegalArgumentException("Pick rules exceed the network size limit");
    }
    private static void identifier(String value) {
        if (value == null || value.length() > MAX_ID_LENGTH || !ID.matcher(value).matches()) throw new IllegalArgumentException("Invalid block identifier");
    }
    public PickRules withEnabled(boolean value) { return new PickRules(value, includeLogDefaults, includeAnvilDefaults, includeCopperDefaults, rules); }
    public PickRules withLogDefaults(boolean value) { return new PickRules(enabled, value, includeAnvilDefaults, includeCopperDefaults, rules); }
    public PickRules withAnvilDefaults(boolean value) { return new PickRules(enabled, includeLogDefaults, value, includeCopperDefaults, rules); }
    public PickRules withCopperDefaults(boolean value) { return new PickRules(enabled, includeLogDefaults, includeAnvilDefaults, value, rules); }
    public PickRules put(Rule rule) {
        var next = new java.util.ArrayList<>(rules); next.removeIf(old -> old.target().equals(rule.target())); next.add(rule);
        return new PickRules(enabled, includeLogDefaults, includeAnvilDefaults, includeCopperDefaults, next);
    }
    public PickRules withoutOverride(String target) { return new PickRules(enabled, includeLogDefaults, includeAnvilDefaults, includeCopperDefaults, rules.stream().filter(rule -> !rule.target().equals(target)).toList()); }
    public Map<String, List<String>> effective(Map<String, List<String>> defaults) {
        return effective(new PickDefaults(defaults, Map.of(), Map.of()));
    }
    public boolean hasDefault(String target, PickDefaults defaults) {
        return includeLogDefaults && defaults.logs().containsKey(target) || includeAnvilDefaults && defaults.anvils().containsKey(target)
                || includeCopperDefaults && defaults.copper().containsKey(target);
    }
    public Map<String, List<String>> effective(PickDefaults defaults) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (includeLogDefaults) result.putAll(defaults.logs());
        if (includeAnvilDefaults) result.putAll(defaults.anvils());
        if (includeCopperDefaults) result.putAll(defaults.copper());
        for (var rule : rules) result.put(rule.target(), rule.fallbacks());
        return java.util.Collections.unmodifiableMap(result);
    }
}
