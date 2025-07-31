package nl.inl.blacklab.server.lib.results;

import java.util.List;
import java.util.Map;

import org.apache.lucene.document.Document;

import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.ResultProperty;
import nl.inl.blacklab.search.results.CorpusSize;
import nl.inl.blacklab.search.results.docs.DocResults;
import nl.inl.blacklab.search.results.hitresults.HitGroup;
import nl.inl.blacklab.search.results.hitresults.HitGroups;
import nl.inl.blacklab.search.results.hitresults.HitResults;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.server.jobs.ContextSettings;
import nl.inl.blacklab.server.lib.ConcordanceContext;
import nl.inl.blacklab.server.lib.WebserviceParams;

public class ResultHitGroup {

    HitGroup group;

    private final Map<ResultProperty, PropertyValue> properties;

    private CorpusSize subcorpusSize = null;

    private final long numberOfDocsInGroup;

    private ConcordanceContext concordanceContext = null;

    private Map<Integer, String> docIdToPid = null;

    private ResultListOfHits listOfHits = null;

    ResultHitGroup(WebserviceParams params, HitGroups groups, HitGroup group, DocProperty metadataGroupProperties,
            DocResults subcorpus, List<HitProperty> prop, Map<Integer, Document> luceneDocs) {
        this.group = group;
        PropertyValue id = group.identity();

        if (metadataGroupProperties != null) {
            // Find size of corresponding subcorpus group
            PropertyValue docPropValues = groups.groupCriteria().docPropValues(id);
            subcorpusSize = WebserviceOperations.findSubcorpusSize(params, subcorpus.query(), metadataGroupProperties,
                    docPropValues);
        }

        numberOfDocsInGroup = group.storedResults().docsStats().countedTotal();

        properties = group.getGroupProperties(prop);

        if (params.getIncludeGroupContents()) {
            Hits hitsInGroup = group.storedResults().getHits();
            ContextSettings contextSettings = params.contextSettings();
            concordanceContext = ConcordanceContext.get(hitsInGroup, contextSettings.concType(),
                    contextSettings.size());
            docIdToPid = WebserviceOperations.collectDocsAndPids(params.blIndex(), hitsInGroup, luceneDocs);
        }

        if (params.getIncludeGroupContents()) {
            HitResults hitsInGroup = getGroup().storedResults();
            listOfHits = WebserviceOperations.listOfHits(params, hitsInGroup, getConcordanceContext(),
                    getDocIdToPid());
        }
    }

    public Map<ResultProperty, PropertyValue> getProperties() {
        return properties;
    }

    public CorpusSize getSubcorpusSize() {
        return subcorpusSize;
    }

    public long getNumberOfDocsInGroup() {
        return numberOfDocsInGroup;
    }

    public ConcordanceContext getConcordanceContext() {
        return concordanceContext;
    }

    public Map<Integer, String> getDocIdToPid() {
        return docIdToPid;
    }

    public HitGroup getGroup() {
        return group;
    }

    public ResultListOfHits getListOfHits() {
        return listOfHits;
    }
}
