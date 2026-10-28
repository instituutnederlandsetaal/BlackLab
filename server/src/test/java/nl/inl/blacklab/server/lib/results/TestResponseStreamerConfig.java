package nl.inl.blacklab.server.lib.results;

import java.io.IOException;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;

import nl.inl.blacklab.server.config.BLSConfig;
import nl.inl.blacklab.server.datastream.DataFormat;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.datastream.DataStreamAbstract;
import nl.inl.util.Json;

public class TestResponseStreamerConfig {

    private JsonNode serializeConfig(boolean debugMode) throws IOException {
        BLSConfig config = new BLSConfig();
        config.setIndexLocations(List.of("/path/to/index"));
        config.setUserIndexes("/path/to/user-indexes");

        DataStream ds = DataStreamAbstract.create(DataFormat.JSON, false, ApiVersion.CURRENT);
        ResponseStreamer rs = ResponseStreamer.get(ds, ApiVersion.CURRENT);
        rs.config(config, debugMode);
        return Json.getJsonObjectMapper().readTree(ds.getOutput());
    }

    @Test
    public void testConfigIncludesPathsInDebugMode() throws IOException {
        JsonNode configJson = serializeConfig(true);
        Assert.assertTrue(configJson.has("indexLocations"));
        Assert.assertTrue(configJson.has("userIndexes"));
        Assert.assertEquals("/path/to/user-indexes", configJson.get("userIndexes").asText());
    }

    @Test
    public void testConfigOmitsPathsWithoutDebugMode() throws IOException {
        JsonNode configJson = serializeConfig(false);
        Assert.assertFalse(configJson.has("indexLocations"));
        Assert.assertFalse(configJson.has("userIndexes"));
    }
}
