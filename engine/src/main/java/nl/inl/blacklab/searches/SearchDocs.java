package nl.inl.blacklab.searches;

import java.util.List;

import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SampleParameters;
import nl.inl.blacklab.search.results.docs.DocResults;

/** A search that produces DocResults. */
public abstract class SearchDocs extends SearchForResults<DocResults> {

    protected SearchDocs(QueryInfo queryInfo) {
        super(queryInfo);
    }
    
    /**
     * Group hits by a property.
     * 
     * @param groupBy what to group by
     * @param maxResultsToGatherPerGroup how many results to gather per group
     * @return resulting operation
     */
    public SearchDocGroups group(DocProperty groupBy, long maxResultsToGatherPerGroup) {
        return new SearchDocGroupsFromDocs(queryInfo(), this, groupBy, maxResultsToGatherPerGroup);
    }

    /**
     * Sort hits.
     * 
     * @param sortBy what to sort by
     * @return resulting operation
     */
    public SearchDocs sort(DocProperty sortBy) {
        if (sortBy == null)
            return this;
        return new SearchDocsSorted(queryInfo(), this, sortBy);
    }
    
    /**
     * Sample hits.
     * 
     * @param par how many hits to sample; seed
     * @return resulting operation
     */
    public SearchDocs sample(SampleParameters par) {
        return new SearchDocsSampled(queryInfo(), this, par);
    }

    /**
     * Get hits with a certain property value.
     * 
     * @param property property to test 
     * @param value value to test for
     * @return resulting operation
     */
    public SearchDocs filter(DocProperty property, PropertyValue value) {
        return new SearchDocsFiltered(queryInfo(), this, property, value);
    }

    /**
     * Get window of hits.
     * 
     * @param first first hit to select
     * @param number number of hits to select
     * @return resulting operation
     */
    public SearchDocs window(long first, long number) {
        return new SearchDocsWindow(queryInfo(), this, first, number);
    }

    /**
     * Facet the doc results by several properties.
     * 
     * Faceting is basically grouping without storing any results, just the counts. 
     * 
     * @param facets facets to facet on
     * @return resulting operation
     */
    public SearchFacets facet(List<DocProperty> facets) {
        return new SearchFacets(queryInfo(), this, facets);
    }
    
}
