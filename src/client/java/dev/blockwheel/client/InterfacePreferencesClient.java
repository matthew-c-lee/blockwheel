package dev.blockwheel.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.blockwheel.Blockwheel;
import dev.blockwheel.core.InterfacePreferences;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Instance-wide UI preferences; never sent to the server. */
public final class InterfacePreferencesClient {
    private static InterfacePreferences settings = InterfacePreferences.DEFAULT;
    private static String error;
    private InterfacePreferencesClient() {}
    public static InterfacePreferences settings() { return settings; }
    public static String error() { return error; }
    public static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("blockwheel-client.json"); }
    public static void initialize() { if (Files.exists(path())) reload(); else save(InterfacePreferences.DEFAULT); }
    public static boolean reload() {
        try {
            if (Files.size(path()) > 16_384) throw new IOException("Interface preferences are too large");
            var json = JsonParser.parseString(Files.readString(path(), StandardCharsets.UTF_8));
            settings = InterfacePreferences.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(); error = null; return true;
        } catch (IOException | RuntimeException exception) {
            error = "ui.blockwheel.interface.load_failed";
            Blockwheel.LOGGER.warn("Could not load interface preferences; keeping the file and current preferences", exception); return false;
        }
    }
    public static boolean save(InterfacePreferences value) {
        Path temporary = null;
        try {
            var json = InterfacePreferences.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow().getAsJsonObject();
            json.addProperty("version", 1); json.addProperty("hide_button_tooltips", value.hideButtonTooltips());
            json.addProperty("hide_builder_messages", value.hideBuilderMessages());
            json.addProperty("show_builder_button", value.showBuilderButton()); json.addProperty("show_shulker_refill_controls", value.showShulkerRefillControls());
            Files.createDirectories(path().getParent()); temporary = Files.createTempFile(path().getParent(), ".blockwheel-ui-", ".tmp");
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n", StandardCharsets.UTF_8);
            try { Files.move(temporary, path(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, path(), StandardCopyOption.REPLACE_EXISTING); }
            settings = value; error = null; return true;
        } catch (IOException | RuntimeException exception) {
            error = "ui.blockwheel.interface.save_failed";
            Blockwheel.LOGGER.warn("Could not save interface preferences", exception); return false;
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { /* Keep the original file on failure. */ }
        }
    }
}
