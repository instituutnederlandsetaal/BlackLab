package nl.inl.blacklab.server.lib.results;

import java.util.Map;

import org.apache.lucene.document.Document;

import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.results.CorpusSize;
import nl.inl.blacklab.search.results.docs.DocResults;
import nl.inl.blacklab.search.results.hitresults.HitGroup;
import nl.inl.blacklab.search.results.hitresults.HitGroups;
import nl.inl.blacklab.search.results.hitresults.HitResults;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.server.jobs.ContextSettings;
import nl.inl.blacklab.server.lib.ConcordanceContext;
import nl.inl.blacklab.server.lib.requests.RequestHitsGrouped;

public class ResultHitGroup {

    HitGroup group;

    private CorpusSize subcorpusSize = null;

    private final long numberOfDocsInGroup;

    private ConcordanceContext concordanceContext = null;

    private Map<Integer, String> docIdToPid = null;

    private ResultListOfHits listOfHits = null;

    ResultHitGroup(RequestHitsGrouped reqGroup, HitGroups groups, HitGroup group, DocProperty metadataGroupProperties,
            DocResults subcorpus, Map<Integer, Document> luceneDocs) {
        this.group = group;
        PropertyValue id = group.identity();

        if (metadataGroupProperties != null) {
            // Find size of corresponding subcorpus group
            PropertyValue docPropValues = groups.groupCriteria().docPropValues(id);
            subcorpusSize = WebserviceOperations.findSubcorpusSize(reqGroup.index(), subcorpus.query(), metadataGroupProperties,
                    docPropValues);
        }

        numberOfDocsInGroup = group.docsStats().countedTotal();

        if (reqGroup.includeGroupContents()) {
            HitResults groupResults = group.storedResults();
            Hits hitsInGroup = groupResults.getHits();
            ContextSettings contextSettings = reqGroup.contextSettings();
            concordanceContext = ConcordanceContext.get(hitsInGroup, contextSettings.concType(),
                    contextSettings.size());
            docIdToPid = WebserviceOperations.collectDocsAndPids(reqGroup.index(), hitsInGroup, luceneDocs);
            ConcordanceContext concordanceContext1 = getConcordanceContext();
            listOfHits = new ResultListOfHits(groupResults, concordanceContext1, getDocIdToPid(), contextSettings,
                    reqGroup.hitsResponseSettings().annotationsToInclude(),
                    reqGroup.hitsResponseSettings().omitEmptyCaptures());
        }
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
