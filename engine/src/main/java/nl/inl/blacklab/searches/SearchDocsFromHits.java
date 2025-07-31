package nl.inl.blacklab.searches;

import java.util.Objects;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.docs.DocResults;

public class SearchDocsFromHits extends SearchDocs {

    private final SearchHits source;

    private long maxHits = 0;

    public SearchDocsFromHits(QueryInfo queryInfo, SearchHits hitSearch, long maxHitsToGatherPerDocument) {
        super(queryInfo);
        this.source = hitSearch;
        this.maxHits = maxHitsToGatherPerDocument;
    }

    @Override
    public DocResults executeInternal(ActiveSearch<DocResults> activeSearch) throws InvalidQuery {
        return executeChildSearch(activeSearch, source).perDocResults(maxHits);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        SearchDocsFromHits that = (SearchDocsFromHits) o;
        return maxHits == that.maxHits && source.equals(that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), source, maxHits);
    }

    @Override
    public String toString() {
        return toString("docs", source, maxHits);
    }

}
