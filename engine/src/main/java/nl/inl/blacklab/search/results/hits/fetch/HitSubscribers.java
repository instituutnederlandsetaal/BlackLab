package nl.inl.blacklab.search.results.hits.fetch;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.search.results.hits.Hits;

/** A collection of HitSubscriber objects that acts as a single one. */
public class HitSubscribers implements HitSubscriber {

    /** Subscribers that want to receive hits */
    List<HitSubscriber> subscribers = new ArrayList<>();

    /** Newly added subscribers that need to catch up with the hits so far first */
    List<HitSubscriber> newSubscribers = new ArrayList<>();

    /** How to catch up a new subscriber (i.e. send them all the hits so far) */
    private final Consumer<HitSubscriber> newSubscriberCatchUp;

    /** Are we done? */
    private boolean isDone = false;

    public HitSubscribers(Consumer<HitSubscriber> newSubscriberCatchUp) {
        this.newSubscriberCatchUp = newSubscriberCatchUp;
    }

    @Override
    public void start(LeafReaderContext lrc, Hits results) {
        updateNewSubscribers();
        for (HitSubscriber s: subscribers) {
            s.start(lrc, results);
        }
    }

    public synchronized void add(HitSubscriber subscriber) {
        if (newSubscribers.contains(subscriber) || subscribers.contains(subscriber))
            throw new IllegalArgumentException("Subscriber already added");
        if (!isDone)
            newSubscribers.add(subscriber);
        else {
            // We're done; just call the catch up function immediately.
            newSubscriberCatchUp.accept(subscriber);
        }
    }

    public synchronized void hits(LeafReaderContext lrc, Hits batchHits, long batchStart, long batchEnd, int batchNumDocs,
            long batchOffsetInTotal) {
        updateNewSubscribers();
        if (batchEnd > batchStart) {
            for (HitSubscriber s: subscribers) {
                s.hits(lrc, batchHits, batchStart, batchEnd, batchNumDocs, batchOffsetInTotal);
            }
        }
    }

    @Override
    public synchronized void counted(long hitsCounted, int docsCounted) {
        updateNewSubscribers();
        for (HitSubscriber s: newSubscribers) {
            s.counted(hitsCounted, docsCounted);
        }
    }

    @Override
    public synchronized void flush(LeafReaderContext lrc, Hits segmentHits) {
        updateNewSubscribers();
        for (HitSubscriber s: subscribers) {
            s.flush(lrc, segmentHits);
        }
    }

    @Override
    public synchronized void done(LeafReaderContext lrc) {
        if (isDone)
            throw new IllegalStateException("Already done");
        updateNewSubscribers();
        for (HitSubscriber s: subscribers) {
            s.done(lrc);
        }
        isDone = true; // do this last, or we get problems if a new subscriber is in the queue
    }

    private void updateNewSubscribers() {
        if (!newSubscribers.isEmpty()) {
            // New subscribers; let them catch up first
            newSubscribers.forEach(newSubscriberCatchUp);
            subscribers.addAll(newSubscribers);
            newSubscribers.clear();
        }
    }

    @Override
    public synchronized boolean needsMoreHits() {
        updateNewSubscribers();
        for (HitSubscriber s: subscribers) {
            if (s.needsMoreHits())
                return true;
        }
        return false;
    }

    @Override
    public void error(LeafReaderContext lrc, Throwable exception) {
        updateNewSubscribers();
        for (HitSubscriber s: subscribers) {
            s.error(lrc, exception);
        }
    }
}
