package nl.inl.blacklab.searches;

import java.util.Objects;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.hitresults.HitResults;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SearchSettings;

/** A search that yields hits. */
public class SearchHitsWindow extends SearchHits {

    private final SearchHits source;
    private final long first;
    private final long number;

    SearchHitsWindow(QueryInfo queryInfo, SearchHits source, long first, long number) {
        super(queryInfo);
        this.source = source;
        this.first = first;
        this.number = number;
    }

    @Override
    public HitResults executeInternal(ActiveSearch<HitResults> activeSearch) throws InvalidQuery {
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
        SearchHitsWindow other = (SearchHitsWindow) obj;
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

    @Override
    public SearchSettings searchSettings() {
        return source.searchSettings();
    }
}
