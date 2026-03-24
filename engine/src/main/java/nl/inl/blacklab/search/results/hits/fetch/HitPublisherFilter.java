package nl.inl.blacklab.search.results.hits.fetch;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.search.results.hits.EphemeralHit;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.search.results.hits.HitsMutable;

/** Publishes only those hits from another publisher that pass the filter. */
public class HitPublisherFilter implements HitPublisher {

    /** Publishes the hits we filter */
    private final HitPublisher source;

    /** Filtered hits already published. LOCKING. */
    private final HitsMutable alreadyPublishedHits;

    /** How many docs we've counted in the hits published so far */
    private int numberOfDocs = 0;

    /** The current batch of hits (not yet published). NONLOCKING. */
    private final HitsMutable currentBatchOfHits;

    /** Have all hits been filtered? */
    private final AtomicBoolean isDone = new AtomicBoolean(false);

    /** Our subscribers, that we will publish our hits to. */
    private final HitSubscribers subscribers;

    /** Will count down when all hits have been found. Used by getStatic(). */
    private final CountDownLatch allHitsFound = new CountDownLatch(1);

    /** If set to true, we need to collect all hits. Ignore subscriber's ideas about pausing. */
    private final AtomicBoolean needAllHits = new AtomicBoolean(false);

    public HitPublisherFilter(HitPublisher source, HitFilter filter) {
        this.source = source;
        alreadyPublishedHits = HitsMutable.create(source.context(), -1, true, true);
        currentBatchOfHits = HitsMutable.create(source.context(), -1, true, false);

        // Keep track of our subscribers
        subscribers = new HitSubscribers(hs -> {
            // Send all hits so far to the new subscriber
            LeafReaderContext lrc = source.context().leafReaderContext();
            hs.start(lrc, alreadyPublishedHits);
            if (!alreadyPublishedHits.isEmpty()) {
                // This method is called when the batch has already been added to alreadyPublishedHits,
                // but not yet reported to subscribers. Take this into account.
                long howManyActuallyPublished = alreadyPublishedHits.size() - currentBatchOfHits.size();
                hs.hits(lrc, alreadyPublishedHits, 0, howManyActuallyPublished, numberOfDocs, 0);
            }
            if (isDone.get()) {
                hs.flush(lrc, alreadyPublishedHits);
                hs.done(lrc);
            }
        });

        // We receive hits from our source
        source.subscribe(new HitSubscriber() {

            @Override
            public void start(LeafReaderContext lrc, Hits results) {
                // nothing here
            }

            @Override
            public boolean needsMoreHits() {
                // Do any of our subscribers need more?
                if (subscribers.needsMoreHits())
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
                long filteredStart = alreadyPublishedHits.size();
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
                    subscribers.hits(lrc, currentBatchOfHits, 0, currentBatchOfHits.size(), numberOfDocsBatch, filteredStart);
                    alreadyPublishedHits.addAll(currentBatchOfHits);
                }
                HitPublisherFilter.this.numberOfDocs += numberOfDocsBatch;
            }

            @Override
            public void flush(LeafReaderContext lrc, Hits results) {
                subscribers.flush(lrc, results);
            }

            @Override
            public void done(LeafReaderContext lrc) {
                subscribers.done(lrc);
                isDone.set(true); // do this last, or we get problems if a new subscriber is in the queue
                allHitsFound.countDown();
            }

            @Override
            public void error(LeafReaderContext lrc, Throwable exception) {
                subscribers.error(lrc, exception);
            }
        });
    }

    public LeafReaderContext getLrc() {
        return source.context().leafReaderContext();
    }

    @Override
    public void subscribe(HitSubscriber subscriber) {
        subscribers.add(subscriber);
        activate();
    }

    @Override
    public void activate() {
        source.activate();
    }

    @Override
    public Hits.HitsContext context() {
        return alreadyPublishedHits.context();
    }

    @Override
    public Hits getStatic() {
        // Indicate that we need all hits and start fetch thread if needed
        needAllHits.set(true);
        source.activate(); // make sure the fetch thread is running

        // Wait for all hits to be fetched
        try {
            allHitsFound.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // preserve interrupted status
            throw new InterruptedSearch(e);
        }
        return alreadyPublishedHits.getStatic();
    }
}
