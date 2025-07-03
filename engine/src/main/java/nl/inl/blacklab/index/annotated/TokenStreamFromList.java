package nl.inl.blacklab.index.annotated;

import java.util.Iterator;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.util.BytesRef;
import org.eclipse.collections.api.IntIterable;
import org.eclipse.collections.api.iterator.IntIterator;

/**
 * Takes an {@code Iterable<String>} and iterates through it as a TokenStream.
 *
 * The Strings are taken as terms, and the position increment is always 1.
 */
public class TokenStreamFromList extends TokenStream {

    /** Iterator over the terms */
    protected final Iterator<String> iterator;

    /** Iterator over the position increments */
    private final IntIterator incrementIt;

    /** Iterator over the payloads, if any */
    private Iterator<BytesRef> payloadIt = null;

    /**
     * Term text of the current token
     */
    protected final CharTermAttribute termAttr;

    /**
     * Position increment of the current token
     */
    protected final PositionIncrementAttribute positionIncrementAttr;

    /**
     * Payload of the current token
     */
    protected PayloadAttribute payloadAttr = null;

    public TokenStreamFromList(Iterable<String> tokens, IntIterable increments, Iterable<BytesRef> payload) {
        clearAttributes();
        termAttr = addAttribute(CharTermAttribute.class);
        positionIncrementAttr = addAttribute(PositionIncrementAttribute.class);
        positionIncrementAttr.setPositionIncrement(1);

        iterator = tokens.iterator();
        incrementIt = increments == null ? null : increments.intIterator();
        if (payload != null) {
            payloadAttr = addAttribute(PayloadAttribute.class);
            payloadIt = payload.iterator();
        }
    }

    @Override
    public final boolean incrementToken() {
        // Capture token contents
        if (iterator.hasNext()) {
            String word = iterator.next();
            termAttr.copyBuffer(word.toCharArray(), 0, word.length());
            positionIncrementAttr.setPositionIncrement(incrementIt == null ? 1 : incrementIt.next());
            if (payloadAttr != null) {
                if (payloadIt.hasNext())
                    payloadAttr.setPayload(payloadIt.next());
                else
                    payloadAttr.setPayload(null);
            }
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((incrementIt == null) ? 0 : incrementIt.hashCode());
        result = prime * result + ((iterator == null) ? 0 : iterator.hashCode());
        result = prime * result + ((payloadAttr == null) ? 0 : payloadAttr.hashCode());
        result = prime * result + ((payloadIt == null) ? 0 : payloadIt.hashCode());
        result = prime * result + ((positionIncrementAttr == null) ? 0 : positionIncrementAttr.hashCode());
        result = prime * result + ((termAttr == null) ? 0 : termAttr.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        TokenStreamFromList other = (TokenStreamFromList) obj;
        if (incrementIt == null) {
            if (other.incrementIt != null)
                return false;
        } else if (!incrementIt.equals(other.incrementIt))
            return false;
        if (iterator == null) {
            if (other.iterator != null)
                return false;
        } else if (!iterator.equals(other.iterator))
            return false;
        if (payloadAttr == null) {
            if (other.payloadAttr != null)
                return false;
        } else if (!payloadAttr.equals(other.payloadAttr))
            return false;
        if (payloadIt == null) {
            if (other.payloadIt != null)
                return false;
        } else if (!payloadIt.equals(other.payloadIt))
            return false;
        if (positionIncrementAttr == null) {
            if (other.positionIncrementAttr != null)
                return false;
        } else if (!positionIncrementAttr.equals(other.positionIncrementAttr))
            return false;
        if (termAttr == null) {
            if (other.termAttr != null)
                return false;
        } else if (!termAttr.equals(other.termAttr))
            return false;
        return true;
    }

}
