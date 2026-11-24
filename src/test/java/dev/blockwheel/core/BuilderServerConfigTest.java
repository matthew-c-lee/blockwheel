package dev.blockwheel.core;

import dev.blockwheel.server.BuilderServerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class BuilderServerConfigTest {
    @TempDir Path directory;
    @Test void defaultFileAndExplicitRemoteDenialAreReadFromServerConfiguration() throws Exception {
        var path = directory.resolve("blockwheel-server.json");
        assertTrue(BuilderServerConfig.load(path).allowRemoteEnderChestSupply()); assertTrue(Files.exists(path));
        Files.writeString(path, "{\"allow_remote_ender_chest_supply\":false}");
        assertFalse(BuilderServerConfig.load(path).allowRemoteEnderChestSupply());
    }
    @Test void malformedPolicyFailsClosedWithoutOverwritingTheAdministratorsFile() throws Exception {
        var path = directory.resolve("blockwheel-server.json"); String invalid = "{\"allow_remote_ender_chest_supply\":\"true\"}";
        Files.writeString(path, invalid);
        assertFalse(BuilderServerConfig.load(path).allowRemoteEnderChestSupply()); assertEquals(invalid, Files.readString(path));
    }
}
