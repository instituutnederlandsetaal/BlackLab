package nl.inl.blacklab.search.results.hits.fetch;

import org.apache.lucene.index.LeafReaderContext;

public interface HitFetcherSegment extends Runnable {

    void initialize();

    @Override
    void run();

//    boolean ensureResultsRead(long number);

    LeafReaderContext getLeafReaderContext();

    boolean isDone();
}
