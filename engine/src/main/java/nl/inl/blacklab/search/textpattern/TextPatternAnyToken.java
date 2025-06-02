package nl.inl.blacklab.search.textpattern;

import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryAnyToken;

/**
 * A 'gap' of a number of tokens we don't care about, with minimum and maximum
 * length.
 *
 * This may be used to implement a 'wildcard' token in a pattern language.
 */
public class TextPatternAnyToken extends TextPattern {
    /*
     * The minimum number of tokens in this stretch.
     */
    protected final int min;

    /*
     * The maximum number of tokens in this stretch.
     */
    protected final int max;

    public TextPatternAnyToken(int min, int max) {
        this.min = min;
        this.max = max;
    }

    public static TextPattern anyNGram() {
        return new TextPatternAnyToken(0, MAX_UNLIMITED);
    }

    public TextPattern repeat(int nmin, int nmax) {
        if (min == 1 && max == 1)
            return new TextPatternAnyToken(nmin, nmax);
        return TextPatternRepetition.get(this, nmin, nmax);
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) {
        return new SpanQueryAnyToken(context.queryInfo(), min, max, context.luceneField());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternAnyToken tp) {
            return min == tp.min && max == tp.max;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return min + 31 * max;
    }

    @Override
    public String toString() {
        return "ANYTOKEN(" + min + ", " + BLSpanQuery.inf(max) + ")";
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }
}
