package nl.inl.blacklab.tools.frequency.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectRBTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;
import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.tools.frequency.config.BuilderConfig;
import nl.inl.blacklab.tools.frequency.config.FreqListConfig;
import nl.inl.blacklab.tools.frequency.config.MetadataConfig;
import nl.inl.util.LuceneUtil;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.util.BytesRef;

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

    public AnnotationInfo(final BlackLabIndex index, final BuilderConfig bCfg, final FreqListConfig fCfg) {
        this.index = index;
        this.annotatedField = index.annotatedField(bCfg.getAnnotatedField());
        this.annotations = fCfg.annotations().stream().map(annotatedField::annotation).toArray(Annotation[]::new);
        this.terms = Arrays.stream(annotations).map(index::annotationForwardIndex).map(AnnotationForwardIndex::terms)
                .toArray(Terms[]::new);
        this.cutoffAnnotation = fCfg.cutoff() != null ? annotatedField.annotation(fCfg.cutoff().annotation()) : null;
        this.metaToId = new IdMap();
        this.wordToId = new IdMap();
        this.freqMetadata = new FreqMetadata(index, fCfg);
        this.groupedMetaIdx = fCfg.metadataFields().stream().filter(MetadataConfig::outputAsId)
                .mapToInt(m -> fCfg.metadataFields().indexOf(m)).toArray();

        this.nonGroupedMetaIdx = fCfg.metadataFields().stream().filter(m -> !m.outputAsId())
                .mapToInt(m -> fCfg.metadataFields().indexOf(m)).toArray();
    }

    public static Set<String> UniqueTermsFromField(BlackLabIndex index, String field) {
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
