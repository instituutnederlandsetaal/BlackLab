package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Objects;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.search.spans.Spans;

/**
 * Base class for retrieving lists ("buckets") of related matches
 * instead of individual matches like with Spans.
 *
 * This is useful for efficiently processing related matches (i.e.
 * using lists of consecutive matches to resolve a repeating clause).
 *
 * Note that in these classes, we avoid the term 'group' and 'grouping'
 * because we already use these terms for the generic way of grouping spans
 * (nl.inl.blacklab.search.grouping), while this is more focused on speed and
 * efficiency of certain specific operations.
 *
 * Specifically, SpansInBuckets is designed to have random access to the
 * contents of a bucket, but for efficiency's sake, only has sequential access
 * to the buckets themselves. Also, SpansInBuckets uses subclassing instead of
 * GroupIdentity objects to determine what goes in a bucket. This makes it
 * easier to optimize.
 *
 * Note that with SpansInBuckets, all hits in a bucket must be from a single
 * document.
 */
public abstract class SpansInBuckets extends DocIdSetIterator implements SpanGuaranteeGiver {
    
    /** What initial capacity to reserve for lists to avoid too much reallocation */
    public static final int LIST_INITIAL_CAPACITY = 1000;

    /** Return value for nextBucket() etc. indicating that there are no more */
    public static final int NO_MORE_BUCKETS = Spans.NO_MORE_POSITIONS;

    /** Our source spans */
    protected final BLSpans source;

    /** If true, we need to call nextStartPosition() once before gathering hits in current bucket.
     *  (this happens at the start of the document; in any other case, we're already at the first
     *   hit in the next bucket, because we determine if we're done with a bucket by iterating over
     *   hits until we find one that doesn't belong in the current bucket)
     *  (we used to just advance to the first hit in the document whenever we started a new one,
     *   but this gets messy in combination with two-phase iterators, which advance through documents
     *   without looking at hits)
     */
    protected boolean beforeFirstBucketHit = false;

    /** Our current bucket of hits.
     *  Also contains the logic for how to gather a bucket. */
    protected Bucket bucket;

    /** Context, containing e.g. match info names */
    protected HitQueryContext hitQueryContext;

    /** Is there match info (e.g. captured groups) for each hit that we need to store? */
    protected boolean doMatchInfo;

    /** Does our clause capture any match info? If not, we don't need to mess with those */
    protected boolean clauseCapturesMatchInfo = true;

    protected SpansInBuckets(BLSpans source) {
        this.source = source; // might be null when testing (MockSpans)
    }

    /** Set the bucket to use.
     *
     * Separate setter instead of constructor parameter because we usually
     * instantiate an inner class.
     */
    void setBucket(Bucket bucket) {
        this.bucket = Objects.requireNonNull(bucket);
    }

    @Override
    public int docID() {
        return source.docID();
    }

    /**
     * Assert that, if our clause is positioned at a doc, nextStartPosition() has also been called.
     *
     * Sanity check to be called from assertions at the start and end of methods that change the internal state.
     *
     * We require this because nextBucket() expects the clause to be positioned at a hit. This is because
     * for certain bucketing operations we can only decide we're done with a bucket if we're at the first hit
     * that doesn't belong in the bucket.
     *
     * If {@link #beforeFirstBucketHit} is set and we're not at a hit, that's fine too: in that case, we've just
     * started a document and haven't nexted yet (we defer that because of two-phase iterators).
     *
     * @return true if positioned at a hit (or at a doc and nextStartPosition() has been called), false if not
     */
    protected boolean positionedAtHitIfPositionedInDoc() {
        return positionedAtHitIfPositionedInDoc(source);
    }

    public int bucketSize() {
        return bucket.size();
    }

    public int bucketStart() {
        return startPosition(0);
    }

    public int bucketEnd() {
        return endPosition(0);
    }

    public int startPosition(int indexInBucket) {
        return bucket.startPosition(indexInBucket);
    }

    public int endPosition(int indexInBucket) {
        return bucket.endPosition(indexInBucket);
    }

    @Override
    public int nextDoc() throws IOException {
        assert positionedAtHitIfPositionedInDoc();
        if (source.docID() != DocIdSetIterator.NO_MORE_DOCS) {
            if (source.nextDoc() != NO_MORE_DOCS) {
                prepareForFirstBucketInDocument(source);
            }
        }
        assert positionedAtHitIfPositionedInDoc();
        assert source.docID() == DocIdSetIterator.NO_MORE_DOCS || (beforeFirstBucketHit && source.startPosition() < 0);
        return source.docID();
    }

    public int nextBucket() throws IOException {
        assert positionedAtHitIfPositionedInDoc();
        assert source.docID() >= 0;
        if (source.docID() < 0) {
            // Not nexted yet, no bucket
            return -1;
        }
        ensureAtFirstHit(source);
        if (source.docID() == DocIdSetIterator.NO_MORE_DOCS || source.startPosition() == Spans.NO_MORE_POSITIONS)
            return NO_MORE_BUCKETS;
        assert(source.startPosition() >= 0 && source.startPosition() != Spans.NO_MORE_POSITIONS);
        assert positionedAtHitIfPositionedInDoc();
        return bucket.gatherHitsInternal();
    }

    /**
     * Go to the next bucket at or beyond the specified start point.
     * <p>
     * Always at least advances to the next bucket, even if we were already at or
     * beyond the specified target.
     * <p>
     * Note that this will only work correctly if the underlying Spans is startpoint-sorted.
     *
     * @param targetPos the target start point
     * @return docID if we're at a valid bucket, or NO_MORE_BUCKETS if we're done.
     */
    public int advanceBucket(int targetPos) throws IOException {
        assert positionedAtHitIfPositionedInDoc();
        assert source.docID() >= 0;
        if (source.startPosition() >= targetPos) {
            int i = nextBucket();
            assert positionedAtHitIfPositionedInDoc();
            return i;
        }
        if (source.advanceStartPosition(targetPos) == Spans.NO_MORE_POSITIONS) {
            assert positionedAtHitIfPositionedInDoc();
            return NO_MORE_BUCKETS;
        }
        assert(source.startPosition() >= 0 && source.startPosition() != Spans.NO_MORE_POSITIONS);
        assert positionedAtHitIfPositionedInDoc();
        int i = bucket.gatherHitsInternal();
        assert positionedAtHitIfPositionedInDoc();
        return i;
    }

    @Override
    public int advance(int target) throws IOException {
        assert target >= 0 && target > docID();
        assert positionedAtHitIfPositionedInDoc();
        int docId = source.advance(target);
        if (docId != NO_MORE_DOCS) {
            prepareForFirstBucketInDocument(source);
        }
        assert source.docID() == DocIdSetIterator.NO_MORE_DOCS || (beforeFirstBucketHit && source.startPosition() < 0);
        assert docId >= 0 && docId >= target;
        assert positionedAtHitIfPositionedInDoc();
        return docId;
    }

    public void setHitQueryContext(HitQueryContext context) {
        clauseCapturesMatchInfo = hasMatchInfo();
        this.hitQueryContext = context;
        source.setHitQueryContext(context);
    }

    public void getMatchInfo(int indexInBucket, MatchInfo[] matchInfo) {
        if (!doMatchInfo)
            return;
        MatchInfo[] thisMatchInfo = bucket.matchInfos(indexInBucket);
        if (thisMatchInfo != null) {
            int n = Math.min(matchInfo.length, thisMatchInfo.length);
            for (int i = 0; i < n; i++) {
                if (thisMatchInfo[i] != null) // don't overwrite other clause's captures!
                    matchInfo[i] = thisMatchInfo[i];
            }
        }
    }

    public boolean hasMatchInfo() {
        return source.hasMatchInfo();
    }

    /**
     * Get the "active" relation info.
     * <p>
     * A query that finds and combines several relations always has one
     * active relation. This relation is used when we call rspan(),
     * or if we combine the query with another relation query, e.g. using the
     * &amp; operator.
     *
     * @return the relation info, or null if active relation available
     */
    public RelationInfo getRelationInfo(int indexInBucket) {
        return doMatchInfo ? bucket.relationInfo(indexInBucket) : null;
    }

    @Override
    public SpanGuarantees guarantees() {
        return source.guarantees();
    }

    public TwoPhaseIterator asTwoPhaseIterator() {
        assert positionedAtHitIfPositionedInDoc();
        TwoPhaseIterator inner = source.asTwoPhaseIterator();
        if (inner != null) {
            return new TwoPhaseIterator(inner.approximation()) {
                @Override
                public boolean matches() throws IOException {
                    if (!inner.matches())
                        return false;
                    assert source.docID() >= 0 && source.docID() != NO_MORE_DOCS;
                    prepareForFirstBucketInDocument(source);
                    return true;
                }

                @Override
                public float matchCost() {
                    return inner.matchCost();
                }

                @Override
                public String toString() {
                    return "SpansInBucketsAbstract@asTwoPhaseIterator(source=" + source + ", iter=" + inner + ")";
                }
            };
        } else {
            return new TwoPhaseIterator(source) {
                @Override
                public boolean matches() throws IOException {
                    assert source.docID() >= 0 && source.docID() != NO_MORE_DOCS;
                    prepareForFirstBucketInDocument(source);
                    return true;
                }

                @Override
                public float matchCost() {
                    return source.positionsCost(); // overestimate
                }

                @Override
                public String toString() {
                    return "SpansInBucketsAbstract@asTwoPhaseIterator(source=" + source + ")";
                }
            };
        }
    }

    @Override
    public long cost() {
        return source.cost();
    }

    /**
     * Assert that, if our clause is positioned at a doc, nextStartPosition() has also been called.
     *
     * Sanity check to be called from assertions at the start and end of methods that change the internal state.
     *
     * We require this because nextBucket() expects the clause to be positioned at a hit. This is because
     * for certain bucketing operations we can only decide we're done with a bucket if we're at the first hit
     * that doesn't belong in the bucket.
     *
     * If {@link #beforeFirstBucketHit} is set and we're not at a hit, that's fine too: in that case, we've just
     * started a document and haven't nexted yet (we defer that because of two-phase iterators).
     *
     * @return true if positioned at a hit (or at a doc and nextStartPosition() has been called), false if not
     */
    protected boolean positionedAtHitIfPositionedInDoc(BLSpans source) {
        return source.docID() < 0 || source.docID() == NO_MORE_DOCS ||  // not in doc?
                (beforeFirstBucketHit && source.startPosition() < 0) ||     // just started a doc?
                source.startPosition() >= 0;                                // positioned at hit in doc
    }

    protected void prepareForFirstBucketInDocument(BLSpans source) {
        // Mark that we've just started a new document, and we need to call source.nextStartPosition()
        // first before gathering hits in the current bucket.
        // (from the second bucket in a document onwards, we always know we're already at the first hit
        //  in the bucket)
        beforeFirstBucketHit = true;
        assert positionedAtHitIfPositionedInDoc(source);
    }

    protected void ensureAtFirstHit(BLSpans source) throws IOException {
        if (beforeFirstBucketHit) {
            // We've just started a new document, and haven't called nextStartPosition() yet. Do so now.
            source.nextStartPosition();
            beforeFirstBucketHit = false;
        }
    }

    public float positionsCost() {
        throw new UnsupportedOperationException(); // asTwoPhaseIterator never returns null here.
    }

    public int width() {
        return source.width();
    }

    /** Represents a bucket of hits, and the logic how to gather it */
    interface Bucket {
        /** Bucket size */
        int size();

        /** Start position of hit in bucket */
        int startPosition(int indexInBucket);

        /** End position of hit in bucket */
        int endPosition(int indexInBucket);

        /** Match info for hit in bucket */
        MatchInfo[] matchInfos(int indexInBucket);

        /** Relation info for hit in bucket */
        RelationInfo relationInfo(int indexInBucket);

        /** Gather a new bucket */
        int gatherHitsInternal() throws IOException;
    }
}
