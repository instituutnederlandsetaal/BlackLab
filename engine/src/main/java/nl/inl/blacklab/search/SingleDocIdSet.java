package nl.inl.blacklab.search;

import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.DocIdSetIterator;

/**
 * A DocIdSet with a single Lucene document id in it.
 *
 * Used by SingleDocIdFilter (or directly by BlackLabIndex.createSpanQuery) for
 * finding hits in a single document (for highlighting).
 */
public class SingleDocIdSet extends DocIdSet {
    final int id;

    public SingleDocIdSet(int id) {
        this.id = id;
    }

    @Override
    public DocIdSetIterator iterator() {
        return new DocIdSetIterator() {
            private boolean nexted = false;

            private boolean done = false;

            @Override
            public int nextDoc() {
                if (nexted)
                    done = true;
                if (done)
                    return NO_MORE_DOCS;
                nexted = true;
                return id;
            }

            @Override
            public int docID() {
                if (!nexted)
                    return -1;
                if (done)
                    return NO_MORE_DOCS;
                return id;
            }

            @Override
            public int advance(int target) {
                if (nexted)
                    done = true;
                if (done || target > id)
                    return NO_MORE_DOCS;
                nexted = true;
                return id;
            }

            @Override
            public long cost() {
                return 0; // (we don't use this)
            }

            @Override
            public String toString() {
                return "SingleDocIdSetIt(" + id + ")";
            }
        };
    }

    @Override
    public long ramBytesUsed() {
        return Integer.BYTES;
    }

    @Override
    public String toString() {
        return "SingleDocIdSet(" + id + ")";
    }
}
