package nl.inl.blacklab.tools.frequency.counter.search;

import java.util.ArrayList;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.HitGroupPropertyIdentity;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyDocumentStoredField;
import nl.inl.blacklab.resultproperty.HitPropertyHitText;
import nl.inl.blacklab.resultproperty.HitPropertyMultiple;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryAnyToken;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.searches.SearchCacheDummy;
import nl.inl.blacklab.searches.SearchHitGroups;
import nl.inl.blacklab.tools.frequency.config.frequency.FrequencyListConfig;
import nl.inl.blacklab.tools.frequency.config.frequency.MetadataConfig;
import nl.inl.blacklab.tools.frequency.counter.FrequencyCounter;
import nl.inl.blacklab.tools.frequency.data.helper.IndexHelper;

/**
 * Non memory-optimized FrequencyCounter using search queries.
 */
public final class SearchFrequencyCounter extends FrequencyCounter {

    public SearchFrequencyCounter(final BlackLabIndex index, final FrequencyListConfig cfg, final IndexHelper helper) {
        super(index, cfg, helper);
    }

    @Override
    public void count() {
        super.count(); // prints debug info
        try {
            // Create our search
            index.setCache(new SearchCacheDummy()); // don't cache results
            final var search = getSearch();
            // Execute search
            final HitGroups result = search.execute();
            // write output file
            tsvWriter.write(result);
        } catch (final InvalidQuery e) {
            throw new BlackLabRuntimeException("Error creating frequency list: " + cfg.name(), e);
        }
    }

    private SearchHitGroups getSearch() {
        final var queryInfo = QueryInfo.create(index);
        final BLSpanQuery anyToken = new SpanQueryAnyToken(queryInfo, 1, 1,
                helper.annotations().annotatedField().name());
        final var groupBy = getGroupBy();
        return index.search()
                .find(anyToken)
                .groupStats(groupBy, 0)
                .sort(new HitGroupPropertyIdentity());
    }

    private HitProperty getGroupBy() {
        final var groupProps = new ArrayList<HitProperty>();
        // Add annotations to group by
        for (final var annotation: helper.annotations().annotations()) {
            groupProps.add(new HitPropertyHitText(index, annotation));
        }
        // Add metadata fields to group by
        for (final String name: cfg.metadata().stream().map(MetadataConfig::name).toList()) {
            groupProps.add(new HitPropertyDocumentStoredField(index, name));
        }
        return new HitPropertyMultiple(groupProps);
    }
}
