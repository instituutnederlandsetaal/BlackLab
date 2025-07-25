package nl.inl.blacklab.search.results;

import nl.inl.blacklab.resultproperty.ResultProperty;

/**
 * Grouped results of some type
 * 
 * @param <T> result type, e.g. Hit for groups of hits
 */
public interface ResultGroups {

    /**
     * What were these results grouped on?
     * @return group criteria
     */
    ResultProperty groupCriteria();
    
    /**
     * Get the total number of results that were grouped
     *
     * @return the number of results that were grouped
     */
    long sumOfGroupSizes();

    /**
     * Return the size of the largest group
     *
     * @return size of the largest group
     */
    long largestGroupSize();

    /**
     * Return the number of groups
     *
     * @return number of groups
     */
    long size();
    
    /**
     * Get our original query info.
     * 
     * @return query info
     */
    QueryInfo queryInfo();

}
