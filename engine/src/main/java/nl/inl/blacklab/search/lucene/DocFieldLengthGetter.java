package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NumericDocValues;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;

/**
 * Used to get the field length in tokens for a document.
 *
 * This is used by SpanQueryNot and SpanQueryExpansion to make sure we don't go
 * beyond the document end.
 *
 * This class is instantiated and used by a single Spans to get lengths for a single
 * index segment. It does not need to be thread-safe.
 */
public class DocFieldLengthGetter {

    /**
     * For testing, we don't have an IndexReader available, so we use test values
     */
    private boolean useTestValues = false;

    /** Lengths may have been cached using FieldCache */
    private final NumericDocValues cachedFieldLengths;

    public DocFieldLengthGetter(LeafReader reader, String fieldName) {
        // Field name to check for the length of the field in tokens
        String lengthTokensFieldName = AnnotatedFieldNameUtil.lengthTokensField(fieldName);

        // Cache the lengths to speed things up
        if (reader != null) {
            try {
                cachedFieldLengths = reader.getNumericDocValues(lengthTokensFieldName);
                if (cachedFieldLengths == null) {
                    // this is fine if there are no real documents in this segment (i.e. only metadata value doc)
                }
            } catch (IOException e) {
                throw BlackLabException.wrapRuntime(e);
            }
        } else {
            // Only used for test (all lengths are the same)
            cachedFieldLengths = null;
        }
    }

    /**
     * For testing, we don't have an IndexReader available, so we use test values.
     *
     * The test values are: there are 3 documents (0, 1 and 2) and each is 5 tokens
     * long.
     *
     * @param test whether or not we want to use test values
     */
    public void setTest(boolean test) {
        this.useTestValues = test;
    }

    /**
     * Get the number of indexed tokens for our field in the specified document.
     *
     * Used to produce all tokens that aren't hits in our clause.
     *
     * NOTE: this includes the "extra closing token" at the end that may contain punctuation
     * after the last word! You must subtract 1 for indices that have this extra closing
     * token (all recent indices do).
     *
     * @param doc the document
     * @return the number of tokens
     */
    public int getFieldLength(int doc) {

        if (useTestValues)
            return 6; // while testing, all documents have same length

        if (cachedFieldLengths == null) {
            // We must be in a segment that only contains the metadata doc.
            // Return 0 for the length, which is essentially correct.
            // (metadata doc contains no annotated field value).
            return 0;
        }

        try {
            if (cachedFieldLengths.advanceExact(doc)){
                return (int)cachedFieldLengths.longValue();
            }
            return 0;
        } catch (IOException e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }
}
