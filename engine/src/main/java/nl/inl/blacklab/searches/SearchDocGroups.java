package nl.inl.blacklab.searches;

import nl.inl.blacklab.resultproperty.DocGroupProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.results.SampleParameters;
import nl.inl.blacklab.search.results.docs.DocGroups;

/** A search that yields groups of documents. */
public abstract class SearchDocGroups extends SearchForResults<DocGroups> {

    protected SearchDocGroups(AbstractSearch<?> source) {
        super(source.context());
    }
    
    /**
     * Sort hits.
     * 
     * @param sortBy what to sort by
     * @return resulting operation
     */
    public SearchDocGroups sort(DocGroupProperty sortBy) {
        if (sortBy == null)
            return this;
        return new SearchDocGroupsSorted(this, sortBy);
    }
    
    /**
     * Sample hits.
     * 
     * @param par how many hits to sample; seed
     * @return resulting operation
     */
    public SearchDocGroups sample(SampleParameters par) {
        return new SearchDocGroupsSampled(this, par);
    }

    /**
     * Get hits with a certain property value.
     * 
     * @param property property to test 
     * @param value value to test for
     * @return resulting operation
     */
    public SearchDocGroups filter(DocGroupProperty property, PropertyValue value) {
        return new SearchDocGroupsFiltered(this, property, value);
    }

    /**
     * Get window of hits.
     * 
     * @param first first hit to select
     * @param number number of hits to select
     * @return resulting operation
     */
    public SearchDocGroups window(long first, long number) {
        return new SearchDocGroupsWindow(this, first, number);
    }
    
}
