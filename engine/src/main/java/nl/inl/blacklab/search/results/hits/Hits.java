package nl.inl.blacklab.search.results.hits;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.LeafReaderContext;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;
import nl.inl.blacklab.search.results.hitresults.Concordances;
import nl.inl.blacklab.search.results.hitresults.ContextSize;
import nl.inl.blacklab.search.results.hitresults.HitResults;
import nl.inl.blacklab.search.results.hitresults.Kwics;
import nl.inl.blacklab.search.results.hits.fetch.HitPublisher;
import nl.inl.blacklab.search.results.hits.fetch.HitSubscriber;

/**
 * A list of simple hits.
 * <p>
 * Contrary to {@link HitResults}, this only contains doc, start and end
 * for each hit, so no captured groups information, and no other
 * bookkeeping (hit/doc retrieved/counted stats, etc.).
 * <p>
 * This is a read-only interface.
 */
public interface Hits extends Iterable<EphemeralHit> {

    Logger logger = LogManager.getLogger(Hits.class);

    /** Context for our hit: field, match info definitions and segment they came from (or null if global) */
    record HitsContext(AnnotatedField field, MatchInfoDefs matchInfoDefs, LeafReaderContext leafReaderContext) {
        public HitsContext(AnnotatedField field, MatchInfoDefs matchInfoDefs, LeafReaderContext leafReaderContext) {
            this.field = field;
            this.matchInfoDefs = matchInfoDefs != null ? matchInfoDefs : MatchInfoDefs.EMPTY;
            this.leafReaderContext = leafReaderContext;
        }

        public HitsContext(AnnotatedField field, MatchInfoDefs matchInfoDefs) {
            this(field, matchInfoDefs, null);
        }

        public HitsContext(AnnotatedField field) {
            this(field, MatchInfoDefs.EMPTY, null);
        }

        public static HitsContext fromHitQueryContext(HitQueryContext hitQueryContext,
                LeafReaderContext leafReaderContext) {
            return new HitsContext(hitQueryContext.getField(), hitQueryContext.getMatchInfoDefs(), leafReaderContext);
        }

        public BlackLabIndex index() {
            return field.index();
        }

        public HitsContext withoutLeafReaderContext() {
            return new HitsContext(field, matchInfoDefs, null);
        }
    }

    /** An empty list of hits. */
    static Hits empty(Hits.HitsContext context) {
        return new HitsListNoLock32(context, -1);
    }

    static Hits fromLists(AnnotatedField field,
            int[] docs, int[] starts, int[] ends) {
        IntList lDocs = new IntArrayList(docs);
        IntList lStarts = new IntArrayList(starts);
        IntList lEnds = new IntArrayList(ends);
        return new HitsListNoLock32(new HitsContext(field), lDocs, lStarts, lEnds, null);
    }

    static Hits single(HitsContext context, int doc, int matchStart, int matchEnd) {
        if (doc < 0 || matchStart < 0 || matchEnd < 0 || matchStart > matchEnd) {
            throw new IllegalArgumentException("Invalid hit: doc=" + doc + ", start=" + matchStart + ", end=" + matchEnd);
        }
        return new HitsSingle(context, doc, matchStart, matchEnd);
    }

    HitsContext context();

    /**
     * Type of each of our match infos.
     *
     * @return list of match info definitions
     */
    default AnnotatedField field() {
        return context().field();
    }

    default BlackLabIndex index() {
        return context().index();
    }

    /**
     * Type of each of our match infos.
     *
     * @return list of match info definitions
     */
    default MatchInfoDefs matchInfoDefs() {
        return context().matchInfoDefs();
    }

    /**
     * Get the number of hits.
     *
     * Depending on the implementation, this may lock until enough hits
     * have been fetched.
     *
     * @return number of hits
     */
    long size();

    /**
     * Check if this hits object has at least the specified number of hits.
     *
     * Depending on the implementation, this may lock until enough hits
     * have been fetched.
     *
     * @param minSize minimum number of hits required
     * @return true if there are at least min hits, false otherwise
     */
    boolean sizeAtLeast(long minSize);

    /** For lazy Hits implementations, returns the current size.
     * For non-lazy implementations, just returns size().
     */
    long sizeSoFar();

    /**
     * Check if this hits object is empty.
     *
     * Depending on the implementation, this may lock until it
     * knows whether there are any hits or not.
     *
     * @return true if there are no hits, false otherwise
     */
    boolean isEmpty();

    /**
     * Return the specified hit.
     * Implementations of this method should be thread-safe.
     *
     * @param index index of the desired hit
     * @return the hit, or null if it's beyond the last hit
     */
    PermanentHit get(long index);

    /**
     * Copy hit information into a temporary object.
     *
     * @param index index of the desired hit
     * @param hit object to copy values to
     */
    void getEphemeral(long index, EphemeralHit hit);

    /**
     * Get Lucene document id for the specified hit
     *
     * @param index hit index
     * @return document id
     */
    int doc(long index);

    /**
     * Get start position for the specified hit
     *
     * @param index hit index
     * @return document id
     */
    int start(long index);

    /**
     * Get end position for the specified hit
     *
     * @param index hit index
     * @return document id
     */
    int end(long index);

    MatchInfo[] matchInfos(long hitIndex);

    MatchInfo matchInfo(long hitIndex, int matchInfoIndex);

    /**
     * Get the most efficient interface to these Hits.
     *
     * Most efficient means that it will return a non-locking
     * object with direct access to the hits lists.
     *
     * Hits instances will typically wait until all hits are fetched
     * (if applicable), then return their internal hits object.
     *
     * HitsInternal instances will return themselves (if it's non-locking),
     * or a non-locking version of the same hits (if it's a locking instance).
     *
     * CAUTION: make sure any other threads are done modifying this object
     * before calling this method!
     *
     * @return internal hits object.
     */
    Hits getStatic();

    /**
     * Get a sublist of hits, starting at the specified index.
     *
     * If first + windowSize is larger than the number of hits,
     * the sublist returned will be smaller than windowSize.
     *
     * @param first first hit in the sublist (0-based)
     * @param windowSize size of the sublist
     * @return sublist of hits
     */
    Hits sublist(long first, long windowSize);

    /**
     * Get an iterator over the hits in this Hits object.
     * <p>
     * The iterator is not thread-safe.
     * <p>
     * It will return an EphemeralHit object for each hit, which is temporary
     * and should not be retained.
     *
     * @return iterator over the hits in this Hits object
     */
    Iterator<EphemeralHit> iterator();

    /**
     * Return an iterator over the hits in this Hits object that are in the specified segment.
     *
     * NOTE: returns the hits unchanged, so does NOT subtract docBase from the document ids!
     *
     * @param lrc the LeafReaderContext for the segment to iterate over
     * @return iterator over the segment hits
     */
    default Iterator<EphemeralHit> segmentIterator(LeafReaderContext lrc) {
        return new Iterator<>() {

            long nextHit = -1; // "not started yet"

            EphemeralHit hit = new EphemeralHit();

            {
                // Find first hit
                findNextHit();
            }

            @Override
            public boolean hasNext() {
                return nextHit < size();
            }

            @Override
            public EphemeralHit next() {
                if (nextHit >= size())
                    throw new NoSuchElementException("No more hits available");
                getEphemeral(nextHit, hit);
                findNextHit();
                return hit;
            }

            private void findNextHit() {
                nextHit++;
                while (nextHit < size()) {
                    if (doc(nextHit) >= lrc.docBase && doc(nextHit) < lrc.docBase + lrc.reader().maxDoc()) {
                        // Found a hit in this segment
                        return;
                    }
                    nextHit++;
                }
            }
        };
    }

    /**
     * Sort these hits on the specified property.
     *
     * @param sortBy the hit property to sort on
     * @return a sorted copy of these hits
     */
    Hits sorted(HitProperty sortBy);

    /**
     * Group hits by the specified property.
     * @param groupBy the hit property to group on
     * @param maxResultsToStorePerGroup maximum number of hits to store per group
     * @return grouped hits
     */
    Map<PropertyValue, Group> grouped(HitProperty groupBy, long maxResultsToStorePerGroup);

    /**
     * Count the number of distinct documents in the specified range of hits.
     *
     * @param startIndex start index, inclusive
     * @param endIndex end index, exclusive
     * @return number of distinct documents in the specified range of hits
     */
    int countDocs(long startIndex, long endIndex);

    /**
     * Count the number of distinct documents.
     * @return number of distinct documents
     */
    default int countDocs() {
        return countDocs(0, size());
    }

    /**
     * Do we have match info available?
     *
     * @return true if we have match info, false if not
     */
    boolean hasMatchInfo();

    /**
     * Create concordances from the forward index.
     *
     * @param contextSize desired context size
     * @return concordances
     */
    default Concordances concordances(ContextSize contextSize) {
        return concordances(contextSize, ConcordanceType.FORWARD_INDEX);
    }

    /**
     * Create concordances.
     *
     * @param contextSize desired context size
     * @param type concordance type: from forward index or original content
     * @return concordances
     */
    Concordances concordances(ContextSize contextSize, ConcordanceType type);

    /**
     * Create KWICs (keywords in context).
     * @param contextSize desired context size around the hits
     * @return KWICs
     */
    Kwics kwics(ContextSize contextSize);

    /**
     * Return a new hits object with only the hits in the specified document.
     * @param docId document id
     * @return new hits object with only hits in the specified document
     */
    Hits filteredByDocId(int docId);

    /** Return publishers per segment (if available).
     *
     * Operations can subscribe to the publishers to process hits
     * per segment in parallel.
     *
     * @return list of hit publishers, one per segment, or null if not available
     */
    default List<HitPublisher> publishersPerSegment() {
        return null;
    }

    /** Fetch all hits and return Hits per segment (if available).
     *
     * This is a convenience method that uses publishersPerSegment().
     *
     * @return list of Hits, one per segment, or null if not available
     */
    default List<Hits> hitsPerSegment() {
        List<HitPublisher> hitPublishers = publishersPerSegment();
        if (hitPublishers == null)
            return null;
        return hitPublishers.stream().map(HitPublisher::getStatic).toList();
    }

    /**
     * Return a publisher for these hits.
     *
     * The default implementation fetches all hits immediately,
     * then publishes all of them in one batch.
     *
     * @return hit publisher
     */
    default HitPublisher publisher() {
        return new HitPublisher() {
            @Override
            public HitsContext context() {
                return Hits.this.context();
            }

            @Override
            public Hits getStatic() {
                return Hits.this.getStatic();
            }

            @Override
            public void subscribe(HitSubscriber subscriber) {
                size(); // fetch all hits
                LeafReaderContext lrc = context().leafReaderContext();
                subscriber.start(lrc, Hits.this);
                if (size() > 0)
                    subscriber.hits(lrc, Hits.this.getStatic(), 0, size(),
                            countDocs(), 0);
                subscriber.flush(lrc, Hits.this.size());
                subscriber.done(lrc);
            }

            @Override
            public void activate() {
                // Default subscribe() implementation already fetched all hits, so nothing to do here.
            }
        };
    }

    /**
     * Perform an operation per-segment if possible, using HitSubscribers.
     * If we don't have per-segment publishers, we will just use a the "global"
     * publisher() for the whole hits object.
     *
     * @param subscriberSupplier supplier of HitSubscribers to use for each segment
     * @param prefetchAll
     */
    void performPerSegment(Supplier<HitSubscriber> subscriberSupplier, boolean prefetchAll);
}
