package nl.inl.blacklab.forwardindex;

import java.io.IOException;
import java.text.CollationKey;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;

import it.unimi.dsi.fastutil.ints.IntArrays;
import nl.inl.blacklab.codec.BLTerms;
import nl.inl.blacklab.codec.BlackLabCodecUtil;
import nl.inl.blacklab.codec.BlackLabPostingsReader;
import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.util.BlockTimer;

/** Keeps a list of unique terms and their sort positions.
 *
 * This version is integrated into the Lucene index.
 */
public class TermsIntegrated extends TermsAbstract {

    private static final Comparator<TermInIndex> CMP_TERM_SENSITIVE = Comparator.comparing(a -> a.ckSensitive);

    private static final Comparator<TermInIndex> CMP_TERM_INSENSITIVE = Comparator.comparing(a -> a.ckInsensitive);

    /** Information about a term in the index and its collation keys.
     */
    private class TermInIndex {
        /** Term string */
        String term;

        /** Sensitive collation key */
        CollationKey ckSensitive;

        /** Insensitive collation key */
        CollationKey ckInsensitive;

        /** This term's global id */
        int globalTermId;

        public TermInIndex(String term, int globalTermId) {
            this.term = term;
            this.globalTermId = globalTermId;
            ckSensitive = collatorSensitive.getCollationKey(term);
            ckInsensitive = collatorInsensitive.getCollationKey(term);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof TermInIndex that))
                return false;
            return globalTermId == that.globalTermId;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(globalTermId);
        }

    }

    private IndexReader indexReader;

    private final String luceneField;

    /** Per segment (by ord number): the translation of that segment's term ids to
     *  global term ids.
     *  Hopefully eventually no longer needed.
     */
    private final Map<Integer, int[]> segmentToGlobalTermIds = new HashMap<>();

    public TermsIntegrated(Collators collators, IndexReader indexReader, String luceneField)
            throws InterruptedException {
        super(collators);

        try (BlockTimer bt = BlockTimer.create(LOG_TIMINGS, "Determine " + luceneField + " terms list")) {
            this.indexReader = indexReader;
            this.luceneField = luceneField;

            // Read the terms from all the different segments and determine global term ids
            Pair<TermInIndex[], String[]> termAndStrings;
            try (BlockTimer bt2 = BlockTimer.create(LOG_TIMINGS, luceneField + ": readTermsFromIndex")) {
                termAndStrings = readTermsFromIndex();
            }
            TermInIndex[] terms = termAndStrings.getLeft();
            String[] termStrings = termAndStrings.getRight();

            // Determine the sort orders for the global terms list
            List<int[]> sortedInverted;
            try (BlockTimer bt2 = BlockTimer.create(LOG_TIMINGS, luceneField + ": determineSort and invert")) {
                sortedInverted = List.of(true, false).parallelStream()
                        .map(sensitive -> {
                            Comparator<TermInIndex> cmp = sensitive ? CMP_TERM_SENSITIVE : CMP_TERM_INSENSITIVE;

                            // Get a sorted term index array (sort position > term id)
                            // Note that multiple terms may be equal according to the comparator,
                            // but they still get separate sort positions. This will be fixed later on the
                            // second invert pass.
                            int[] sorted = determineSort(terms, cmp);
                            // Invert array because that's what finishInitialization needs.
                            // Produces a term id > sort position array.
                            // NOTE: gives equal sort positions to equal terms, so the second invert can collect
                            // all the equal terms into one entry.
                            return getTermIdToSortValueArray(terms, sorted, cmp);
                        })
                        .toList();
            }
            int[] termId2SensitivePosition = sortedInverted.get(0);
            int[] termId2InsensitivePosition = sortedInverted.get(1);
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

            // Process the values we've determined so far the same way as with the external forward index.
            try (BlockTimer bt2 = BlockTimer.create(LOG_TIMINGS, luceneField + ": finishInitialization")) {
                finishInitialization(luceneField, termStrings, termId2SensitivePosition, termId2InsensitivePosition);
            }

            // clear temporary variables
            this.indexReader = null;
        }
    }

    private Pair<TermInIndex[], String[]> readTermsFromIndex() throws InterruptedException {
        // Globally unique terms that occur in our index (sorted by global id)
        Map<String, TermInIndex> globalTermIds = new LinkedHashMap<>();

        // Intentionally single-threaded; multi-threaded is slower (because we have to lock the map?)
        for (LeafReaderContext l: indexReader.leaves()) {
            readTermsFromSegment(globalTermIds, l);
        }

        TermInIndex[] terms = globalTermIds.values().toArray(TermInIndex[]::new);
        String[] termStrings = Arrays.stream(terms).map(t -> t.term).toArray(String[]::new);
        return Pair.of(terms, termStrings);
    }

    private synchronized void readTermsFromSegment(Map<String, TermInIndex> globalTermIds, LeafReaderContext lrc)
            throws InterruptedException {
        BLTerms segmentTerms;
        try {
            segmentTerms = (BLTerms) lrc.reader().terms(luceneField);
        } catch (IOException e) {
            throw new InvalidIndex(e);
        }
        if (segmentTerms == null) {
            // can happen if segment only contains index metadata doc
            return;
        }
        BlackLabPostingsReader postingsReader = BlackLabCodecUtil.getPostingsReader(lrc);
        try (TermsIntegratedSegment s = new TermsIntegratedSegment(postingsReader, luceneField, lrc.ord)) {
            Iterator<TermsIntegratedSegment.TermInSegment> termIterator = s.iterator();
            int[] segmentToGlobal = segmentToGlobalTermIds.computeIfAbsent(s.ord(), __ -> new int[s.size()]);
            while (termIterator.hasNext()) {
                // Make sure this can be interrupted if e.g. a commandline utility completes
                // before this initialization is finished.
                if (Thread.currentThread().isInterrupted())
                    throw new InterruptedException();

                TermsIntegratedSegment.TermInSegment t = termIterator.next();
                TermInIndex tii = globalTermIds.computeIfAbsent(t.term,
                        __ -> new TermInIndex(t.term, globalTermIds.size()));
                // Remember the mapping from segment id to global id
                segmentToGlobal[t.id] = tii.globalTermId;
            }
        }
    }

    private int[] determineSort(TermInIndex[] terms, Comparator<TermInIndex> cmp) {
        // Initialize array of indexes to be sorted
        int[] sorted = new int[terms.length];
        for (int i = 0; i < terms.length; i++) {
            sorted[i] = i;
        }
        IntArrays.parallelQuickSort(sorted, (a, b) -> cmp.compare(terms[a], terms[b]));
        return sorted;
    }

    /**
     * Get an array of sort values for the given term ids, based on the given sorted term id array.
     *
     * Essentially inverts sortedTermIds so the values become the indexes and vice versa.
     * Also makes sure that if multiple terms are considered equal (insensitive comparison),
     * they all get the same sort value.
     *
     * @param terms terms the sortedTermIds array refers to
     * @param sortedTermIds array of term ids sorted by term string
     * @param cmp comparator to use for equality test
     * @return an array of sort values in order of term ids (i.e. first item is the sort value for term id 0, etc.)
     */
    private int[] getTermIdToSortValueArray(TermInIndex[] terms, int[] sortedTermIds, Comparator<TermInIndex> cmp) {
        assert terms.length == sortedTermIds.length;
        int[] result = new int[sortedTermIds.length];
        int prevSortPosition = -1;
        int prevTermId = -1;
        for (int i = 0; i < sortedTermIds.length; i++) {
            int termId = sortedTermIds[i];
            int sortPosition = i;
            if (prevTermId >= 0 && cmp.compare(terms[prevTermId], terms[termId]) == 0) {
                // Keep the same sort position because the terms are the same
                sortPosition = prevSortPosition;
                // This should never happen with sensitive sort (all values should be unique)
                //assert cmp == CMP_TERM_INSENSITIVE : "Duplicate term in sensitive sort: " + terms[prevTermId].term + " vs. " + terms[termId].term;
            } else {
                // Remember the sort position in case the next term is identical
                prevSortPosition = sortPosition;
            }
            assert sortPosition >= 0 && sortPosition < terms.length;
            result[termId] = sortPosition;
            assert termId >= 0 && termId < terms.length;
            prevTermId = termId;
        }
        return result;
    }

    @Override
    public int[] segmentIdsToGlobalIds(int ord, int[] snippet) {
        int[] mapping = segmentToGlobalTermIds.get(ord);
        int[] converted = new int[snippet.length];
        for (int i = 0; i < snippet.length; i++) {
            converted[i] = snippet[i] < 0 ? snippet[i] : mapping[snippet[i]];
        }
        return converted;
    }

}
