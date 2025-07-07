package nl.inl.blacklab.tools.frequency.data;

import java.util.ArrayList;
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
    private final Map<ArrayList<String>, Integer> metaToId;
    private final AtomicInteger metaId = new AtomicInteger(0);
    private final Integer[] groupedMetaIdx;
    private final Integer[] nonGroupedMetaIdx;

    public AnnotationInfo(final BlackLabIndex index, final BuilderConfig bCfg, final FreqListConfig fCfg) {
        this.index = index;
        this.annotatedField = index.annotatedField(bCfg.getAnnotatedField());
        this.annotations = fCfg.annotations().stream().map(annotatedField::annotation).toList();
        this.terms = annotations.stream().map(index::annotationForwardIndex).map(AnnotationForwardIndex::terms)
                .toList();
        this.cutoffAnnotation = fCfg.cutoff() != null ? annotatedField.annotation(fCfg.cutoff().annotation()) : null;
        this.metaToId = new ConcurrentHashMap<>();
        this.groupedMetaIdx = fCfg.metadataFields().stream().filter(MetadataConfig::outputAsId)
                .map(m -> fCfg.metadataFields().indexOf(m)).toArray(Integer[]::new);
        this.nonGroupedMetaIdx = fCfg.metadataFields().stream().filter(m -> !m.outputAsId())
                .map(m -> fCfg.metadataFields().indexOf(m)).toArray(Integer[]::new);
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

    public Integer[] getGroupedMetaIdx() {
        return groupedMetaIdx;
    }

    public Integer[] getNonGroupedMetaIdx() {
        return nonGroupedMetaIdx;
    }

    public Map<ArrayList<String>, Integer> getMetaToId() {
        return metaToId;
    }

    public void putMetaToId(DocumentMetadata meta) {
        // retrieve key
        ArrayList<String> key = getMetaKey(meta.values());
        if (!metaToId.containsKey(key)) {
            int id = metaId.getAndIncrement();
            metaToId.put(key, id);
        }
    }

    private ArrayList<String> getMetaKey(String[] meta) {
        ArrayList<String> key = new ArrayList<>(groupedMetaIdx.length);
        for (int i = 0; i < groupedMetaIdx.length; i++) {
            key.add(i, meta[groupedMetaIdx[i]]);
        }
        return key;
    }

    public int getMetaId(String[] meta) {
        return metaToId.get(getMetaKey(meta));
    }
}
