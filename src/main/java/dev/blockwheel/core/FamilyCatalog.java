package dev.blockwheel.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Immutable catalog shared by discovery, validation and the server's UI snapshot. */
public final class FamilyCatalog {
    public record BlockSpec(String id, Set<String> tags, boolean fullCube) {
        public BlockSpec { tags = Set.copyOf(tags); }
    }
    public record Member(Shape shape, String blockId) {}
    public record Family(String id, List<Member> members) {
        public Family { members = List.copyOf(members); }
        public Optional<Member> member(String blockId) {
            return members.stream().filter(member -> member.blockId().equals(blockId)).findFirst();
        }
        public Optional<Member> shape(String shapeId) {
            return members.stream().filter(member -> member.shape().id().equals(shapeId)).findFirst();
        }
    }
    public record Override(String id, Map<String, String> members, boolean disabled) {
        public Override { members = Map.copyOf(members); }
    }

    private final Map<String, Family> byBlock;
    private final List<Family> families;

    private FamilyCatalog(List<Family> families) {
        this.families = List.copyOf(families);
        Map<String, Family> lookup = new HashMap<>();
        for (Family family : families) for (Member member : family.members()) {
            if (lookup.put(member.blockId(), family) != null) throw new IllegalArgumentException("Block in multiple families: " + member.blockId());
        }
        byBlock = Map.copyOf(lookup);
    }

    public static FamilyCatalog empty() { return new FamilyCatalog(List.of()); }
    public Optional<Family> family(String blockId) { return Optional.ofNullable(byBlock.get(blockId)); }
    public List<Family> families() { return families; }
    public static FamilyCatalog of(List<Family> families) { return new FamilyCatalog(families); }
    public FamilyCatalog filterMembers(java.util.function.Predicate<Member> allowed) {
        return new FamilyCatalog(families.stream().map(family -> new Family(family.id(), family.members().stream().filter(allowed).toList()))
                .filter(family -> family.members().size() >= 2).toList());
    }
    public FamilyCatalog filterFamilies(java.util.function.Predicate<Family> allowed) {
        return new FamilyCatalog(families.stream().filter(allowed).toList());
    }

    /** Build an immutable view for optional shapes; singleton families cannot be converted. */
    public FamilyCatalog filterShapes(java.util.function.Predicate<Shape> enabled) {
        return new FamilyCatalog(families.stream().map(family -> new Family(family.id(), family.members().stream()
                .filter(member -> enabled.test(member.shape())).toList())).filter(family -> family.members().size() >= 2).toList());
    }

    /**
     * Crafting ingredients establish the base where possible. Conservative same-namespace
     * naming handles recipe-free blocks. Conflicting forms are omitted, never picked by chance.
     */
    public static FamilyCatalog discover(Collection<BlockSpec> blocks, List<Shape> definitions,
                                         Map<String, String> recipeBases, List<Override> overrides) {
        List<Shape> shapes = definitions.stream().sorted(Shape.ORDER).toList();
        List<Shape> bases = shapes.stream().filter(Shape::base).toList();
        if (bases.size() != 1) throw new IllegalArgumentException("Exactly one base shape is required");
        if (shapes.size() > 32) throw new IllegalArgumentException("At most 32 shapes are supported");
        Shape base = bases.getFirst();
        Map<String, BlockSpec> all = new TreeMap<>();
        for (BlockSpec block : blocks) all.put(block.id(), block);
        Map<String, Shape> forms = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (BlockSpec block : all.values()) {
            List<Shape> matches = shapes.stream().filter(shape -> shape.matches(block.id(), block.tags())).toList();
            if (matches.size() == 1) forms.put(block.id(), matches.getFirst());
            else if (matches.size() > 1) ambiguous.add(block.id());
        }

        Map<String, Map<String, String>> groups = new TreeMap<>();
        Set<String> conflicts = new HashSet<>();
        for (var entry : new TreeMap<>(forms).entrySet()) {
            String blockId = entry.getKey();
            Shape shape = entry.getValue();
            String anchor = recipeBases.get(blockId);
            if (!isBase(anchor, all, forms, ambiguous) || !shape.allowsBase(all.get(anchor).tags())) anchor = null;
            if (anchor == null) {
                Optional<String> stem = shape.stem(blockId);
                if (stem.isEmpty()) continue; // A tag alone cannot safely guess the family.
                for (String suffix : base.baseSuffixes()) {
                    String candidate = stem.get() + suffix;
                    if (isBase(candidate, all, forms, ambiguous) && shape.allowsBase(all.get(candidate).tags())) { anchor = candidate; break; }
                }
                if (anchor == null && !shape.baseTags().isEmpty()) continue;
                if (anchor == null) anchor = stem.get(); // A family may legitimately have no full block.
            }
            Map<String, String> members = groups.computeIfAbsent(anchor, unused -> new LinkedHashMap<>());
            if (isBase(anchor, all, forms, ambiguous)) members.put(base.id(), anchor);
            String conflictKey = anchor + "|" + shape.id();
            String previous = members.putIfAbsent(shape.id(), blockId);
            if (previous != null && !previous.equals(blockId)) conflicts.add(conflictKey);
        }
        for (var group : groups.entrySet()) {
            group.getValue().keySet().removeIf(shape -> conflicts.contains(group.getKey() + "|" + shape));
        }

        Map<String, Shape> shapeLookup = new HashMap<>();
        for (Shape shape : shapes) {
            if (shapeLookup.put(shape.id(), shape) != null) throw new IllegalArgumentException("Duplicate shape: " + shape.id());
        }
        Set<String> explicit = new HashSet<>();
        for (Override override : overrides) {
            Set<String> members = new HashSet<>(override.members().values());
            if (members.size() != override.members().size()) throw new IllegalArgumentException("Duplicate family member: " + override.id());
            if (!Collections.disjoint(explicit, members)) throw new IllegalArgumentException("Conflicting family overrides: " + override.id());
            explicit.addAll(members);
            for (var member : override.members().entrySet()) {
                if (!shapeLookup.containsKey(member.getKey()) || !all.containsKey(member.getValue())) {
                    throw new IllegalArgumentException("Unknown shape or block in family: " + override.id());
                }
            }
            // Detach every explicitly assigned block from all inferred families first.
            groups.values().forEach(group -> group.values().removeIf(members::contains));
            groups.remove(override.id());
            if (!override.disabled()) groups.put(override.id(), new LinkedHashMap<>(override.members()));
        }

        List<Family> result = new ArrayList<>();
        for (var entry : groups.entrySet()) {
            if (entry.getValue().size() < 2) continue;
            List<Member> members = entry.getValue().entrySet().stream()
                    .map(member -> new Member(shapeLookup.get(member.getKey()), member.getValue()))
                    .sorted((a, b) -> Shape.ORDER.compare(a.shape(), b.shape())).toList();
            result.add(new Family(entry.getKey(), members));
        }
        return new FamilyCatalog(result);
    }

    private static boolean isBase(String id, Map<String, BlockSpec> blocks, Map<String, Shape> forms, Set<String> ambiguous) {
        return id != null && blocks.containsKey(id) && blocks.get(id).fullCube()
                && !forms.containsKey(id) && !ambiguous.contains(id);
    }
}
