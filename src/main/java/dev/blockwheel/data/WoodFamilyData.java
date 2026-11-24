package dev.blockwheel.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.blockwheel.core.FamilyCatalog;
import dev.blockwheel.core.WoodFamilies;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.BlockItem;
import java.util.ArrayList;
import java.util.List;

/** Opt-in view derived from registered blocks and verified recipes; no recipe graph traversal. */
public final class WoodFamilyData {
    private WoodFamilyData() {}
    private record Recipe(String output, int count, List<String> ingredients) {}
    public static FamilyCatalog expand(ResourceManager resources, FamilyCatalog catalog) {
        var recipes = new ArrayList<Recipe>();
        resources.listResources("recipe", id -> id.getPath().endsWith(".json")).forEach((id, resource) -> {
            try (var reader = resource.openAsReader()) {
                var json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has("fabric:load_conditions") || json.has("conditions") || !json.has("result")) return;
                var output = json.getAsJsonObject("result"); if (output.has("components")) return;
                var inputs = new ArrayList<String>(); String type = json.get("type").getAsString();
                if (type.equals("minecraft:crafting_shapeless")) {
                    for (var ingredient : json.getAsJsonArray("ingredients")) inputs.add(simple(ingredient));
                } else if (type.equals("minecraft:crafting_shaped")) {
                    var key = json.getAsJsonObject("key");
                    for (var row : json.getAsJsonArray("pattern")) for (char symbol : row.getAsString().toCharArray())
                        if (symbol != ' ') inputs.add(simple(key.get(Character.toString(symbol))));
                } else return;
                if (!inputs.contains(null) && !inputs.isEmpty()) recipes.add(new Recipe(output.get("id").getAsString(),
                        output.has("count") ? output.get("count").getAsInt() : 1, List.copyOf(inputs)));
            } catch (Exception ignored) { /* Unsupported recipes are not proof of a material relationship. */ }
        });
        var relations = new ArrayList<WoodFamilies.Relation>();
        resources.listResources("blockwheel/wood_families", id -> id.getPath().endsWith(".json")).entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey()).forEach(entry -> {
            try (var reader = entry.getValue().openAsReader()) {
                var json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has("disabled") && json.get("disabled").getAsBoolean()) return;
                int perLog = json.get("planks_per_log").getAsInt(), logInput = json.get("log_input").getAsInt(), woodOutput = json.get("wood_output").getAsInt();
                int recipeInput = json.has("recipe_log_input") ? json.get("recipe_log_input").getAsInt() : logInput;
                int recipeOutput = json.has("recipe_wood_output") ? json.get("recipe_wood_output").getAsInt() : woodOutput;
                if (recipeInput < 1 || recipeInput > 16 || recipeOutput < 1 || recipeOutput > 16)
                    throw new IllegalArgumentException("Invalid wood recipe quantities");
                boolean proof = !json.has("require_recipes") || json.get("require_recipes").getAsBoolean();
                if (json.has("planks")) {
                    add(relations, recipes, json.get("planks").getAsString(), json.get("log").getAsString(),
                            json.has("wood") ? json.get("wood").getAsString() : null, perLog, logInput, woodOutput, recipeInput, recipeOutput, proof);
                } else {
                    String suffix = json.get("plank_suffix").getAsString();
                    if (suffix.isEmpty()) throw new IllegalArgumentException("Empty plank suffix");
                    for (var family : catalog.families()) {
                        var base = family.members().stream().filter(member -> member.shape().base()).findFirst().orElse(null);
                        if (base == null || !base.blockId().endsWith(suffix)) continue;
                        String stem = base.blockId().substring(0, base.blockId().length() - suffix.length());
                        for (var element : json.getAsJsonArray("pairs")) {
                            var pair = element.getAsJsonObject();
                            add(relations, recipes, base.blockId(), stem + pair.get("log_suffix").getAsString(),
                                    stem + pair.get("wood_suffix").getAsString(), perLog, logInput, woodOutput, recipeInput, recipeOutput, proof);
                        }
                    }
                }
            } catch (Exception exception) { throw new IllegalArgumentException("Invalid experimental wood relationships: " + entry.getKey(), exception); }
        });
        return WoodFamilies.expand(catalog, relations);
    }
    private static void add(List<WoodFamilies.Relation> result, List<Recipe> recipes, String planks, String log, String wood,
                            int perLog, int logInput, int woodOutput, int recipeInput, int recipeOutput, boolean proof) {
        if (!block(planks) || !block(log) || FamilySafety.excluded(log) || FamilySafety.excluded(planks)
                || !Identifier.parse(planks).getNamespace().equals(Identifier.parse(log).getNamespace())) return;
        if (proof && !plankRecipe(recipes, planks, log, perLog)) return;
        String availableWood = wood;
        if (wood != null && (!block(wood) || FamilySafety.excluded(wood)
                || !Identifier.parse(planks).getNamespace().equals(Identifier.parse(wood).getNamespace())
                || proof && ((long) recipeOutput * logInput > (long) recipeInput * woodOutput
                || !recipes.stream().anyMatch(recipe -> recipe.output().equals(wood) && recipe.count() == recipeOutput
                && recipe.ingredients().size() == recipeInput && recipe.ingredients().stream().allMatch(ingredient -> ingredient.equals(log)))
                // A 1:1 exchange is inferred only when both items have the same plank yield.
                || logInput == woodOutput && !plankRecipe(recipes, planks, wood, perLog)))) availableWood = null;
        result.add(new WoodFamilies.Relation(planks, log, availableWood, perLog, logInput, woodOutput));
    }
    private static boolean plankRecipe(List<Recipe> recipes, String planks, String input, int count) {
        return recipes.stream().anyMatch(recipe -> recipe.output().equals(planks) && recipe.count() == count
                && recipe.ingredients().size() == 1 && matches(recipe.ingredients().getFirst(), input));
    }
    private static boolean block(String id) {
        var block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
        return block.asItem() instanceof BlockItem item && item.getBlock() == block && !block.defaultBlockState().hasBlockEntity();
    }
    private static String simple(JsonElement value) {
        if (value == null) return null;
        if (value.isJsonPrimitive()) return value.getAsString();
        return value.isJsonObject() && value.getAsJsonObject().has("item") ? value.getAsJsonObject().get("item").getAsString() : null;
    }
    private static boolean matches(String ingredient, String block) {
        if (!ingredient.startsWith("#")) return ingredient.equals(block);
        return BuiltInRegistries.BLOCK.getValue(Identifier.parse(block)).builtInRegistryHolder().tags()
                .anyMatch(tag -> tag.location().toString().equals(ingredient.substring(1)));
    }
}
