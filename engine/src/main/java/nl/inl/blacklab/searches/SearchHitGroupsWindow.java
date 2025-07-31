package nl.inl.blacklab.searches;

import java.util.Objects;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.hitresults.HitGroups;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * A search operation that yields groups of hits.
 */
public class SearchHitGroupsWindow extends SearchHitGroups {

    private final SearchHitGroups source;
    private final long first;
    private final long number;

    public SearchHitGroupsWindow(QueryInfo queryInfo, SearchHitGroups source, long first, long number) {
        super(queryInfo);
        this.source = source;
        this.first = first;
        this.number = number;
    }

    @Override
    public HitGroups executeInternal(ActiveSearch<HitGroups> activeSearch) throws InvalidQuery {
        return executeChildSearch(activeSearch, source).window(first, number);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        SearchHitGroupsWindow that = (SearchHitGroupsWindow) o;
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
