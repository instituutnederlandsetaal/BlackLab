package nl.inl.blacklab.search.textpattern;

import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.extensions.XFSpans;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryDefaultValue;

/**
 * A default value in a query, that will be replaced by something based on context.
 *
 * In a function call, it will be replaced by the default value for that parameter.
 * For an operand of the relation operator --reltype--> it will be replaced with []* ("any span")
 * In any other place, it will produce an error.
 */
public class TextPatternDefaultValue extends TextPattern {

    private static final TextPatternDefaultValue instance = new TextPatternDefaultValue();

    public static TextPatternDefaultValue get() { return instance; }

    private TextPatternDefaultValue() {}

    /**
     * In certain contexts, the default value (_) should be replaced with []*
     *
     * Specifically, this will replace a default value, optionally captured in a
     * group, but no other structure.
     *
     * @param parent the pattern
     * @return same pattern, or new pattern with default value replaced
     */
    public static TextPattern replaceWithAnyToken(TextPattern parent) {
        if (parent instanceof TextPatternDefaultValue) {
            // e.g. [...] --> _
            return TextPatternAnyToken.anyNGram();
        } else if (parent instanceof TextPatternCaptureGroup cg) {
            TextPattern clause = replaceWithAnyToken(cg.getClause());
            if (clause != cg.getClause()) { // if default value was replaced...
                // e.g. [...] --> A:_
                return new TextPatternCaptureGroup(clause, cg.getCaptureName());
            }
        }
        return parent;
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        return new SpanQueryDefaultValue(context.queryInfo(), context.luceneField());
    }

    @Override
    public boolean equals(Object obj) {
        return obj == instance;
    }

    @Override
    public int hashCode() {
        return TextPatternDefaultValue.class.hashCode();
    }

    @Override
    public String toString() {
        return "DEFVAL()";
    }

    @Override
    protected TextPattern applyWithSpans() {
        // Turn this into with-spans([]+), because with-spans(_) would mean "default value for the 1st parameter", which
        // with-spans() doesn't have.
        return new TextPatternQueryFunction(XFSpans.FUNC_WITH_SPANS,
                List.of(new TextPatternAnyToken(1, BLSpanQuery.MAX_UNLIMITED)));
    }

    @Override
    protected TextPattern applyRspanAll() {
        // Special case: this is almost certainly a parallel query target with no restrictions,
        // (i.e. "return the matching spans in this other version of the document").
        // rspan([]+, 'all') would match way too much, and the rspan() call won't do anything useful; just skip it.
        return this;
    }
}
