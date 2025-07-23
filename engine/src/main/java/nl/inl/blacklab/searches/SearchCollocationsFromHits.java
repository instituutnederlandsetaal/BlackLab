package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * Search operation that yields collocations.
 */
public class SearchCollocationsFromHits extends SearchCollocations {

    private final SearchHits source;
    private final Annotation annotation;
    private final ContextSize contextSize;
    private final MatchSensitivity sensitivity;

    public SearchCollocationsFromHits(QueryInfo queryInfo, SearchHits source, Annotation annotation, ContextSize contextSize, MatchSensitivity sensitivity) {
        super(queryInfo);
        this.source = source;
        this.annotation = annotation;
        this.contextSize = contextSize;
        this.sensitivity = sensitivity;
    }

    @Override
    public TermFrequencyList executeInternal(ActiveSearch<TermFrequencyList> activeSearch) throws InvalidQuery {
        Hits hits = executeChildSearch(activeSearch, source);
        return hits.collocations(annotation, contextSize, sensitivity, true);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((annotation == null) ? 0 : annotation.hashCode());
        result = prime * result + ((contextSize == null) ? 0 : contextSize.hashCode());
        result = prime * result + ((sensitivity == null) ? 0 : sensitivity.hashCode());
        result = prime * result + ((source == null) ? 0 : source.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        SearchCollocationsFromHits other = (SearchCollocationsFromHits) obj;
        if (annotation == null) {
            if (other.annotation != null)
                return false;
        } else if (!annotation.equals(other.annotation))
            return false;
        if (contextSize == null) {
            if (other.contextSize != null)
                return false;
        } else if (!contextSize.equals(other.contextSize))
            return false;
        if (sensitivity != other.sensitivity)
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
        return toString("colloc", source, annotation, contextSize, sensitivity);
    }

}
