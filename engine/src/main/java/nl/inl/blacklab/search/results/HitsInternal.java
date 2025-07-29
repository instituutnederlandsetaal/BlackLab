package nl.inl.blacklab.search.results;

import java.io.IOException;
import java.util.function.Consumer;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.queries.spans.SpanWeight;
import org.apache.lucene.queries.spans.Spans;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.util.Bits;

import it.unimi.dsi.fastutil.ints.IntIterator;
import nl.inl.blacklab.Constants;
import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.BLSpanWeight;
import nl.inl.blacklab.search.lucene.BLSpans;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;

/**
 * A list of simple hits.
 * <p>
 * Contrary to {@link Hits}, this only contains doc, start and end
 * for each hit, so no captured groups information, and no other
 * bookkeeping (hit/doc retrieved/counted stats, etc.).
 * <p>
 * This is a read-only interface.
 */
public interface HitsInternal extends Iterable<EphemeralHit>, HitsForHitProps {

    /** Gather all hits for this query from this segment. */
    static HitsInternalMutable gatherAll(BLSpanWeight weight, LeafReaderContext lrc, HitQueryContext context) {
        final HitsInternalMutable hits = create(context.getField(), context.getMatchInfoDefs(), -1,
                true, false);
        try {
            BLSpans clause = weight.getSpans(lrc, SpanWeight.Postings.OFFSETS);
            if (clause == null) {
                // No hits in this segment
                return hits;
            }
            TwoPhaseIterator twoPhaseIt = clause.asTwoPhaseIterator();
            DocIdSetIterator twoPhaseApprox = twoPhaseIt == null ? clause : twoPhaseIt.approximation();
            Bits liveDocs = lrc.reader().getLiveDocs();
            MatchInfo[] matchInfos = null;
            if (context.numberOfMatchInfos() > 0) {
                matchInfos = new MatchInfo[context.numberOfMatchInfos()];
            }
            while (true) {
                // Find the next probably-matching document.
                int doc = twoPhaseApprox.nextDoc();
                if (doc == DocIdSetIterator.NO_MORE_DOCS)
                    break;
                boolean actualMatch = twoPhaseIt == null || twoPhaseIt.matches();
                boolean docIsLive = liveDocs == null || liveDocs.get(doc);
                if (actualMatch && docIsLive) {
                    // Live document that actually matches. Fetch the hits.
                    while (true) {
                        int start = clause.nextStartPosition();
                        if (start == Spans.NO_MORE_POSITIONS)
                            break;
                        int end = clause.endPosition();
                        if (clause.hasMatchInfo())
                            clause.getMatchInfo(matchInfos);
                        hits.add(lrc.docBase + doc, start, end, matchInfos);
                    }
                }
            }
            return hits;
        } catch (IOException e) {
            throw new InvalidIndex(e);
        }
    }

    @Override
    AnnotatedField field();

    @Override
    BlackLabIndex index();

    /** An empty list of hits. */
    static HitsInternal empty(AnnotatedField field, MatchInfoDefs matchInfoDefs) {
        return new HitsInternalNoLock32(field, matchInfoDefs, -1);
    }

    /**
     * Create an empty HitsInternal with an initial capacity.
     *
     * @param initialCapacity initial hits capacity, or default if negative
     * @param allowHugeLists if true, the object created can hold more than {@link Constants#JAVA_MAX_ARRAY_SIZE} hits
     * @param mustLock if true, return a locking implementation. If false, implementation may not be locking.
     * @return HitsInternal object
     */
    static HitsInternalMutable create(AnnotatedField field, MatchInfoDefs matchInfoDefs, long initialCapacity, boolean allowHugeLists, boolean mustLock) {
        return create(field, matchInfoDefs, initialCapacity, allowHugeLists ? Long.MAX_VALUE : Constants.JAVA_MAX_ARRAY_SIZE, mustLock);
    }

    static HitsInternalMutable create(AnnotatedField field, MatchInfoDefs matchInfoDefs, long initialCapacity, long maxCapacity, boolean mustLock) {
        if (maxCapacity > Constants.JAVA_MAX_ARRAY_SIZE && BlackLab.config().getSearch().isEnableHugeResultSets()) {
            if (mustLock)
                return new HitsInternalLock(field, matchInfoDefs, initialCapacity);
            return new HitsInternalNoLock(field, matchInfoDefs, initialCapacity);
        }
        if (initialCapacity > Constants.JAVA_MAX_ARRAY_SIZE)
            throw new UnsupportedOperationException("initialCapacity=" + initialCapacity + " > " + Constants.JAVA_MAX_ARRAY_SIZE + " && !allowHugeLists");
        if (mustLock)
            return new HitsInternalLock32(field, matchInfoDefs, (int)initialCapacity);
        return new HitsInternalNoLock32(field, matchInfoDefs, (int)initialCapacity);
    }

    /**
     * Perform an operation with read lock.
     * <p>
     * If the implementation doesn't support locking, it will simply
     * perform the operation without it.
     *
     * @param cons operation to perform
     */
    void withReadLock(Consumer<HitsInternal> cons);

    /** How many hits does this contains? */
    long size();

    /**
     * Iterate over the doc ids of the hits.
     * <p>
     * NOTE: iterating does not lock the arrays, to do that,
     * it should be performed in a {@link #withReadLock} callback.
     *
     * @return iterator over the doc ids
     */
    IntIterator docsIterator();

    /**
     * Iterate over the hits.
     * <p>
     * NOTE: iterating does not lock the arrays, to do that,
     * it should be performed in a {@link #withReadLock} callback.
     *
     * @return iterator
     */
    @Override
    Iterator iterator();

    /**
     * Return a new object with sorted hits.
     *
     * @param p sort property
     * @return sorted hits
     */
    HitsInternal sorted(HitProperty p);

    @Override
    default HitsInternal getInternalHits() {
        return this;
    }

    /**
     * For iterating through the hits using EphemeralHit
     */
    interface Iterator extends java.util.Iterator<EphemeralHit> {

    }

    static boolean debugCheckAllReasonable(HitsInternal hits) {
        for (EphemeralHit h: hits) {
            assert debugCheckReasonableHit(h);
        }
        return true;
    }

    static boolean debugCheckReasonableHit(Hit h) {
        return debugCheckReasonableHit(h.doc(), h.start(), h.end());
    }

    static boolean debugCheckReasonableHit(int doc, int start, int end) {
        assert doc >= 0 : "Hit doc id must be non-negative, is " + doc;
        assert doc != Spans.NO_MORE_DOCS : "Hit doc id must not equal NO_MORE_DOCS";
        assert start >= 0 : "Hit start must be non-negative, is " + start;
        assert end >= 0 : "Hit end must be non-negative, is " + start;
        assert start <= end : "Hit start " + start + " > end " + end;
        assert start != Spans.NO_MORE_POSITIONS : "Hit start must not equal NO_MORE_POSITIONS";
        assert end != Spans.NO_MORE_POSITIONS : "Hit end must not equal NO_MORE_POSITIONS";
        return true;
    }
}
