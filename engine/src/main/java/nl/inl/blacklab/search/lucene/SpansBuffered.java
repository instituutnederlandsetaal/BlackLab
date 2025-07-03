package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.search.spans.SpanCollector;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * Spans that can be rewound to the last mark set.
 *
 * Similar to Java's BufferedInputStream.
 */
class SpansBuffered extends BLSpans {

    protected class Bucket {

        private final static int LIST_INITIAL_CAPACITY = 100;

        private final boolean doMatchInfo;

        /** Starts and ends of hits in our bucket */
        private final LongList startsEnds = new LongArrayList(LIST_INITIAL_CAPACITY);

        /**
         * For each hit we fetched, store the match info (e.g. captured groups, relations),
         * so we don't lose this information.
         */
        private  ObjectArrayList<MatchInfo[]> matchInfos = null;

        /**
         * For each hit we fetched, store the active relation info, if any.
         */
        private ObjectArrayList<RelationInfo> activeRelationPerHit = null;

        Bucket(boolean doMatchInfo) {
            this.doMatchInfo = doMatchInfo;
        }

        public int size() {
            return startsEnds.size();
        }

        public int startPosition(int indexInBucket) {
            return (int)(startsEnds.getLong(indexInBucket) >> 32);
        }

        public int endPosition(int indexInBucket) {
            return (int) startsEnds.getLong(indexInBucket);
        }

        public MatchInfo[] matchInfos(int indexInBucket) {
            return matchInfos.get(indexInBucket);
        }

        public RelationInfo relationInfo(int indexInBucket) {
            return activeRelationPerHit.get(indexInBucket);
        }

        private void clear() {
            startsEnds.clear();
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
        }

        public void add(long span) {
            startsEnds.add(span);
        }

        public void add(long span, MatchInfo[] matchInfo, RelationInfo relationInfo) {
            add(span);
            if (doMatchInfo) {
                matchInfos.add(matchInfo);
                activeRelationPerHit.add(relationInfo == null ? null : relationInfo.copy());
            }
        }

        public void removeElements(int from, int to) {
            startsEnds.removeElements(from, to);
            if (doMatchInfo) {
                matchInfos.removeElements(from, to);
                activeRelationPerHit.removeElements(from, to);
            }
        }

        public boolean isEmpty() {
            return startsEnds.isEmpty();
        }
    }

    private final BLSpans clause;

    private HitQueryContext context;

    private static final int BEFORE_FIRST_HIT = -1;

    private static final int READING_FROM_CLAUSE = -2;

    /** Index in buffered hits. If READING_FROM_CLAUSE, we're reading directly from the clause. */
    private int indexInBuffer = READING_FROM_CLAUSE;

    /** Was mark() called before the first call to nextStartPosition()?
     *  (note that this is implicit when starting a document)
     */
    private boolean markBeforeFirstHit;

    /** Dit the clause return NO_MORE_POSITIONS for the current doc? */
    private boolean clauseExhausted;

    /** Do we have to capture match info and/or current relation? */
    private boolean doMatchInfo;

    /** Cached hits that we can rewind to. */
    private Bucket buffer;

    /**
     * Constructs buffered spans.
     *
     * @param clause the clause to buffer hits from
     */
    public SpansBuffered(BLSpans clause) {
        super(clause.guarantees());
        this.clause = clause;
        markBeforeFirstHit = true;
        clauseExhausted = false;
    }

    @Override
    public void setHitQueryContext(HitQueryContext context) {
        if (context != null) // (can only happen in test)
            super.setHitQueryContext(context);
        doMatchInfo = context != null && (childClausesCaptureMatchInfo && context.numberOfMatchInfos() > 0 ||
                context.hasRelationCaptures() || clause instanceof SpansRelations);
        // (NOTE: SpansRelations is a special case, because it may not have match info, but it obviously does have
        //  relation info)
        buffer = new Bucket(doMatchInfo);
    }

    @Override
    protected void passHitQueryContextToClauses(HitQueryContext context) {
        this.context = context;
        clause.passHitQueryContextToClauses(context);
    }

    /**
     * @return the Lucene document id of the current hit
     */
    @Override
    public int docID() {
        return clause.docID();
    }

    /**
     * @return end position of current hit
     */
    @Override
    public int endPosition() {
        if (indexInBuffer == READING_FROM_CLAUSE)
            return clause.endPosition();
        if (indexInBuffer == BEFORE_FIRST_HIT)
            return BEFORE_FIRST_HIT; // before first hit
        return buffer.endPosition(indexInBuffer);
    }

    @Override
    public int nextDoc() throws IOException {
        clearBuffer();
        markBeforeFirstHit = true;
        clauseExhausted = false;
        return clause.nextDoc();
    }

    /** Reset buffer for a new document, or because mark() was called. */
    private void clearBuffer() {
        buffer.clear();
        indexInBuffer = READING_FROM_CLAUSE;
    }

    public void mark() {
        if (indexInBuffer == READING_FROM_CLAUSE) {
            markBeforeFirstHit = clause.startPosition() == BEFORE_FIRST_HIT;
            // Keep only current hit (the last one added) in buffer
            if (buffer.size() > 1)
                buffer.removeElements(0, buffer.size() - 1);
        } else {
            markBeforeFirstHit = indexInBuffer == BEFORE_FIRST_HIT;
            if (indexInBuffer > 0) {
                // We were already producing buffered hits; drop part of the buffer and adjust the index accordingly.
                buffer.removeElements(0, indexInBuffer);
                indexInBuffer = 0;
            }
        }
    }

    public void reset() throws IOException {

        //return; //@@@TEST

        // Go to the start of the buffer
        if (markBeforeFirstHit) {
            // mark() was (implicitly?) called before the first call to nextStartPosition()
            indexInBuffer = clause.startPosition() == BEFORE_FIRST_HIT ? READING_FROM_CLAUSE : BEFORE_FIRST_HIT;
        } else {
            // mark() was called at a valid hit
            indexInBuffer = buffer.isEmpty() ? READING_FROM_CLAUSE : 0;
        }
    }

    /**
     * Go to next span.
     *
     * @return true if we're at the next span, false if we're done
     */
    @Override
    public int nextStartPosition() throws IOException {
        if (indexInBuffer != READING_FROM_CLAUSE) {
            // See if there's another hit in the buffer
            indexInBuffer++;
            if (indexInBuffer >= buffer.size()) {
                // No, back to iterating from the clause
                indexInBuffer = READING_FROM_CLAUSE;
            } else {
                return buffer.startPosition(indexInBuffer);
            }
        }

        // Read hit from clause, add to buffer
        if (clauseExhausted)
            return NO_MORE_POSITIONS;
        int start = clause.nextStartPosition();
        if (start == NO_MORE_POSITIONS) {
            clauseExhausted = true;
            return NO_MORE_POSITIONS;
        }
        long span = ((long) start << 32) | clause.endPosition();
        if (doMatchInfo) {
            // Store match information such as captured groups and active relation (if any)
            int n = context == null ? 0 : context.numberOfMatchInfos();
            MatchInfo[] matchInfo = new MatchInfo[n];
            clause.getMatchInfo(matchInfo);
            RelationInfo relationInfo = clause.getRelationInfo();
            buffer.add(span, matchInfo, relationInfo);
        } else {
            buffer.add(span);
        }
        return start;
    }

    /**
     * Skip to the specified document (or the first document after it containing
     * hits).
     *
     * @param doc the doc number to skip to (or past)
     * @return true if we're still pointing to a valid hit, false if we're done
     */
    @Override
    public int advance(int doc) throws IOException {
        clearBuffer();
        markBeforeFirstHit = true;
        clauseExhausted = false;
        return clause.advance(doc);
    }

    /**
     * @return start of current span
     */
    @Override
    public int startPosition() {
        if (indexInBuffer == READING_FROM_CLAUSE)
            return clause.startPosition();
        return buffer.startPosition(indexInBuffer);
    }

    @Override
    public String toString() {
        return "BUF(" + clause + ")";
    }

    @Override
    public void getMatchInfo(MatchInfo[] matchInfo) {
        if (!doMatchInfo)
            return;
        if (indexInBuffer == READING_FROM_CLAUSE)
            clause.getMatchInfo(matchInfo);
        else {
            MatchInfo[] thisMatchInfo = buffer.matchInfos(indexInBuffer);
            if (thisMatchInfo != null) {
                int n = Math.min(matchInfo.length, thisMatchInfo.length);
                for (int i = 0; i < n; i++) {
                    if (thisMatchInfo[i] != null) // don't overwrite other clause's captures!
                        matchInfo[i] = thisMatchInfo[i];
                }
            }
        }
    }

    @Override
    public boolean hasMatchInfo() {
        return clause.hasMatchInfo();
    }

    @Override
    public RelationInfo getRelationInfo() {
        if (indexInBuffer == READING_FROM_CLAUSE)
            return clause.getRelationInfo();
        return buffer.relationInfo(indexInBuffer);
    }

    @Override
    public int width() {
        return endPosition() - startPosition();
    }

    @Override
    public void collect(SpanCollector collector) throws IOException {
        // (payloads and such are not supported by this class yet)
    }

    @Override
    public float positionsCost() {
        return clause.positionsCost();
    }

    @Override
    public TwoPhaseIterator asTwoPhaseIterator() {
        TwoPhaseIterator twoPhaseIt = clause.asTwoPhaseIterator();
        return twoPhaseIt == null ? null : new TwoPhaseIterator(twoPhaseIt.approximation()) {
            @Override
            public boolean matches() throws IOException {
                clearBuffer();
                markBeforeFirstHit = true;
                clauseExhausted = false;
                return twoPhaseIt.matches();
            }

            @Override
            public float matchCost() {
                return 0;
            }
        };
    }

}
