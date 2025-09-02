package nl.inl.blacklab.tools.frequency.data.helper;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.tools.frequency.config.frequency.FrequencyListConfig;
import nl.inl.blacklab.tools.frequency.writers.database.AnnotationWriter;
import nl.inl.blacklab.tools.frequency.writers.database.LookupTableWriter;
import nl.inl.blacklab.tools.frequency.writers.database.MetaGroupWriter;

/**
 * The helper index maps config strings to BlackLab Annotations,
 * calculates which tokens are cut off, and generates IDs for annotations (database format).
 */
public record IndexHelper(
        CutoffHelper cutoff,
        AnnotationHelper annotations,
        DatabaseHelper database
) {
    public static IndexHelper create(final BlackLabIndex index, final FrequencyListConfig cfg) {
        final var annotations = AnnotationHelper.create(index, cfg);
        final var database = DatabaseHelper.create(index, cfg);
        final var cutoff = cfg.cutoff() == null ? null : CutoffHelper.create(index, cfg, annotations.annotatedField());
        return new IndexHelper(cutoff, annotations, database);
    }

    public void writeDatabase(final FrequencyListConfig cfg) {
        // if database format, write lookup tables
        if (cfg.runConfig().databaseFormat()) {
            new LookupTableWriter(cfg).write(annotations);
            new MetaGroupWriter(cfg).write(database);
            new AnnotationWriter(cfg).write(database.wordToId());
        }
    }
}
