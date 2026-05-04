package nl.inl.blacklab.search.textpattern;

import java.util.List;
import java.util.Objects;

import org.apache.lucene.queries.spans.BLSpanOrQuery;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryNot;
import nl.inl.blacklab.search.matchfilter.MatchFilter;
import nl.inl.blacklab.search.matchfilter.MatchFilterNot;
import nl.inl.blacklab.search.matchfilter.MatchFilterOr;

/**
 * Implication operation (A -> B) <==> (!A | B)
 */
public class TextPatternImplication extends TextPattern {

    public static int TP_PRECEDENCE = 8;

    TextPattern antecedent;

    TextPattern consequent;

    public TextPatternImplication(TextPattern antecedent, TextPattern consequent) {
        super(TP_PRECEDENCE);
        this.antecedent = antecedent;
        this.consequent = consequent;
    }

    @Override
    public EvalResult evaluate(QueryExecutionContext context) throws InvalidQuery {
        if (context.isInConstraint()) {
            // We're in the constraint part of the query; create MatchFilter
            MatchFilter a = antecedent.toMatchFilter(context);
            MatchFilter b = consequent.toMatchFilter(context);
            return new MatchFilterOr(new MatchFilterNot(a), b);
        } else {
            // Regular query
            BLSpanQuery a = antecedent.toQuery(context);
            BLSpanQuery b = consequent.toQuery(context);
            return new BLSpanOrQuery(new SpanQueryNot(a), b);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        TextPatternImplication that = (TextPatternImplication) o;
        return Objects.equals(antecedent, that.antecedent) && Objects.equals(consequent,
                that.consequent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(antecedent, consequent);
    }

    @Override
    public String toString() {
        return "IMPL(" + clausesToString(getClauses()) + ")";
    }

    public List<TextPattern> getClauses() {
        return List.of(antecedent, consequent);
    }

    @Override
    public boolean isBracketQuery() {
        return getClauses().stream().allMatch(TextPattern::isBracketQuery);
    }

    @Override
    public boolean isRelationsQuery() {
        return getClauses().stream().anyMatch(TextPattern::isRelationsQuery);
    }

    public TextPattern getAntecedent() {
        return antecedent;
    }

    public TextPattern getConsequent() {
        return consequent;
    }

    @Override
    public <T> T accept(TextPatternVisitor<T> visitor) {
        return visitor.visitImplication(this);
    }
}
