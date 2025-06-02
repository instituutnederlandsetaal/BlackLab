package nl.inl.blacklab.search.textpattern;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
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
}
