package nl.inl.blacklab.search.textpattern;

import org.apache.lucene.index.Term;

import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanFuzzyQuery;

/**
 * A TextPattern matching a word with fuzzy matching.
 */
public class TextPatternFuzzy extends TextPattern {

    protected final String value;

    private final int maxEdits;

    private final int prefixLength;

    public TextPatternFuzzy(String value, int maxEdits) {
        this(value, maxEdits, 0);
    }

    public TextPatternFuzzy(String value, int maxEdits, int prefixLength) {
        this.value = value;
        this.maxEdits = maxEdits;
        this.prefixLength = prefixLength;
    }

    public Term getTerm(String fieldName) {
        return new Term(fieldName, value);
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) {
        int prefixLength1 = prefixLength;
        return new SpanFuzzyQuery(context.queryInfo(), new Term(context.luceneField(), context.optDesensitize(value)),
                maxEdits, prefixLength1);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternFuzzy tp) {
            return value.equals(tp.value) && maxEdits == tp.maxEdits && prefixLength == tp.prefixLength;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return value.hashCode() + 13 * maxEdits + 31 * prefixLength;
    }

    @Override
    public String toString() {
        return "FUZZY(" + value + ", " + maxEdits + ", " + prefixLength + ")";
    }
}
