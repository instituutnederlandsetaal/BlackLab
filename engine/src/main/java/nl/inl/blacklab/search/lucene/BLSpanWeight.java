package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Map;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanWeight;

/**
 * Base class for our SpanWeight classes. Ensures that getSpans returns a
 * BLSpans.
 */
public abstract class BLSpanWeight extends SpanWeight {

    protected BLSpanWeight(SpanQuery query, IndexSearcher searcher, Map<Term, TermStates> termStates, float boost)
            throws IOException {
        super(query, searcher, termStates,boost);
    }

    @Override
    public abstract BLSpans getSpans(LeafReaderContext ctx, Postings requiredPostings) throws IOException;
    
//	@Override
//	public boolean isCacheable(LeafReaderContext ctx) {
//		// OPT look into SpanWeight.isCacheable() and implement properly
//		return false;
//	}

}
