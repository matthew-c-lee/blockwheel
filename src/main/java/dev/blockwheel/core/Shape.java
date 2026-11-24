package dev.blockwheel.core;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** A data-defined form. Units are material value, not crafting recipe yield. */
public record Shape(String id, String translationKey, int units, int order, boolean base,
                    List<String> suffixes, List<String> blockTags, List<String> baseSuffixes, List<String> baseTags) {
    public static final Comparator<Shape> ORDER = Comparator.comparingInt(Shape::order).thenComparing(Shape::id);

    public Shape {
        if (units < 1 || units > 64) throw new IllegalArgumentException("Shape units must be 1..64: " + id);
        if (translationKey.isBlank() || translationKey.length() > 256) throw new IllegalArgumentException("Invalid shape label: " + id);
        suffixes = List.copyOf(suffixes);
        blockTags = List.copyOf(blockTags);
        baseSuffixes = List.copyOf(baseSuffixes);
        baseTags = List.copyOf(baseTags);
        if (suffixes.stream().anyMatch(String::isEmpty)) throw new IllegalArgumentException("Empty shape suffix: " + id);
        if (!base && suffixes.isEmpty() && blockTags.isEmpty()) throw new IllegalArgumentException("Shape needs suffixes or tags: " + id);
    }

    public Shape(String id, String translationKey, int units, int order, boolean base,
                 List<String> suffixes, List<String> blockTags, List<String> baseSuffixes) {
        this(id, translationKey, units, order, base, suffixes, blockTags, baseSuffixes, List.of());
    }

    /** Optional discovery scope, independent from the preferred storage shape of an expanded family. */
    public boolean allowsBase(Set<String> tags) {
        return baseTags.isEmpty() || baseTags.stream().anyMatch(tags::contains);
    }

    public Optional<String> stem(String blockId) {
        return suffixes.stream().filter(blockId::endsWith).max(Comparator.comparingInt(String::length))
                .map(suffix -> blockId.substring(0, blockId.length() - suffix.length()));
    }

    public boolean matches(String blockId, Set<String> tags) {
        return !base && (stem(blockId).isPresent() || blockTags.stream().anyMatch(tags::contains));
    }
}
