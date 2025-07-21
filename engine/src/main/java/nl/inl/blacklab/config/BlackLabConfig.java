package nl.inl.blacklab.config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
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

    private static BlackLabConfig read(Reader reader, boolean isJson) throws InvalidConfiguration {
        try {
            ObjectMapper mapper = isJson ? Json.getJsonObjectMapper() : Json.getYamlObjectMapper();
            return mapper.readValue(reader, BlackLabConfig.class);
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
     * @return configuration configuration from file
     */
    public static synchronized BlackLabConfig readConfigFile(File file) throws IOException {
        if (file == null || !file.canRead())
            throw new FileNotFoundException("Configuration file " + file + " is unreadable.");

        if (!FilenameUtils.isExtension(file.getName(), Arrays.asList("yaml", "yml", "json")))
            throw new InvalidConfiguration("Configuration file " + file + " is of an unsupported type.");

        boolean isJson = file.getName().endsWith(".json");
        return readConfigFile(file.getCanonicalPath(), FileUtils.readFileToString(file, StandardCharsets.UTF_8), isJson);
    }

    /**
     * Read config file.
     *
     * @param fileName config file name
     * @param fileContents contents of the config file
     * @param isJson if true, reads JSON. Otherwise, reads YAML.
     */
    private static synchronized BlackLabConfig readConfigFile(String fileName, String fileContents, boolean isJson) throws InvalidConfiguration {
        logger.debug("Reading global BlackLab config");
        return BlackLabConfig.read(new StringReader(fileContents), isJson);
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
