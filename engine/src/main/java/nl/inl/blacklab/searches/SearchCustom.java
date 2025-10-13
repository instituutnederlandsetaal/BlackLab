package nl.inl.blacklab.searches;

import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SearchResult;

/**
 * A custom search action.
 * 
 * When subclassing this, always implement equals() and hashCode(), and make
 * sure to always call super.equals() and super.hashCode() in them!
 * 
 * @param <S> source search type
 * @param <T> target result type
 */
public abstract class SearchCustom<S extends Search<T>, T extends SearchResult> extends AbstractSearch<T> {

    protected SearchCustom(QueryInfo queryInfo) {
        super(queryInfo);
    }
    
    /**
     * Apply this operation to the source search.
     * 
     * @param source search to apply this operation to
     * @return new search operation
     */
    public abstract SearchCustom<S, T> apply(S source);
    
    @Override
    public abstract String toString();

}
