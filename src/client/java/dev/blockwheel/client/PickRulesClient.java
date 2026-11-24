package dev.blockwheel.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.blockwheel.Blockwheel;
import dev.blockwheel.core.PickDefaults;
import dev.blockwheel.data.RegistryPickDefaults;
import dev.blockwheel.core.PickRules;
import dev.blockwheel.network.BuilderPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

/** Instance-wide JSON preferences; the server remains responsible for target and inventory access. */
public final class PickRulesClient {
    private static PickRules rules = PickRules.DEFAULT;
    private static String error;
    private static PickDefaults generated;
    private PickRulesClient() {}
    public static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("blockwheel-pick-rules.json"); }
    public static PickRules rules() { return rules; }
    public static String error() { return error; }
    public static PickDefaults defaults() { if (generated == null) generated = RegistryPickDefaults.create(); return generated; }
    public static void initialize() {
        if (Files.exists(path())) {
            if (!reload()) rules = new PickRules(false, false, List.of());
        } else save(PickRules.DEFAULT);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> send());
    }
    public static void send() {
        if (ClientPlayNetworking.canSend(BuilderPayloads.PickPreferences.TYPE)) ClientPlayNetworking.send(new BuilderPayloads.PickPreferences(rules));
    }
    public static boolean reload() {
        try {
            if (Files.size(path()) > 1_048_576) throw new IOException("Pick rule file is too large");
            var parsed = JsonParser.parseString(Files.readString(path(), StandardCharsets.UTF_8));
            rules = PickRules.CODEC.parse(JsonOps.INSTANCE, parsed).getOrThrow(); error = null; send(); return true;
        } catch (IOException | RuntimeException exception) {
            error = "ui.blockwheel.pick.load_failed";
            Blockwheel.LOGGER.warn("Could not load pick rules; keeping the file and current preferences", exception); return false;
        }
    }
    public static boolean save(PickRules value) {
        Path temporary = null;
        try {
            var json = PickRules.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow().getAsJsonObject();
            json.addProperty("version", 1); json.addProperty("enabled", value.enabled());
            json.addProperty("include_log_defaults", value.includeLogDefaults());
            json.addProperty("include_anvil_defaults", value.includeAnvilDefaults()); json.addProperty("include_copper_defaults", value.includeCopperDefaults());
            if (!json.has("rules")) json.add("rules", new JsonArray());
            Files.createDirectories(path().getParent()); temporary = Files.createTempFile(path().getParent(), ".blockwheel-pick-", ".tmp");
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n", StandardCharsets.UTF_8);
            try { Files.move(temporary, path(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, path(), StandardCopyOption.REPLACE_EXISTING); }
            rules = value; error = null; send(); return true;
        } catch (IOException | RuntimeException exception) {
            error = "ui.blockwheel.pick.save_failed"; Blockwheel.LOGGER.warn("Could not save pick rules", exception); return false;
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { /* Retain the original config on failure. */ }
        }
    }
}
