package nl.inl.blacklab.config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import nl.inl.blacklab.exceptions.InvalidConfiguration;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.util.Json;

public class BlackLabConfig {

    private static final Logger logger = LogManager.getLogger(BlackLabConfig.class);
    public static final List<String> CONFIG_EXTENSIONS = Arrays.asList("json", "yaml", "yml");

    private static BlackLabConfig read(Reader reader, Reader overrideReader, boolean isJson) throws InvalidConfiguration {
        try {
            ObjectMapper mapper = isJson ? Json.getJsonObjectMapper() : Json.getYamlObjectMapper();
            BlackLabConfig config = mapper.readValue(reader, BlackLabConfig.class);
            if (overrideReader != null) {
                mapper.readerForUpdating(config).readValue(overrideReader, BlackLabConfig.class);
            }
            return config;
        } catch (IOException e) {
            throw new InvalidConfiguration("Invalid configuration (" + e.getMessage() + ")", e);
        }
    }

    /**
     * Load the global blacklab configuration. This file configures several global
     * settings, as well as providing default settings for any new {@link BlackLabIndex}
     * constructed hereafter.
     *
     * If no explicit config file has been set by the time when the first BlackLabIndex
     * is opened, BlackLab automatically attempts to find and load a configuration
     * file in a number of preset locations.
     *
     * Attempting to set another configuration when one is already loaded will throw
     * an UnsupportedOperationException.
     *
     * @param file file to read
     * @param overrideFile override file to read (if any); will override settings from the main file
     * @return configuration configuration from file
     */
    public static synchronized BlackLabConfig readConfigFile(File file, File overrideFile) throws IOException {
        if (file == null || !file.canRead())
            throw new FileNotFoundException("Configuration file " + file + " is unreadable.");

        if (!FilenameUtils.isExtension(file.getName(), BlackLabConfig.CONFIG_EXTENSIONS))
            throw new InvalidConfiguration("Configuration file " + file + " is of an unsupported type.");

        boolean isJson = file.getName().endsWith(".json");
        String fileContents = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
        String overrideContents = overrideFile == null ? null :  FileUtils.readFileToString(overrideFile, StandardCharsets.UTF_8);

        if (overrideContents != null)
            logger.debug("Reading global BlackLab config from {} with overrides from {}", fileContents, overrideContents);
        else
            logger.debug("Reading global BlackLab config from {}", fileContents);
        return BlackLabConfig.read(new StringReader(fileContents),
                overrideContents == null ? null : new StringReader(overrideContents),
                isJson);
    }

    private int configVersion = 2;

    private BLConfigSearch search = new BLConfigSearch();

    private BLConfigIndexing indexing = new BLConfigIndexing();

    private BLConfigPlugins plugins = new BLConfigPlugins();

    BLConfigLog log = new BLConfigLog();

    /**
     * Feature flags: enable/disable certain (experimental) features.
     */
    Map<String, String> featureFlags = new HashMap<>();

    public int getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(int configVersion) {
        if (configVersion != 2)
            throw new InvalidConfiguration("Unsupported config version: " + configVersion + ". Expected 2.");
        this.configVersion = configVersion;
    }

    public BLConfigSearch getSearch() {
        return search;
    }

    public void setSearch(BLConfigSearch search) {
        this.search = search;
    }

    public BLConfigIndexing getIndexing() {
        return indexing;
    }

    public void setIndexing(BLConfigIndexing indexing) {
        this.indexing = indexing;
    }

    public BLConfigPlugins getPlugins() {
        return plugins;
    }

    public void setPlugins(BLConfigPlugins plugins) {
        this.plugins = plugins;
    }

    public BLConfigLog getLog() {
        return log;
    }

    public void setLog(BLConfigLog log) {
        this.log = log;
    }

    public Map<String, String> getFeatureFlags() {
        return featureFlags;
    }

    public void setFeatureFlags(Map<String, String> featureFlags) {
        this.featureFlags = featureFlags;
    }
}
