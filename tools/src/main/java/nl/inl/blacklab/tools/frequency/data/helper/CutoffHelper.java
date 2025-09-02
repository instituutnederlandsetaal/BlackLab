package nl.inl.blacklab.tools.frequency.data.helper;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import nl.inl.blacklab.forwardindex.Terms;

import nl.inl.blacklab.search.indexmetadata.AnnotatedField;

import nl.inl.blacklab.search.indexmetadata.Annotation;

import org.apache.lucene.search.IndexSearcher;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.tools.frequency.config.frequency.FrequencyListConfig;
import nl.inl.util.LuceneUtil;
import nl.inl.util.Timer;

public record CutoffHelper(
        Set<String> aboveCutoff,
        Terms terms
) {
    public static CutoffHelper create(final BlackLabIndex index, final FrequencyListConfig cfg, final AnnotatedField annotatedField) {
        final var annotation = annotatedField.annotation(cfg.cutoff().annotation());
        final Set<String> termsAboveCutoff = calculateCutoff(index, cfg, annotation);
        final var terms = index.annotationForwardIndex(annotation).terms();
        return new CutoffHelper(termsAboveCutoff, terms);
    }

    private static Set<String> calculateCutoff(BlackLabIndex index, FrequencyListConfig cfg, Annotation annotation) {
        final Set<String> termsAboveCutoff = new ObjectOpenHashSet<>();
        if (cfg.cutoff() != null) {
            final var t = new Timer();
            final var sensitivity = annotation.sensitivity(MatchSensitivity.SENSITIVE);
            final var searcher = new IndexSearcher(index.reader());
            final Map<String, Integer> termFrequenciesMap = LuceneUtil.termFrequencies(searcher, null, sensitivity,
                    Collections.emptySet());
            // Only save the strings when the frequency is above the cutoff
            for (final Map.Entry<String, Integer> entry: termFrequenciesMap.entrySet()) {
                if (entry.getValue() >= cfg.cutoff().count()) {
                    termsAboveCutoff.add(entry.getKey());
                }
            }
            final String logging = " Retrieved " + termFrequenciesMap.size() + " term frequencies " +
                    "for annotation '" + cfg.cutoff().annotation() + "' with " +
                    termsAboveCutoff.size() + " above cutoff in " + t.elapsedDescription(true);
            System.out.println(logging);
        }
        return termsAboveCutoff;
    }
}
