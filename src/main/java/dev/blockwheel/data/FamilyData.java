package dev.blockwheel.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.blockwheel.Blockwheel;
import dev.blockwheel.core.FamilyCatalog;
import dev.blockwheel.core.Shape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Reads effective data-pack resources on startup and /reload, including mod-provided packs. */
public final class FamilyData {
    private FamilyData() {}

    public static FamilyCatalog load(ResourceManager resources) {
        List<Shape> shapes = new ArrayList<>();
        jsonResources(resources, "blockwheel/shapes").forEach((id, json) -> {
            if (bool(json, "disabled", false)) return;
            shapes.add(new Shape(id, text(json, "translation_key", "shape." + id.replace(':', '.').replace('/', '.')),
                    json.get("units").getAsInt(), json.has("order") ? json.get("order").getAsInt() : 100,
                    bool(json, "base", false), strings(json, "suffixes"), strings(json, "block_tags"), strings(json, "base_suffixes"), strings(json, "base_tags")));
        });
        List<FamilyCatalog.BlockSpec> blocks = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!(block.asItem() instanceof BlockItem blockItem) || blockItem.getBlock() != block) continue;
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            Set<String> tags = new HashSet<>();
            block.builtInRegistryHolder().tags().forEach(tag -> tags.add(tag.location().toString()));
            boolean cube = !block.defaultBlockState().hasBlockEntity()
                    && block.defaultBlockState().isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            blocks.add(new FamilyCatalog.BlockSpec(id, tags, cube));
        }
        Map<String, FamilyCatalog.BlockSpec> lookup = new HashMap<>();
        blocks.forEach(block -> lookup.put(block.id(), block));
        Map<String, String> recipes = recipeBases(resources, shapes, lookup);
        List<FamilyCatalog.Override> overrides = new ArrayList<>();
        jsonResources(resources, "blockwheel/families").forEach((id, json) -> {
            Map<String, String> members = new TreeMap<>();
            if (json.has("members")) json.getAsJsonObject("members").entrySet().forEach(entry -> {
                String shape = identifier(entry.getKey());
                String block = identifier(entry.getValue().getAsString());
                members.put(shape, block);
            });
            overrides.add(new FamilyCatalog.Override(id, members, bool(json, "disabled", false)));
        });
        return FamilySafety.apply(FamilyCatalog.discover(blocks, shapes, recipes, overrides));
    }

    private static Map<String, String> recipeBases(ResourceManager resources, List<Shape> shapes,
                                                  Map<String, FamilyCatalog.BlockSpec> blocks) {
        Map<String, String> result = new HashMap<>();
        Set<String> conflicts = new HashSet<>();
        resources.listResources("recipe", id -> id.getPath().endsWith(".json")).forEach((id, resource) -> {
            try (var reader = resource.openAsReader()) {
                JsonObject recipe = JsonParser.parseReader(reader).getAsJsonObject();
                // Stonecutting can change a material's finish, so it is deliberately not an equivalence relation.
                if (!text(recipe, "type", "").equals("minecraft:crafting_shaped")) return;
                JsonElement output = recipe.get("result");
                String target = output != null && output.isJsonObject() ? text(output.getAsJsonObject(), "id", "") : "";
                if (output != null && output.isJsonObject() && output.getAsJsonObject().has("components")) return;
                var outputBlock = blocks.get(target);
                if (outputBlock == null || shapes.stream().noneMatch(shape -> shape.matches(target, outputBlock.tags()))) return;
                if (!recipe.has("key")) return;
                Set<String> materials = new HashSet<>();
                for (JsonElement ingredient : recipe.getAsJsonObject("key").asMap().values()) {
                    String item = simpleIngredient(ingredient);
                    if (item == null) return; // Alternatives and tags are not a unique material relationship.
                    if (item.equals("minecraft:stick") || item.equals("minecraft:nether_brick")) continue;
                    var block = blocks.get(item);
                    if (block == null || !block.fullCube() || shapes.stream().anyMatch(shape -> shape.matches(item, block.tags()))) return;
                    materials.add(item);
                }
                if (materials.size() == 1) {
                    String base = materials.iterator().next();
                    String previous = result.putIfAbsent(target, base);
                    if (previous != null && !previous.equals(base)) conflicts.add(target);
                }
            } catch (Exception error) {
                Blockwheel.LOGGER.debug("Cannot infer family from recipe {}", id, error);
            }
        });
        conflicts.forEach(result::remove);
        return result;
    }

    private static String simpleIngredient(JsonElement ingredient) {
        if (ingredient.isJsonPrimitive()) {
            String value = ingredient.getAsString();
            return value.startsWith("#") ? null : value;
        }
        if (ingredient.isJsonObject() && ingredient.getAsJsonObject().has("item")) {
            return ingredient.getAsJsonObject().get("item").getAsString();
        }
        return null;
    }

    private static Map<String, JsonObject> jsonResources(ResourceManager resources, String directory) {
        Map<String, JsonObject> result = new TreeMap<>();
        resources.listResources(directory, id -> id.getPath().endsWith(".json")).forEach((id, resource) -> {
            String path = id.getPath().substring(directory.length() + 1, id.getPath().length() - 5);
            try (var reader = resource.openAsReader()) {
                result.put(id.getNamespace() + ":" + path, JsonParser.parseReader(reader).getAsJsonObject());
            } catch (Exception error) {
                throw new IllegalArgumentException("Invalid Blockwheel data: " + id, error);
            }
        });
        return result;
    }

    private static String identifier(String value) { return Identifier.parse(value).toString(); }
    private static String text(JsonObject json, String key, String fallback) { return json.has(key) ? json.get(key).getAsString() : fallback; }
    private static boolean bool(JsonObject json, String key, boolean fallback) { return json.has(key) ? json.get(key).getAsBoolean() : fallback; }
    private static List<String> strings(JsonObject json, String key) {
        if (!json.has(key)) return List.of();
        return json.getAsJsonArray(key).asList().stream().map(JsonElement::getAsString).toList();
    }
}
