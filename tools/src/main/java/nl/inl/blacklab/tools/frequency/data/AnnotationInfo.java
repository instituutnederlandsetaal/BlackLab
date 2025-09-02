package nl.inl.blacklab.tools.frequency.data;

import java.util.Arrays;
import java.util.Set;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.tools.frequency.config.AnnotationConfig;
import nl.inl.blacklab.tools.frequency.config.FrequencyListConfig;
import nl.inl.blacklab.tools.frequency.config.MetadataConfig;

/**
 * Info about an annotation we're grouping on.
 */
public final class AnnotationInfo {
    private final AnnotatedField annotatedField;
    private final Annotation[] annotations;
    private final Terms[] terms;
    private final Annotation cutoffAnnotation;
    private final BlackLabIndex index;
    private final IdMap metaToId;
    private final IdMap wordToId;
    private final int[] groupedMetaIdx;
    private final int[] nonGroupedMetaIdx;
    private final FreqMetadata freqMetadata;

    public AnnotationInfo(final BlackLabIndex index, final FrequencyListConfig cfg) {
        this.index = index;
        this.annotatedField = index.annotatedField(cfg.annotatedField());
        this.annotations = cfg.annotations().stream().map((AnnotationConfig a) -> annotatedField.annotation(a.name()))
                .toArray(Annotation[]::new);
        this.terms = Arrays.stream(annotations).map(index::annotationForwardIndex).map(AnnotationForwardIndex::terms)
                .toArray(Terms[]::new);
        this.cutoffAnnotation = cfg.cutoff() != null ? annotatedField.annotation(cfg.cutoff().annotation()) : null;
        this.metaToId = new IdMap();
        this.wordToId = new IdMap();
        this.freqMetadata = new FreqMetadata(index, cfg);
        this.groupedMetaIdx = cfg.metadata().stream().filter(MetadataConfig::outputAsId)
                .mapToInt(m -> cfg.metadata().indexOf(m)).toArray();

        this.nonGroupedMetaIdx = cfg.metadata().stream().filter(m -> !m.outputAsId())
                .mapToInt(m -> cfg.metadata().indexOf(m)).toArray();
    }

    public static Set<String> UniqueTermsFromField(final BlackLabIndex index, final String field) {
        final var values = new ObjectLinkedOpenHashSet<String>();
        index.forEachDocument((__, id) -> {
            final var f = index.luceneDoc(id).getField(field);
            if (f != null)
                values.add(f.stringValue());
        });
        // return a sorted set for consistent ordering
        return values;
    }

    public Terms[] getTerms() {
        return terms;
    }

    public AnnotatedField getAnnotatedField() {
        return annotatedField;
    }

    public Annotation[] getAnnotations() {
        return annotations;
    }

    public AnnotationForwardIndex getForwardIndexOf(final Annotation annotation) {
        return index.annotationForwardIndex(annotation);
    }

    public Annotation getCutoffAnnotation() {
        return cutoffAnnotation;
    }

    public int[] getNonGroupedMetaIdx() {
        return nonGroupedMetaIdx;
    }

    public int[] getGroupedMetaIdx() {
        return groupedMetaIdx;
    }

    public IdMap getMetaToId() {
        return metaToId;
    }

    public IdMap getWordToId() {
        return wordToId;
    }

    public FreqMetadata getFreqMetadata() {
        return freqMetadata;
    }
}
