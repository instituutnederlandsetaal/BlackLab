package nl.inl.blacklab.searches;

import nl.inl.blacklab.resultproperty.HitGroupProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.results.QueryTimings;
import nl.inl.blacklab.search.results.hitresults.HitGroups;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SampleParameters;

/** A search that yields groups of hits. */
public abstract class SearchHitGroups extends SearchForResults<HitGroups> {

    protected SearchHitGroups(Search<?> source) {
        super(source);
    }

    protected SearchHitGroups(QueryInfo queryInfo, SearchContext context) {
        super(queryInfo, context);
    }

    /**
     * Sort hits.
     * 
     * @param sortBy what to sort by
     * @return resulting operation
     */
    public SearchHitGroups sort(HitGroupProperty sortBy) {
        if (sortBy == null)
            return this;
        return new SearchHitGroupsSorted(this, sortBy);
    }
    
    /**
     * Sample hits.
     * 
     * @param par how many hits to sample; seed
     * @return resulting operation
     */
    public SearchHitGroups sample(SampleParameters par) {
        return new SearchHitGroupsSampled(this, par);
    }

    /**
     * Get hits with a certain property value.
     * 
     * @param property property to test 
     * @param value value to test for
     * @return resulting operation
     */
    public SearchHitGroups filter(HitGroupProperty property, PropertyValue value) {
        return new SearchHitGroupsFiltered(this, property, value);
    }

    /**
     * Get window of hits.
     * 
     * @param first first hit to select
     * @param number number of hits to select
     * @return resulting operation
     */
    public SearchHitGroups window(long first, long number) {
        return new SearchHitGroupsWindow(this, first, number);
    }

}
