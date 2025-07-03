package nl.inl.blacklab.search.results;

import java.util.Iterator;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.Constants;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyDocumentId;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;

/**
 * A collection of matches being fetched as they are needed.
 *
 * Should be thread-safe and most methods are safe w.r.t. hits having been fetched.
 */
public abstract class HitsAbstract extends ResultsAbstract<Hit, HitProperty> implements Hits {

    protected static final Logger logger = LogManager.getLogger(HitsAbstract.class);

    /**
     * Minimum number of hits to fetch in an ensureHitsRead() block.
     *
     * This prevents locking again and again for a single hit when iterating.
     *
     * See {@link HitsFromQuery} and {@link HitsFiltered}.
     */
    protected static final int FETCH_HITS_MIN = 20;

    /** Our internal list of simple hits. */
    protected final HitsInternal hitsInternal;

    /**
     * Our match info (e.g. captured groups, relations), or null if we have none.
     */
    protected MatchInfoDefs matchInfoDefs = null;

    /**
     * The number of hits we've seen and counted so far. May be more than the number
     * of hits we've retrieved if that exceeds maxHitsToRetrieve.
     */
    protected long hitsCounted = 0;

    /**
     * The number of separate documents we've seen in the hits retrieved.
     */
    protected long docsRetrieved = 0;

    /**
     * The number of separate documents we've counted so far (includes non-retrieved
     * hits).
     */
    protected long docsCounted = 0;

    private final ResultsStats docsStats = new ResultsStats() {

        @Override
        public boolean processedAtLeast(long lowerBound) {
            while (!doneProcessingAndCounting() && docsProcessedSoFar() < lowerBound) {
                ensureResultsRead(hitsInternal.size() + FETCH_HITS_MIN);
            }
            return docsProcessedSoFar() >= lowerBound;
        }

        @Override
        public long processedTotal() {
            return docsProcessedTotal();
        }

        @Override
        public long processedSoFar() {
            return docsProcessedSoFar();
        }

        @Override
        public long countedSoFar() {
            return docsCountedSoFar();
        }

        @Override
        public long countedTotal() {
            return docsCountedTotal();
        }

        @Override
        public boolean done() {
            return doneProcessingAndCounting();
        }

        @Override
        public MaxStats maxStats() {
            return HitsAbstract.this.maxStats();
        }

        @Override
        public String toString() {
            return "ResultsStats(" + HitsAbstract.this + ")";
        }

    };

    /** Construct an empty Hits object.
     *
     * @param queryInfo query info for corresponding query
     * @param readOnly if true, returns an immutable Hits object; otherwise, a mutable one
     */
    protected HitsAbstract(QueryInfo queryInfo, boolean readOnly) {
        this(queryInfo, readOnly ? HitsInternal.EMPTY_SINGLETON : HitsInternal.create(-1, true, true), null);
    }

    /**
     * Construct a Hits object from a hits array.
     *
     * NOTE: if you pass null, a new, mutable HitsArray is used. For an immutable empty Hits object, use
     * {@link #HitsAbstract(QueryInfo, boolean)}.
     *
     * @param queryInfo query info for corresponding query
     * @param hits hits array to use for this object. The array is used as-is, not copied.
     */
    protected HitsAbstract(QueryInfo queryInfo, HitsInternal hits, MatchInfoDefs matchInfoDefs) {
        super(queryInfo);
        this.hitsInternal = hits == null ? HitsInternal.create(-1, true, true) : hits;
        this.matchInfoDefs = matchInfoDefs;
    }

    // Inherited from Results
    //--------------------------------------------------------------------

    /**
     * Get a window into this list of hits.
     *
     * Use this if you're displaying part of the result set, like in a paging
     * interface. It makes sure BlackLab only works with the hits you want to
     * display and doesn't do any unnecessary processing on the other hits.
     *
     * HitsWindow includes methods to assist with paging, like figuring out if there
     * hits before or after the window.
     *
     * @param first first hit in the window (0-based)
     * @param windowSize size of the window
     * @return the window
     */
    @Override
    public Hits window(long first, long windowSize) {
        // Error if first out of range
        boolean emptyResultSet = !hitsProcessedAtLeast(1);
        if (first < 0 || (emptyResultSet && first > 0) ||
            (!emptyResultSet && !hitsProcessedAtLeast(first + 1))) {
            //throw new IllegalArgumentException("First hit out of range");
            return Hits.empty(queryInfo());
        }

        // Auto-clamp number
        // take care not to always call size(), as that blocks until we're done!
        // Instead, first call ensureResultsRead so we block until we have either have enough or finish
        this.ensureResultsRead(first + windowSize);
        // and only THEN do this, since now we know if we don't have this many hits, we're done, and it's safe to call size
        boolean enoughHitsForFullWindow = hitsProcessedAtLeast(first + windowSize);
        long number;
        if (enoughHitsForFullWindow)
            number = windowSize;
        else {
            number = size() - first;
            assert number < windowSize;
        }

        // Copy the hits we're interested in.
        MutableInt docsRetrieved = new MutableInt(0); // Bypass warning (enclosing scope must be effectively final)
        HitsInternalMutable window = HitsInternal.create(number, number, false);

        this.hitsInternal.withReadLock(h -> {
            int prevDoc = -1;
            EphemeralHit hit = new EphemeralHit();
            for (long i = first; i < first + number; i++) {
                h.getEphemeral(i, hit);
                // OPT: copy context as well..?

                int doc = hit.doc;
                if (doc != prevDoc) {
                    docsRetrieved.add(1);
                    prevDoc = doc;
                }
                window.add(hit);
            }
        });
        boolean hasNext = hitsProcessedAtLeast(first + windowSize + 1);
        WindowStats windowStats = new WindowStats(hasNext, first, windowSize, number);
        return Hits.list(queryInfo(), window, windowStats, null,
                hitsCounted, docsRetrieved.getValue(), docsRetrieved.getValue(),
                matchInfoDefs(), hasAscendingLuceneDocIds());
    }

    /**
     * Take a sample of hits by wrapping an existing Hits object.
     *
     * @param sampleParameters sample parameters
     * @return the sample
     */
    @Override
    public Hits sample(SampleParameters sampleParameters) {

        // Determine total number of hits (fetching all of them)
        long totalNumberOfHits = size();
        if (totalNumberOfHits > Constants.JAVA_MAX_ARRAY_SIZE) {
            // TODO: we might want to enable this, because the whole point of sampling is to make sense
            //       of huge result sets without having to look at every hit.
            //       Ideally, old seeds would keep working as well (although that may not be practical,
            //       and not likely to be a huge issue)
            throw new RuntimeException("Cannot sample from more than " + Constants.JAVA_MAX_ARRAY_SIZE + " hits");
        }

        // We can later provide an optimized version that uses a HitsSampleCopy or some such
        // (this class could save memory by only storing the hits we're interested in)
        Set<Long> chosenHitIndices = new TreeSet<>(); // we need indexes sorted (see below)
        long numberOfHitsToSelect = sampleParameters.numberOfHits(totalNumberOfHits);
        if (numberOfHitsToSelect >= size()) {
            numberOfHitsToSelect = size(); // default to all hits in this case
            for (long i = 0; i < numberOfHitsToSelect; ++i) {
                chosenHitIndices.add(i);
            }
        } else {
            // Choose the hits
            Random random = new Random(sampleParameters.seed());
            for (int i = 0; i < numberOfHitsToSelect; i++) {
                // Choose a hit we haven't chosen yet
                long hitIndex;
                do {
                    hitIndex = random.nextInt((int)Math.min(Constants.JAVA_MAX_ARRAY_SIZE, size()));
                } while (chosenHitIndices.contains(hitIndex));
                chosenHitIndices.add(hitIndex);
            }
        }

        MutableInt docsInSample = new MutableInt(0);
        HitsInternalMutable sample = HitsInternal.create(numberOfHitsToSelect, numberOfHitsToSelect, false);

        this.hitsInternal.withReadLock(hr -> {
            int previousDoc = -1;
            EphemeralHit hit = new EphemeralHit();
            for (Long hitIndex : chosenHitIndices) {
                hr.getEphemeral(hitIndex, hit);
                if (hit.doc != previousDoc) { // this works because indexes are sorted (TreeSet)
                    docsInSample.add(1);
                    previousDoc = hit.doc;
                }

                sample.add(hit);
            }
        });

        return Hits.list(queryInfo(), sample, null, sampleParameters, sample.size(),
                docsInSample.getValue(), docsInSample.getValue(), matchInfoDefs(),
                hasAscendingLuceneDocIds());
    }

    /**
     * Return a new Hits object with these hits sorted by the given property.
     *
     * This keeps the existing sort (or lack of one) intact and allows you to cache
     * different sorts of the same result set.
     *
     * @param sortProp the hit property to sort on
     * @return a new Hits object with the same hits, sorted in the specified way
     */
    @Override
    public Hits sort(HitProperty sortProp) {
        // We need a HitProperty with the correct Hits object
        // If we need context, make sure we have it.
        sortProp = sortProp.copyWith(this);

        // Perform the actual sort.
        this.ensureAllResultsRead();
        HitsInternal sorted = this.hitsInternal.sort(sortProp); // TODO use wrapper objects
        sortProp.disposeContext(); // we don't need the context information anymore, free memory

        long hitsCounted = hitsCountedSoFar();
        long docsRetrieved = docsProcessedSoFar();
        long docsCounted = docsCountedSoFar();
        boolean ascendingLuceneDocIds = sortProp instanceof HitPropertyDocumentId && !sortProp.isReverse();
        return Hits.list(queryInfo(), sorted, null, null,
                hitsCounted, docsRetrieved, docsCounted, matchInfoDefs(), ascendingLuceneDocIds);
    }

    /**
     * Return a Hits object with these hits in ascending Lucene doc id order.
     *
     * Necessary for operations that make use of DocValues, which use sequential access.
     *
     * If already in ascending order, returns itself.
     *
     * @return hits in ascending Lucene doc id order
     */
    @Override
    public Hits withAscendingLuceneDocIds() {
        return hasAscendingLuceneDocIds() ? this : sort(new HitPropertyDocumentId());
    }

    @Override
    public HitGroups group(HitProperty criteria, long maxResultsToStorePerGroup) {
        ensureAllResultsRead();
        return HitGroups.fromHits(this, criteria, maxResultsToStorePerGroup);
    }

    /**
     * Select only the hits where the specified property has the specified value.
     *
     * @param property property to select on, e.g. "word left of hit"
     * @param value value to select on, e.g. 'the'
     * @return filtered hits
     */
    @Override
    public Hits filter(HitProperty property, PropertyValue value) {
        return new HitsFiltered(this, property, value);
    }

    @Override
    protected long resultsCountedTotal() {
        return hitsCountedTotal();
    }

    @Override
    protected long resultsCountedSoFar() {
        return hitsCountedSoFar();
    }

    @Override
    protected boolean resultsProcessedAtLeast(long lowerBound) {
        return hitsProcessedAtLeast(lowerBound);
    }

    @Override
    protected long resultsProcessedTotal() {
        return hitsProcessedTotal();
    }

    @Override
    protected long resultsProcessedSoFar() {
        return hitsProcessedSoFar();
    }

    @Override
    public long numberOfResultObjects() {
        return this.hitsInternal.size();
    }

    @Override
    public Iterator<Hit> iterator() {
        // We need to wrap the internal iterator, as we probably shouldn't
        return new Iterator<>() {
            final Iterator<EphemeralHit> i = ephemeralIterator();

            @Override
            public boolean hasNext() {
                return i.hasNext();
            }

            @Override
            public Hit next() {
                return i.next().toHit();
            }
        };
    }

    @Override
    public Iterator<EphemeralHit> ephemeralIterator() {
        ensureAllResultsRead();
        return hitsInternal.iterator();
    }

    @Override
    public Hit get(long i) {
        ensureResultsRead(i + 1);
        return this.hitsInternal.get(i);
    }

    @Override
    public void getEphemeral(long i, EphemeralHit hit) {
        ensureResultsRead(i + 1);
        this.hitsInternal.getEphemeral(i, hit);
    }

    /**
     * Count occurrences of context words around hit.
     *
     * @param annotation what annotation to get collocations for
     * @param contextSize how many words around the hits to use
     * @param sensitivity what sensitivity to use
     * @param sort sort the resulting collocations by descending frequency?
     *
     * @return the frequency of each occurring token
     */
    @Override
    public TermFrequencyList collocations(Annotation annotation, ContextSize contextSize, MatchSensitivity sensitivity, boolean sort) {
        // TODO: implement these using HitProperty objects that return HitPropertyValueContextWord(s);
        //       more versatile and we can eliminate Contexts?
        return Contexts.collocations(this, annotation, contextSize, sensitivity, sort);
    }

    /**
     * Count occurrences of context words around hit.
     *
     * Sorts the results from most to least frequent.
     *
     * @param annotation what annotation to get collocations for
     * @param contextSize how many words around the hits to use
     * @param sensitivity what sensitivity to use
     * @return the frequency of each occurring token
     */
    @Override
    public TermFrequencyList collocations(Annotation annotation, ContextSize contextSize, MatchSensitivity sensitivity) {
        return collocations(annotation, contextSize, sensitivity, true);
    }

    /**
     * Count occurrences of context words around hit.
     *
     * Matches case- and diacritics-sensitively, and sorts the results from most to least frequent.
     *
     * @param annotation what annotation to get collocations for
     * @param contextSize how many words around the hits to use
     *
     * @return the frequency of each occurring token
     */
    @Override
    public TermFrequencyList collocations(Annotation annotation, ContextSize contextSize) {
        return collocations(annotation, contextSize, MatchSensitivity.SENSITIVE, true);
    }

    /**
     * Return a per-document view of these hits.
     *
     * @param maxHits maximum number of hits to store per document
     *
     * @return the per-document view.
     */
    @Override
    public DocResults perDocResults(long maxHits) {
        return DocResults.fromHits(queryInfo(), this, maxHits);
    }

    /**
     * Create concordances from the forward index.
     *
     * @param contextSize desired context size
     * @return concordances
     */
    @Override
    public Concordances concordances(ContextSize contextSize) {
        return concordances(contextSize, ConcordanceType.FORWARD_INDEX);
    }

    // Getting / iterating over the hits
    //--------------------------------------------------------------------

    @Override
    public Hits getHitsInDoc(int docId) {
        ensureAllResultsRead();
        HitsInternalMutable hitsInDoc = HitsInternal.create(-1, size(), false);
        // all hits read, no lock needed.
        for (EphemeralHit h : this.hitsInternal) {
            if (h.doc == docId)
                hitsInDoc.add(h);
        }
        return new HitsList(queryInfo(), hitsInDoc, matchInfoDefs);
    }

    // Stats
    // ---------------------------------------------------------------

    @Override
    public ResultsStats hitsStats() {
        return resultsStats();
    }

    /**
     * Block until this many hits have been processed.
     *
     * Returns false if there's not enough hits to process.
     *
     * @param lowerBound number of hits to wait for
     * @return true if this many hits are now available, false if not
     */
    protected boolean hitsProcessedAtLeast(long lowerBound) {
        ensureResultsRead(lowerBound);
        return this.hitsInternal.size() >= lowerBound;
    }

    protected long hitsProcessedSoFar() {
        return this.hitsInternal.size();
    }

    protected long hitsProcessedTotal() {
        ensureAllResultsRead();
        return this.hitsInternal.size();
    }

    protected long hitsCountedTotal() {
        ensureAllResultsRead();
        return hitsCounted;
    }

    @Override
    public ResultsStats docsStats() {
        return docsStats;
    }

    protected long docsProcessedTotal() {
        ensureAllResultsRead();
        return docsRetrieved;
    }

    protected long docsCountedTotal() {
        ensureAllResultsRead();
        return docsCounted;
    }

    protected long hitsCountedSoFar() {
        return hitsCounted;
    }

    protected long docsCountedSoFar() {
        return docsCounted;
    }

    protected long docsProcessedSoFar() {
        return docsRetrieved;
    }

    // Deriving other Hits / Results instances
    //--------------------------------------------------------------------

    /** Assumes this hit is within our lists. */
    @Override
    public Hits window(Hit hit) {
        HitsInternalMutable r = HitsInternal.create(1, false, false);
        r.add(hit);

        return Hits.list(
            this.queryInfo(),
            r,
            new WindowStats(false, 1, 1, 1),
            null, // window is not sampled
            1,
            1,
            1,
            matchInfoDefs(),
            true);
    }

    // Match info (captured groups, relations)
    //--------------------------------------------------------------------

    @Override
    public MatchInfoDefs matchInfoDefs() {
        return matchInfoDefs == null ? MatchInfoDefs.EMPTY : matchInfoDefs;
    }

    @Override
    public boolean hasMatchInfo() {
        return matchInfoDefs != null;
    }

    // Hits display
    //--------------------------------------------------------------------

    @Override
    public Concordances concordances(ContextSize contextSize, ConcordanceType type) {
        if (contextSize == null)
            contextSize = index().defaultContextSize();
        if (type == null)
            type = ConcordanceType.FORWARD_INDEX;
        return new Concordances(this, type, contextSize);
    }

    @Override
    public Kwics kwics(ContextSize contextSize) {
        if (contextSize == null)
            contextSize = index().defaultContextSize();
        return new Kwics(this, contextSize);
    }

    /**
     * Get Lucene document id for the specified hit
     * @param index hit index
     * @return document id
     */
    @Override
    public int doc(long index) {
        ensureResultsRead(index + 1);
        return hitsInternal.doc(index);
    }

    /**
     * Get start position for the specified hit
     * @param index hit index
     * @return document id
     */
    @Override
    public int start(long index) {
        ensureResultsRead(index + 1);
        return hitsInternal.start(index);
    }

    /**
     * Get end position for the specified hit
     * @param index hit index
     * @return document id
     */
    @Override
    public int end(long index) {
        ensureResultsRead(index + 1);
        return hitsInternal.end(index);
    }

    @Override
    public HitsInternal getInternalHits() {
        ensureAllResultsRead();
        return hitsInternal;
    }
}
