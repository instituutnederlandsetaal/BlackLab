package nl.inl.blacklab.tools.frequency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.searches.SearchCacheDummy;
import nl.inl.blacklab.tools.frequency.builder.FreqListBuilder;
import nl.inl.blacklab.tools.frequency.config.ArgsParser;
import nl.inl.blacklab.tools.frequency.config.Config;
import nl.inl.blacklab.tools.frequency.writers.AnnotationWriter;
import nl.inl.blacklab.tools.frequency.writers.LookupTableWriter;
import nl.inl.blacklab.tools.frequency.writers.MetaGroupWriter;
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
            index.setCache(new SearchCacheDummy()); // don't cache results
            // Generate the frequency lists
            final var t = new Timer();
            makeFrequencyLists(index, config);
            System.out.println("TOTAL TIME: " + t.elapsedDescription(true));
        }
    }

    private static void makeFrequencyLists(final BlackLabIndex index, final Config cfg) {
        for (final var fl: cfg.frequencyLists()) {
            final var t = new Timer();
            final var builder = FreqListBuilder.from(index, fl);
            builder.makeFrequencyList();
            // if database format, write lookup tables
            if (cfg.runConfig().databaseFormat()) {
                if (fl.ngramSize() == 1) {
                    new LookupTableWriter(index, fl).write();
                }
                new MetaGroupWriter(fl, builder.getAnnotationInfo()).write();
                new AnnotationWriter(fl, builder.getAnnotationInfo()).write();
            }
            System.out.println("  Generating " + fl.name() + " took " + t.elapsedDescription());
        }
    }
}
