package nl.inl.blacklab.forwardindex;

import java.text.Collator;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.codec.BLTerms;
import nl.inl.blacklab.exceptions.InterruptedSearch;
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
    public static AnnotationForwardIndex open(ForwardIndex forwardIndex, BlackLabIndex index, Annotation annotation, Collator collator) {
        if (!annotation.hasForwardIndex())
            throw new IllegalArgumentException("Annotation doesn't have a forward index: " + annotation);

        Collators collators = new Collators(collator);
        return new AnnotationForwardIndexImpl(index, annotation, collators);
    }

    private final IndexReader indexReader;

    private final Annotation annotation;

    /** The Lucene field that contains our forward index */
    private final String luceneField;

    /** Collators to use for comparisons */
    private final Collators collators;

    private TermsIntegrated terms;

    private boolean initialized = false;

    public AnnotationForwardIndexImpl(BlackLabIndex index, Annotation annotation, Collators collators) {
        super();
        this.indexReader = index.reader();
        this.annotation = annotation;
        this.collators = collators;
        AnnotationSensitivity annotSens = annotation.hasSensitivity(
                MatchSensitivity.SENSITIVE) ?
                annotation.sensitivity(MatchSensitivity.SENSITIVE) :
                annotation.sensitivity(MatchSensitivity.INSENSITIVE);
        this.luceneField = annotSens.luceneField();
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
    public int numberOfTerms() {
        int numberOfTerms = 0;
        for (LeafReaderContext lrc: indexReader.leaves()) {
            Terms terms = BLTerms.forSegment(lrc, luceneField).reader();
            // FIXME: INCORRECT, we're counting terms multiple times this way.
            numberOfTerms += terms.numberOfTerms();
        }
        return numberOfTerms;
    }

    @Override
    public Annotation annotation() {
        return annotation;
    }

    @Override
    public String toString() {
        return "AnnotationForwardIndexIntegrated (" + this.luceneField + ")";
    }
}
