package nl.inl.blacklab.analysis;

import java.io.IOException;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

import nl.inl.util.StringUtil;

/**
 * Removes any accents from the input.
 *
 * NOTE: Lucene includes ASCIIFoldingFilter, but this works with non-ASCII
 * characters too.
 */
public class RemoveAllAccentsFilter extends TokenFilter {

    private final CharTermAttribute termAtt;

    /**
     * @param input the token stream from which to remove accents
     */
    public RemoveAllAccentsFilter(TokenStream input) {
        super(input);
        termAtt = addAttribute(CharTermAttribute.class);
    }

    @Override
    public final boolean incrementToken() throws IOException {
        if (input.incrementToken()) {
            String t = new String(termAtt.buffer(), 0, termAtt.length());
            t = StringUtil.stripAccents(t);
            termAtt.copyBuffer(t.toCharArray(), 0, t.length());
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((termAtt == null) ? 0 : termAtt.hashCode());
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
        RemoveAllAccentsFilter other = (RemoveAllAccentsFilter) obj;
        if (termAtt == null) {
            if (other.termAtt != null)
                return false;
        } else if (!termAtt.equals(other.termAtt))
            return false;
        return true;
    }

}
