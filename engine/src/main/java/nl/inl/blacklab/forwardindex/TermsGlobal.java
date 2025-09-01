package nl.inl.blacklab.forwardindex;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;

import com.ibm.icu.text.CollationKey;
import com.ibm.icu.text.Collator;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import nl.inl.blacklab.Constants;
import nl.inl.blacklab.codec.BLTerms;
import nl.inl.blacklab.codec.BlackLabPostingsReader;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.util.BlockTimer;

/** Keeps a list of unique terms and their sort positions.
 * <p>
 * Each term gets a sensitive and insensitive sort position.
 * Multiple terms may get the same sort position, if those terms
 * are considered equal by the collators involved.
 */
public class TermsGlobal implements Terms {

    private static final Logger logger = LogManager.getLogger(TermsGlobal.class);

    /** Will automatically be set to true if assertions are enabled (-ea). */
    protected static boolean DEBUGGING = false;

    /** If assertions are enabled, should we validate term sorts initially? Slows down index opening. */
    protected static final boolean DEBUG_VALIDATE_SORT = false;

    /** Log the timing of different initialization tasks? */
    protected static final boolean LOG_TIMINGS = false;

    /** Collator to use for sensitive string comparisons */
    protected Collator collatorSensitive;

    /** Collator to use for insensitive string comparisons */
    protected Collator collatorInsensitive;

    /** How many terms total are there? (always valid) */
    private int numberOfTerms;

    /** Mapping from term id to sensitive sort position */
    private int[] termId2SensitivePosition;

    /** Mapping from term id to sensitive sort position */
    private int[] termId2InsensitivePosition;

    /** Mapping from insensitive sort position to term id */
    private int[] insensitive2TermId;

    /** Mapping from sensitive sort position to term id */
    private int[] sensitive2TermId;

    /** What segment should we read each term string from? */
    private final ObjectList<Terms> termSegmentTerms = new ObjectArrayList<>();

    /** Mapping from global term id to segment term id (in the segment given by termSegment) */
    private final IntList termSegmentTermId = new IntArrayList();

    private boolean initialized = false;

    /** Our lucene field */
    private final String luceneField;

    /** Per segment (by term object): the translation of that segment's term ids to
     *  global term ids.
     */
    private final Map<LeafReaderContext, int[]> segmentToGlobalTermIds = new HashMap<>();

    /** Only used during initialization */
    private final Map<String, Integer> globalTermIds;

    public TermsGlobal(String luceneField) {
        super();
        DEBUGGING = TermsGlobal.class.desiredAssertionStatus(); // assertions enabled?
        this.luceneField = luceneField;

        // Will be used in initialization only, then clear()'ed
        globalTermIds = new LinkedHashMap<>();

        // initialize will be called by the initialization thread or as needed;
        // terms object will only be available after that.
    }

    @Override
    public int numberOfTerms() {
        return numberOfTerms;
    }

    @Override
    public int idToSortPosition(int termId, MatchSensitivity sensitivity) {
        if (termId < 0 || termId >= numberOfTerms)
            return Constants.NO_TERM;
        int[] idToSortPos = sensitivity.isCaseSensitive() ? termId2SensitivePosition : termId2InsensitivePosition;
        return idToSortPos[termId];
    }

    @Override
    public synchronized String get(int id) {
        // NOTE: This method is synchronized because we use the Terms instances
        // we stored in termSegmentTerms, which are not thread-safe.
        if (id >= numberOfTerms || id < 0)
            return "";
        return termSegmentTerms.get(id).get(termSegmentTermId.getInt(id));
    }

    @Override
    public boolean termsEqual(int[] termId, MatchSensitivity sensitivity) {
        if (termId.length < 2)
            return true;
        int expected = idToSortPosition(termId[0], sensitivity);
        for (int termIdIndex = 1; termIdIndex < termId.length; ++termIdIndex) {
            int cur = idToSortPosition(termId[termIdIndex], sensitivity);
            if (cur != expected)
                return false;
        }
        return true;
    }

    @Override
    public int indexOf(String term, MatchSensitivity sensitivity) {
        Collator collator;
        int[] sortPosition2TermId;
        if (sensitivity.isCaseSensitive()) {
            collator = collatorSensitive;
            sortPosition2TermId = sensitive2TermId;
        } else {
            collator = collatorInsensitive;
            sortPosition2TermId = insensitive2TermId;
        }
        // Binary search for the term
        int lo = 0;
        int hi = numberOfTerms - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int termId = sortPosition2TermId[mid];
            String midVal = get(termId);
            int cmp = collator.compare(midVal, term);
            if (cmp < 0)
                lo = mid + 1;
            else if (cmp > 0)
                hi = mid - 1;
            else
                return termId; // term found
        }
        return Constants.NO_TERM;  // term not found
    }

    @Override
    public int termToSortPosition(String term, MatchSensitivity sensitivity) {
        return idToSortPosition(indexOf(term, sensitivity), sensitivity);
    }

    @Override
    public void convertToGlobalTermIds(LeafReaderContext lrc, int[] segmentTermIds) {
        int[] segmentToGlobal = segmentToGlobalTermIds.get(lrc);
        for (int i = 0; i < segmentTermIds.length; i++) {
            if (segmentTermIds[i] != Constants.NO_TERM)
                segmentTermIds[i] = segmentToGlobal[segmentTermIds[i]];
        }
    }

    @Override
    public int toGlobalTermId(LeafReaderContext lrc, int segmentTermId) {
        if (segmentTermId == Constants.NO_TERM)
            return Constants.NO_TERM;
        int[] segmentToGlobal = segmentToGlobalTermIds.get(lrc);
        return segmentToGlobal[segmentTermId];
    }

    public synchronized void initialize(IndexReader indexReader) throws InterruptedException {
        if (initialized)
            return;
        try {
            initialized = true;

            // Determine collators for this field by looking at one of the segments
            Collators collators = indexReader.leaves().stream()
                    .map(lrc -> BLTerms.forSegment(lrc, luceneField))
                    .filter(Objects::nonNull)
                    .map(BLTerms::getCollators)
                    .findFirst()
                    .orElseThrow();
            collatorSensitive = collators.get(MatchSensitivity.SENSITIVE);
            collatorInsensitive = collators.get(MatchSensitivity.INSENSITIVE);

            try (BlockTimer ignored = BlockTimer.create(LOG_TIMINGS, "Determine " + luceneField + " terms list")) {

                // Read the terms from all the different segments and determine global term ids
                String[] terms;
                try (BlockTimer ignored1 = BlockTimer.create(LOG_TIMINGS, luceneField + ": readTermsFromIndex")) {
                    terms = readTermsFromIndex(indexReader, luceneField);
                    numberOfTerms = terms.length;
                }

                // Determine the sort orders for the global terms list
                List<Pair<int[],int[]>> sortedInverted;
                try (BlockTimer ignored1 = BlockTimer.create(LOG_TIMINGS, luceneField + ": determineSort and invert")) {
                    sortedInverted = List.of(true, false).parallelStream()
                            .map(sensitive -> {

                                // Calculate the collation keys for all terms
                                CollationKey[] ck = new CollationKey[numberOfTerms];
                                Collator collator = sensitive ? collatorSensitive : collatorInsensitive;
                                for (int i = 0; i < numberOfTerms; i++) {
                                    ck[i] = collator.getCollationKey(terms[i]);
                                }

                                // Get a sorted term index array (sort position > term id)
                                // Note that multiple terms may be equal according to the comparator,
                                // but they still get separate sort positions. This will be fixed later on the
                                // second invert pass.
                                int[] sorted = determineSort(ck);
                                // Invert array because that's what finishInitialization needs.
                                // Produces a term id > sort position array.
                                // NOTE: gives equal sort positions to equal terms, so the second invert can collect
                                // all the equal terms into one entry.
                                return Pair.of(sorted, invertSortedTermsArray(ck, sorted));
                            })
                            .toList();
                }
                sensitive2TermId = sortedInverted.get(0).first();
                termId2SensitivePosition = sortedInverted.get(0).second();
                insensitive2TermId = sortedInverted.get(1).first();
                termId2InsensitivePosition = sortedInverted.get(1).second();

                assert this.termId2SensitivePosition.length == numberOfTerms;
                assert this.termId2InsensitivePosition.length == numberOfTerms;
                assert this.sensitive2TermId.length == numberOfTerms;
                if (DEBUGGING && DEBUG_VALIDATE_SORT) {
                    // Make sure all sort positions are in the arrays
                    boolean[] found = new boolean[termId2SensitivePosition.length];
                    for (int k: termId2SensitivePosition) {
                        assert k >= 0;
                        found[k] = true;
                    }
                    for (boolean b: found)
                        assert b;
                    // Insensitive doesn't have all sort positions because some terms are lumped together,
                    // creating gaps, but they still have to be non-negative.
                    for (int j: termId2InsensitivePosition) {
                        assert j >= 0;
                    }
                }
            }
        } catch (Exception e) {
            logger.error(e);
        }
    }

    private String[] readTermsFromIndex(IndexReader indexReader, String luceneField) {
        // Intentionally single-threaded; multi-threaded is slower.
        // Probably because reading from a single file sequentially is more efficient than alternating between
        // several files..?
        for (LeafReaderContext lrc: indexReader.leaves()) {
            // Ensure that each segment reads its terms into memory.
            // We don't do this in parallel because it would likely thrash the disk too much.
            BLTerms terms = BlackLabPostingsReader.forSegment(lrc).terms(luceneField);
            if (terms != null)
                terms.reader();
        }
        // Now that all the terms are in memory, we can read them in parallel to
        // determine the global term ids and sort orders.
        // TODO: probably because of locking the globalTermIds. Try with a local map per thread and merge at the end.
        // TODO: deal with ConcurrentModificationException so we can actually make it parallel.
        Exception e = indexReader.leaves().parallelStream()
                .map(lrc -> {
                        try {
                            BLTerms blTerms = BLTerms.forSegment(lrc, luceneField);
                            if (blTerms == null)
                                return null;
                            Terms terms = blTerms.reader();
                            String[] segmentTerms = readTermsFromSegment(terms);
                            int[] segmentToGlobal;
                            synchronized (segmentToGlobalTermIds) {
                                segmentToGlobal = segmentToGlobalTermIds.computeIfAbsent(lrc,
                                        __ -> new int[segmentTerms.length]);
                            }
                            synchronized (globalTermIds) {
                                for (int segmentTermId = 0; segmentTermId < segmentTerms.length; segmentTermId++) {
                                    int globalTermId = globalTermIds.computeIfAbsent(segmentTerms[segmentTermId],
                                            __ -> globalTermIds.size());
                                    // Remember the mapping from segment id to global id
                                    segmentToGlobal[segmentTermId] = globalTermId;
                                    if (globalTermId == globalTermIds.size() - 1) {
                                        // New term, remember where it came from
                                        termSegmentTerms.add(terms);
                                        termSegmentTermId.add(segmentTermId);
                                    }
                                }
                            }

                        } catch (InterruptedException e1) {
                            return e1;
                        }
                        return null;
                    })
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (e != null)
            throw new ErrorOpeningIndex(e);

        String[] terms = globalTermIds.keySet().toArray(new String[0]);
        globalTermIds.clear();
        return terms;
    }

    private String[] readTermsFromSegment(Terms terms)
            throws InterruptedException {
        String[] segmentTerms = new String[terms.numberOfTerms()];
        for (int segmentTermId = 0; segmentTermId < terms.numberOfTerms(); segmentTermId++) {
            // Make sure this can be interrupted if e.g. a commandline utility completes
            // before this initialization is finished.
            if (Thread.interrupted())
                throw new InterruptedException();

            segmentTerms[segmentTermId] = terms.get(segmentTermId);
        }
        return segmentTerms;
    }

    private int[] determineSort(CollationKey[] terms) {
        // Initialize array of indexes to be sorted
        int[] sorted = new int[terms.length];
        for (int i = 0; i < terms.length; i++) {
            sorted[i] = i;
        }
        IntArrays.parallelQuickSort(sorted, (a, b) -> terms[a].compareTo(terms[b]));
        return sorted;
    }

    /**
     * Invert the given sorted term id array so the values become the indexes and vice versa.
     * <p>
     * Will make sure that if multiple terms are considered equal (insensitive comparison),
     * they all get the same sort value
     *
     * @param terms terms the array refers to
     * @param sortedTermIds array of term ids sorted by term string
     * @return inverted array
     */
    private int[] invertSortedTermsArray(CollationKey[] terms, int[] sortedTermIds) {
        assert terms.length == sortedTermIds.length;
        int[] termIdToSortPosition = new int[sortedTermIds.length];
        int prevSortPosition = -1;
        int prevTermId = -1;
        for (int i = 0; i < sortedTermIds.length; i++) {
            int termId = sortedTermIds[i];
            int sortPosition = i;
            if (prevTermId >= 0 && terms[prevTermId].compareTo(terms[termId]) == 0) {
                // Keep the same sort position because the terms are the same
                sortPosition = prevSortPosition;
                // This should never happen with sensitive sort (all values should be unique)
                //assert cmp == CMP_TERM_INSENSITIVE : "Duplicate term in sensitive sort: " + terms[prevTermId].term + " vs. " + terms[termId].term;
            } else {
                // Remember the sort position in case the next term is identical
                prevSortPosition = sortPosition;
            }
            termIdToSortPosition[termId] = sortPosition;
            prevTermId = termId;
        }
        return termIdToSortPosition;
    }
}
