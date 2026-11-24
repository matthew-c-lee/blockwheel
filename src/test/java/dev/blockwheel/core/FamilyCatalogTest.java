package dev.blockwheel.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FamilyCatalogTest {
    private static Shape shape(String name, int units, boolean base, List<String> suffixes) {
        return new Shape("blockwheel:" + name, "shape.blockwheel." + name, units, base ? 0 : 10,
                base, suffixes, List.of(), base ? List.of("_planks", "", "_block", "s") : List.of());
    }
    private static final List<Shape> SHAPES = List.of(shape("block", 2, true, List.of()), shape("stairs", 2, false, List.of("_stairs")),
            shape("slab", 1, false, List.of("_slab")), shape("wall", 2, false, List.of("_wall")), shape("fence", 2, false, List.of("_fence")));
    private static List<FamilyCatalog.BlockSpec> blocks(String... ids) {
        return java.util.Arrays.stream(ids).map(id -> new FamilyCatalog.BlockSpec(id, Set.of(),
                !id.endsWith("_stairs") && !id.endsWith("_slab") && !id.endsWith("_wall") && !id.endsWith("_fence") && !id.endsWith("_flooring"))).toList();
    }
    private static FamilyCatalog discover(String... ids) { return FamilyCatalog.discover(blocks(ids), SHAPES, Map.of(), List.of()); }

    @Test void discoversWoodAndPluralFamiliesFromAnyMember() {
        var catalog = discover("minecraft:oak_planks", "minecraft:oak_stairs", "minecraft:oak_slab", "minecraft:oak_fence",
                "minecraft:stone_bricks", "minecraft:stone_brick_wall", "minecraft:stone_brick_stairs", "minecraft:stone_brick_slab");
        assertEquals(2, catalog.families().size());
        assertEquals(4, catalog.family("minecraft:oak_fence").orElseThrow().members().size());
        assertEquals("minecraft:stone_bricks", catalog.family("minecraft:stone_brick_wall").orElseThrow().id());
    }
    @Test void keepsFinishesAndNamespacesSeparate() {
        var catalog = discover("minecraft:stone", "minecraft:stone_slab", "minecraft:smooth_stone", "minecraft:smooth_stone_slab",
                "other:stone", "other:stone_slab", "minecraft:copper_block", "minecraft:cut_copper", "minecraft:cut_copper_stairs");
        assertEquals(4, catalog.families().size());
        assertTrue(catalog.family("minecraft:copper_block").isEmpty());
        assertNotEquals(catalog.family("minecraft:stone"), catalog.family("other:stone"));
    }
    @Test void bambooUsesPlanksAndDoesNotConsumeBambooBlocks() {
        var catalog = discover("minecraft:bamboo_planks", "minecraft:bamboo_block", "minecraft:bamboo_stairs", "minecraft:bamboo_slab");
        assertEquals("minecraft:bamboo_planks", catalog.family("minecraft:bamboo_slab").orElseThrow().id());
        assertTrue(catalog.family("minecraft:bamboo_block").isEmpty());
    }
    @Test void recipesResolveIrregularNamesAndTaggedForms() {
        List<Shape> shapes = new ArrayList<>(SHAPES);
        shapes.set(2, new Shape("blockwheel:slab", "shape.blockwheel.slab", 1, 20, false,
                List.of("_slab"), List.of("example:slabs"), List.of()));
        var blocks = new ArrayList<>(blocks("example:marble"));
        blocks.add(new FamilyCatalog.BlockSpec("example:half_marble", Set.of("example:slabs"), false));
        var catalog = FamilyCatalog.discover(blocks, shapes, Map.of("example:half_marble", "example:marble"), List.of());
        assertEquals(2, catalog.family("example:half_marble").orElseThrow().members().size());
    }
    @Test void scopedSignsUseTaggedPlanksAndCannotAttachToANonwoodRecipeBase() {
        var sign = new Shape("blockwheel:sign", "shape.blockwheel.sign", 4, 60, false,
                List.of(), List.of("minecraft:standing_signs"), List.of(), List.of("minecraft:planks"));
        var definitions = new ArrayList<>(SHAPES); definitions.add(sign);
        var candidates = List.of(new FamilyCatalog.BlockSpec("newwood:planks", Set.of("minecraft:planks"), true),
                new FamilyCatalog.BlockSpec("newwood:notice_board", Set.of("minecraft:standing_signs"), false),
                new FamilyCatalog.BlockSpec("example:stone", Set.of(), true),
                new FamilyCatalog.BlockSpec("example:stone_sign", Set.of("minecraft:standing_signs"), false));
        var catalog = FamilyCatalog.discover(candidates, definitions,
                Map.of("newwood:notice_board", "newwood:planks", "example:stone_sign", "example:stone"), List.of());
        var family = catalog.family("newwood:notice_board").orElseThrow();
        assertEquals("newwood:planks", family.id()); assertEquals(4, family.shape("blockwheel:sign").orElseThrow().shape().units());
        assertTrue(catalog.family("example:stone_sign").isEmpty());
    }
    @Test void baseTagScopeAlsoAppliesToNamingFallbackAndCannotCreateABaselessFamily() {
        var sign = new Shape("blockwheel:sign", "shape.blockwheel.sign", 4, 60, false,
                List.of("_sign"), List.of(), List.of(), List.of("minecraft:planks"));
        var definitions = new ArrayList<>(SHAPES); definitions.add(sign);
        var candidates = new ArrayList<>(blocks("example:stone", "example:stone_stairs", "example:stone_sign", "example:orphan_sign"));
        candidates.add(new FamilyCatalog.BlockSpec("newwood:oak_planks", Set.of("minecraft:planks"), true));
        candidates.add(new FamilyCatalog.BlockSpec("newwood:oak_sign", Set.of(), false));
        var catalog = FamilyCatalog.discover(candidates, definitions, Map.of(), List.of());
        assertTrue(catalog.family("newwood:oak_sign").orElseThrow().member("newwood:oak_planks").isPresent());
        assertTrue(catalog.family("example:stone_sign").isEmpty()); assertTrue(catalog.family("example:orphan_sign").isEmpty());
    }
    @Test void newShapesNeedOnlyDataAndScaleToMoreOptions() {
        List<Shape> shapes = new ArrayList<>(SHAPES);
        shapes.add(shape("flooring", 1, false, List.of("_flooring")));
        var catalog = FamilyCatalog.discover(blocks("example:marble", "example:marble_slab", "example:marble_stairs",
                "example:marble_fence", "example:marble_wall", "example:marble_flooring"), shapes, Map.of(), List.of());
        assertEquals(6, catalog.family("example:marble_flooring").orElseThrow().members().size());
    }
    @Test void explicitOverridesDetachOldInferredRelationships() {
        var catalog = FamilyCatalog.discover(blocks("example:stone", "example:stone_slab", "example:stone_stairs", "example:special"), SHAPES, Map.of(),
                List.of(new FamilyCatalog.Override("example:custom", Map.of("blockwheel:block", "example:special", "blockwheel:slab", "example:stone_slab"), false)));
        assertEquals("example:custom", catalog.family("example:stone_slab").orElseThrow().id());
        assertEquals(2, catalog.family("example:stone").orElseThrow().members().size());
        assertTrue(catalog.family("example:stone").orElseThrow().shape("blockwheel:slab").isEmpty());
    }
    @Test void ambiguousFormsAreOmittedInsteadOfDependingOnIterationOrder() {
        var catalog = FamilyCatalog.discover(blocks("example:stone", "example:stone_slab", "example:stone_stairs", "example:odd_stairs"), SHAPES,
                Map.of("example:odd_stairs", "example:stone"), List.of());
        assertEquals(2, catalog.family("example:stone").orElseThrow().members().size());
        assertTrue(catalog.family("example:odd_stairs").isEmpty());
        assertTrue(catalog.family("example:stone_stairs").isEmpty());
    }
    @Test void refusesConflictingExplicitMembership() {
        var overrides = List.of(new FamilyCatalog.Override("example:a", Map.of("blockwheel:block", "example:stone"), false),
                new FamilyCatalog.Override("example:b", Map.of("blockwheel:block", "example:stone"), false));
        assertThrows(IllegalArgumentException.class, () -> FamilyCatalog.discover(blocks("example:stone"), SHAPES, Map.of(), overrides));
    }
}
