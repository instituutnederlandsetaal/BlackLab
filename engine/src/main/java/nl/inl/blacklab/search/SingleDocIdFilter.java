package nl.inl.blacklab.search;

import java.io.IOException;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;

import nl.inl.blacklab.exceptions.BlackLabException;

/**
 * A Filter that only matches a single Lucene document id.
 * <p>
 * Used for finding hits in a single document (for highlighting).
 */
public class SingleDocIdFilter extends Query {

    final int globalLuceneDocId;

    public SingleDocIdFilter(int luceneDocId) {
        this.globalLuceneDocId = luceneDocId;
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) {
        return new Weight(this) {

            @Override
            public Explanation explain(LeafReaderContext context, int doc) {
                return null;
            }

            @Override
            public Scorer scorer(final LeafReaderContext ctx) {
                return new Scorer(this) {
                    private final int segmentLuceneDocId = globalLuceneDocId - ctx.docBase;

                    @Override
                    public int docID() {
                        return segmentLuceneDocId;
                    }

                    @Override
                    public float score() {
                        return 1.0f;
                    }

                    @Override
                    public DocIdSetIterator iterator() {
                        // Check that id could be in this segment, and bits allows this doc id
                        // Compare the segment-local document id with the segment size.
                        if (segmentLuceneDocId >= 0 && segmentLuceneDocId < ctx.reader().maxDoc()) {
                            // Doc occurs in this segment.
                            return new SingleDocIdSet(segmentLuceneDocId).iterator();
                        }
                        // We're in the wrong segment. Return empty set.
                        try {
                            return DocIdSet.EMPTY.iterator();
                        } catch (IOException e) {
                            throw BlackLabException.wrapRuntime(e);
                        }
                    }

                    @Override
                    public float getMaxScore(int upTo) {
                        return 0;
                    }
                };
            }

            @Override
            public boolean isCacheable(LeafReaderContext ctx) {
                return true;
            }

        };
    }

    @Override
    public void visit(QueryVisitor visitor) {
        visitor.visitLeaf(this);
    }

    @Override
    public String toString(String field) {
        return "SingleDocIdFilter(" + globalLuceneDocId + ")";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + globalLuceneDocId;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        SingleDocIdFilter other = (SingleDocIdFilter) obj;
        if (globalLuceneDocId != other.globalLuceneDocId)
            return false;
        return true;
    }
}
