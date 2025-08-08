package nl.inl.blacklab.forwardindex;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexIntegrated;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

/**
 * Forward index for single annotation (FIs integrated).
 *
 * This implementation works with FIs integrated into the Lucene index.
 *
 * Note that in the integrated case, there's no separate forward index id (fiid),
 * but instead the Lucene docId is used.
 */
public class AnnotationForwardIndexIntegrated implements AnnotationForwardIndex {

    /**
     * Open an integrated forward index.
     *
     * @param annotation annotation for which we want to open the forward index
     * @param collator collator to use
     * @return forward index
     */
    public static AnnotationForwardIndex open(ForwardIndex forwardIndex, BlackLabIndex index, Annotation annotation, Collator collator) {
        if (!annotation.hasForwardIndex())
            throw new IllegalArgumentException("Annotation doesn't have a forward index: " + annotation);

        Collators collators = new Collators(collator);
        return new AnnotationForwardIndexIntegrated(forwardIndex, index, annotation, collators);
    }

    /** The 'parent' forward index object */
    private final ForwardIndex forwardIndex;

    private final BlackLabIndex index;

    private final IndexReader indexReader;

    private final Annotation annotation;

    /** The Lucene field that contains our forward index */
    private final String luceneField;

    /** Collators to use for comparisons */
    private final Collators collators;

    private Terms terms;

    private boolean initialized = false;

    public AnnotationForwardIndexIntegrated(ForwardIndex forwardIndex, BlackLabIndex index, Annotation annotation, Collators collators) {
        super();
        this.forwardIndex = forwardIndex;
        this.indexReader = index.reader();
        this.index = index;
        this.annotation = annotation;
        this.collators = collators;
        AnnotationSensitivity annotSens = annotation.hasSensitivity(
                MatchSensitivity.SENSITIVE) ?
                annotation.sensitivity(MatchSensitivity.SENSITIVE) :
                annotation.sensitivity(MatchSensitivity.INSENSITIVE);
        this.luceneField = annotSens.luceneField();
    }

    @Override
    public ForwardIndex getParent() {
        return forwardIndex;
    }

    @Override
    public synchronized void initialize() {
        if (initialized) {
            return;
        }

        try {
            this.terms = new TermsIntegrated(collators, indexReader, luceneField);
            this.initialized = true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // preserve interrupted status
            throw new InterruptedSearch("Intialization of Forward Index was interrupted", e);
        }
    }

    @Override
    public Terms terms() {
        initialize();
        return terms;
    }

    @Override
    public List<int[]> retrievePartsInt(int globalDocId, int[] start, int[] end) {
        initialize();
        LeafReaderContext lrc = index.getLeafReaderContext(globalDocId);
        List<int[]> segmentResults = BlackLabIndexIntegrated.forwardIndex(lrc)
                .retrieveParts(luceneField, globalDocId - lrc.docBase, start, end);

        List<int[]> results = new ArrayList<>();
        for (int[] snippet: segmentResults) {
            results.add(terms.segmentIdsToGlobalIds(lrc.ord, snippet));
        }
        return results;
    }

    @Override
    public Annotation annotation() {
        return annotation;
    }

    @Override
    public Collators collators() {
        return collators;
    }

    @Override
    public String toString() {
        return "AnnotationForwardIndexIntegrated (" + this.luceneField + ")";
    }
}
