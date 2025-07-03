package org.ivdnt.blacklab.solr;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

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
import org.apache.solr.search.DocIterator;
import org.apache.solr.search.DocSet;

import it.unimi.dsi.fastutil.ints.IntBigArrayBigList;
import it.unimi.dsi.fastutil.ints.IntBigArrays;
import it.unimi.dsi.fastutil.ints.IntBigListIterator;
import nl.inl.blacklab.exceptions.BlackLabException;

/**
 * A Filter that matches Lucene documents from a set of global doc ids.
 * <p>
 * Used to filter BlackLab's span results using Solr's doc result set.
 */
public class DocSetFilter extends Query {

    private final IntBigArrayBigList acceptedDocs;

    /**
     * Construct a DocSetFilter from an sorted list of global doc ids.
     *
     * @param acceptedDocs sorted list of global doc ids
     * @param excludeDocId exclude this docId from the results (metadata document id)
     */
    public DocSetFilter(DocSet acceptedDocs, int excludeDocId) {
        // Make a copy of docs so we can find the starting point for each per segment
        this.acceptedDocs = new IntBigArrayBigList();
        if (acceptedDocs != null) {
            DocIterator it = acceptedDocs.iterator();
            while (it.hasNext()) {
                int docId = it.nextDoc();
                if (docId != excludeDocId)
                    this.acceptedDocs.add(docId);
            }
        }
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
                    int current = -1;

                    @Override
                    public int docID() {
                        return current;
                    }

                    @Override
                    public float score() {
                        return 1.0f;
                    }

                    @Override
                    public DocIdSetIterator iterator() {
                        // Find docBase (or the first higher id) in accepted docs list
                        long start = IntBigArrays.binarySearch(acceptedDocs.elements(), 0, acceptedDocs.size64(), ctx.docBase);
                        if (start < 0) {
                            // Value not found; determine "insertion point" (index of first higher id) instead
                            start = -start - 1;
                        }

                        // Find the highest accepted doc in this segment by looking at the next segment
                        Optional<LeafReaderContext> nextSegment = ctx.parent.leaves().stream()
                                .filter(l -> l.docBase > ctx.docBase)
                                .findFirst();
                        long end;
                        if (nextSegment.isPresent()) {
                            // Find index of first doc in next segment
                            int startOfNextSegment = nextSegment.get().docBase;
                            end = IntBigArrays.binarySearch(acceptedDocs.elements(), start, acceptedDocs.size64(), startOfNextSegment);
                            if (end < 0) {
                                // Value not found; determine "insertion point" (index of first higher id) instead
                                end = -end - 1;
                            }
                        } else {
                            // This is the last segment
                            end = acceptedDocs.size64();
                        }

                        // Create iterator for all accepted docs in this segment
                        IntBigListIterator acceptedDocsInLeaf = acceptedDocs.subList(start, end).iterator();
                        if (acceptedDocsInLeaf.hasNext()) {
                            return new DocIdSetIterator() {
                                @Override
                                public int docID() {
                                    return current;
                                }

                                @Override
                                public int nextDoc() {
                                    if (acceptedDocsInLeaf.hasNext())
                                        current = acceptedDocsInLeaf.nextInt() - ctx.docBase;
                                    else
                                        current = NO_MORE_DOCS;
                                    return current;
                                }

                                @Override
                                public int advance(int target) {
                                    // Advance to the first beyond the current whose document number is greater than
                                    // or equal to target
                                    while (nextDoc() != NO_MORE_DOCS && current < target);
                                    return current;
                                }

                                @Override
                                public long cost() {
                                    return 0;
                                }
                            };
                        }
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
				return false;
			}

        };
    }

    @Override
    public void visit(QueryVisitor queryVisitor) {
        queryVisitor.visitLeaf(this);
    }

    @Override
    public String toString(String field) {
        return "DocSetFilter(" + acceptedDocs.size64() + " docs)";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        DocSetFilter that = (DocSetFilter) o;
        return acceptedDocs.equals(that.acceptedDocs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(acceptedDocs);
    }
}
