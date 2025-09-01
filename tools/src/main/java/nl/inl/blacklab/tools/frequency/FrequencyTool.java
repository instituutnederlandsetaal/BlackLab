package nl.inl.blacklab.tools.frequency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.searches.SearchCacheDummy;
import nl.inl.blacklab.tools.frequency.builder.IndexBasedBuilder;
import nl.inl.blacklab.tools.frequency.builder.SearchBasedBuilder;
import nl.inl.blacklab.tools.frequency.config.CliArgs;
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
        final var cliArgs = CliArgs.parse(args);
        // read config
        final var config = Config.fromFile(cliArgs.configFile(), cliArgs.outputDir());
        // pretty print config
        final var json = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(config);
        System.out.println("Config: " + json);

        try (final BlackLabIndex index = BlackLab.open(cliArgs.indexDir())) {
            config.verify(index); // verify config
            index.setCache(new SearchCacheDummy()); // don't cache results
            // Generate the frequency lists
            final var t = new Timer();
            makeFrequencyLists(index, config);
            System.out.println("TOTAL TIME: " + t.elapsedDescription(true));
        }
    }

    private static void makeFrequencyLists(final BlackLabIndex index, final Config cfg) {
        for (final var fCfg: cfg.frequencyLists()) {
            final var t = new Timer();
            final var builder = cfg.runConfig().regularSearch() ?
                    new SearchBasedBuilder(index, cfg, fCfg) :
                    new IndexBasedBuilder(index, cfg, fCfg);
            builder.makeFrequencyList();
            // if database format, write lookup tables
            if (cfg.runConfig().databaseFormat()) {
                if (fCfg.ngramSize() == 1) {
                    new LookupTableWriter(index, cfg, fCfg).write();
                }
                new MetaGroupWriter(cfg, fCfg, builder.getAnnotationInfo()).write();
                new AnnotationWriter(cfg, fCfg, builder.getAnnotationInfo()).write();
            }
            System.out.println("  Generating " + fCfg.name() + " took " + t.elapsedDescription());
        }
    }
}
