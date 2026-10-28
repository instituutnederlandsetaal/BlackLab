package nl.inl.blacklab.searches;

import java.util.Objects;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.TermFrequencyList;

/**
 * Search operation that yields collocations.
 */
public class SearchCollocationsWindow extends SearchCollocations {

    private final SearchCollocations source;
    private final long first;
    private final long number;

    protected SearchCollocationsWindow(SearchCollocations source, long first, long number) {
        super(source.context());
        this.source = source;
        this.first = first;
        this.number = number;
    }

    @Override
    public TermFrequencyList executeInternal(ActiveSearch<TermFrequencyList> activeSearch) throws InvalidQuery {
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
        SearchCollocationsWindow other = (SearchCollocationsWindow) obj;
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
