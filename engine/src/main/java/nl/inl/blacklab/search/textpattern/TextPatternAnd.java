package nl.inl.blacklab.search.textpattern;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryAndNot;
import nl.inl.blacklab.search.matchfilter.MatchFilter;
import nl.inl.blacklab.search.matchfilter.MatchFilterAnd;

/**
 * AND operation.
 * 
 * Actually just TextPatternAndNot without the option of specifying a NOT part.
 */
public class TextPatternAnd extends TextPattern {

    public static int TP_PRECEDENCE = 8;

    protected final List<TextPattern> clauses = new ArrayList<>();

    public TextPatternAnd(TextPattern... clauses) {
        this(Arrays.asList(clauses));
    }

    public TextPatternAnd(List<TextPattern> clauses) {
        super(TP_PRECEDENCE);
        if (clauses.isEmpty())
            throw new IllegalArgumentException("Must have at least one clause");
        for (TextPattern clause: clauses) {
            if (clause instanceof TextPatternAnd) {
                // Flatten nested ANDs
                this.clauses.addAll(((TextPatternAnd) clause).clauses);
            } else {
                this.clauses.add(clause);
            }
        }
    }

    @Override
    public EvalResult evaluate(QueryExecutionContext context) throws InvalidQuery {
        if (context.isInConstraint()) {
            // We're in the constraint part of the query; create MatchFilter
            if (clauses.size() != 2)
                throw new InvalidQuery("AND in constraint context requires exactly two clauses");
            MatchFilter a = clauses.get(0).toMatchFilter(context);
            MatchFilter b = clauses.get(1).toMatchFilter(context);
            return new MatchFilterAnd(a, b);
        } else {
            // Regular query
            List<BLSpanQuery> chResults = new ArrayList<>(clauses.size());
            for (TextPattern cl: clauses) {
                chResults.add(cl.toQuery(context));
            }
            return new SpanQueryAndNot(chResults, null);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternAnd) {
            return clauses.equals(((TextPatternAnd) obj).clauses);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return clauses.hashCode();
    }

    @Override
    public String toString() {
        return "AND(" + clausesToString(clauses) + ")";
    }

    public List<TextPattern> getClauses() {
        return clauses;
    }

    @Override
    public boolean isBracketQuery() {
        return clauses.stream().allMatch(TextPattern::isBracketQuery);
    }

    @Override
    public boolean isRelationsQuery() {
        return clauses.stream().anyMatch(TextPattern::isRelationsQuery);
    }

    @Override
    public <T> T accept(TextPatternVisitor<T> visitor) {
        return visitor.visitAnd(this);
    }
}
