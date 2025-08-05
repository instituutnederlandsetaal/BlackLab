package nl.inl.blacklab.search.textpattern;

import java.util.Objects;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryConstrained;
import nl.inl.blacklab.search.matchfilter.MatchFilter;

/**
 * Apply a global constraint (or "match filter") to our matches.
 *
 * A global constraint is specified in Corpus Query Language using
 * the :: operator, e.g. <code>a:[] "and" b:[] :: a.word = b.word</code>
 * to find things like "more and more", "less and less", etc.
 */
public class TextPatternConstrained extends TextPattern {

    final TextPattern clause;

    final MatchFilter constraint;

    public TextPatternConstrained(TextPattern clause, MatchFilter constraint) {
        this.clause = clause;
        this.constraint = constraint;
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        BLSpanQuery translate = clause.translate(context);
        ForwardIndexAccessor fiAccessor = translate.getAnnotatedField().forwardIndexAccessor();
        return new SpanQueryConstrained(translate, constraint.withField(translate.getAnnotatedField()), fiAccessor);
    }

    @Override
    public String toString() {
        String producer = clause.toString();
        String filter = constraint.toString();
        return "CONSTRAINED(" + producer + ", " + filter + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TextPatternConstrained that = (TextPatternConstrained) o;
        return Objects.equals(clause, that.clause) && Objects.equals(constraint, that.constraint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clause, constraint);
    }

    public TextPattern getClause() {
        return clause;
    }

    public MatchFilter getConstraint() {
        return constraint;
    }

    @Override
    public boolean isRelationsQuery() {
        return clause.isRelationsQuery();
    }
}
