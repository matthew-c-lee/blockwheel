package dev.blockwheel.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Exact rational relationships become bounded integer values once, during catalog construction. */
public final class WoodFamilies {
    public static final String PLANKS = "blockwheel:block", LOG = "blockwheel:log", WOOD = "blockwheel:wood";
    private WoodFamilies() {}
    public record Relation(String planks, String log, String wood, int planksPerLog, int logInput, int woodOutput) {
        public Relation {
            if (planksPerLog < 1 || planksPerLog > 16 || logInput < 1 || logInput > 16 || woodOutput < 1 || woodOutput > 16
                    || planks.equals(log) || planks.equals(wood) || log.equals(wood)) throw new IllegalArgumentException("Invalid wood relationship");
        }
    }
    public static boolean stripped(String id) {
        String path = id.substring(id.indexOf(':') + 1);
        return path.startsWith("stripped_") || path.endsWith("_stripped_log") || path.endsWith("_stripped_wood")
                || path.endsWith("_stripped_stem") || path.endsWith("_stripped_hyphae") || path.endsWith("_log_stripped")
                || path.endsWith("_wood_stripped") || path.endsWith("_stem_stripped") || path.endsWith("_hyphae_stripped");
    }
    public static FamilyCatalog expand(FamilyCatalog original, List<Relation> relationships) {
        Map<String, Relation> byPlanks = new HashMap<>(); Set<String> conflicts = new HashSet<>();
        for (var relation : relationships) {
            var previous = byPlanks.putIfAbsent(relation.planks(), relation);
            if (previous != null && !previous.equals(relation)) conflicts.add(relation.planks());
        }
        var result = new ArrayList<FamilyCatalog.Family>(); Set<String> added = new HashSet<>();
        for (var family : original.families()) {
            var bases = family.members().stream().filter(member -> member.shape().base()).toList();
            var relation = bases.size() == 1 ? byPlanks.get(bases.getFirst().blockId()) : null;
            if (relation == null || conflicts.contains(relation.planks()) || bases.size() != 1 || !bases.getFirst().blockId().equals(relation.planks())
                    || original.family(relation.log()).isPresent() || added.contains(relation.log()) || stripped(relation.log())
                    || relation.wood() != null && (original.family(relation.wood()).isPresent() || added.contains(relation.wood()) || stripped(relation.wood()))
                    || family.members().size() + (relation.wood() == null ? 1 : 2) > 32) { result.add(family); continue; }
            int oldBase = bases.getFirst().shape().units();
            int product = relation.planksPerLog() * relation.logInput();
            int denominator = relation.woodOutput() / gcd(product, relation.woodOutput());
            int plankUnits = oldBase / gcd(oldBase, denominator) * denominator;
            int scale = plankUnits / oldBase, logUnits = plankUnits * relation.planksPerLog(), woodUnits = logUnits * relation.logInput() / relation.woodOutput();
            if (logUnits > 64 || woodUnits > 64 || family.members().stream().anyMatch(member -> member.shape().units() * scale > 64)) {
                result.add(family); continue;
            }
            var members = new ArrayList<FamilyCatalog.Member>();
            for (var member : family.members()) {
                var shape = member.shape();
                members.add(new FamilyCatalog.Member(new Shape(shape.id(), shape.translationKey(), shape.units() * scale, shape.order(), false,
                        shape.base() ? List.of("_planks") : shape.suffixes(), shape.blockTags(), shape.baseSuffixes(), shape.baseTags()), member.blockId()));
            }
            members.add(new FamilyCatalog.Member(new Shape(LOG, "shape.blockwheel.log", logUnits, -10, true, List.of(), List.of(), List.of()), relation.log()));
            if (relation.wood() != null) members.add(new FamilyCatalog.Member(new Shape(WOOD, "shape.blockwheel.wood", woodUnits, 60, false,
                    List.of("_wood", "_hyphae"), List.of(), List.of()), relation.wood()));
            members.sort((a, b) -> Shape.ORDER.compare(a.shape(), b.shape()));
            result.add(new FamilyCatalog.Family(family.id(), members)); added.add(relation.log());
            if (relation.wood() != null) added.add(relation.wood());
        }
        return FamilyCatalog.of(result);
    }
    private static int gcd(int a, int b) { while (b != 0) { int next = a % b; a = b; b = next; } return a; }
}
