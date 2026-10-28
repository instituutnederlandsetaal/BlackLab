package nl.inl.blacklab.searches;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.search.results.SearchResult;
import nl.inl.blacklab.search.results.SearchSettings;
import nl.inl.blacklab.search.textpattern.CompleteQuery;

/** 
 * Empty search that just knows about its index and annotated field to search,
 * and serves as a starting point for actual searches.
 */
public class SearchEmpty extends AbstractSearch<SearchResult> {
    
    public SearchEmpty(Context context) {
        super(context);
    }

    @Override
    public SearchResult executeInternal(ActiveSearch<SearchResult> activeSearch) {
        throw new UnsupportedOperationException();
    }

    public SearchHits find(CompleteQuery query, SearchSettings searchSettings) {
        if (searchSettings == null) {
            // If no settings given, use the default
            searchSettings = index().searchSettings();
        }
        return new SearchHitsFromQuery(context(), query, searchSettings);
    }

    public SearchHits find(CompleteQuery query) {
        return find(query, null);
    }
    
    public SearchDocs findDocuments(Query documentQuery) {
        return new SearchDocsFromQuery(context(), documentQuery);
    }
    
    @Override
    public String toString() {
        return toString("empty");
    }
    
}
