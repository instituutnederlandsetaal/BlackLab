package nl.inl.blacklab.search;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
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

    final int luceneDocId;

    public SingleDocIdFilter(int luceneDocId) {
        this.luceneDocId = luceneDocId;
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) {
        return new Weight(null) {

            @Override
            public Explanation explain(LeafReaderContext context, int doc) {
                return null;
            }

            /*zyw @Override
            public float getValueForNormalization() throws IOException {
                return 1.0f;
            }

            @Override
            public void normalize(float norm, float boost) {
                // NOP
            }*/

            @Override
            public Scorer scorer(final LeafReaderContext ctx) {
                return new Scorer(this) {
                    @Override
                    public int docID() {
                        return luceneDocId;
                    }

                    @Override
                    public float score() {
                        return 1.0f;
                    }

                    /*zyw @Override
                    public int freq() throws IOException {
                        return 1;
                    }*/

                    @Override
                    public DocIdSetIterator iterator() {
                        // Check that id could be in this segment, and bits allows this doc id
                        if (luceneDocId >= ctx.docBase) {

                            // Check that the id is really in this segment by looking at the next segment
                            Optional<LeafReaderContext> nextSegment = ctx.parent.leaves().stream()
                                    .filter(l -> l.docBase > ctx.docBase)
                                    .findFirst();
                            if (nextSegment.isEmpty() || nextSegment.get().docBase > luceneDocId) {
                                // Doc occurs in this segment.
                                return new SingleDocIdSet(luceneDocId - ctx.docBase).iterator();
                            }
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
                // OPT: Look in to isCacheable() and implement properly
                return false;
            }

        };
    }

    // LUCENE-3041: A query introspection API has been added. Queries should implement a visit() method, taking a QueryVisitor, and either pass the visitor down to any child queries, or call a visitX() or consumeX() method on it. All locations in the code that called Weight.extractTerms() have been changed to use this API, and the extractTerms() method has been deprecated.
    @Override
    public void visit(QueryVisitor visitor) {
        visitor.visitLeaf(this);
    }

    @Override
    public String toString(String field) {
        return "SingleDocIdFilter(" + luceneDocId + ")";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + luceneDocId;
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
        if (luceneDocId != other.luceneDocId)
            return false;
        return true;
    }
}
