package nl.inl.blacklab.search.results.hits.fetch;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.search.results.hits.EphemeralHit;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.search.results.hits.HitsMutable;

/** Publishes only those hits from another publisher that pass the filter. */
public class HitPublisherFilter implements HitPublisher {

    /** Publishes the hits we filter */
    private final HitPublisher source;

    /** Retained hits, subscribers and externally visible publication state. */
    private final HitPublisherOutput output;

    /** The current batch of hits (not yet published). NONLOCKING. */
    private final HitsMutable currentBatchOfHits;

    /** If set to true, we need to collect all hits. Ignore subscriber's ideas about pausing. */
    private final AtomicBoolean needAllHits = new AtomicBoolean(false);

    public HitPublisherFilter(HitPublisher source, HitFilter filter) {
        this.source = source;
        Hits.HitsContext context = source.context();
        output = new HitPublisherOutput(context, true);
        currentBatchOfHits = HitsMutable.create(context, -1, true, false);

        // We receive hits from our source
        source.subscribe(new HitSubscriber() {

            @Override
            public void start(LeafReaderContext lrc, Hits results) {
                // nothing here
            }

            @Override
            public boolean needsMoreHits() {
                // Do any of our subscribers need more?
                if (output.needsMoreHits())
                    return true;
                // Do we need more hits ourselves?
                return needAllHits.get();
            }

            @Override
            public void counted(long hitsCounted, int docsCounted) {
                // We can't filter these; just ignore them here.
                // To pass them on incorrectly suggests that there are this many filtered hits,
                // which is not the case.
            }

            @Override
            public void hits(LeafReaderContext lrc, Hits batchHits, long batchStart, long batchEnd, int batchNumDocs,
                    long batchOffsetInTotal) {
                EphemeralHit h = new EphemeralHit();
                HitFilter filterForBatch = filter.forSegment(batchHits, batchHits.context().leafReaderContext(), null);
                currentBatchOfHits.clear();
                int prevDocId = -1; // this method is always called with whole documents, so this works
                int numberOfDocsBatch = 0;
                for (long i = batchStart; i < batchEnd; i++) {
                    if (filterForBatch.accept(i)) {
                        batchHits.getEphemeral(i, h);
                        currentBatchOfHits.add(h);
                        if (h.doc_ != prevDocId) {
                            prevDocId = h.doc_;
                            numberOfDocsBatch++;
                        }
                    }
                }
                if (!currentBatchOfHits.isEmpty()) {
                    // We added hits; let our subscribers know
                    output.publish(currentBatchOfHits, numberOfDocsBatch);
                }
            }

            @Override
            public void flush(LeafReaderContext lrc, long numPublished) {
                output.flush();
            }

            @Override
            public void done(LeafReaderContext lrc) {
                output.complete();
            }

            @Override
            public void error(LeafReaderContext lrc, Throwable exception) {
                output.fail(exception);
            }
        });
    }

    public LeafReaderContext getLrc() {
        return source.context().leafReaderContext();
    }

    @Override
    public void activate() {
        source.activate();
    }

    @Override
    public Hits.HitsContext context() {
        return output.context();
    }

    @Override
    public Hits getStatic() {
        // Indicate that we need all hits and start fetch thread if needed
        needAllHits.set(true);
        source.activate();

        return output.getStatic();
    }

    @Override
    public void subscribe(HitSubscriber subscriber) {
        output.subscribe(subscriber);
        activate();
    }

}
