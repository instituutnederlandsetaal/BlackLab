package nl.inl.blacklab.tools.frequency.writers;

import java.io.File;
import java.io.IOException;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.tools.frequency.config.BuilderConfig;
import nl.inl.blacklab.tools.frequency.config.FreqListConfig;
import nl.inl.blacklab.tools.frequency.data.AnnotationInfo;
import nl.inl.util.Timer;

public final class LookupTableWriter extends FreqListWriter {
    public LookupTableWriter(final BlackLabIndex index, final BuilderConfig bCfg, final FreqListConfig fCfg) {
        super(bCfg, fCfg, new AnnotationInfo(index, bCfg, fCfg));
    }

    private File getFile(final Annotation annotation) {
        final String fileName = fCfg.getReportName() + "_" + annotation.name() + "_lookup" + getExt();
        return new File(bCfg.getOutputDir(), fileName);
    }

    public void write() {
        System.out.println("  Writing annotation id lookup tables");
        final var t = new Timer();
        for (final var annotation: aInfo.getAnnotations()) {
            // write individual lookup table for each annotation to a separate file
            final var file = getFile(annotation);
            try (final var csv = getCsvWriter(file)) {
                final var terms = aInfo.getTermsOf(annotation);
                // id is simply the index in the terms list
                for (int id = 0; id < terms.numberOfTerms(); id++) {
                    final String token = getToken(terms, id);
                    csv.writeRecord(String.valueOf(id), token);
                }
            } catch (final IOException e) {
                throw reportIOException(e);
            }
        }
        System.out.println("  Wrote annotation id lookup tables in " + t.elapsedDescription(true));
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

}
