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

    public CompleteQuery(TextPattern pattern) {
        this(pattern, null);
    }

    public CompleteQuery(Query filter) {
        this(null, filter);
    }

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

        TextPattern p = pattern == null ?
                other.pattern : (other.pattern == null ? pattern : new TextPatternAnd(pattern, other.pattern));

        Query f = null;
        if (filter != null || other.filter != null) {
            BooleanQuery.Builder bb = new BooleanQuery.Builder();
            if (filter != null)
                bb.add(filter, Occur.MUST);
            if (other.filter != null)
                bb.add(other.filter, Occur.MUST);
            f = bb.build();
        }

        return new CompleteQuery(p, f);
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

        if (!  (pattern == null && other.pattern == null || filter == null && other.filter == null) ) {
            throw new UnsupportedOperationException(
                    "or can only be used to combine contents clauses or metadata clauses; " +
                            "you can't combine the two with eachother with or");
        }

        TextPattern p = pattern == null ? other.pattern :
                (other.pattern == null ? pattern : new TextPatternOr(pattern, other.pattern));

        Query f = null;
        if (filter != null || other.filter != null) {
            BooleanQuery.Builder bb = new BooleanQuery.Builder();
            if (filter != null)
                bb.add(filter, Occur.SHOULD);
            if (other.filter != null)
                bb.add(other.filter, Occur.SHOULD);
            f = bb.build();
        }

        return new CompleteQuery(p, f);
    }

    /**
     * Combine this query with another query using the and-not operator.
     * NOTE: contents queries will be combined using token-level and-not, filter
     * queries will be combined using BooleanQuery (so, at the document level).
     *
     * @param other the query to combine this query with
     * @return the resulting query
     */
    public CompleteQuery andNot(CompleteQuery other) {

        TextPattern p;
        if (pattern != null && other.pattern != null)
            p = new TextPatternAnd(pattern, new TextPatternNot(other.pattern));
        else
            p = pattern == null ? new TextPatternNot(other.pattern) : pattern;

        Query f;
        if (filter != null && other.filter != null) {
            BooleanQuery.Builder bb = new BooleanQuery.Builder();
            bb.add(filter, Occur.MUST);
            bb.add(other.filter, Occur.MUST_NOT);
            f = bb.build();
        } else {
            if (other.filter != null)
                throw new UnsupportedOperationException("Cannot have not without positive clause first!");
            f = filter;
        }

        return new CompleteQuery(p, f);
    }

}
