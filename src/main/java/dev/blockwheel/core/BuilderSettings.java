package dev.blockwheel.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Persistent per-player preferences. The master toggle starts disabled. */
public record BuilderSettings(boolean enabled, int features, RestockSettings restock) {
    public enum Feature { PICKUP, NORMALIZE, RESTOCK, PICK_BLOCK, TOOLTIPS,
        SHULKER_SUPPLY, ENDER_ACCESS, SHULKER_PICK_BLOCK, SHULKER_TOOLTIPS, WOOD_TRAPDOORS, EXPERIMENTAL_WOOD;
        public int bit() { return 1 << ordinal(); }
    }
    public static final int ALL_FEATURES = (1 << Feature.values().length) - 1;
    public static final int LEGACY_FEATURES = 31;
    public static final int DEFAULT_SUPPLY_FEATURES = 14;
    public static final int DEFAULT_FEATURES = LEGACY_FEATURES | DEFAULT_SUPPLY_FEATURES << 5;
    public static final Codec<BuilderSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("enabled", false).forGetter(BuilderSettings::enabled),
            Codec.intRange(0, LEGACY_FEATURES).optionalFieldOf("features", LEGACY_FEATURES).forGetter(value -> value.features() & LEGACY_FEATURES),
            Codec.intRange(0, 15).optionalFieldOf("supply_features", DEFAULT_SUPPLY_FEATURES).forGetter(value -> value.features() >>> 5 & 15),
            Codec.BOOL.optionalFieldOf("wood_trapdoors", false).forGetter(value -> value.feature(Feature.WOOD_TRAPDOORS)),
            Codec.BOOL.optionalFieldOf("experimental_wood", false).forGetter(value -> value.feature(Feature.EXPERIMENTAL_WOOD)),
            RestockSettings.CODEC.optionalFieldOf("restock", RestockSettings.DEFAULT).forGetter(BuilderSettings::restock)
    ).apply(instance, (enabled, features, supply, trapdoors, wood, restock) -> new BuilderSettings(enabled,
            features | supply << 5 | (trapdoors ? Feature.WOOD_TRAPDOORS.bit() : 0) | (wood ? Feature.EXPERIMENTAL_WOOD.bit() : 0), restock)));

    public BuilderSettings(boolean enabled, int features) { this(enabled, features, RestockSettings.DEFAULT); }

    public BuilderSettings {
        if (features < 0 || (features & ~ALL_FEATURES) != 0) throw new IllegalArgumentException("Invalid Builder Mode features");
        java.util.Objects.requireNonNull(restock, "restock");
    }
    public static BuilderSettings defaults() { return new BuilderSettings(false, DEFAULT_FEATURES); }
    public boolean feature(Feature feature) { return (features & feature.bit()) != 0; }
    public boolean active(Feature feature) { return enabled && feature(feature); }
    /** Optional material forms can be used by the radial even while automatic Builder Mode is off. */
    public boolean shapeEnabled(String shapeId) {
        return shapeId.equals("blockwheel:trapdoor") ? feature(Feature.WOOD_TRAPDOORS)
                : shapeId.equals(WoodFamilies.LOG) || shapeId.equals(WoodFamilies.WOOD) ? feature(Feature.EXPERIMENTAL_WOOD) : true;
    }
    public int catalogView() { return (feature(Feature.WOOD_TRAPDOORS) ? 1 : 0) | (feature(Feature.EXPERIMENTAL_WOOD) ? 2 : 0); }
    public boolean shulkerSupply() { return active(Feature.SHULKER_SUPPLY); }
    public BuilderSettings withEnabled(boolean value) { return new BuilderSettings(value, features, restock); }
    public BuilderSettings withRestock(RestockSettings value) { return new BuilderSettings(enabled, features, value); }
    public BuilderSettings withFeature(Feature feature, boolean value) {
        return new BuilderSettings(enabled, value ? features | feature.bit() : features & ~feature.bit(), restock);
    }
}
