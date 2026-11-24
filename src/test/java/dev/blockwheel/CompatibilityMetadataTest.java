package dev.blockwheel;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** Regression coverage for the dependency rejection reported with release 1.0.0. */
class CompatibilityMetadataTest {
    private JsonObject dependencies() throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream("fabric.mod.json")) {
            assertNotNull(stream, "The processed release metadata must be on the test classpath");
            var metadata = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("blockwheel", metadata.get("id").getAsString());
            return metadata.getAsJsonObject("depends");
        }
    }

    private boolean accepts(String dependency, String version) throws Exception {
        String predicate = dependencies().get(dependency).getAsString();
        return VersionPredicate.parse(predicate).test(Version.parse(version));
    }

    @Test void acceptsTheVersionsFromTheReportedLaunchFailure() throws Exception {
        assertTrue(accepts("fabricloader", "0.19.5"));
        assertTrue(accepts("fabric-api", "0.160.0+26.2"));
        assertTrue(accepts("minecraft", "26.2"));
    }

    @Test void continuesToAcceptThePreviouslyTestedNewerVersions() throws Exception {
        assertTrue(accepts("fabricloader", "0.20.1"));
        assertTrue(accepts("fabric-api", "0.161.0+26.2"));
    }

    @Test void rejectsVersionsBelowTheTestedBaseline() throws Exception {
        assertFalse(accepts("fabricloader", "0.19.4"));
        assertFalse(accepts("fabric-api", "0.159.0+26.2"));
        assertFalse(accepts("minecraft", "26.1"));
    }
}
