package nl.inl.blacklab.forwardindex;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.codec.LeafReaderLookup;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

/**
 * Global forward index for single annotation (FIs integrated).
 * <p>
 * This implementation works with FIs integrated into the Lucene index.
 * <p>
 * Note that in the integrated case, there's no separate forward index id (fiid),
 * but instead the Lucene docId is used.
 */
public class AnnotationForwardIndexGlobal implements AnnotationForwardIndex {

    /**
     * Open an integrated forward index.
     *
     * @param annotation annotation for which we want to open the forward index
     * @return forward index
     */
    public static AnnotationForwardIndexGlobal open(BlackLabIndex index, Annotation annotation) {
        if (!annotation.hasForwardIndex())
            throw new IllegalArgumentException("Annotation doesn't have a forward index: " + annotation);
        LeafReaderLookup leafReaderLookup = index.getLeafReaderLookup();
        return new AnnotationForwardIndexGlobal(index, annotation, leafReaderLookup);
    }

    private final IndexReader indexReader;

    private final Annotation annotation;

    /** The Lucene field that contains our forward index */
    private final String luceneField;

    private final TermsGlobal terms;

    private boolean initialized = false;

    /** Index of segments by their doc base (the number to add to get global docId) */
    private final LeafReaderLookup leafReaderLookup;

    public AnnotationForwardIndexGlobal(BlackLabIndex index, Annotation annotation, LeafReaderLookup leafReaderLookup) {
        super();
        this.indexReader = index.reader();
        this.annotation = annotation;
        AnnotationSensitivity annotSens = annotation.hasSensitivity(
                MatchSensitivity.SENSITIVE) ?
                annotation.sensitivity(MatchSensitivity.SENSITIVE) :
                annotation.sensitivity(MatchSensitivity.INSENSITIVE);
        this.luceneField = annotSens.luceneField();

        // Ensure quick lookup of the segment we need
        this.leafReaderLookup = leafReaderLookup;
        terms = new TermsGlobal(luceneField);
    }

    @Override
    public synchronized Terms terms() {
        if (!initialized) {
            try {
                terms.initialize(indexReader);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // preserve interrupted status
                throw new ErrorOpeningIndex(e);
            }
            this.initialized = true;
        }
        return terms;
    }

    @Override
    public int[][] retrieveParts(int docId, int[] start, int[] end) {
        LeafReaderContext lrc = leafReaderLookup.forId(docId);
        AnnotationForwardIndex fi = FieldForwardIndex.get(lrc, luceneField);
        int[][] parts = fi.retrieveParts(docId - lrc.docBase, start, end);
        for (int[] part: parts) {
            terms().convertToGlobalTermIds(lrc, part);
        }
        return parts;
    }

    @Override
    public int[] retrievePart(int docId, int start, int end) {
        LeafReaderContext lrc = leafReaderLookup.forId(docId);
        AnnotationForwardIndex fi = FieldForwardIndex.get(lrc, luceneField);
        int[] part = fi.retrievePart(docId - lrc.docBase, start, end);
        terms().convertToGlobalTermIds(lrc, part);
        return part;
    }

    @Override
    public long docLength(int docId) {
        LeafReaderContext lrc = leafReaderLookup.forId(docId);
        AnnotationForwardIndex fi = FieldForwardIndex.get(lrc, luceneField);
        return (int)fi.docLength(docId - lrc.docBase);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + this.luceneField + ")";
    }

    @Override
    public String getLuceneFieldName() {
        return annotation.forwardIndexSensitivity().luceneField();
    }
}
