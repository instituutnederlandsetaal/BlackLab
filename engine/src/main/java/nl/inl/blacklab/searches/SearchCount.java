package nl.inl.blacklab.searches;

import net.sf.saxon.functions.Abs;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.QueryTimings;
import nl.inl.blacklab.search.results.stats.ResultsStats;

/**
 * A search operation that yields a count as its result.
 */
public abstract class SearchCount extends AbstractSearch<ResultsStats> {

    protected SearchCount(AbstractSearch<?> source) {
        super(source.context());
    }

}
