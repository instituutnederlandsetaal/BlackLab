package nl.inl.blacklab.search.results;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.results.stats.ResultsStats;

/** A search result that comprises a list of hits, docs, groups or termfrequencies. */
public interface Results extends SearchResult {

    /**
     * When setting how many hits to retrieve/count/store in group, this means "no limit".
     */
    long NO_LIMIT = Long.MAX_VALUE;

    /**
     * Get information about the original query.
     * <p>
     * This includes the index, field, max. settings, and max. stats
     * (whether the max. settings were reached).
     *
     * @return query info
     */
    QueryInfo queryInfo();

    AnnotatedField field();

    BlackLabIndex index();

    ResultsStats resultsStats();

    /**
     * This is an alias of resultsStats().waitUntilAllProcessed().
     *
     * @return number of hits processed total
     */
    long size();

}
