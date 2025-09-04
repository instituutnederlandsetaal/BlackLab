package nl.inl.blacklab.tools.frequency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.searches.SearchCacheDummy;
import nl.inl.blacklab.tools.frequency.config.ArgsParser;
import nl.inl.blacklab.tools.frequency.config.Config;
import nl.inl.blacklab.tools.frequency.counter.FrequencyCounter;
import nl.inl.blacklab.tools.frequency.data.helper.IndexHelper;
import nl.inl.util.Timer;

/**
 * Determine frequency lists over annotation(s) and metadata field(s) for the entire index.
 */
public class FrequencyTool {
    public static void main(final String[] args) throws ErrorOpeningIndex, JsonProcessingException {
        // read blacklab.yaml if exists and set config from that
        BlackLab.setConfigFromFile();
        // parse and verify args
        final var parsedArgs = ArgsParser.parse(args);
        // read config
        final var config = Config.fromFile(parsedArgs.configFile()).changeDir(parsedArgs.outputDir());
        // pretty print config
        final var json = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(config);
        System.out.println("Config: " + json);

        try (final var index = BlackLab.open(parsedArgs.indexDir())) {
            config.verify(index); // verify config
            // Generate the frequency lists
            final var t = new Timer();
            makeFrequencyLists(index, config);
            System.out.println("TOTAL TIME: " + t.elapsedDescription(true));
        }
    }

    private static void makeFrequencyLists(final BlackLabIndex index, final Config cfg) {
        for (final var fl: cfg.frequencyLists()) {
            final var t = new Timer();
            final var helper = IndexHelper.create(index, fl);
            final var counter = FrequencyCounter.create(index, fl, helper);
            counter.count();
            helper.writeDatabase(fl);
            System.out.println("  Generating " + fl.name() + " took " + t.elapsedDescription());
        }
    }
}
