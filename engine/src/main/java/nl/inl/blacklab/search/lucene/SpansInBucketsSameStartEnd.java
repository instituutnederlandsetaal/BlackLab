package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Iterator;

import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.payloads.PayloadSpanCollector;
import org.apache.lucene.search.spans.Spans;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * Gather buckets where all hits have the same start and end position.
 * <p>
 * Only makes sense if there's match info that may be different between the
 * otherwise identical matches; if there's no match info, you should just make
 * sure you eliminate any duplicate matches.
 */
class SpansInBucketsSameStartEnd extends SpansInBuckets {

    class SameStartEndBucket implements Bucket {

        /** Current start position */
        protected int currentStartPosition = -1;

        /** Current end position */
        protected int currentEndPosition = -1;

        /** How many hits with this start and end? */
        protected int currentBucketSize = -1;

        /**
         * For each hit we fetched, store the match info (e.g. captured groups, relations),
         * so we don't lose this information.
         */
        protected ObjectArrayList<MatchInfo[]> matchInfos = null;

        /**
         * For each hit we fetched, store the active relation info, if any.
         */
        protected ObjectArrayList<RelationInfo> activeRelationPerHit = null;

        /** Payloads in bucket, if enabled */
        protected ObjectArrayList<byte[]> payloads = null;

        /** Terms, if enabled */
        protected ObjectArrayList<Term> terms = null;

        @Override
        public int size() {
            return currentBucketSize;
        }

        @Override
        public int startPosition(int indexInBucket) {
            return currentStartPosition;
        }

        @Override
        public int endPosition(int indexInBucket) {
            return currentEndPosition;
        }

        @Override
        public MatchInfo[] matchInfos(int indexInBucket) {
            return matchInfos.get(indexInBucket);
        }

        @Override
        public RelationInfo relationInfo(int indexInBucket) {
            return activeRelationPerHit.get(indexInBucket);
        }

        /** Get relation id from payload (if enabled) */
        public byte[] payload(int indexInBucket) {
            return payloads.get(indexInBucket);
        }

        public Term term(int indexInBucket) {
            return terms.get(indexInBucket);
        }

        public void clear() {
            currentStartPosition = -1;
            currentEndPosition = -1;
            currentBucketSize = 0;
            if (doMatchInfo) {
                if (matchInfos == null)
                    matchInfos = new ObjectArrayList<>(LIST_INITIAL_CAPACITY);
                else
                    matchInfos.clear();
                if (activeRelationPerHit == null)
                    activeRelationPerHit = new ObjectArrayList<>(LIST_INITIAL_CAPACITY);
                else
                    activeRelationPerHit.clear();
            }
            if (collectPayloadsAndTerms) {
                if (payloads == null)
                    payloads = new ObjectArrayList<>(LIST_INITIAL_CAPACITY);
                else
                    payloads.clear();
                if (terms == null)
                    terms = new ObjectArrayList<>(LIST_INITIAL_CAPACITY);
                else
                    terms.clear();
            }
        }

        @Override
        public int gatherHitsInternal() throws IOException {
            assert positionedAtHitIfPositionedInDoc();
            doMatchInfo = hitQueryContext != null && (clauseCapturesMatchInfo && hitQueryContext.numberOfMatchInfos() > 0 ||
                    hitQueryContext.hasRelationCaptures());
            clear();
            assert(source.startPosition() >= 0 && source.startPosition() != Spans.NO_MORE_POSITIONS);
            gatherHits();
            assert(source.startPosition() >= 0);
            assert positionedAtHitIfPositionedInDoc();
            return source.docID();
        }

        private void gatherHits() throws IOException {
            currentStartPosition = source.startPosition();
            currentEndPosition = source.endPosition();
            currentBucketSize = 0;
            int sourceStart = currentStartPosition;
            while (sourceStart != Spans.NO_MORE_POSITIONS &&
                    sourceStart == currentStartPosition &&
                    source.endPosition() == currentEndPosition) {
                assert positionedAtHitIfPositionedInDoc();
                assert source.startPosition() >= 0 && source.startPosition() != Spans.NO_MORE_POSITIONS;
                assert source.endPosition() >= 0 && source.endPosition() != Spans.NO_MORE_POSITIONS;
                if (doMatchInfo) {
                    // Store match information such as captured groups and active relation (if any)
                    int n = hitQueryContext == null ? 0 : hitQueryContext.numberOfMatchInfos();
                    MatchInfo[] matchInfo = new MatchInfo[n];
                    source.getMatchInfo(matchInfo);
                    matchInfos.add(matchInfo);
                    RelationInfo relationInfo = source.getRelationInfo();
                    activeRelationPerHit.add(relationInfo == null ? null  : relationInfo.copy());
                }
                if (collectPayloadsAndTerms) {
                    // This is used for matching relations with the new RelationsStrategySeparateTerms.
                    // so we know every term has a payload, and every payload starts with the relationId.
                    // Collect them now.
                    collector.reset();
                    try {
                        source.collect(collector);
                        Iterator<byte[]> iterator = collector.getPayloads().iterator();
                        byte[] payload = iterator.hasNext() ? iterator.next() : null;
                        payloads.add(payload);
                        terms.add(collectedTerm);
                    } catch (IOException e) {
                        throw new RuntimeException("Error getting payload");
                    }
                }
                assert positionedAtHitIfPositionedInDoc();
                currentBucketSize++;
                sourceStart = source.nextStartPosition();
            }
        }
    }

    /** Collect payload and terms? */
    protected final boolean collectPayloadsAndTerms;

    /** Used to collect payloads and terms, if enabled */
    private final PayloadSpanCollector collector;

    /** Current term while collecting bucket hits */
    private Term collectedTerm;

    /**
     * Construct the spans in buckets object.
     *
     * @param source (startpoint-sorted) source spans
     */
    public SpansInBucketsSameStartEnd(BLSpans source) {
        this(source, false);
    }

    public byte[] payload(int index) {
        return ((SameStartEndBucket)bucket).payload(index);
    }

    public Term term(int index) {
        return ((SameStartEndBucket)bucket).term(index);
    }

    /**
     * Construct the spans in buckets object.
     *
     * @param source (startpoint-sorted) source spans
     * @param collectPayloadsAndTerms whether to retrieve payloads and terms
     *                    (if true, rel strategy MUST be the new RelationsStrategySeparateTerms!!)
     */
    public SpansInBucketsSameStartEnd(BLSpans source, boolean collectPayloadsAndTerms) {
        super(source);
        this.collectPayloadsAndTerms = collectPayloadsAndTerms;
        this.collector = !collectPayloadsAndTerms ? null : new PayloadSpanCollector() {
            @Override
            public void collectLeaf(PostingsEnum postings, int position, Term term) throws IOException {
                collectedTerm = term;
                super.collectLeaf(postings, position, term);
            }
        };
        setBucket(new SameStartEndBucket());
    }

    @Override
    public String toString() {
        return "SIB-STARTEND(" + source + ")";
    }
}
