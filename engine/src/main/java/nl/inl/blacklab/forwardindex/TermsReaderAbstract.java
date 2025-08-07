package nl.inl.blacklab.forwardindex;

import java.text.Collator;
import java.util.Arrays;

import org.eclipse.collections.api.set.primitive.MutableIntSet;

import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.hash.TIntObjectHashMap;
import it.unimi.dsi.fastutil.BigArrays;
import it.unimi.dsi.fastutil.bytes.ByteBigArrayBigList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntListIterator;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.util.BlockTimer;

/**
 * Abstract base class for TermsIntegrated.
 *
 * Each term gets a sensitive and insensitive sort position.
 * Multiple terms may get the same sort position, if those terms
 * are considered equal by the collators involved.
 * For each sort position, a "group offset" is assigned, which is
 * an offset into the groupTermIds array. At this
 * offset is the number of terms n in the group, and then the
 * n term ids themselves.
 * Finally, there's a big character array containing the term
 * string data, and an offsets array that specified for each term id
 * where that term string begins in the character array.
 */
public abstract class TermsReaderAbstract implements Terms {

    /** Will automatically be set to true if assertions are enabled (-ea). */
    protected static boolean DEBUGGING = false;

    /** If assertions are enabled, should we validate term sorts initially? Slows down index opening. */
    protected static final boolean DEBUG_VALIDATE_SORT = false;

    /** Log the timing of different initialization tasks? */
    protected static final boolean LOG_TIMINGS = false;

    /** How many terms total are there? (always valid) */
    private int numberOfTerms;

    /** Collator to use for sensitive string comparisons */
    protected final Collator collatorSensitive;

    /** Collator to use for insensitive string comparisons */
    protected final Collator collatorInsensitive;

    /** Insensitive sort position to start index of group in groupTermIds */
    private int[] insensitivePosition2GroupOffset;

    /** Sensitive sort position to start index of group in groupTermIds */
    private int[] sensitivePosition2GroupOffset;

    /** Term id to sensitive sort position */
    private int[] termId2SensitivePosition;

    /** Term id to sensitive sort position */
    private int[] termId2InsensitivePosition;

    /**
     * Array containing the termids for each group, preceded by the group length.
     *
     * Contains a leading int specifying how many ids for a given group, followed by the list of ids.
     * For a group of size 2 containing the ids 4 and 8, contains [..., 2, 4, 8, ...]
     * {@link #insensitivePosition2GroupOffset} and {@link #sensitivePosition2GroupOffset} contain the index of the leading int
     * in this array for all sensitive/insensitive sorting positions respectively.
     */
    private int[] groupTermIds;

    /** Indicates how much of groupTermIds has been filled in */
    private int groupTermIdsOffset = 0;

    /** The character data for all terms. */
    private ByteBigArrayBigList termCharData;

    /**
     * Contains the start position in termCharData array for each term id.
     * The length of the term must be computed by using the start position of the next term.
     */
    private long[] termId2CharDataOffset;

    /**
     * While creating groups, we keep a mapping from the first term id in
     * each group to the group offset, so we can quickly check if a group is
     * identical to an existing one.
     * This relies on the fact that the term ids in groups are sorted during this
     * process, and that a term will only occur once in groupTermIds when we're
     * processing the second array (e.g. insensitive after we've done sensitive).
     */
    private int[] cacheFirstTermIdInGroup2GroupOffset;

    protected TermsReaderAbstract(Collators collators) {
        DEBUGGING = TermsReaderAbstract.class.desiredAssertionStatus(); // assertions enabled?
        collatorSensitive = collators.get(MatchSensitivity.SENSITIVE);
        collatorInsensitive = collators.get(MatchSensitivity.INSENSITIVE);
    }

    protected void finishInitialization(String name, String[] terms, int[] termId2SensitivePosition,
            int[] termId2InsensitivePosition) {

        numberOfTerms = terms.length;
        this.termId2SensitivePosition = termId2SensitivePosition;
        this.termId2InsensitivePosition = termId2InsensitivePosition;

        assert this.termId2SensitivePosition.length == numberOfTerms;
        assert this.termId2InsensitivePosition.length == numberOfTerms;

        TIntObjectHashMap<IntList> sensitivePosition2TermIds;
        TIntObjectHashMap<IntList> insensitivePosition2TermIds;
        try (BlockTimer ignored = BlockTimer.create(LOG_TIMINGS, name + ": finish > invert mapping")) {
            sensitivePosition2TermIds = findTermIdsForSortPositions(this.termId2SensitivePosition);
            insensitivePosition2TermIds = findTermIdsForSortPositions(this.termId2InsensitivePosition);
        }

        try (BlockTimer ignored = BlockTimer.create(LOG_TIMINGS, name + ": finish > fillTermDataGroups")) {
            fillTermDataGroups(sensitivePosition2TermIds, insensitivePosition2TermIds);
        }

        if (DEBUGGING && DEBUG_VALIDATE_SORT) {
            debugVerifySorts(terms);
        }

        try (BlockTimer ignored = BlockTimer.create(LOG_TIMINGS, name + ": finish > fillTermCharData")) {
            fillTermCharData(terms);
        }
    }

    private TIntObjectHashMap<IntList> findTermIdsForSortPositions(int[] termIdToSortPosition) {
        // Invert the mapping of term id-> insensitive sort position into insensitive sort position -> term ids
        TIntObjectHashMap<IntList> sortPosition2TermIds = new TIntObjectHashMap<>(numberOfTerms);
        IntArrayList newList = new IntArrayList(1); // we'll use this later, if there's no entry yet
        for (int termId = 0; termId < termIdToSortPosition.length; ++termId) {
            int sortPosition = termIdToSortPosition[termId];
            IntList termIdsForSortPosition = sortPosition2TermIds.putIfAbsent(sortPosition, newList);
            if (termIdsForSortPosition == null) {
                // There was no list associated with this sortPosition yet, so our new list was used.
                termIdsForSortPosition = newList;
                newList = new IntArrayList(1); // prepare a new list for next time this happens
            }
            // Note that termId always increases, and we always add at the end of the list, so we know that
            // these lists are sorted. This will be important later when we try to re-use groups.
            termIdsForSortPosition.add(termId);
        }
        return sortPosition2TermIds;
    }

    /**
     * Initializes the following members:
     * - {@link #termId2SensitivePosition}
     * - {@link #termId2InsensitivePosition}
     * - {@link #groupTermIds}
     * - {@link #sensitivePosition2GroupOffset}
     * - {@link #insensitivePosition2GroupOffset}
     */
    private void fillTermDataGroups(
            TIntObjectHashMap<IntList> sensitivePosition2TermIds,
            TIntObjectHashMap<IntList> insensitivePosition2TermIds) {
        // Allocate groupTermIds to the maximum size needed first; we'll compact it later.
        // Max size:
        // - each term occurs twice (once for sensitive, once for insensitive)
        // - for each (sensitive and insensitive) sort position, we get one integer specifying the group size
        int maxEntries = numberOfTerms * 2 + sensitivePosition2TermIds.size() + insensitivePosition2TermIds.size();
        groupTermIds = new int[maxEntries];

        // Create the cache we'll use to help us detect duplicate groups
        cacheFirstTermIdInGroup2GroupOffset = new int[numberOfTerms];
        Arrays.fill(cacheFirstTermIdInGroup2GroupOffset, -1);

        // Create groups for sort positions
        sensitivePosition2GroupOffset = createTermIdGroups(sensitivePosition2TermIds);
        insensitivePosition2GroupOffset = createTermIdGroups(insensitivePosition2TermIds);

        // Free up some memory
        cacheFirstTermIdInGroup2GroupOffset = null; // don't need this anymore
        groupTermIds = Arrays.copyOf(groupTermIds, groupTermIdsOffset); // trim to actual size
    }

    private int[] createTermIdGroups(TIntObjectHashMap<IntList> sortPosition2TermIds) {
        // NOTE: the sortPosition2GroupOffset array we create here may have empty spots
        //       (not every sort position exists), but we'll fill those in at the end
        int[] sortPosition2GroupOffset = new int[numberOfTerms];
        Arrays.fill(sortPosition2GroupOffset, -1);
        TIntObjectIterator<IntList> it = sortPosition2TermIds.iterator();
        while (it.hasNext()) {
            it.advance();
            final int sortPosition = it.key();
            final IntList termIds = it.value();

            // See if we can re-use an existing group.
            int groupOffset = findGroup(termIds);
            if (groupOffset < 0) {
                // This group doesn't exist yet; create new group
                groupOffset = groupTermIdsOffset;
                groupTermIds[groupTermIdsOffset] = termIds.size();
                groupTermIdsOffset++;
                IntListIterator termIdIt = termIds.iterator();
                boolean first = true;
                while (termIdIt.hasNext()) {
                    int termId = termIdIt.nextInt();
                    if (first) {
                        // Remember mapping from this first term id to the group offset,
                        // so we can quickly check if we have a duplicate group later.
                        cacheFirstTermIdInGroup2GroupOffset[termId] = groupTermIdsOffset;
                    }
                    first = false;
                    groupTermIds[groupTermIdsOffset] = termId;
                    groupTermIdsOffset++;
                }
            }
            sortPosition2GroupOffset[sortPosition] = groupOffset;
        }
        fixSortPosition2GroupOffsetArray(sortPosition2GroupOffset); // fix empty spaces in array so binary search works
        return sortPosition2GroupOffset;
    }

    private int findGroup(IntList termIds) {
        // Because we know that termIds are sorted (see above), and we keep track of a map from
        // "first term id in group" to the group offset, we can quickly check if the other group starting
        // with the same term id is identical to this one, and if so, re-use it to save memory.

        // Is there a group starting with the same term id?
        if (cacheFirstTermIdInGroup2GroupOffset[termIds.getInt(0)] != -1) {
            // Yes, does it have the same size?
            int groupOffset = cacheFirstTermIdInGroup2GroupOffset[termIds.getInt(0)];
            int groupSize = groupTermIds[groupOffset];
            if (groupSize == termIds.size()) {
                // Yes, are all the term ids the same?
                for (int i = 0; i < groupSize; ++i) {
                    if (groupTermIds[groupOffset + 1 + i] != termIds.getInt(i)) {
                        return -1; // no, not identical
                    }
                }
                // Yes, we have a match!
                return groupOffset;
            }
            return -1; // not the same size
        }
        return -1; // no group starting with same term id
    }

    private static void fixSortPosition2GroupOffsetArray(int[] sortPosition2GroupOffset) {
        // fill empty spots using the last good entry
        // if we don't do this binary searching over this array won't work (as it contains random uninitialized values
        // and if we land on one of them we'd compare wrong)
        int last = 0;
        for (int i = 0; i < sortPosition2GroupOffset.length; ++i) {
            if (sortPosition2GroupOffset[i] != -1)
                last = sortPosition2GroupOffset[i];
            else
                sortPosition2GroupOffset[i] = last;
        }
    }

    /**
     * Converts terms string array to byte data and stored offsets.
     *
     * Initializes
     * - {@link #termCharData}
     * - {@link #termId2CharDataOffset}
     */
    private void fillTermCharData(String[] terms) {
        // convert all to byte[] and tally total number of bytes
        // free the String instances while doing this so memory usage doesn't spike so much
        termCharData = new ByteBigArrayBigList();
        long bytesWritten = 0;
        termId2CharDataOffset = new long[numberOfTerms];
        for (int i = 0; i < numberOfTerms; ++i) {
            termId2CharDataOffset[i] = bytesWritten;
            byte[] bytes = terms[i].getBytes(TERMS_CHARSET);
            byte[][] bb = BigArrays.wrap(bytes);
            termCharData.addElements(bytesWritten, bb);
            bytesWritten += bytes.length;
        }
        termCharData.trim(); // clear extra space.
    }

    @Override
    public int indexOf(String term) {
        int groupOffset = getGroupOffset(term, sensitivePosition2GroupOffset, collatorSensitive);
        if (groupOffset == -1)
            return -1;
        // Return the first term in this group
        return groupTermIds[groupOffset + 1];
    }

    @Override
    public void indexOf(MutableIntSet results, String term, MatchSensitivity sensitivity) {
        int groupOffset = getGroupOffset(term, sensitivity);
        if (groupOffset == -1) {
            results.add(-1);
            return;
        }
        int groupSize = groupTermIds[groupOffset];
        for (int i = 0; i < groupSize; ++i) {
            results.add(groupTermIds[groupOffset + 1 + i]);
        }
    }

    @Override
    public int numberOfTerms() {
        return numberOfTerms;
    }

    @Override
    public int idToSortPosition(int id, MatchSensitivity sensitivity) {
        return sensitivity.isCaseSensitive() ?
                idToSortPositionSensitive(id) :
                idToSortPositionInsensitive(id);
    }

    private int idToSortPositionSensitive(int termId) {
        if (termId < 0 || termId >= numberOfTerms)
            return -1;
        return termId2SensitivePosition[termId];
    }

    private int idToSortPositionInsensitive(int termId) {
        if (termId < 0 || termId >= numberOfTerms)
            return -1;
        return termId2InsensitivePosition[termId];
    }

    @Override
    public String get(int id) {
        if (id >= numberOfTerms || id < 0)
            return "";
        long startOffsetBytes = termId2CharDataOffset[id];
        boolean isLastId = id == (termId2CharDataOffset.length - 1);
        long endOffsetBytes = (isLastId ? termCharData.size64() : termId2CharDataOffset[id+1]);
        int termLengthBytes = (int) (endOffsetBytes - startOffsetBytes);

        byte[] termBytes = new byte[termLengthBytes];
        termCharData.getElements(startOffsetBytes, termBytes, 0, termLengthBytes);
        return new String(termBytes, TERMS_CHARSET);
    }

    private int getGroupOffset(String term, MatchSensitivity sensitivity) {
        final Collator collator = sensitivity.isCaseSensitive() ? collatorSensitive : collatorInsensitive;
        final int[] sortPosition2GroupOffset = sensitivity.isCaseSensitive() ?
                sensitivePosition2GroupOffset :
                insensitivePosition2GroupOffset;
        return getGroupOffset(term, sortPosition2GroupOffset, collator);
    }

    private int getGroupOffset(String term, int[] sortPosition2GroupOffset, Collator collator) {
        // Binary search to find the matching group offset
        int l = 0;
        int r = sortPosition2GroupOffset.length - 1;

        int matchingGroupOffset = -1;
        while (l <= r) {

            final int sortPositionToCheck = l + (r - l) / 2;
            final int groupOffset = sortPosition2GroupOffset[sortPositionToCheck];
            final int termIdToCompareTo = groupTermIds[groupOffset + 1]; // OPT: < numterms optimization
            final String termToCompareTo = get(termIdToCompareTo);

            final int result = collator.compare(term, termToCompareTo);

            if (result == 0) {
                matchingGroupOffset = groupOffset;
                break;
            }

            if (result < 0) {
                // position we're looking for is before this result, move right (upper) boundary to just before current position
                r = sortPositionToCheck - 1;
            } else {
                // position we're looking for is after this result, move left (lower) boundary to just after current position
                l = sortPositionToCheck + 1;
            }
        }

        return matchingGroupOffset;
    }

    private void debugVerifySorts(String[] terms) {
        // Verify sorts
        String prev = null;
        for (int groupOffset: sensitivePosition2GroupOffset) {
            assert groupOffset >= 0;
            assert groupTermIds[groupOffset] == 1;
            int termId = groupTermIds[groupOffset + 1];
            assert termId >= 0 && termId < terms.length;
            String term = terms[termId];
            if (prev != null)
                assert collatorSensitive.compare(prev, term) == -1;
            prev = term;
        }
        prev = null;
        int prevTermId = -1;
        for (int groupOffset: insensitivePosition2GroupOffset) {
            assert groupOffset >= 0;
            int termId = groupTermIds[groupOffset + 1];
            if (prevTermId != -1 && termId != prevTermId) {
                String firstTerm = terms[termId];
                if (prev != null)
                    assert collatorInsensitive.compare(prev, firstTerm) <= 0;
                prev = firstTerm;
            }
            prevTermId = termId;
        }
    }
}
