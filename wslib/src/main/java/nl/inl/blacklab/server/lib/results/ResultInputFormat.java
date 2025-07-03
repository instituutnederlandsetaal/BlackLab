package nl.inl.blacklab.server.lib.results;

import java.io.BufferedReader;
import java.io.IOException;

import org.apache.commons.io.IOUtils;

import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.InputFormat;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.server.exceptions.NotFound;

public class ResultInputFormat {
    private final ConfigInputFormat config;

    ResultInputFormat(String formatName) {
        InputFormat inputFormat = DocumentFormats.getFormat(formatName).orElseThrow(
                () -> new NotFound("NOT_FOUND", "Format '" + formatName + "' does not exist."));
        if (!inputFormat.isConfigurationBased())
            throw new NotFound("NOT_FOUND", "Format '" + formatName
                    + "' is not configuration-based, and therefore cannot be displayed.");
        config = inputFormat.getConfig();
    }

    public ConfigInputFormat getConfig() {
        return config;
    }

    public String getXslt() {
        return XslGenerator.generateXsltFromConfig(config);
    }

    public String getFileContents() {
        // Read the format file
        try (BufferedReader reader = config.getFormatFile()) {
            return IOUtils.toString(reader);
        } catch (IOException e1) {
            throw new InvalidInputFormatConfig(e1);
        }
    }
}
