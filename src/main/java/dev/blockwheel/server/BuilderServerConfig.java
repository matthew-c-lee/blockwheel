package dev.blockwheel.server;

import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Server-owned policy; a client preference cannot grant remote ender access. */
public record BuilderServerConfig(boolean allowRemoteEnderChestSupply) {
    public static BuilderServerConfig load() {
        return load(FabricLoader.getInstance().getConfigDir().resolve("blockwheel-server.json"));
    }
    public static BuilderServerConfig load(Path path) {
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, "{\n  \"allow_remote_ender_chest_supply\": true\n}\n");
                return new BuilderServerConfig(true);
            }
            var object = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            var value = object.get("allow_remote_ender_chest_supply");
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
                throw new IllegalArgumentException("allow_remote_ender_chest_supply must be a boolean");
            }
            return new BuilderServerConfig(value.getAsBoolean());
        } catch (Exception error) {
            org.slf4j.LoggerFactory.getLogger("blockwheel").error("Blockwheel server policy could not be loaded; remote ender access is disabled", error);
            return new BuilderServerConfig(false);
        }
    }
}
