package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.queries.spans.Spans;
import org.apache.lucene.search.DocIdSetIterator;

import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.search.results.EphemeralHit;
import nl.inl.blacklab.search.results.HitsInternal;
import nl.inl.blacklab.search.results.HitsInternalMutable;

/** Gather all hits from a single index segment for further processing (e.g. sorting). */
public abstract class SegmentHitsFetchAll implements SegmentHits {

    /** Our hits */
    protected final HitsInternalMutable hits;

    /** Our current hit index */
    private long currentIndex = -1;

    public SegmentHitsFetchAll(BLSpans clause, HitQueryContext context) {
        hits = gatherHits(clause, context);
    }

    @Override
    public boolean nextHit(EphemeralHit h) {
        if (currentIndex < hits.size() - 1) {
            currentIndex++;
            hits.getEphemeral(currentIndex, h);
            return true;
        }
        return false;
    }

    protected HitsInternalMutable gatherHits(BLSpans clause, HitQueryContext context) {
        try {
            MatchInfo[] matchInfos = null;
            if (context.numberOfMatchInfos() > 0) {
                matchInfos = new MatchInfo[context.numberOfMatchInfos()];
            }
            final HitsInternalMutable hits = HitsInternal.create(context.getField(), context.getMatchInfoDefs(), -1,
                    true, false);
            while (true) {
                int doc = clause.nextDoc();
                if (doc == DocIdSetIterator.NO_MORE_DOCS)
                    break;
                while (true) {
                    int start = clause.nextStartPosition();
                    if (start == Spans.NO_MORE_POSITIONS)
                        break;
                    int end = clause.endPosition();
                    if (clause.hasMatchInfo())
                        clause.getMatchInfo(matchInfos);
                    hits.add(doc, start, end, matchInfos);
                }
            }
        } catch (IOException e) {
            throw new InvalidIndex(e);
        }
        return hits;
    }
}
