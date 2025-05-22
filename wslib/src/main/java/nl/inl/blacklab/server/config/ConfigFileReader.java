package nl.inl.blacklab.server.config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import nl.inl.blacklab.exceptions.InvalidConfiguration;
import nl.inl.blacklab.server.exceptions.ConfigurationException;
import nl.inl.util.FileUtil;
import nl.inl.util.Json;

/**
 * Finds and opens a config file to be read.
 */
public class ConfigFileReader {
    private static final Logger logger = LogManager.getLogger(ConfigFileReader.class);

    public static final Charset CONFIG_ENCODING = StandardCharsets.UTF_8;

    private static final List<String> CONFIG_EXTENSIONS = Arrays.asList("json", "yaml", "yml");

    public static BLSConfig getBlsConfig(List<File> searchDirs, String configFileName) throws ConfigurationException {
        ConfigFileReader cfr = new ConfigFileReader(searchDirs, configFileName);
        return cfr.getConfig();
    }

    private String configFileRead = "(none)";

    private String configFileContents = null;

    private boolean configFileIsJson;

    private final JsonNode configFileJsonNode;

    public ConfigFileReader(List<File> searchDirs, String configFileName) throws ConfigurationException {
        configFileIsJson = false;

        File configFile = FileUtil.findFile(searchDirs, configFileName, CONFIG_EXTENSIONS);
        if (configFile != null && configFile.canRead()) {
            logger.debug("Reading configuration file " + configFile);
            try {
                configFileContents = FileUtils.readFileToString(configFile, CONFIG_ENCODING);
                configFileRead = configFile.getAbsolutePath();
            } catch (FileNotFoundException e) {
                throw new ConfigurationException("Config file not found", e);
            } catch (IOException e) {
                throw new ConfigurationException("Error reading config file: " + configFile, e);
            }
            configFileIsJson = configFile.getName().endsWith(".json");
        }

        if (configFileContents == null) {
            logger.debug(configFileName + ".(json|yaml) not found in webapps dir; searching classpath...");

            for (String ext : CONFIG_EXTENSIONS) {
                InputStream is = getClass().getClassLoader().getResourceAsStream(configFileName + "." + ext);
                if (is == null)
                    continue;

                logger.debug("Reading configuration file from classpath: " + configFileName);
                try {
                    configFileContents = IOUtils.toString(is, CONFIG_ENCODING);
                } catch (IOException e) {
                    throw new ConfigurationException("Error reading config file from classpath: " + configFileName, e);
                }
                configFileIsJson = ext.equals("json");
                configFileRead = configFileName + "." + ext + " (from classpath)";
                break;
            }
        }

        if (configFileContents == null) {
            String descDirs = StringUtils.join(searchDirs, ", ");
            throw new ConfigurationException("Couldn't find blacklab-server.(json|yaml) in dirs " + descDirs
                    + ", or on classpath. Please place this configuration file in one of these locations. "
                    + "See https://blacklab.ivdnt.org/server/configuration.html#minimal-config-file for a "
                    + "minimal configuration file.");
        } else {
            ObjectMapper mapper = isJson() ? Json.getJsonObjectMapper() : Json.getYamlObjectMapper();
            try {
                configFileJsonNode = mapper.readTree(new StringReader(configFileContents));
            } catch (IOException e) {
                throw new ConfigurationException("Error parsing config file: " + configFileRead, e);
            }
        }
    }

    public boolean isJson() {
        return configFileIsJson;
    }

    public BLSConfig getConfig() throws InvalidConfiguration {
        return BLSConfig.read(new StringReader(configFileContents), isJson());
    }

}
