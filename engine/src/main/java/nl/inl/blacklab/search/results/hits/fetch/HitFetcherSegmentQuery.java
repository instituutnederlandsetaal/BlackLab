package nl.inl.blacklab.search.results.hits.fetch;

import java.io.IOException;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.queries.spans.SpanWeight;
import org.apache.lucene.queries.spans.Spans;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.util.Bits;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.search.lucene.BLSpanWeight;
import nl.inl.blacklab.search.lucene.BLSpans;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.results.hits.EphemeralHit;

/** 
 * Helper class for use with {@link HitFetcherQuery}.
 * <p>
 * HitFetcherQuery constructs one HitFetcherQuerySegment instance per segment ({@link LeafReaderContext}) of
 * the index. The HitFetcherQuerySegment will then produce results for the segment, periodically merging them back to
 * the global resultset passed in.
 */
public class HitFetcherSegmentQuery extends HitFetcherSegmentAbstract {

    /** Set until initialized (needed to get spans); null afterwards */
    BLSpanWeight weight;

    /** Set until initialized (needed to construct own hitQueryContext); null afterwards */
    HitQueryContext sourceHitQueryContext;
    
    private int numMatchInfos;

    BLSpans spans; // usually lazy initialization - takes a long time to set up and holds a large amount of memory.
    // Set to null after we're finished

    /** Allows us to more efficiently step to the next potentially matching document */
    private DocIdSetIterator twoPhaseApproximation;

    /** Allows us to check that doc matched by approximation is an actual match */
    private TwoPhaseIterator twoPhaseIt;

    private Bits liveDocs;

    private boolean hasPrefetchedHit = false;

    /**
     * Construct an uninitialized HitFetcherQuerySegment that will retrieve its own Spans object on when it's ran.
     * <p>
     * HitFetcherQuerySegment will self-initialize (meaning its Spans object and HitQueryContext are set). This is done
     * because HitFetcherQuerySegments can hold a lot of memory and time to set up and only a few are active at a time.
     * <p>
     * All HitFetcherQuerySegments share an instance of MatchInfoDefs (via the hit query context, of which each
     * HitFetcherQuerySegment gets a personalized copy, but with the same shared MatchInfoDefs instance).
     * <p>
     * HitFetcherQuerySegments will register their match infos with the MatchInfoDefs instance. Often the first
     * HitFetcherQuerySegment will register all match infos, but sometimes the first HitFetcherQuerySegment only
     * matches some match infos, and subsequent HitFetcherQuerySegments will register additional match infos. This is
     * dealt with later (when merging two matchInfo[] arrays of different length).
     * <p>
     *
     * @param weight    span weight we're querying
     * @param state     our state
     */
    HitFetcherSegmentQuery(
        BLSpanWeight weight,
        State state) {
        super(state);
        this.weight = weight;
        this.sourceHitQueryContext = state.hitQueryContext;
        state.hitQueryContext = null; // will be replaced with our own copy during initialize()
        this.spans = null;
    }

    @Override
    public void initialize() {
        try {
            BLSpans spansForWeight = this.weight.getSpans(state.lrc, SpanWeight.Postings.OFFSETS);
            this.weight = null;
            if (spansForWeight == null) { // This is normal, sometimes a section of the index does not contain hits.
                this.isDone = true;
                return;
            }
            // If the resulting spans are not known to be sorted and unique, ensure that now.
            this.spans = BLSpans.ensureSortedUnique(spansForWeight);

            // We use two-phase iteration which allows us to skip to matching documents quickly.
            // Determine two-phase iterator and approximation now (approximation will return documents
            // that may match; iterator can check if one actually does match).
            this.twoPhaseIt = spans.asTwoPhaseIterator();
            this.twoPhaseApproximation = twoPhaseIt == null ? spans : twoPhaseIt.approximation();

            // Get query context for this spans and register it with our query.
            // Then determine the number of match infos (query registers match infos with the context)
            state.hitQueryContext = this.sourceHitQueryContext.withSpans(this.spans);
            this.sourceHitQueryContext = null;
            this.spans.setHitQueryContext(state.hitQueryContext);
            this.numMatchInfos = state.hitQueryContext.numberOfMatchInfos();
        } catch (IOException e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    /**
     * Step through all hits in all documents in this spans object.
     *
     * @param liveDocs used to check if the document is still alive in the index.
     * @return true if the spans has been advanced to the next hit, false if out of hits.
     */
    private boolean advanceSpansToNextHit(Bits liveDocs) throws IOException {
        // Make sure we've nexted at least once
        int doc = twoPhaseApproximation.docID();
        if (doc != -1) {
            // See if there's more matches in the current document
            int start = spans.nextStartPosition();
            if (start != Spans.NO_MORE_POSITIONS) {
                // Yes, we're at the next valid match.
                return true;
            }
        }

        // No more matches in this document. Find first match in next matching document.
        while (true) {
            assert twoPhaseApproximation.docID() != DocIdSetIterator.NO_MORE_DOCS;
            doc = twoPhaseApproximation.nextDoc();
            if (doc == DocIdSetIterator.NO_MORE_DOCS) {
                // We're done.
                return false;
            }
            boolean actualMatch = twoPhaseIt == null || twoPhaseIt.matches();
            if (actualMatch && (liveDocs == null || liveDocs.get(doc))) {
                // Document matches. Put us at the first match.
                int startPos = spans.nextStartPosition();
                assert startPos >= 0;
                assert startPos != Spans.NO_MORE_POSITIONS;
                return true;
            }
        }
    }

    protected void runPrepare() throws IOException {
        this.liveDocs = state.lrc.reader().getLiveDocs();
        if (!hasPrefetchedHit) {
            prevDoc = spans.docID();
            hasPrefetchedHit = advanceSpansToNextHit(liveDocs);
        }
    }

    protected boolean runGetHit(EphemeralHit hit) throws IOException {
        if (!hasPrefetchedHit)
            return false;
        assert spans.docID() != DocIdSetIterator.NO_MORE_DOCS;
        assert spans.startPosition() != Spans.NO_MORE_POSITIONS;
        assert spans.endPosition() != Spans.NO_MORE_POSITIONS;
        hit.doc_ = spans.docID();
        hit.start_ = spans.startPosition();
        hit.end_ = spans.endPosition();
        if (numMatchInfos > 0) {
            hit.matchInfos_ = new MatchInfo[numMatchInfos];
            state.hitQueryContext.getMatchInfo(hit.matchInfos_);
        } else {
            hit.matchInfos_ = null;
        }

        // Position spans for the next hit after this
        hasPrefetchedHit = advanceSpansToNextHit(liveDocs);
        return true;
    }

    protected void runCleanup() {
        this.spans = null;
    }
}
