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
 * Owns the externally visible output of one segment publisher.
 *
 * Publication, catch-up and terminal callbacks are serialized on this object. A batch is added to the retained hits
 * and its public counters are advanced before any subscriber sees it, so late subscribers cannot observe a prefix
 * that disagrees with the retained store. Subscriber callbacks must not subscribe to this same output reentrantly.
 *
 * Producer lifecycle synchronization deliberately lives elsewhere. In particular, {@link #needsMoreHits()} only
 * reads a volatile, never-mutated subscriber-array snapshot: it never takes the output monitor and allocates nothing.
 */
final class HitPublisherOutput {

    private static final HitSubscriber[] NO_SUBSCRIBERS = {};

    private final Hits.HitsContext context;

    private final LeafReaderContext lrc;

    /** Persistent published hits, or null if this publisher only streams batches. */
    private final HitsMutable retainedHits;

    /** Completed after terminal callbacks with null for success or the exact source failure object. */
    private final CompletableFuture<Throwable> terminal = new CompletableFuture<>();

    /** Never mutated after assignment, so demand polling needs neither locking nor allocation. */
    private volatile HitSubscriber[] subscribers = NO_SUBSCRIBERS;

    private long publishedHits;

    private int publishedDocs;

    private long countedHits;

    private int countedDocs;

    HitPublisherOutput(Hits.HitsContext context, boolean retainHits) {
        this.context = context;
        lrc = context.leafReaderContext();
        retainedHits = retainHits ? HitsMutable.create(context, -1, true, true) : null;
    }

    Hits.HitsContext context() {
        return context;
    }

    boolean retainsHits() {
        return retainedHits != null;
    }

    /** Whether a success or failure outcome has been published. */
    boolean isComplete() {
        return terminal.isDone();
    }

    /**
     * Register a subscriber after replaying the complete public prefix. A terminal subscriber is replayed but not
     * retained. The flush establishes the replayed prefix even when the producer is currently paused.
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
        if (terminal.isDone()) {
            Throwable failure = terminal.getNow(null);
            if (failure == null)
                subscriber.done(lrc);
            else
                subscriber.error(lrc, failure);
            return;
        }
        HitSubscriber[] updated = Arrays.copyOf(subscribers, subscribers.length + 1);
        updated[subscribers.length] = subscriber;
        subscribers = updated;
    }

    /** Publish a complete-document batch. */
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

    synchronized void flush() {
        ensureOpen();
        for (HitSubscriber subscriber: subscribers)
            subscriber.flush(lrc, publishedHits);
    }

    /** Mark successful completion. The producer must flush the final published prefix before calling this. */
    synchronized void complete() {
        ensureOpen();
        for (HitSubscriber subscriber: subscribers)
            subscriber.done(lrc);
        subscribers = NO_SUBSCRIBERS;
        terminal.complete(null);
    }

    synchronized void fail(Throwable failure) {
        ensureOpen();
        try {
            for (HitSubscriber subscriber: subscribers)
                subscriber.error(lrc, failure);
        } finally {
            subscribers = NO_SUBSCRIBERS;
            terminal.complete(failure);
        }
    }

    /** Pure demand poll; see the class contract. */
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
            Throwable failure = terminal.get();
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
        if (terminal.isDone())
            throw new IllegalStateException("Publisher output is already complete");
    }

}
