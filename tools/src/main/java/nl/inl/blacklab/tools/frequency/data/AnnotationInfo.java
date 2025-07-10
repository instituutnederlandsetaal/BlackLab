package nl.inl.blacklab.tools.frequency.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.tools.frequency.config.BuilderConfig;
import nl.inl.blacklab.tools.frequency.config.FreqListConfig;
import nl.inl.blacklab.tools.frequency.config.MetadataConfig;

/**
 * Info about an annotation we're grouping on.
 */
public final class AnnotationInfo {
    private final AnnotatedField annotatedField;
    private final List<Annotation> annotations;
    private final List<Terms> terms;
    private final Annotation cutoffAnnotation;
    private final BlackLabIndex index;
    private final Map<List<String>, Integer> metaToId;
    private final Map<List<Integer>, Integer> wordToId;
    private final AtomicInteger metaId = new AtomicInteger(0);
    private final AtomicInteger wordId = new AtomicInteger(0);
    private final int[] groupedMetaIdx;
    private final int[] nonGroupedMetaIdx;

    public AnnotationInfo(final BlackLabIndex index, final BuilderConfig bCfg, final FreqListConfig fCfg) {
        this.index = index;
        this.annotatedField = index.annotatedField(bCfg.getAnnotatedField());
        this.annotations = fCfg.annotations().stream().map(annotatedField::annotation).toList();
        this.terms = annotations.stream().map(index::annotationForwardIndex).map(AnnotationForwardIndex::terms)
                .toList();
        this.cutoffAnnotation = fCfg.cutoff() != null ? annotatedField.annotation(fCfg.cutoff().annotation()) : null;
        this.metaToId = new ConcurrentHashMap<>();
        this.wordToId = new ConcurrentHashMap<>();
        this.groupedMetaIdx = fCfg.metadataFields().stream().filter(MetadataConfig::outputAsId)
                .mapToInt(m -> fCfg.metadataFields().indexOf(m)).toArray();
        this.nonGroupedMetaIdx = fCfg.metadataFields().stream().filter(m -> !m.outputAsId())
                .mapToInt(m -> fCfg.metadataFields().indexOf(m)).toArray();
    }

    public List<Terms> getTerms() {
        return terms;
    }

    public AnnotatedField getAnnotatedField() {
        return annotatedField;
    }

    public List<Annotation> getAnnotations() {
        return annotations;
    }

    public AnnotationForwardIndex getForwardIndexOf(final Annotation annotation) {
        return index.annotationForwardIndex(annotation);
    }

    public Terms getTermsOf(final Annotation annotation) {
        return terms.get(annotations.indexOf(annotation));
    }

    public Annotation getCutoffAnnotation() {
        return cutoffAnnotation;
    }

    public int[] getNonGroupedMetaIdx() {
        return nonGroupedMetaIdx;
    }

    public Map<List<String>, Integer> getMetaToId() {
        return metaToId;
    }

    public Map<List<Integer>, Integer> getWordToId() {
        return wordToId;
    }

    public int putOrGetMetaToId(final String[] meta) {
        // calculate key
        // TODO compare with Arrays.asList(meta).stream(... etc.)
        final var key = getMetaKey(meta);
        if (metaToId.containsKey(key)) {
            // return ID if it exists
            return metaToId.get(key);
        } else {
            // otherwise, create ID and return
            final int id = metaId.getAndIncrement();
            metaToId.put(key, id);
            return id;
        }
    }

    public int putOrGetWordId(final int[] tokens) {
        // calculate key
        final var key = Arrays.asList(Arrays.stream(tokens).boxed().toArray(Integer[]::new));
        if (wordToId.containsKey(key)) {
            // return ID if it exists
            return wordToId.get(key);
        } else {
            // otherwise, create ID and return
            final int id = wordId.getAndIncrement();
            wordToId.put(key, id);
            return id;
        }
    }

    private ArrayList<String> getMetaKey(final String[] meta) {
        final var key = new ArrayList<String>(groupedMetaIdx.length);
        for (int i = 0; i < groupedMetaIdx.length; i++) {
            key.add(i, meta[groupedMetaIdx[i]]);
        }
        return key;
    }
}
