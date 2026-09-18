package nl.inl.blacklab.server.lib;

import java.util.Map;

import nl.inl.blacklab.exceptions.UnsupportedConcordanceRepresentation;
import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.results.hitresults.Concordances;
import nl.inl.blacklab.search.results.hitresults.ContextSize;
import nl.inl.blacklab.search.results.hitresults.Kwics;
import nl.inl.blacklab.search.results.hits.Hit;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.server.exceptions.BadRequest;

/** Keyword in context (KWIC) hits, either reconstructed from the forward index or
 *  fragments cut from the original input document. */
public class ConcordanceContext {

    public static ConcordanceContext kwics(Kwics kwics) {
        return new ConcordanceContext(kwics, null);
    }

    public static ConcordanceContext concordances(Concordances concordances) {
        return new ConcordanceContext(null, concordances);
    }

    public static ConcordanceContext get(Hits hits, ConcordanceType concordanceType, ContextSize contextSize) {
        ConcordanceContext concordanceContext;
        if (concordanceType == ConcordanceType.CONTENT_STORE)
            concordanceContext = ConcordanceContext.concordances(contentStoreConcordances(hits, contextSize));
        else
            concordanceContext = ConcordanceContext.kwics(hits.kwics(contextSize));
        return concordanceContext;
    }

    public static Concordances contentStoreConcordances(Hits hits, ContextSize contextSize) {
        try {
            return hits.concordances(contextSize, ConcordanceType.CONTENT_STORE);
        } catch (UnsupportedConcordanceRepresentation e) {
            throw new BadRequest("UNSUPPORTED_CONCORDANCE_REPRESENTATION", e.getMessage(), e);
        }
    }

    private final Concordances concordances;

    private final Kwics kwics;

    private ConcordanceContext(Kwics kwics, Concordances concordances) {
        this.kwics = kwics;
        this.concordances = concordances;
    }

    public boolean isConcordances() {
        return concordances != null;
    }

    public Concordance getConcordance(Hit hit) {
        return concordances.get(hit);
    }

    public Kwic getKwic(Hit hit) {
        return kwics.get(hit);
    }

    public Map<AnnotatedField, Kwic> getForeignKwics(Hit hit) {
        return kwics.getForeignKwics(hit);
    }
}
