package nl.inl.blacklab.tools.frequency.config;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.tools.frequency.config.frequency.FrequencyListConfig;

/**
 * Configuration for making frequency lists.
 *
 * @param runConfig      How frequency lists generation should be run.
 * @param frequencyLists Frequency lists to make.
 */
public record Config(
        RunConfig runConfig,
        List<FrequencyListConfig> frequencyLists
) {
    public Config {
        frequencyLists = frequencyLists.stream().map((f) -> f.changeRunConfig(runConfig)).toList();
    }

    /**
     * Read config from file.
     */
    public static Config fromFile(final File file) {
        try {
            final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            return mapper.readValue(file, Config.class);
        } catch (final IOException e) {
            throw new BlackLabRuntimeException("Error reading config file " + file, e);
        }
    }

    /**
     * Verify config for duplicate frequency list names and verifies each list.
     */
    public void verify(final BlackLabIndex index) {
        final var names = new HashSet<String>();
        for (final var fl: frequencyLists) {
            final String name = fl.name();
            if (names.contains(name))
                throw new IllegalArgumentException("Frequency list occurs twice: " + name);
            names.add(name);
            fl.verify(index);
        }
    }

    public Config changeDir(final File dir) {
        return new Config(runConfig.changeDir(dir), frequencyLists);
    }
}
