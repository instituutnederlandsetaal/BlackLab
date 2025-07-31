package nl.inl.blacklab.searches;

import java.util.Objects;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.docs.DocResults;

public class SearchDocsWindow extends SearchDocs {

    private final SearchDocs source;

    private final long first;

    private final long number;

    public SearchDocsWindow(QueryInfo queryInfo, SearchDocs docsSearch, long first, long number) {
        super(queryInfo);
        this.source = docsSearch;
        this.first = first;
        this.number = number;
    }

    @Override
    public DocResults executeInternal(ActiveSearch<DocResults> activeSearch) throws InvalidQuery {
        return executeChildSearch(activeSearch, source).window(first, number);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        SearchDocsWindow that = (SearchDocsWindow) o;
        return first == that.first && number == that.number && source.equals(that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), source, first, number);
    }

    @Override
    public String toString() {
        return toString("window", source, first, number);
    }

}
