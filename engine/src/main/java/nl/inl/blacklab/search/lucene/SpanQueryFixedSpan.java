package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.spans.SpanCollector;
import org.apache.lucene.util.Bits;

import nl.inl.blacklab.search.BlackLabIndexAbstract;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * A query matching a fixed, predetermined span for every matching document.
 *
 * If a document is too short to contain the span, it will be skipped.
 *
 * This operation is used for the "document snippet" operation, so we
 * can get the entire sentence around the span, and can fetch relations
 * within the sentence. There is currently no CQL syntax for this operation.
 */
public class SpanQueryFixedSpan extends BLSpanQuery {

    public SpanGuarantees createGuarantees() {
        return new SpanGuaranteesAdapter(SpanGuarantees.TERM) {
            @Override
            public int hitsLengthMin() {
                return length();
            }
            @Override
            public int hitsLengthMax() {
                return length();
            }
        };
    }

    private final String luceneField;

    private final int start;

    private final int end;

    public SpanQueryFixedSpan(QueryInfo queryInfo, String luceneField, int start, int end) {
        super(queryInfo);
        this.luceneField = luceneField;
        this.start = start;
        this.end = end;
        this.guarantees = createGuarantees();
    }

    int length() {
        return end - start;
    }

    @Override
    public void visit(QueryVisitor visitor) {
        if (visitor.acceptField(getField())) {
            visitor.visitLeaf(this);
        }
    }

    @Override
    public BLSpanWeight createWeight(final IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        return new BLSpanWeight(this, searcher, null, boost) {
            @Override
            public void extractTerms(Set<Term> terms) {
                // No terms
            }

            @Override
            public boolean isCacheable(LeafReaderContext ctx) {
                return true;
            }

            @Override
            public void extractTermStates(Map<Term, TermStates> contexts) {
                // No terms
            }

            @Override
            public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) {
                LeafReader reader = context.reader();
                Bits liveDocs = reader.getLiveDocs();
                int maxDoc = reader.maxDoc();
                DocFieldLengthGetter lengthGetter = new DocFieldLengthGetter(reader, getField());
                SpanGuarantees g = new SpanGuaranteesAdapter(SpanGuarantees.TERM) {
                    @Override
                    public int hitsLengthMin() {
                        return length();
                    }

                    @Override
                    public int hitsLengthMax() {
                        return length();
                    }
                };
                return new BLSpans(g) {

                    boolean atFirstInCurrentDoc = false;

                    boolean docExhausted = false;

                    int currentDoc = -1;

                    @Override
                    public int nextDoc() throws IOException {
                        assert docID() != NO_MORE_DOCS;
                        atFirstInCurrentDoc = false;
                        if (currentDoc >= maxDoc) {
                            currentDoc = NO_MORE_DOCS;
                            return NO_MORE_DOCS;
                        }
                        // Go to next nondeleted doc
                        boolean skipThisDoc;
                        do {
                            currentDoc++;
                            if (currentDoc >= maxDoc)
                                break;
                            boolean currentDocIsDeletedDoc = liveDocs != null && !liveDocs.get(currentDoc);
                            int docLength = lengthGetter.getFieldLength(currentDoc)
                                    - BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN;
                            boolean currentDocIsTooShort = docLength < end;
                            skipThisDoc = currentDocIsDeletedDoc || currentDocIsTooShort;
                        } while (currentDoc < maxDoc && skipThisDoc);
                        if (currentDoc > maxDoc)
                            throw new RuntimeException("currentDoc > maxDoc!!");
                        if (currentDoc == maxDoc) {
                            currentDoc = NO_MORE_DOCS;
                            return NO_MORE_DOCS; // no more docs; we're done
                        }
                        atFirstInCurrentDoc = true;
                        docExhausted = false;
                        return currentDoc;
                    }

                    @Override
                    public int advance(int doc) throws IOException {
                        assert doc >= 0 && doc > docID();
                        assert currentDoc < doc;
                        atFirstInCurrentDoc = false;
                        if (currentDoc == NO_MORE_DOCS)
                            return NO_MORE_DOCS;
                        if (doc >= maxDoc) {
                            currentDoc = NO_MORE_DOCS;
                            return NO_MORE_DOCS;
                        }

                        // Advance to first livedoc containing matches at or after requested docID
                        currentDoc = doc - 1;
                        nextDoc();
                        return currentDoc;
                    }

                    @Override
                    public int nextStartPosition() throws IOException {
                        assert positionedInDoc();
                        assert !docExhausted;
                        if (atFirstInCurrentDoc) {
                            atFirstInCurrentDoc = false;
                            return startPosition();
                        }
                        docExhausted = true;
                        return NO_MORE_POSITIONS;
                    }

                    @Override
                    protected void passHitQueryContextToClauses(HitQueryContext context) {
                        // (no clauses)
                    }

                    @Override
                    public void getMatchInfo(MatchInfo[] matchInfo) {
                        // (no match info)
                    }

                    @Override
                    public boolean hasMatchInfo() {
                        return false;
                    }

                    @Override
                    public RelationInfo getRelationInfo() {
                        return null; // no relation info
                    }

                    @Override
                    public int startPosition() {
                        return atFirstInCurrentDoc ? -1 : (docExhausted ? NO_MORE_POSITIONS : start);
                    }

                    @Override
                    public int endPosition() {
                        return atFirstInCurrentDoc ? -1 : (docExhausted ? NO_MORE_POSITIONS : end);
                    }

                    @Override
                    public int width() {
                        return 0;
                    }

                    @Override
                    public void collect(SpanCollector collector) {
                        // (nothing to collect)
                    }

                    @Override
                    public float positionsCost() {
                        return 0;
                    }

                    @Override
                    public int docID() {
                        return currentDoc;
                    }

                    @Override
                    public String toString() {
                        return "FIXEDSPAN(" + start + ", " + end + ")";
                    }
                }; // no hits
            }
        };
    }

    @Override
    public String toString(String field) {
        return "SpanQueryFixedSpan{" +
                "luceneField='" + luceneField + '\'' +
                ", start=" + start +
                ", end=" + end +
                '}';
    }

    @Override
    public String getRealField() {
        return luceneField;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        SpanQueryFixedSpan that = (SpanQueryFixedSpan) o;
        return start == that.start && end == that.end && Objects.equals(luceneField, that.luceneField);
    }

    @Override
    public int hashCode() {
        return Objects.hash(luceneField, start, end);
    }

    @Override
    public BLSpanQuery inverted() {
        return new SpanQueryAnyToken(queryInfo, 1, 1, luceneField);
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        return 0; // no hits, no cost
    }

    @Override
    public int forwardMatchingCost() {
        return 0; // no hits, no cost
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) {
        return this;
    }
}
