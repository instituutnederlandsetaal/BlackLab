package nl.inl.blacklab.searches;

import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.ResultProperty;
import nl.inl.blacklab.search.TermFrequency;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SampleParameters;

/**
 * Search operation that yields collocations.
 */
public abstract class SearchCollocations extends AbstractSearch<TermFrequencyList> {

    protected SearchCollocations(QueryInfo queryInfo) {
        super(queryInfo);
    }

    /**
     * Sort collocations.
     * 
     * @param sortBy what to sort by
     * @return resulting operation
     */
    public SearchCollocations sortBy(ResultProperty<TermFrequency> sortBy) {
        return new SearchCollocationsSorted(queryInfo(), this, sortBy);
    }

    /**
     * Sample collocations.
     * 
     * @param par how many hits to sample; seed
     * @return resulting operation
     */
    public SearchCollocations sample(SampleParameters par) {
        return new SearchCollocationsSampled(queryInfo(), this, par);
    }

    /**
     * Filter collocations.
     * 
     * @param property property to filter on 
     * @param value value to keep
     * @return resulting operation
     */
    public SearchCollocations filter(ResultProperty<TermFrequency> property, PropertyValue value) {
        return new SearchCollocationsFiltered(queryInfo(), this, property, value);
    }

    /**
     * Get window of hits.
     * 
     * @param first first hit to select
     * @param number number of hits to select
     * @return resulting operation
     */
    public SearchCollocations window(long first, long number) {
        return new SearchCollocationsWindow(queryInfo(), this, first, number);
    }
}
