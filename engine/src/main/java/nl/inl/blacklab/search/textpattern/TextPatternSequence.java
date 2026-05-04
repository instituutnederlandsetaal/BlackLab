package nl.inl.blacklab.search.textpattern;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQuerySequence;

/**
 * A sequence of patterns. The patterns specified may be any pattern, and may
 * themselves be sequences if desired.
 */
public class TextPatternSequence extends TextPattern {

    public static int TP_PRECEDENCE = 7;

    protected final List<TextPattern> clauses = new ArrayList<>();

    public TextPatternSequence(TextPattern... clauses) {
        this(Arrays.asList(clauses));
    }

    public TextPatternSequence(List<TextPattern> clauses) {
        super(TP_PRECEDENCE);
        for (TextPattern clause : clauses) {
            if (clause instanceof TextPatternSequence) {
                // Flatten nested sequences
                this.clauses.addAll(((TextPatternSequence) clause).clauses);
            } else {
                this.clauses.add(clause);
            }
        }
    }

    @Override
    public EvalResult evaluate(QueryExecutionContext context) throws InvalidQuery {
        List<BLSpanQuery> chResults = new ArrayList<>();
        for (TextPattern cl : clauses) {
            chResults.add(cl.toQuery(context));
        }
        if (chResults.size() == 1)
            return chResults.get(0); // just one part, return that
        return new SpanQuerySequence(chResults);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternSequence) {
            return clauses.equals(((TextPatternSequence) obj).clauses);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return clauses.hashCode();
    }

    @Override
    public String toString() {
        return "SEQ(" + clausesToString(clauses) + ")";
    }

    public List<TextPattern> getClauses() {
        return clauses;
    }

    @Override
    public boolean isRelationsQuery() {
        return clauses.stream().anyMatch(TextPattern::isRelationsQuery);
    }

    @Override
    public <T> T accept(TextPatternVisitor<T> visitor) {
        return visitor.visitSequence(this);
    }
}
