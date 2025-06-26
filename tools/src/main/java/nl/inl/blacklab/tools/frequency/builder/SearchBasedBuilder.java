package nl.inl.blacklab.tools.frequency.builder;

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
import nl.inl.blacklab.searches.SearchHitGroups;
import nl.inl.blacklab.tools.frequency.config.BuilderConfig;
import nl.inl.blacklab.tools.frequency.config.FreqListConfig;
import nl.inl.blacklab.tools.frequency.writers.TsvWriter;

// Non memory-optimized version
public final class SearchBasedBuilder extends FreqListBuilder {
    private final TsvWriter tsvWriter;

    public SearchBasedBuilder(final BlackLabIndex index, final BuilderConfig bCfg, final FreqListConfig fCfg) {
        super(index, bCfg, fCfg);
        this.tsvWriter = new TsvWriter(bCfg, fCfg, aInfo);
    }

    @Override
    public void makeFrequencyList() {
        super.makeFrequencyList(); // prints debug info
        try {
            // Create our search
            final var search = getSearch();
            // Execute search
            HitGroups result = null;
            for (int i = 0; i < Math.max(1, bCfg.getRepetitions()); i++) {
                result = search.execute();
            }
            // write output file
            tsvWriter.write(result);
        } catch (InvalidQuery e) {
            throw new BlackLabRuntimeException("Error creating frequency list: " + fCfg.getReportName(), e);
        }
    }

    private SearchHitGroups getSearch() {
        final var queryInfo = QueryInfo.create(index);
        BLSpanQuery anyToken = new SpanQueryAnyToken(queryInfo, 1, 1, aInfo.getAnnotatedField().name());
        final var groupBy = getGroupBy();
        return index.search()
                .find(anyToken)
                .groupStats(groupBy, 0)
                .sort(new HitGroupPropertyIdentity());
    }

    private HitProperty getGroupBy() {
        final var groupProps = new ArrayList<HitProperty>();
        // Add annotations to group by
        for (final var annotation: aInfo.getAnnotations()) {
            groupProps.add(new HitPropertyHitText(index, annotation));
        }
        // Add metadata fields to group by
        for (final String name: fCfg.metadataFields()) {
            groupProps.add(new HitPropertyDocumentStoredField(index, name));
        }
        return new HitPropertyMultiple(groupProps);
    }
}
