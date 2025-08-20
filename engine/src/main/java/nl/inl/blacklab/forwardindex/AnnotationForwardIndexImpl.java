package nl.inl.blacklab.forwardindex;

import java.util.List;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.codec.LeafReaderLookup;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

/**
 * Global forward index for single annotation (FIs integrated).
 *
 * This implementation works with FIs integrated into the Lucene index.
 *
 * Note that in the integrated case, there's no separate forward index id (fiid),
 * but instead the Lucene docId is used.
 */
public class AnnotationForwardIndexImpl implements AnnotationForwardIndex {

    /**
     * Open an integrated forward index.
     *
     * @param annotation annotation for which we want to open the forward index
     * @param collator collator to use
     * @return forward index
     */
    public static AnnotationForwardIndex open(BlackLabIndex index, Annotation annotation) {
        if (!annotation.hasForwardIndex())
            throw new IllegalArgumentException("Annotation doesn't have a forward index: " + annotation);

        Collators collators = new Collators(index.collator());
        LeafReaderLookup leafReaderLookup = index.getLeafReaderLookup();
        return new AnnotationForwardIndexImpl(index, annotation, collators, leafReaderLookup);
    }

    private final IndexReader indexReader;

    private final Annotation annotation;

    /** The Lucene field that contains our forward index */
    private final String luceneField;

    /** Collators to use for comparisons */
    private final Collators collators;

    private TermsIntegrated terms;

    private boolean initialized = false;

    /** Index of segments by their doc base (the number to add to get global docId) */
    private final LeafReaderLookup leafReaderLookup;

    public AnnotationForwardIndexImpl(BlackLabIndex index, Annotation annotation, Collators collators, LeafReaderLookup leafReaderLookup) {
        super();
        this.indexReader = index.reader();
        this.annotation = annotation;
        this.collators = collators;
        AnnotationSensitivity annotSens = annotation.hasSensitivity(
                MatchSensitivity.SENSITIVE) ?
                annotation.sensitivity(MatchSensitivity.SENSITIVE) :
                annotation.sensitivity(MatchSensitivity.INSENSITIVE);
        this.luceneField = annotSens.luceneField();

        // Ensure quick lookup of the segment we need
        this.leafReaderLookup = leafReaderLookup;
    }

    @Override
    public synchronized void initialize() {
        if (initialized) {
            return;
        }

        TermsIntegratedRef ref = TermsIntegratedRef.get(indexReader, luceneField);
        this.terms = ref.get();
        this.initialized = true;
    }

    @Override
    public Terms terms() {
        initialize();
        return terms;
    }

    @Override
    public List<int[]> retrieveParts(int docId, int[] start, int[] end) {
        initialize();
        LeafReaderContext lrc = leafReaderLookup.forId(docId);
        AnnotForwardIndex fi = FieldForwardIndex.get(lrc, luceneField);
        List<int[]> parts = fi.retrieveParts(docId - lrc.docBase, start, end);
        Terms segmentTerms = fi.terms();
        parts.stream().forEach(segmentTerms::convertToGlobalTermIds);
        return parts;
    }

    @Override
    public int[] retrievePart(int docId, int start, int end) {
        initialize();
        LeafReaderContext lrc = leafReaderLookup.forId(docId);
        AnnotForwardIndex fi = FieldForwardIndex.get(lrc, luceneField);
        int[] part = fi.retrievePart(docId - lrc.docBase, start, end);
        fi.terms().convertToGlobalTermIds(part);
        return part;
    }

    @Override
    public long docLength(int docId) {
        LeafReaderContext lrc = leafReaderLookup.forId(docId);
        AnnotForwardIndex fi = FieldForwardIndex.get(lrc, luceneField);
        return (int)fi.docLength(docId - lrc.docBase);
    }

    @Override
    public Annotation annotation() {
        return annotation;
    }

//    @Override
//    public Collators collators() {
//        return collators;
//    }
//
//    @Override
//    public int numDocs() {
//        return indexReader.numDocs();
//    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + this.luceneField + ")";
    }

    @Override
    public String getLuceneFieldName() {
        return annotation.forwardIndexSensitivity().luceneField();
    }
}
