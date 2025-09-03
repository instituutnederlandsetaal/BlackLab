package nl.inl.blacklab.search.results;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.BLSpanQuery;

/**
 * Information about the original query.
 */
public final class QueryInfo {

    public static QueryInfo create(BlackLabIndex index) {
        return create(index, null, true);
    }

    public static QueryInfo create(BlackLabIndex index, AnnotatedField field) {
        return create(index, field, true);
    }

    public static QueryInfo create(BlackLabIndex index, AnnotatedField field, boolean useCache) {
        return new QueryInfo(index, field, useCache);
    }

    private final BlackLabIndex index;

    /** The field these hits came from (will also be used as concordance field) */
    private final AnnotatedField field;

    /** Should we use the cache for this query, or bypass it? */
    private final boolean useCache;

    /** How long executing certain parts of the operation took. */
    private final QueryTimings timings = new QueryTimings();

    private QueryInfo(BlackLabIndex index, AnnotatedField field, boolean useCache) {
        super();
        this.index = index;
        this.field = field == null ? index.mainAnnotatedField() : field;
        this.useCache = useCache;
    }

    /** @return the index that was searched. */
    public BlackLabIndex index() {
        return index;
    }

    /** @return field that was searched */
    public AnnotatedField field() {
        return field;
    }

    /** @return should we use the cache for this query, or bypass it? */
    public boolean useCache() {
        return useCache;
    }

    /** Get timings objects.
     *
     * Describes how long executing certain parts of the operation took.
     * Mostly useful for debugging.
     *
     * @return the query timings object
     */
    public QueryTimings timings() {
        return timings;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((field == null) ? 0 : field.hashCode());
        result = prime * result + ((index == null) ? 0 : index.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        QueryInfo other = (QueryInfo) obj;
        if (field == null) {
            if (other.field != null)
                return false;
        } else if (!field.equals(other.field))
            return false;
        if (index == null) {
            if (other.index != null)
                return false;
        } else if (!index.equals(other.index))
            return false;
        return true;
    }

    /**
     * Return QueryInfo with the correct field, taken from query.
     *
     * Will return a new QueryInfo if the query's field differs from ours;
     * otherwise, will return this.
     *
     * @param query the query to get the field from
     * @return a QueryInfo with the correct field
     */
    public QueryInfo optOverrideField(BLSpanQuery query) {
        if (query.getAnnotatedField().equals(field))
            return this;
        return new QueryInfo(index, query.getAnnotatedField(), useCache);
    }
}

