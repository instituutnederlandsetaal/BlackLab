package nl.inl.blacklab.server.config;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.config.BlackLabConfig;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.server.exceptions.ConfigurationException;
import nl.inl.util.FileUtil;

/**
 * Finds and opens a config file to be read.
 */
public class ConfigFileReader {
    private static final Logger logger = LogManager.getLogger(ConfigFileReader.class);

    private static final Charset CONFIG_ENCODING = StandardCharsets.UTF_8;

    public static BLSConfig getBlsConfig(String configFileName) throws ConfigurationException {
        // Find config file
        File configFile = FileUtil.findFile(List.of(BlackLab.configDir()), configFileName, BlackLabConfig.CONFIG_EXTENSIONS);
        if (configFile == null)
            throw new ConfigurationException("Couldn't find blacklab-server.(json|yaml) in BlackLab config dir " +
                    BlackLab.configDir() + " .  See https://blacklab.ivdnt.org/server/configuration.html .");
        if (!configFile.canRead())
            throw new ConfigurationException("Config file found but not readable: " + configFileName);
        boolean isJson = configFile.getName().endsWith(".json");
        try {
            // Find override file
            File overrideFile = FileUtil.findFile(List.of(BlackLab.configDir()),
                    configFileName + BlackLab.OVERRIDE_FILE_SUFFIX, BlackLabConfig.CONFIG_EXTENSIONS);
            if (overrideFile != null && !overrideFile.canRead()) {
                throw new ConfigurationException("Override config file found but not readable: " + overrideFile);
            }
            if (overrideFile != null)
                logger.debug("Reading configuration file {} and override file {}", configFile, overrideFile);
            else
                logger.debug("Reading configuration file {}", configFile);
            Reader configReader = new StringReader(FileUtils.readFileToString(configFile, CONFIG_ENCODING));

            Reader overrideReader = null;
            if (overrideFile != null) {
                String overrideFileContents = FileUtils.readFileToString(overrideFile, CONFIG_ENCODING);
                overrideReader = new StringReader(overrideFileContents);
            }
            return BLSConfig.read(configReader, overrideReader, isJson);

        } catch (IOException e) {
            throw new ConfigurationException("Error reading config file: " + configFile, e);
        }

    }

}
