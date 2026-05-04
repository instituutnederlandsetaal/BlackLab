package nl.inl.blacklab.search.results;

import java.util.List;
import java.util.Map;

import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.ResultProperty;
import nl.inl.blacklab.search.results.stats.ResultsStats;

/**
 * A generic group of results, with its group identity and the results themselves.
 */
public interface HitOrDocGroup {

    /** The grouping value, which all results in the group have in common.
     *  (i.e. if you group by hit text, a group's identity is the hit text these hits all have) */
    PropertyValue identity();

    /** Total size of the group */
    long size();

    /** Get stats about the group, including total size, number stored, etc. */
    ResultsStats resultsStats();

    /** Get the results in this group */
    Results storedResults();

    /** How many of the group's results are currently stored */
    long numberOfStoredResults();

    int compareTo(HitOrDocGroup o);

    /** Match the group identity values to the given criteria.
     * If you grouped on a single property, this is trivial. For multiple properties,
     * matches the first property to the first value, etc.
     * Used to produce the API response.
     */
    Map<ResultProperty, PropertyValue> getGroupProperties(List<? extends ResultProperty> criteria);

}
