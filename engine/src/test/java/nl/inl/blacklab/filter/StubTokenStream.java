package nl.inl.blacklab.filter;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

public class StubTokenStream extends TokenStream {
    private final CharTermAttribute ta;

    private int i = -1;

    private final String[] terms;

    public StubTokenStream(String[] terms) {
        this.terms = terms;
        ta = addAttribute(CharTermAttribute.class);
        PositionIncrementAttribute pa = addAttribute(PositionIncrementAttribute.class);
        pa.setPositionIncrement(1);
    }

    @Override
    public final boolean incrementToken() {
        i++;
        if (i >= terms.length)
            return false;
        ta.copyBuffer(terms[i].toCharArray(), 0, terms[i].length());
        return true;
    }

}
