package nl.inl.blacklab.index.annotated;

import java.util.Iterator;
import java.util.List;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.eclipse.collections.api.iterator.IntIterator;
import org.eclipse.collections.api.list.primitive.IntList;

/**
 * Takes a List&lt;String&gt; plus two List&lt;Integer&gt;'s and iterates
 * through them as a TokenStream.
 *
 * The Strings are taken as terms. The two integer-lists are taken as start
 * chars and end chars. Token position increment is always 1.
 */
class TokenStreamWithOffsets extends TokenStream {
    /**
     * Term text of the current token
     */
    protected final CharTermAttribute termAttr;

    /**
     * Position increment of the current token
     */
    protected final PositionIncrementAttribute positionIncrementAttr;

    /**
     * Character offsets of the current token
     */
    private final OffsetAttribute offsetAttr;

    protected final Iterator<String> iterator;

    protected final IntIterator incrementIt;

    private final IntIterator startCharIt;

    private final IntIterator endCharIt;
    
    private int currentStartChar = -1;

    private int currentEndChar = -1;

    public TokenStreamWithOffsets(List<String> tokens, IntList increments, IntList startChar,
            IntList endChar) {
        assert onlyIncrease(startChar, endChar);
        clearAttributes();
        termAttr = addAttribute(CharTermAttribute.class);
        offsetAttr = addAttribute(OffsetAttribute.class);
        positionIncrementAttr = addAttribute(PositionIncrementAttribute.class);
        positionIncrementAttr.setPositionIncrement(1);

        iterator = tokens.iterator();
        incrementIt = increments.intIterator();
        startCharIt = startChar.intIterator();
        endCharIt = endChar.intIterator();
    }

    private boolean onlyIncrease(IntList startChar, IntList endChar) {
        int prevStart = -1;
        int prevEnd = -1;
        for (int i = 0; i < startChar.size(); i++) {
            int start = startChar.get(i);
            int end = endChar.get(i);
            if (start < prevStart || end < prevEnd) {
                return false;
            }
            prevStart = start;
            prevEnd = end;
        }
        return true;
    }

    @Override
    public final boolean incrementToken() {
        // Capture token contents
        if (iterator.hasNext()) {
            // Set the term and position increment
            String term = iterator.next();
            termAttr.copyBuffer(term.toCharArray(), 0, term.length());
            int positionIncrement = incrementIt.next();
            positionIncrementAttr.setPositionIncrement(positionIncrement);
            
            // Find the appropriate start and end chars and set the offset
            for (int i = 0; i < positionIncrement; i++) {
                currentStartChar = startCharIt.next();
                currentEndChar = endCharIt.next();
            }
            offsetAttr.setOffset(currentStartChar, currentEndChar);
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((endCharIt == null) ? 0 : endCharIt.hashCode());
        result = prime * result + ((incrementIt == null) ? 0 : incrementIt.hashCode());
        result = prime * result + ((iterator == null) ? 0 : iterator.hashCode());
        result = prime * result + ((offsetAttr == null) ? 0 : offsetAttr.hashCode());
        result = prime * result + ((positionIncrementAttr == null) ? 0 : positionIncrementAttr.hashCode());
        result = prime * result + ((startCharIt == null) ? 0 : startCharIt.hashCode());
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
        TokenStreamWithOffsets other = (TokenStreamWithOffsets) obj;
        if (endCharIt == null) {
            if (other.endCharIt != null)
                return false;
        } else if (!endCharIt.equals(other.endCharIt))
            return false;
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
        if (offsetAttr == null) {
            if (other.offsetAttr != null)
                return false;
        } else if (!offsetAttr.equals(other.offsetAttr))
            return false;
        if (positionIncrementAttr == null) {
            if (other.positionIncrementAttr != null)
                return false;
        } else if (!positionIncrementAttr.equals(other.positionIncrementAttr))
            return false;
        if (startCharIt == null) {
            if (other.startCharIt != null)
                return false;
        } else if (!startCharIt.equals(other.startCharIt))
            return false;
        if (termAttr == null) {
            if (other.termAttr != null)
                return false;
        } else if (!termAttr.equals(other.termAttr))
            return false;
        return true;
    }

}
