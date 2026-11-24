package dev.blockwheel;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Function;

/** Real test-only modded blocks, including non-64 stack limits and a data-defined shape. */
public final class BuilderTestContent implements ModInitializer {
    public static Item BASE, STAIRS, SLAB, FLOOR;
    public static Item FOREST_PLANKS, FOREST_STAIRS, FOREST_SLAB, FOREST_LOG, FOREST_WOOD;
    @Override public void onInitialize() {
        var base = block("builder_marble", Block::new, 32);
        BASE = base.asItem();
        STAIRS = block("builder_marble_stairs", properties -> new Steps(base.defaultBlockState(), properties), 16).asItem();
        SLAB = block("builder_marble_slab", SlabBlock::new, 16).asItem();
        FLOOR = block("builder_marble_flooring", SlabBlock::new, 16).asItem();
        var planks = block("forest_planks", Block::new, 32); FOREST_PLANKS = planks.asItem();
        FOREST_STAIRS = block("forest_stairs", properties -> new Steps(planks.defaultBlockState(), properties), 16).asItem();
        FOREST_SLAB = block("forest_slab", SlabBlock::new, 16).asItem();
        FOREST_LOG = block("forest_log", Block::new, 64).asItem();
        FOREST_WOOD = block("forest_wood", Block::new, 64).asItem();
    }
    private static Block block(String path, Function<BlockBehaviour.Properties, Block> factory, int limit) {
        var id = Identifier.fromNamespaceAndPath("blockwheel-test", path);
        var block = Registry.register(BuiltInRegistries.BLOCK, id, factory.apply(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, id))));
        Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(block, new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)).stacksTo(limit)));
        return block;
    }
    private static final class Steps extends StairBlock {
        Steps(BlockState base, BlockBehaviour.Properties properties) { super(base, properties); }
    }
}
