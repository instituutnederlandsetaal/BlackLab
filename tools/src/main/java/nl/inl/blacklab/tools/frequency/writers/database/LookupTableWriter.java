package nl.inl.blacklab.tools.frequency.writers.database;

import java.io.File;
import java.io.IOException;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.tools.frequency.config.frequency.FrequencyListConfig;
import nl.inl.blacklab.tools.frequency.data.helper.AnnotationHelper;
import nl.inl.blacklab.tools.frequency.writers.FreqListWriter;
import nl.inl.util.Timer;

public final class LookupTableWriter extends FreqListWriter {
    public LookupTableWriter(final FrequencyListConfig cfg) {
        super(cfg);
    }

    private static String getToken(final Terms terms, final int id) {
        final var sb = new StringBuilder(MatchSensitivity.INSENSITIVE.desensitize(terms.get(id)));
        // Escape any \ with \\
        final int len = sb.length();
        for (int i = 0; i < len; i++) {
            if (sb.charAt(i) == '\\') {
                sb.replace(i, i + 1, "\\\\");
                i++; // skip the next char, which is now escaped
            }
        }
        return sb.toString();
    }

    private File getFile(final Annotation annotation) {
        final String fileName = cfg.name() + "_" + cfg.annotationPrettyName(annotation) + getExt();
        return new File(cfg.runConfig().outputDir(), fileName);
    }

    public void write(final AnnotationHelper helper) {
        if (cfg.ngramSize() != 1) {
            System.out.println("  Ngram size is not 1, skipping annotation id lookup tables.");
            return;
        }
        final var annotations = helper.annotations();
        if (annotations.isEmpty()) {
            System.out.println("  No annotations found, skipping annotation id lookup tables.");
            return;
        }
        final var t = new Timer();
        for (int i = 0; i < annotations.size(); i++) {
            final var annotation = annotations.get(i);
            // write individual lookup table for each annotation to a separate file
            final var file = getFile(annotation);
            try (final var csv = getCsvWriter(file)) {
                final var terms = helper.forwardIndices().get(i).terms();
                // id is simply the index in the terms list
                for (int id = 1, len = terms.numberOfTerms(); id < len; id++) {
                    final String token = getToken(terms, id);
                    final int sortedID = terms.idToSortPosition(id, MatchSensitivity.INSENSITIVE);
                    csv.writeRecord(String.valueOf(sortedID), token);
                }
            } catch (final IOException e) {
                throw reportIOException(e);
            }
        }
        System.out.println("  Wrote annotation id lookup tables in " + t.elapsedDescription(true));
    }
}
