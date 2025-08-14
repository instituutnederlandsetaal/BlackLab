package nl.inl.blacklab.search.results.hits;

import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.Constants;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.ResultsAbstract;
import nl.inl.blacklab.search.results.SampleParameters;
import nl.inl.blacklab.search.results.WindowStats;
import nl.inl.blacklab.search.results.docs.DocResults;
import nl.inl.blacklab.search.results.stats.ResultsStats;
import nl.inl.blacklab.search.results.stats.ResultsStatsSaved;

/**
 * A collection of matches being fetched as they are needed.
 *
 * Should be thread-safe and most methods are safe w.r.t. hits having been fetched.
 */
public abstract class HitResultsAbstract extends ResultsAbstract implements HitResults {

    protected static final Logger logger = LogManager.getLogger(HitResultsAbstract.class);

    /**
     * Minimum number of hits to fetch in an ensureHitsRead() block.
     *
     * This prevents locking again and again for a single hit when iterating.
     *
     * See {@link HitResultsFromQuery} and {@link HitResultsFiltered}.
     */
    protected static final int FETCH_HITS_MIN = 20;

    /** Our internal list of hits. */
    protected final Hits hitsInternal;

    /** Mutable interface to our list of hits, if mutation is allowed. */
    protected final HitsMutable hitsMutable;

    /** A view to our actual hits.
     *  Will ensure that enough hits have been fetched (if applicable).
     */
    Hits hitsView;

    /**
     * Construct a Hits object from a hits array.
     *
     * @param queryInfo query info for corresponding query
     * @param hits hits to use for this object. Used as-is, not copied.
     */
    protected HitResultsAbstract(QueryInfo queryInfo, Hits hits, boolean mutable) {
        super(queryInfo);
        if (hits == null)
            throw new IllegalArgumentException("HitsAbstract must be constructed with valid hits object (got null)");
        this.hitsInternal = hits;
        this.hitsMutable = mutable ? (HitsMutable)hits : null;
        hitsView = new LazyHitsView();
    }

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
    public HitResults window(long first, long windowSize) {
        Hits hs = getHits();
        Hits window = hs.sublist(first, windowSize);

//        // Error if first out of range
//        boolean emptyResultSet = !resultsStats().waitUntil().processedAtLeast(1);
//        if (first < 0 || (emptyResultSet && first > 0) ||
//            (!emptyResultSet && !resultsStats().waitUntil().processedAtLeast(first + 1))) {
//            //throw new IllegalArgumentException("First hit out of range");
//            return Hits.empty(queryInfo());
//        }
//
//        // Auto-clamp number
//        // take care not to always call size(), as that blocks until we're done!
//        // Instead, first call ensureResultsRead so we block until we have either have enough or finish
//        boolean enoughHitsForFullWindow = this.ensureResultsRead(first + windowSize);
//        // and only THEN do this, since now we know if we don't have this many hits, we're done, and it's safe to call size
//        long number;
//        if (enoughHitsForFullWindow)
//            number = windowSize;
//        else {
//            number = size() - first;
//            assert number < windowSize;
//        }

        // Copy the hits we're interested in.
        MutableLong docsRetrieved = new MutableLong(0); // Bypass warning (enclosing scope must be effectively final)
//        HitsInternalMutable window = HitsInternal.create(field(), getHits().matchInfoDefs(), number, number,
//                false);

        // Count docs
        int prevDoc = -1;
        for (EphemeralHit hit: window) {
            int doc = hit.doc();
            if (doc != prevDoc) {
                docsRetrieved.add(1);
                prevDoc = doc;
            }
        }

        boolean hasNext = resultsStats().waitUntil().processedAtLeast(first + windowSize + 1);
        WindowStats windowStats = new WindowStats(hasNext, first, windowSize, window.size());
        ResultsStats hitsStats = new ResultsStatsSaved(window.size());
        ResultsStats docsStats = new ResultsStatsSaved(docsRetrieved.longValue());
        return new HitResultsList(queryInfo(), window, windowStats, null,
                hitsStats, docsStats);
    }

    /**
     * Take a sample of hits by wrapping an existing Hits object.
     *
     * @param sampleParameters sample parameters
     * @return the sample
     */
    @Override
    public HitResults sample(SampleParameters sampleParameters) {

        Hits sample = sampleHits(getHits(), sampleParameters);

        // Count the number of documents in the sample
        MutableInt docsInSample = new MutableInt(0);
        int previousDoc = -1;
        for (EphemeralHit hit: sample) {
            if (hit.doc() != previousDoc) { // this works because indexes are sorted (TreeSet)
                docsInSample.add(1);
                previousDoc = hit.doc();
            }
        }

        ResultsStats hitsStats = new ResultsStatsSaved(sample.size());
        ResultsStats docsStats = new ResultsStatsSaved(docsInSample.getValue());
        return new HitResultsList(queryInfo(), sample, null, sampleParameters, hitsStats, docsStats);
    }

    public static Hits sampleHits(Hits hitsList, SampleParameters sampleParameters) {
        // Fetch all hits and get most efficient implementation (nonlocking)
        hitsList = hitsList.getStatic();
        long totalNumberOfHits = hitsList.size();

        Set<Long> chosenHitIndices = new TreeSet<>(); // we need indexes sorted (see below)
        long numberOfHitsToSelect = sampleParameters.numberOfHits(totalNumberOfHits);
        if (numberOfHitsToSelect > Constants.JAVA_MAX_SET_SIZE) {
            throw new UnsupportedOperationException("Cannot sample more than " + Constants.JAVA_MAX_SET_SIZE +
                    " hits (tried to sample " + numberOfHitsToSelect + " from a total of " + totalNumberOfHits + ")");
        }
        if (numberOfHitsToSelect >= totalNumberOfHits) {
            numberOfHitsToSelect = totalNumberOfHits; // default to all hits in this case
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
                    hitIndex = random.nextInt((int)Math.min(Constants.JAVA_MAX_ARRAY_SIZE, totalNumberOfHits));
                } while (chosenHitIndices.contains(hitIndex));
                chosenHitIndices.add(hitIndex);
            }
        }

        // Add the chosen hits indexes to the sample.
        HitsMutable sample = HitsMutable.create(hitsList.field(), hitsList.matchInfoDefs(), numberOfHitsToSelect,
                numberOfHitsToSelect, false);
        EphemeralHit hit = new EphemeralHit();
        for (Long hitIndex: chosenHitIndices) {
            hitsList.getEphemeral(hitIndex, hit);
            sample.add(hit);
        }
        return sample;
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
    public HitResults sorted(HitProperty sortProp) {
        // We need a HitProperty with the correct Hits object
        // If we need context, make sure we have it.
        sortProp = sortProp.copyWith(getHits());

        // Perform the actual sort.
        ensureResultsRead(-1);
        Hits sorted = getHits().sorted(sortProp);
        sortProp.disposeContext(); // we don't need the context information anymore, free memory

        return new HitResultsList(queryInfo(), sorted, null, null,
                resultsStats(), docsStats());
    }

    @Override
    public HitGroups group(HitProperty criteria, long maxResultsToStorePerGroup) {
        ensureResultsRead(-1);
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
    public HitResults filter(HitProperty property, PropertyValue value) {
        return new HitResultsFiltered(this, property, value);
    }

    @Override
    public long numberOfResultObjects() {
        return this.hitsInternal.size();
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
     * Return a per-document view of these hits.
     *
     * @param maxHits maximum number of hits to store per document
     *
     * @return the per-document view.
     */
    @Override
    public DocResults perDocResults(long maxHits) {
        return DocResults.fromHits(queryInfo(), getHits(), maxHits);
    }

    // Deriving other Hits / Results instances
    //--------------------------------------------------------------------

    /** Assumes this hit is within our lists. */
    @Override
    public HitResults window(Hit hit) {
        HitsMutable r = HitsMutable.create(field(), getHits().matchInfoDefs(), 1, false, false);
        r.add(hit);

        return new HitResultsList(
                queryInfo(),
                r,
                new WindowStats(false, 1, 1, 1),
                null, // window is not sampled
                new ResultsStatsSaved(1),
                new ResultsStatsSaved(1));
    }

    // Match info (captured groups, relations)
    //--------------------------------------------------------------------

    // Hits display
    //--------------------------------------------------------------------

    @Override
    public Hits getHits() {
        return hitsView;
    }

    @Override
    public Map<LeafReaderContext, Hits> getSegmentHits() {
        return null;
    }

    @Override
    public WindowStats windowStats() {
        return null;
    }

    private class LazyHitsView implements Hits {
        @Override
        public AnnotatedField field() {
            return queryInfo().field();
        }

        @Override
        public BlackLabIndex index() {
            return queryInfo().index();
        }

        @Override
        public MatchInfoDefs matchInfoDefs() {
            return hitsInternal.matchInfoDefs();
        }

        @Override
        public boolean hasMatchInfo() {
            return hitsInternal.hasMatchInfo();
        }

        @Override
        public long size() {
            ensureResultsRead(-1);
            return hitsInternal.size();
        }

        @Override
        public boolean isEmpty() {
            return !ensureResultsRead(1);
        }

        @Override
        public Hit get(long index) {
            ensureResultsRead(index + 1);
            return hitsInternal.get(index);
        }

        @Override
        public void getEphemeral(long index, EphemeralHit hit) {
            ensureResultsRead(index + 1);
            hitsInternal.getEphemeral(index, hit);
        }

        @Override
        public Iterator<EphemeralHit> iterator() {
            ensureResultsRead(-1);
            return hitsInternal.iterator();
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
        public MatchInfo[] matchInfos(long hitIndex) {
            ensureResultsRead(hitIndex + 1);
            return hitsInternal.matchInfos(hitIndex);
        }

        @Override
        public MatchInfo matchInfo(long hitIndex, int matchInfoIndex) {
            ensureResultsRead(hitIndex + 1);
            return hitsInternal.matchInfo(hitIndex, matchInfoIndex);
        }

        @Override
        public Hits sublist(long first, long windowSize) {
            ensureResultsRead(first + windowSize);
            return hitsInternal.sublist(first, windowSize);
        }

        @Override
        public Hits sorted(HitProperty sortProp) {
            ensureResultsRead(-1);
            return hitsInternal.sorted(sortProp);
        }

        @Override
        public Hits getStatic() {
            ensureResultsRead(-1);
            return hitsInternal.getStatic();
        }

        @Override
        public Hits filteredByDocId(int docId) {
            ensureResultsRead(-1);
            return hitsInternal.filteredByDocId(docId);
        }

        @Override
        public Concordances concordances(ContextSize contextSize, ConcordanceType type) {
            ensureResultsRead(-1);
            return hitsInternal.concordances(contextSize, type);
        }

        @Override
        public Kwics kwics(ContextSize contextSize) {
            ensureResultsRead(-1);
            return hitsInternal.kwics(contextSize);
        }

        @Override
        public Concordances concordances(ContextSize contextSize) {
            ensureResultsRead(-1);
            return hitsInternal.concordances(contextSize);
        }
    }
}
