package nl.inl.blacklab.tools.frequency.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
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
    private final Annotation[] annotations;
    private final Terms[] terms;
    private final Annotation cutoffAnnotation;
    private final BlackLabIndex index;
    private final Object2IntOpenHashMap<List<String>> metaToId;
    private final Object2IntOpenCustomHashMap<int[]> wordToId;
    private int metaId = 1;
    private int wordId = 1;
    private final int[] groupedMetaIdx;
    private final int[] nonGroupedMetaIdx;

    public AnnotationInfo(final BlackLabIndex index, final BuilderConfig bCfg, final FreqListConfig fCfg) {
        this.index = index;
        this.annotatedField = index.annotatedField(bCfg.getAnnotatedField());
        this.annotations = fCfg.annotations().stream().map(annotatedField::annotation).toArray(Annotation[]::new);
        this.terms = Arrays.stream(annotations).map(index::annotationForwardIndex).map(AnnotationForwardIndex::terms)
                .toArray(Terms[]::new);
        this.cutoffAnnotation = fCfg.cutoff() != null ? annotatedField.annotation(fCfg.cutoff().annotation()) : null;
        this.metaToId = new Object2IntOpenHashMap<>();
        metaToId.defaultReturnValue(-1);
        this.wordToId = new Object2IntOpenCustomHashMap<>(IntArrays.HASH_STRATEGY);
        wordToId.defaultReturnValue(-1);
        this.groupedMetaIdx = fCfg.metadataFields().stream().filter(MetadataConfig::outputAsId)
                .mapToInt(m -> fCfg.metadataFields().indexOf(m)).toArray();
        this.nonGroupedMetaIdx = fCfg.metadataFields().stream().filter(m -> !m.outputAsId())
                .mapToInt(m -> fCfg.metadataFields().indexOf(m)).toArray();
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

    public Map<List<String>, Integer> getMetaToId() {
        return metaToId;
    }

    public Map<int[], Integer> getWordToId() {
        return wordToId;
    }

    public int putOrGetMetaToId(final String[] meta) {
        // calculate key
        final int idToPut = metaId;
        final var key = getMetaKey(meta);
        final int id = metaToId.putIfAbsent(key, idToPut);
        if (id == -1) {
            // new ID was created
            metaId++; // increment for next time
            return idToPut;

        } else {
            // existing ID
            return id;
        }
    }

    public int putOrGetWordId(final int[] tokens) {
        // calculate key
        final int idToPut = wordId;
        final int id = wordToId.putIfAbsent(tokens, idToPut);
        if (id == -1) {
            // new ID was created
            wordId++; // increment for next time
            return idToPut;

        } else {
            // existing ID
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
