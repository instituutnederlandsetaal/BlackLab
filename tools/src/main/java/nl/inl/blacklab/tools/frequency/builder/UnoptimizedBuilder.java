package nl.inl.blacklab.tools.frequency.builder;

import java.util.ArrayList;
import java.util.List;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.HitGroupPropertyIdentity;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyDocumentStoredField;
import nl.inl.blacklab.resultproperty.HitPropertyHitText;
import nl.inl.blacklab.resultproperty.HitPropertyMultiple;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryAnyToken;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.searches.SearchHitGroups;
import nl.inl.blacklab.tools.frequency.config.BuilderConfig;
import nl.inl.blacklab.tools.frequency.config.FreqListConfig;
import nl.inl.blacklab.tools.frequency.writers.TsvWriter;

// Non memory-optimized version
public class UnoptimizedBuilder extends FreqListBuilder {

    public UnoptimizedBuilder(BlackLabIndex index, BuilderConfig bCfg, FreqListConfig fCfg) {
        super(index, bCfg, fCfg);
    }

    @Override
    public void makeFrequencyList() {
        super.makeFrequencyList(); // prints debug info

        // Create our search
        try {
            // Execute search and write output file
            SearchHitGroups search = getSearch();
            HitGroups result = null;
            for (int i = 0; i < Math.max(1, bCfg.getRepetitions()); i++) {
                result = search.execute();
            }
            TsvWriter.write(fCfg, result, bCfg);
        } catch (InvalidQuery e) {
            throw new BlackLabRuntimeException("Error creating fCfg " + fCfg.getReportName(), e);
        }
    }

    private SearchHitGroups getSearch() {
        QueryInfo queryInfo = QueryInfo.create(index);
        BLSpanQuery anyToken = new SpanQueryAnyToken(queryInfo, 1, 1, annotatedField.name());
        HitProperty groupBy = getGroupBy();
        return index.search()
                .find(anyToken)
                .groupStats(groupBy, 0)
                .sort(new HitGroupPropertyIdentity());
    }

    private HitProperty getGroupBy() {
        List<HitProperty> groupProps = new ArrayList<>();
        // Add annotations to group by
        for (String name: fCfg.getAnnotations()) {
            Annotation annotation = annotatedField.annotation(name);
            groupProps.add(new HitPropertyHitText(index, annotation));
        }
        // Add metadata fields to group by
        for (String name: fCfg.getMetadataFields()) {
            groupProps.add(new HitPropertyDocumentStoredField(index, name));
        }
        return new HitPropertyMultiple(groupProps.toArray(new HitProperty[0]));
    }
}
