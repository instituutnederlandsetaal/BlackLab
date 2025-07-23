package nl.inl.blacklab.search.results;

import java.util.stream.Stream;

import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.ResultProperty;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;

public interface Results<T, P extends ResultProperty<T>> extends SearchResult, Iterable<T> {
    /**
     * When setting how many hits to retrieve/count/store in group, this means "no limit".
     */
    int NO_LIMIT = -1;

    /**
     * Get information about the original query.
     * <p>
     * This includes the index, field, max. settings, and max. stats
     * (whether the max. settings were reached).
     *
     * @return query info
     */
    QueryInfo queryInfo();

    /**
     * Get the field these hits are from.
     *
     * @return field
     */
    AnnotatedField field();

    /**
     * Get the index these hits are from.
     *
     * @return index
     */
    BlackLabIndex index();

    int resultsObjId();

    /**
     * If this is a hits window, return the window stats.
     *
     * @return window stats, or null if this is not a hits window
     */
    WindowStats windowStats();

    /**
     * Is this sampled from another instance?
     *
     * @return true if it's a sample, false if not
     */
    boolean isSample();

    /**
     * If this is a sample, return the sample parameters.
     * <p>
     * Also includes the explicitly set or randomly chosen seed.
     *
     * @return sample parameters, or null if this is not a sample
     */
    SampleParameters sampleParameters();

    /**
     * Return a stream of these hits.
     *
     * @return stream
     */
    Stream<T> stream();

    /**
     * Return a parallel stream of these hits.
     *
     * @return stream
     */
    Stream<T> parallelStream();

    /**
     * Return the specified hit.
     * Implementations of this method should be thread-safe.
     *
     * @param i index of the desired hit
     * @return the hit, or null if it's beyond the last hit
     */
    T get(long i);

    /**
     * Group these hits by a criterium (or several criteria).
     *
     * @param criteria                  the hit property to group on
     * @param maxResultsToStorePerGroup maximum number of results to store per group, or -1 for all
     * @return a HitGroups object representing the grouped hits
     */
    ResultGroups<T> group(P criteria, long maxResultsToStorePerGroup);

    /**
     * Select only the results where the specified property has the specified value.
     *
     * @param property property to select on, e.g. "word left of hit"
     * @param value    value to select on, e.g. 'the'
     * @return filtered hits
     */
    Results<T, P> filter(P property, PropertyValue value);

    /**
     * Return a new Results object with these results sorted by the given property.
     * <p>
     * This keeps the existing sort (or lack of one) intact and allows you to cache
     * different sorts of the same resultset. The result objects are reused between
     * the two Results instances, so not too much additional memory is used.
     *
     * @param sortProp the property to sort on
     * @return a new Results object with the same results, sorted in the specified way
     */
    Results<T, P> sort(P sortProp);

    /**
     * Take a sample of results.
     *
     * @param sampleParameters sample parameters
     * @return the sample
     */
    Results<T, P> sample(SampleParameters sampleParameters);

    /**
     * Get a window into this list of results.
     * <p>
     * Use this if you're displaying part of the resultset, like in a paging
     * interface. It makes sure BlackLab only works with the results you want to
     * display and doesn't do any unnecessary processing on the other hits.
     * <p>
     * The resulting instance will has "window stats" to assist with paging,
     * like figuring out if there hits before or after the window.
     *
     * @param first      first result in the window (0-based)
     * @param windowSize desired size of the window (if there's enough results)
     * @return the window
     */
    Results<T, P> window(long first, long windowSize);

    @Override
    String toString();

    ResultsStats resultsStats();

    /**
     * This is an alias of resultsProcessedTotal().
     *
     * @return number of hits processed total
     */
    long size();

    /**
     * Check if we're done retrieving/counting hits.
     * <p>
     * If you're retrieving hits in a background thread, call this method from
     * another thread to check if all hits have been processed.
     *
     * @return true iff all hits have been retrieved/counted.
     */
    boolean doneProcessingAndCounting();
}
