package nl.inl.blacklab.search.textpattern;

import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;

/**
 * Represents a combination of a contents query (a TextPattern) and a metadata
 * "filter query" (a regular Lucene Query).
 * This kind of query is produced by parsing SRU CQL, for example.
 *
 * @param pattern The query to find a structure in the contents
 * @param filter  The query that determines what documents to search for the structure
 */
public record CompleteQuery(TextPattern pattern, Query filter) {

    /**
     * Get the query to find a structure in the contents
     *
     * @return the structural contents query
     */
    @Override
    public TextPattern pattern() {
        return pattern;
    }

    /**
     * Get the query that determines what documents to search for the structure
     *
     * @return the metadata filter query
     */
    @Override
    public Query filter() {
        return filter;
    }

    /**
     * Combine this query with another query using the and operator.
     * NOTE: contents queries will be combined using token-level and, filter queries
     * will be combined using BooleanQuery (so, at the document level).
     *
     * @param other the query to combine this query with
     * @return the resulting query
     */
    public CompleteQuery and(CompleteQuery other) {
        TextPattern a, b, c;
        Query d, e, f;

        a = pattern;
        b = other.pattern;
        if (a != null && b != null)
            c = new TextPatternAnd(a, b); // NOTE: token-level and!
        else
            c = a == null ? b : a;

        d = filter;
        e = other.filter;
        if (d != null && e != null) {
            BooleanQuery.Builder bb = new BooleanQuery.Builder();
            bb.add(d, Occur.MUST);
            bb.add(e, Occur.MUST);
            f = bb.build();
        } else
            f = d == null ? e : d;

        return new CompleteQuery(c, f);
    }

    /**
     * Combine this query with another query using the or operator.
     * NOTE: you can combine two content queries or two filter queries, or both, but
     * you can't combine one content query and one filter query.
     *
     * @param other the query to combine this query with
     * @return the resulting query
     */
    public CompleteQuery or(CompleteQuery other) {
        TextPattern a = pattern;
        TextPattern b = other.pattern;
        Query d = filter;
        Query e = other.filter;

        if (a == null && b != null || a != null && b == null ||
                d == null && e != null || d != null && e == null) {
            throw new UnsupportedOperationException(
                    "or can only be used to combine contents clauses or metadata clauses; " +
                            "you can't combine the two with eachother with or");
        }

        TextPattern c;
        if (a != null && b != null)
            c = new TextPatternOr(a, b);
        else
            c = a == null ? b : a;

        Query f;
        if (d != null && e != null) {
            BooleanQuery.Builder bb = new BooleanQuery.Builder();
            bb.add(d, Occur.SHOULD);
            bb.add(e, Occur.SHOULD);
            f = bb.build();
        } else
            f = d == null ? e : d;

        return new CompleteQuery(c, f);
    }

    /**
     * Combine this query with another query using the and-not operator.
     * NOTE: contents queries will be combined using token-level and-not, filter
     * queries will be combined using BooleanQuery (so, at the document level).
     *
     * @param other the query to combine this query with
     * @return the resulting query
     */
    public CompleteQuery not(CompleteQuery other) {
        TextPattern a, b, c;
        Query d, e, f;

        a = pattern;
        b = other.pattern;
        if (a != null && b != null)
            c = new TextPatternAnd(a, new TextPatternNot(b));
        else
            c = a == null ? new TextPatternNot(b) : a;

        d = filter;
        e = other.filter;
        if (d != null && e != null) {
            BooleanQuery.Builder bb = new BooleanQuery.Builder();
            bb.add(d, Occur.MUST);
            bb.add(e, Occur.MUST_NOT);
            f = bb.build();
        } else {
            if (e != null && d == null)
                throw new UnsupportedOperationException("Cannot have not without positive clause first!");
            f = d;
        }

        return new CompleteQuery(c, f);
    }
}
