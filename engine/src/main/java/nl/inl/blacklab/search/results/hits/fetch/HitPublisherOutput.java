package nl.inl.blacklab.search.results.hits.fetch;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.search.results.hits.HitsMutable;

/**
 * Holds the output that subscribers can see for one segment publisher.
 *
 * Sending hits, catching up a new subscriber, and notifying subscribers
 * that processing has finished or failed are all synchronized methods.
 * This ensures that updates happen atomically and a new subscriber sees
 * the same hits and counts as existing subscribers.
 *
 * You must never add another subscriber from one of the subscriber methods
 * (hits, counted, flush, done, error). This would be a "reentrant" call and
 * could cause deadlocks or other problems.
 *
 * The producer's start and stop coordination is handled elsewhere.
 */
final class HitPublisherOutput {

    private static final HitSubscriber[] NO_SUBSCRIBERS = {};

    private final Hits.HitsContext context;

    private final LeafReaderContext lrc;

    /** Persistent published hits. */
    private final HitsMutable retainedHits;

    /** Completed after terminal callbacks (complete, fail) with null for success or the exact source failure object. */
    private final CompletableFuture<Throwable> completionOutcome = new CompletableFuture<>();

    /**
     * Our subscribers. For performance reasons, this is a volatile reference to a read-only array
     * instead of e.g. a synchronized collection. This allows us to avoid locking when checking
     * whether any subscriber needs more hits. It also means no allocation is needed for iterating
     * over the subscribers, which is important because this is a hot path.
     */
    private volatile HitSubscriber[] subscribers = NO_SUBSCRIBERS;

    /** Number of published (processed) hits */
    private long publishedHits;

    /** Number of documents the published hits cover */
    private int publishedDocs;

    /** Number of hits that were only counted, not published (processed) */
    private long countedHits;

    /** Number of documents the counted-only hits cover */
    private int countedDocs;

    HitPublisherOutput(Hits.HitsContext context) {
        this.context = context;
        lrc = context.leafReaderContext();
        retainedHits = HitsMutable.create(context, -1, true, true);
    }

    Hits.HitsContext context() {
        return context;
    }

    boolean retainsHits() {
        return retainedHits != null;
    }

    /** Whether a success or failure outcome has been published. */
    boolean isComplete() {
        return completionOutcome.isDone();
    }

    /**
     * Register a subscriber after sending it all the published hits so far.
     * <p>
     * A subscriber that subcribes after we're already done will get all the hits, but won't be stored.
     * The flush() notifies the subscriber that it has received all the hits so far and should process them,
     * making them available to its client. It is important especially if the producer is paused: then it
     * wouldn’t publish more hits or send its next flush, so the new subscriber could otherwise wait
     * indefinitely despite already having received the replay
     */
    synchronized void subscribe(HitSubscriber subscriber) {
        for (HitSubscriber existing: subscribers) {
            if (subscriber.equals(existing))
                throw new IllegalArgumentException("Subscriber already added");
        }
        subscriber.start(lrc, retainedHits);
        if (publishedHits > 0) {
            if (retainedHits == null)
                throw new IllegalStateException("Cannot catch up late subscriber, published hits were not saved");
            subscriber.hits(lrc, retainedHits, 0, publishedHits, publishedDocs, 0);
        }
        if (countedHits > 0)
            subscriber.counted(countedHits, countedDocs);
        subscriber.flush(lrc, publishedHits);
        if (completionOutcome.isDone()) {
            // We were already completed, so notify the subscriber of that.
            // The subscriber is not stored in this case, because it won't receive any more hits.
            Throwable failure = completionOutcome.getNow(null);
            if (failure == null)
                subscriber.done(lrc);
            else
                subscriber.error(lrc, failure);
            return;
        }
        // Add the subscriber to our list of subscribers (update the volatile reference with a new array).
        HitSubscriber[] updated = Arrays.copyOf(subscribers, subscribers.length + 1);
        updated[subscribers.length] = subscriber;
        subscribers = updated;
    }

    /** Publish a batch of hits.
     * <p>
     * These always represent whole documents, so the number of documents in the batch is also provided.
     *
     * @param batch the batch of hits to publish
     * @param batchDocs the number of documents in the batch
     */
    synchronized void publish(Hits batch, int batchDocs) {
        ensureOpen();
        long batchSize = batch.size();
        if (batchSize == 0)
            return;
        long offset = publishedHits;
        if (retainedHits != null)
            retainedHits.addAll(batch);
        publishedHits += batchSize;
        publishedDocs += batchDocs;
        for (HitSubscriber subscriber: subscribers)
            subscriber.hits(lrc, batch, 0, batchSize, batchDocs, offset);
    }

    /** Publish additional hits that were counted but not retained. */
    synchronized void counted(long hits, int docs) {
        ensureOpen();
        if (hits == 0 && docs == 0)
            return;
        countedHits += hits;
        countedDocs += docs;
        for (HitSubscriber subscriber: subscribers)
            subscriber.counted(hits, docs);
    }

    /** Tell subscribers to process the hits they've received so far. */
    synchronized void flush() {
        ensureOpen();
        for (HitSubscriber subscriber: subscribers)
            subscriber.flush(lrc, publishedHits);
    }

    /** Mark successful completion.
     * The producer must publish all its hits to subscribers and call flush before calling this. */
    synchronized void complete() {
        ensureOpen();
        for (HitSubscriber subscriber: subscribers)
            subscriber.done(lrc);
        subscribers = NO_SUBSCRIBERS;
        completionOutcome.complete(null);
    }

    /**
     * Mark failed completion.
     * @param failure the exception that caused the failure
     */
    synchronized void fail(Throwable failure) {
        ensureOpen();
        try {
            for (HitSubscriber subscriber: subscribers)
                subscriber.error(lrc, failure);
        } finally {
            subscribers = NO_SUBSCRIBERS;
            completionOutcome.complete(failure);
        }
    }

    /** Do any of our subscribers require more hits at this time?
     * <p>
     * Could be called often, so performance is important.
     * It does not need synchronization because it only checks a
     * (volatile reference to a) read-only array. Using an array
     * instead of list also means no iterator needs to be allocated. */
    boolean needsMoreHits() {
        for (HitSubscriber subscriber: subscribers) {
            if (subscriber.needsMoreHits())
                return true;
        }
        return false;
    }

    Hits getStatic() {
        if (retainedHits == null)
            throw new IllegalStateException("This publisher doesn't save its hits, cannot get static view");
        try {
            Throwable failure = completionOutcome.get();
            if (failure != null)
                throw BlackLabException.wrapRuntime(failure);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedSearch(e);
        } catch (ExecutionException e) {
            throw BlackLabException.wrapRuntime(e.getCause());
        }
        return retainedHits.getStatic();
    }

    private void ensureOpen() {
        if (completionOutcome.isDone())
            throw new IllegalStateException("Publisher output is already complete");
    }

}
