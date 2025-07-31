package nl.inl.blacklab.searches;

import java.util.Objects;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.docs.DocGroups;

/** A search that yields groups of documents. */
public class SearchDocGroupsFromDocs extends SearchDocGroups {

    private final SearchDocs source;

    private final DocProperty property;

    private final long maxDocs;

    public SearchDocGroupsFromDocs(QueryInfo queryInfo, SearchDocs source, DocProperty property, long maxDocsToStorePerGroup) {
        super(queryInfo);
        this.source = source;
        this.property = property;
        this.maxDocs = maxDocsToStorePerGroup;
    }

    @Override
    public DocGroups executeInternal(ActiveSearch<DocGroups> activeSearch) throws InvalidQuery {
        return executeChildSearch(activeSearch, source).group(property, maxDocs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), source, property, maxDocs);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        SearchDocGroupsFromDocs that = (SearchDocGroupsFromDocs) o;
        return maxDocs == that.maxDocs && source.equals(that.source) && property.equals(that.property);
    }

    @Override
    public String toString() {
        return toString("group", source, property, maxDocs);
    }

}
