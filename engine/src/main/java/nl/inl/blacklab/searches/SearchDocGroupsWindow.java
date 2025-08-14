package nl.inl.blacklab.searches;

import java.util.Objects;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.docs.DocGroups;

/** A search that yields groups of documents. */
public class SearchDocGroupsWindow extends SearchDocGroups {

    private final SearchDocGroups source;
    private final long first;
    private final long number;

    public SearchDocGroupsWindow(QueryInfo queryInfo, SearchDocGroups source, long first, long number) {
        super(queryInfo);
        this.source = source;
        this.first = first;
        this.number = number;
    }

    @Override
    public DocGroups executeInternal(ActiveSearch<DocGroups> activeSearch) throws InvalidQuery {
        return executeChildSearch(activeSearch, source).window(first, number);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), source, first, number);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        SearchDocGroupsWindow other = (SearchDocGroupsWindow) obj;
        if (first != other.first)
            return false;
        if (number != other.number)
            return false;
        if (source == null) {
            if (other.source != null)
                return false;
        } else if (!source.equals(other.source))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return toString("window", source, first, number);
    }
}
