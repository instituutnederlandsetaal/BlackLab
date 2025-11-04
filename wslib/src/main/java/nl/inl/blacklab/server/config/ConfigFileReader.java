package nl.inl.blacklab.server.config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.exceptions.InvalidConfiguration;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.server.exceptions.ConfigurationException;
import nl.inl.util.FileUtil;

/**
 * Finds and opens a config file to be read.
 */
public class ConfigFileReader {
    private static final Logger logger = LogManager.getLogger(ConfigFileReader.class);

    public static final Charset CONFIG_ENCODING = StandardCharsets.UTF_8;

    private static final List<String> CONFIG_EXTENSIONS = Arrays.asList("json", "yaml", "yml");

    public static BLSConfig getBlsConfig(String configFileName) throws ConfigurationException {
        File configFile = FileUtil.findFile(List.of(BlackLab.configDir()), configFileName, CONFIG_EXTENSIONS);
        if (configFile == null) {
            throw new ConfigurationException("Couldn't find blacklab-server.(json|yaml) in BlackLab config dir " +
                    BlackLab.configDir() + " .  See https://blacklab.ivdnt.org/server/configuration.html .");
        }
        if (!configFile.canRead()) {
            throw new ConfigurationException("Config file found but not readable: " + configFileName);
        }
        ConfigFileReader cfr = new ConfigFileReader(configFile);
        return cfr.getConfig();
    }

    private String configFileContents;

    private boolean configFileIsJson;

    public ConfigFileReader(File configFile) throws ConfigurationException {
        if (configFile == null || !configFile.canRead())
            throw new ConfigurationException("Config file not found or not readable: " + configFile);
        configFileIsJson = false;
        logger.debug("Reading configuration file " + configFile);
        try {
            configFileContents = FileUtils.readFileToString(configFile, CONFIG_ENCODING);
        } catch (FileNotFoundException e) {
            throw new ConfigurationException("Config file not found", e);
        } catch (IOException e) {
            throw new ConfigurationException("Error reading config file: " + configFile, e);
        }
        configFileIsJson = configFile.getName().endsWith(".json");
    }

    public boolean isJson() {
        return configFileIsJson;
    }

    public BLSConfig getConfig() throws InvalidConfiguration {
        return BLSConfig.read(new StringReader(configFileContents), isJson());
    }

}
