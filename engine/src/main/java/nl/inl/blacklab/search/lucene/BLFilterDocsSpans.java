/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Objects;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.search.spans.SpanCollector;
import org.apache.lucene.search.spans.Spans;

/**
 * A {@link Spans} implementation wrapping another spans instance (or any doc iterator, such as SpansInBuckets),
 * providing a framework for quickly skipping non-matching docs.
 * <p>
 * Based on Lucene's FilterSpans.
 */
public abstract class BLFilterDocsSpans<T extends DocIdSetIterator> extends BLSpans {

    /**
     * The Spans object we're wrapping
     */
    protected final T in;

    /**
     * Wrap the given {@link T}.
     */
    protected BLFilterDocsSpans(T in, SpanGuarantees guarantees) {
        super(guarantees == null ? SpanGuarantees.from(in) : guarantees);
        this.in = Objects.requireNonNull(in);
    }

    @Override
    public int nextDoc() throws IOException {
        assert docID() != NO_MORE_DOCS;
        while (true) {
            int doc = in.nextDoc();
            if (doc == NO_MORE_DOCS) {
                return NO_MORE_DOCS;
            } else if (twoPhaseCurrentDocMatches()) {
                return doc;
            }
        }
    }

    @Override
    public int advance(int target) throws IOException {
        assert target >= 0 && target > in.docID();
        int doc = in.advance(target);
        while (doc != NO_MORE_DOCS && !twoPhaseCurrentDocMatches()) {
            doc = in.nextDoc();
        }
        return doc;
    }

    @Override
    public int docID() {
        assert in != null;
        return in.docID();
    }

    @Override
    public int width() {
        return in instanceof Spans ? ((Spans) in).width() : 0;
    }

    @Override
    public void collect(SpanCollector collector) throws IOException {
        if (in instanceof Spans)
            ((Spans) in).collect(collector);
    }

    @Override
    public final long cost() {
        return in.cost();
    }

    @Override
    public abstract String toString();

    @Override
    public TwoPhaseIterator asTwoPhaseIterator() {
        TwoPhaseIterator inner;
        if (in instanceof SpansInBuckets) {
            inner = ((SpansInBuckets) in).asTwoPhaseIterator();
        } else {
            inner = in instanceof Spans ? ((Spans) in).asTwoPhaseIterator() : null;
        }
        if (inner != null) {
            // wrapped instance has an approximation
            return new TwoPhaseIterator(inner.approximation()) {
                @Override
                public boolean matches() throws IOException {
                    return inner.matches() && twoPhaseCurrentDocMatches();
                }

                @Override
                public float matchCost() {
                    return inner.matchCost(); // underestimate
                }

                @Override
                public String toString() {
                    return "BLFilterSpans@asTwoPhaseIterator(inner=" + inner + ", in=" + in + ")";
                }
            };
        } else {
            // wrapped instance has no approximation, but
            // we can still defer matching until absolutely needed.
            return new TwoPhaseIterator(in) {
                @Override
                public boolean matches() throws IOException {
                    return twoPhaseCurrentDocMatches();
                }

                @Override
                public float matchCost() {
                    return in instanceof Spans ? ((Spans) in).positionsCost() : in.cost(); // overestimate
                }

                @Override
                public String toString() {
                    return "BLFilterSpans@asTwoPhaseIterator(in=" + in + ")";
                }
            };
        }
    }

    @Override
    public float positionsCost() {
        throw new UnsupportedOperationException(); // asTwoPhaseIterator never returns null here.
    }

    /**
     * Returns true if the current document matches.
     * <p>
     * This is called during two-phase processing.
     */
    protected abstract boolean twoPhaseCurrentDocMatches() throws IOException;

    @Override
    protected void passHitQueryContextToClauses(HitQueryContext context) {
        if (in instanceof BLSpans)
            ((BLSpans) in).setHitQueryContext(context);
        else if (in instanceof SpansInBuckets)
            ((SpansInBuckets) in).setHitQueryContext(context);
    }

    @Override
    public void getMatchInfo(MatchInfo[] matchInfo) {
        if (childClausesCaptureMatchInfo) {
            if (in instanceof BLSpans)
                ((BLSpans) in).getMatchInfo(matchInfo);
            else if (in instanceof SpansInBuckets)
                throw new IllegalStateException("Subclass must handle this case");
        }
    }

    @Override
    public boolean hasMatchInfo() {
        if (in instanceof SpansInBuckets)
            return ((SpansInBuckets) in).hasMatchInfo();
        else if (in instanceof BLSpans)
            return ((BLSpans) in).hasMatchInfo();
        else
            return false;
    }

    @Override
    public RelationInfo getRelationInfo() {
        if (childClausesCaptureMatchInfo) {
            if (in instanceof BLSpans)
                return ((BLSpans) in).getRelationInfo();
            else if (in instanceof SpansInBuckets)
                throw new IllegalStateException("Subclass must handle this case");
        }
        return null;
    }
}
