package nl.inl.blacklab.server.lib.results;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.lucene.document.Document;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.results.docs.DocResult;
import nl.inl.blacklab.search.results.hitresults.Concordances;
import nl.inl.blacklab.search.results.hitresults.HitResults;
import nl.inl.blacklab.search.results.hitresults.Kwics;
import nl.inl.blacklab.search.results.hits.EphemeralHit;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.server.jobs.ContextSettings;
import nl.inl.blacklab.server.lib.WebserviceParams;

public class ResultDocResult {
    private final String pid;
    private final ResultDocInfo docInfo;
    private final List<Concordance> concordancesToShow;
    private final List<Kwic> kwicsToShow;
    private final Collection<Annotation> annotationsToList;
    private final long numberOfHits;

    public ResultDocResult(Collection<MetadataField> metadataFieldsToList,
            WebserviceParams params, Collection<Annotation> annotationsToList, DocResult dr) {
        this.annotationsToList = annotationsToList;
        // Find pid
        BlackLabIndex index = params.blIndex();
        Document document = index.luceneDoc(dr.docId());
        pid = WebserviceOperations.getDocumentPid(index, dr.identity().value(), document);
        docInfo = WebserviceOperations.docInfo(index, pid, document, metadataFieldsToList);
        // Snippets
        HitResults hitResults = dr.storedResults().window(0, 5);
        numberOfHits = dr.storedResults().size();

        concordancesToShow = new ArrayList<>();
        kwicsToShow = new ArrayList<>();
        if (hitResults.resultsStats().processedAtLeast(1)) {
            ContextSettings contextSettings = params.contextSettings();
            Concordances theConcordances = null;
            Kwics theKwics = null;
            Hits hitsList = hitResults.getHits();
            if (contextSettings.concType() == ConcordanceType.CONTENT_STORE)
                theConcordances = hitsList.concordances(contextSettings.size(), ConcordanceType.CONTENT_STORE);
            else
                theKwics = hitsList.kwics(index.defaultContextSize());
            for (EphemeralHit hit: hitsList) {
                // TODO: use RequestHandlerDocSnippet.getHitOrFragmentInfo()
                if (contextSettings.concType() == ConcordanceType.CONTENT_STORE) {
                    // Add concordance from original XML
                    Concordance c = theConcordances.get(hit);
                    concordancesToShow.add(c);
                } else {
                    // Add KWIC info
                    Kwic c = theKwics.get(hit);
                    kwicsToShow.add(c);
                }
            } // for hits2
        } // if snippets
    }

    public String getPid() {
        return pid;
    }

    public ResultDocInfo getDocInfo() {
        return docInfo;
    }

    public List<Concordance> getConcordancesToShow() {
        return concordancesToShow;
    }

    public List<Kwic> getKwicsToShow() {
        return kwicsToShow;
    }

    public long numberOfHits() {
        return numberOfHits;
    }

    public int numberOfHitsToShow() {
        return Math.max(concordancesToShow.size(), kwicsToShow.size());
    }

    public boolean hasConcordances() {
        return !concordancesToShow.isEmpty();
    }

    public Collection<Annotation> getAnnotationsToList() {
        return annotationsToList;
    }
}
