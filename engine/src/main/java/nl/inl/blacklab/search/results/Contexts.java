package nl.inl.blacklab.search.results;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.apache.lucene.index.LeafReaderContext;

import it.unimi.dsi.fastutil.BigList;
import it.unimi.dsi.fastutil.objects.ObjectBigArrayBigList;
import nl.inl.blacklab.Constants;
import nl.inl.blacklab.codec.BlackLabCodecUtil;
import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.ForwardIndexSegmentReader;
import nl.inl.blacklab.forwardindex.TermsSegmentReader;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexIntegrated;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;
import nl.inl.util.ThreadAborter;

/**
 * Utility functions for working with annotation context(s) for a list of hits.
 */
public class Contexts {

    /** In context arrays, how many bookkeeping ints are stored at the start? */
    private static final int NUMBER_OF_BOOKKEEPING_INTS = 3;

    /**
     * In context arrays, what index after the bookkeeping units indicates the hit
     * start?
     */
    private static final int HIT_START_INDEX = 0;

    /**
     * In context arrays, what index indicates the hit end (first word after the hit)?
     */
    private static final int AFTER_START_INDEX = 1;

    /** In context arrays, what index indicates the length of the context? */
    private static final int LENGTH_INDEX = 2;

    private Contexts() {
    }

    /**
     * Retrieves the KWIC information (KeyWord In Context: before, hit and after
     * context) for a number of hits in the same document from the ContentStore.
     *
     * Used by Kwics.retrieveKwics().
     *
     * @param hits hits in this one document
     * @param forwardIndexes forward indexes for the annotations
     * @param contextSize number of words before and after hit to fetch
     * @param kwicConsumer where to add the KWICs
     */
    static void makeKwicsSingleDocForwardIndex(
            HitsSimple hits,
            List<AnnotationForwardIndex> forwardIndexes,
            ContextSize contextSize,
            BiConsumer<Hit, Kwic> kwicConsumer
    ) {
        if (hits.isEmpty())
            return;
        assert !forwardIndexes.isEmpty();

        // Get the contexts (arrays of term ids) and make the KWICs by looking up the terms
        int[][] contexts = getContextWordsSingleDocument(hits, 0, hits.size(),
                contextSize, forwardIndexes, hits.matchInfoDefs());
        int numberOfAnnotations = forwardIndexes.size();
        List<Annotation> annotations = forwardIndexes.stream()
                .map(AnnotationForwardIndex::annotation)
                .toList();
        int docId = hits.doc(0);
        LeafReaderContext lrc = hits.index().getLeafReaderContext(docId);
        List<TermsSegmentReader> annotationTerms = forwardIndexes.stream()
                .map(afi -> {
                    String luceneField = afi.annotation().forwardIndexSensitivity().luceneField();
                    try {
                        return BlackLabCodecUtil.getPostingsReader(lrc).terms(luceneField).reader();
                    } catch (IOException e) {
                        throw new InvalidIndex(e);
                    }
                })
                .toList();
        int hitIndex = 0;
        Iterator<EphemeralHit> it = hits.ephemeralIterator();
        while (it.hasNext()) {
            Hit h = it.next().toHit();
            int[] hitContext = contexts[hitIndex];
            int contextLength = hitContext[Contexts.LENGTH_INDEX];
            List<String> tokens = new ArrayList<>(contextLength * numberOfAnnotations);
            // For each word in the context...
            for (int indexInContext = 0; indexInContext < contextLength; indexInContext++) {
                // For each annotation...
                int annotIndex = indexInContext + Contexts.NUMBER_OF_BOOKKEEPING_INTS;
                for (TermsSegmentReader terms: annotationTerms) {
                    tokens.add(terms.get(hitContext[annotIndex]));
                    annotIndex += contextLength; // jmup to next annotation in context array
                }
            }
            int fragmentStartInDoc = h.start() - hitContext[Contexts.HIT_START_INDEX];
            kwicConsumer.accept(h, new Kwic(annotations, tokens, hitContext[Contexts.HIT_START_INDEX],
                    hitContext[Contexts.AFTER_START_INDEX], fragmentStartInDoc));
            hitIndex++;
        }
    }

    /**
     * Get context words from the forward index.
     *
     * @param hits the hits
     * @param start first hit to get context words for
     * @param end first hit NOT to get context for (hit after the last to get context for)
     * @param contextSize how many words of context we want
     * @param contextSources forward indices to get context from
     * @return the context words for each hit, as an array of int arrays.
     */
    private static int[][] getContextWordsSingleDocument(HitsSimple hits, long start, long end,
            ContextSize contextSize, List<AnnotationForwardIndex> contextSources, MatchInfoDefs matchInfoDefs) {
        if (end - start > Constants.JAVA_MAX_ARRAY_SIZE)
            throw new UnsupportedOperationException("Cannot handle more than " + Constants.JAVA_MAX_ARRAY_SIZE + " hits in a single doc");
        final int n = (int)(end - start);
        if (n == 0)
            return new int[0][];
        int[] startsOfSnippets = new int[n];
        int[] endsOfSnippets = new int[n];

        EphemeralHit hit = new EphemeralHit();
        for (long i = start; i < end; ++i) {
            hits.getEphemeral(i, hit);
            int j = (int)(i - start);
            contextSize.getSnippetStartEnd(hit, matchInfoDefs, false, startsOfSnippets, j, endsOfSnippets, j);
        }

        int fiNumber = 0;
        int doc = hits.doc(start);
        int[][] contexts = new int[n][];
        LeafReaderContext lrc = hits.index().getLeafReaderContext(doc);
        for (AnnotationForwardIndex forwardIndex: contextSources) {
            if (forwardIndex == null)
                throw new IllegalArgumentException("Cannot get context from without a forward index");
            // Get all the words from the forward index
            String luceneField = forwardIndex.annotation().forwardIndexSensitivity().luceneField();
            List<int[]> words = BlackLabIndexIntegrated.forwardIndex(lrc)
                    .retrieveParts(luceneField, doc - lrc.docBase, startsOfSnippets, endsOfSnippets);

            // Build the actual concordances
//            int hitNum = 0;
            for (int i = 0; i < n; ++i) {
                long hitIndex = start + i;
                int[] theseWords = words.get(i);
                hits.getEphemeral(hitIndex, hit);

                int firstWordIndex = startsOfSnippets[i];

                if (fiNumber == 0) {
                    // Allocate context array and set hit and right start and context length
                    contexts[i] = new int[NUMBER_OF_BOOKKEEPING_INTS
                            + theseWords.length * contextSources.size()];
                    // Math.min() so we don't go beyond actually retrieved snippet (which may have been limited because of config)!
                    contexts[i][HIT_START_INDEX] = Math.min(theseWords.length, hit.start() - firstWordIndex);
                    contexts[i][AFTER_START_INDEX] = Math.min(theseWords.length, hit.end() - firstWordIndex);
                    contexts[i][LENGTH_INDEX] = theseWords.length;
                }
                // Copy the context we just retrieved into the context array
                int copyStart = fiNumber * theseWords.length + NUMBER_OF_BOOKKEEPING_INTS;
                System.arraycopy(theseWords, 0, contexts[i], copyStart, theseWords.length);
            }

            fiNumber++;
        }
        return contexts;
    }

    /**
     * Retrieve context words for the hits.
     *
     * NOTE: because we work with term ids, which are segment-local, this
     * method can only be used for hits in a single segment.
     *
     * @param hits hits to find contexts for
     * @param annotations the field and annotations to use for the context
     * @param contextSize how large the contexts need to be
     */
    private static BigList<int[]> getContextsSingleSegment(HitsSimple hits, List<Annotation> annotations, ContextSize contextSize) {
        if (annotations == null || annotations.isEmpty())
            throw new IllegalArgumentException("Cannot build contexts without annotations");

        List<AnnotationForwardIndex> fis = new ArrayList<>();
        for (Annotation annotation: annotations) {
            fis.add(hits.index().annotationForwardIndex(annotation));
        }

        // Get the context
        // Group hits per document

        // setup first iteration
        final long size = hits.size(); // TODO ugly, might be slow because of required locking
        int prevDoc = size == 0 ? -1 : hits.doc(0);
        int firstHitInCurrentDoc = 0;

        /*
         * The hit contexts.
         *
         * There may be multiple contexts for each hit. Each
         * int array starts with three bookkeeping integers, followed by the contexts
         * information. The bookkeeping integers are:
         * 0 = hit start, index of the hit word (and length of the context before the hit), counted from the start of the context
         * 1 = after start, start of the context after the hit, counted from the start the context
         * 2 = context length, length of 1 context. As stated above, there may be multiple contexts.
         *
         * The first context therefore starts at index 3.
         */
        BigList<int[]> contexts = new ObjectBigArrayBigList<>(hits.size());

        MatchInfoDefs matchInfoDefs = hits.matchInfoDefs();
        if (size > 0) {
            for (int i = 1; i < size; ++i) { // start at 1: variables already have correct values for primed for hit 0
                final int curDoc = hits.doc(i);
                if (curDoc != prevDoc) {
                    try { ThreadAborter.checkAbort(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new InterruptedSearch(e); }
                    // Process hits in preceding document:
                    int[][] docContextArray = getContextWordsSingleDocument(hits, firstHitInCurrentDoc, i, contextSize, fis, matchInfoDefs);
                    Collections.addAll(contexts, docContextArray);
                    // start a new document
                    prevDoc = curDoc;
                    firstHitInCurrentDoc = i;
                }
            }
            // Process hits in final document
            int[][] docContextArray = getContextWordsSingleDocument(hits, firstHitInCurrentDoc, hits.size(), contextSize, fis, matchInfoDefs);
            Collections.addAll(contexts, docContextArray);
        }
        return contexts;
    }

    /**
     * Retrieve context words for the hits.
     *
     * NOTE: because we work with term ids, which are segment-local, this
     * method can only be used for hits in a single segment.
     *
     * @param hits hits to find contexts for
     * @param annotation the field and annotations to use for the context
     * @param contextSize how large the contexts need to be
     */
    private static BigList<String[]> getCollocations(HitsSimple hits, Annotation annotation, ContextSize contextSize) {
        if (annotation == null)
            throw new IllegalArgumentException("Cannot build contexts without annotations");

        AnnotationForwardIndex fi = hits.index().annotationForwardIndex(annotation);

        // Get the context
        // Group hits per document

        // setup first iteration
        final long size = hits.size(); // TODO ugly, might be slow because of required locking
        int prevDoc = size == 0 ? -1 : hits.doc(0);
        int firstHitInCurrentDoc = 0;

        /*
         * The hit contexts.
         *
         * There may be multiple contexts for each hit. Each
         * int array starts with three bookkeeping integers, followed by the contexts
         * information. The bookkeeping integers are:
         * 0 = hit start, index of the hit word (and length of the context before the hit), counted from the start of the context
         * 1 = right start, start of the context after the hit, counted from the start the context
         * 2 = context length, length of 1 context. As stated above, there may be multiple contexts.
         *
         * The first context therefore starts at index 3.
         */
        BigList<String[]> contexts = new ObjectBigArrayBigList<>(hits.size());

        MatchInfoDefs matchInfoDefs = hits.matchInfoDefs();
        if (size > 0) {
            for (int i = 1; i < size; ++i) { // start at 1: variables already have correct values for primed for hit 0
                final int curDoc = hits.doc(i);
                if (curDoc != prevDoc) {
                    try { ThreadAborter.checkAbort(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new InterruptedSearch(e); }
                    // Process hits in preceding document:
                    String[][] docContextArray = getCollocationWordsSingleDocumentStrings(hits, firstHitInCurrentDoc, i, contextSize, fi, matchInfoDefs);
                    Collections.addAll(contexts, docContextArray);
                    // start a new document
                    prevDoc = curDoc;
                    firstHitInCurrentDoc = i;
                }
            }
            // Process hits in final document
            String[][] docContextArray = getCollocationWordsSingleDocumentStrings(hits, firstHitInCurrentDoc, hits.size(), contextSize, fi, matchInfoDefs);
            Collections.addAll(contexts, docContextArray);
        }
        return contexts;
    }

    private static String[][] getCollocationWordsSingleDocumentStrings(HitsSimple hits, long start, long end, ContextSize contextSize, AnnotationForwardIndex contextSource, MatchInfoDefs matchInfoDefs) {
        int[][] contexts = getContextWordsSingleDocument(hits, start, end, contextSize, List.of(contextSource), matchInfoDefs);
        if (contextSize.isInlineTag())
            throw new IllegalArgumentException("Cannot build contexts with inline tags");
        String[][] stringContexts = new String[contexts.length][];
        int doc = hits.doc(start);
        LeafReaderContext lrc = hits.index().getLeafReaderContext(doc);
        ForwardIndexSegmentReader fi = BlackLabIndexIntegrated.forwardIndex(lrc);
        TermsSegmentReader terms = fi.terms(contextSource.annotation().forwardIndexSensitivity().luceneField());
        for (int j = 0; j < contexts.length; j++) {
            int[] context = contexts[j];
            int beforeContextLength = context[HIT_START_INDEX];
            int afterHitIndex = context[AFTER_START_INDEX];
            int afterContextLength = context[LENGTH_INDEX] - afterHitIndex;
            int n = Math.min(contextSize.before(), beforeContextLength) +
                    Math.min(contextSize.after(), afterContextLength);
            stringContexts[j] = new String[n];
            for (int k = 0; k < beforeContextLength; k++) {
                stringContexts[j][k] = terms.get(context[NUMBER_OF_BOOKKEEPING_INTS + k]);
            }
            for (int k = 0; k < afterContextLength; k++) {
                stringContexts[j][k + beforeContextLength] = terms.get(context[NUMBER_OF_BOOKKEEPING_INTS + afterHitIndex + k]);
            }
        }
        return stringContexts;
    }

    /**
     * Count occurrences of context words around hit.
     *
     * @param hits hits to get collocations for
     * @param annotation annotation to use for the collocations, or null if default
     * @param contextSize how many words around hits to use
     * @param sensitivity what sensitivity to use
     * @param sort whether or not to sort the list by descending frequency
     *
     * @return the frequency of each occurring token
     */
    public static synchronized TermFrequencyList collocations(Hits hits, Annotation annotation, ContextSize contextSize, MatchSensitivity sensitivity, boolean sort) {
        BlackLabIndex index = hits.index();
        if (annotation == null)
            annotation = index.mainAnnotatedField().mainAnnotation();
        if (contextSize == null)
            contextSize = index.defaultContextSize();
        if (sensitivity == null)
            sensitivity = annotation.sensitivity(index.defaultMatchSensitivity()).sensitivity();

        Iterable<String[]> contexts = getCollocations(hits.getHits().getStatic(), annotation, contextSize);
        Map<String, Integer> countPerWord = new HashMap<>();
        for (String[] context: contexts) {
            // Count words
            for (String s: context) {
                countPerWord.compute(s, (k, v) -> v == null ? 1 : v + 1);
            }
        }

        // Get the actual words from the sort positions
        Map<String, Integer> wordFreq = new HashMap<>();
        for (Map.Entry<String, Integer> e : countPerWord.entrySet()) {
            int count = e.getValue();
            String word = sensitivity.desensitize(e.getKey());
            // Note that multiple ids may map to the same word (because of sensitivity settings)
            // Here, those groups are merged.
            wordFreq.compute(word, (k, v) -> v == null ? count : v + count);
        }

        // Transfer from map to list
        return new TermFrequencyList(hits.queryInfo(), wordFreq, sort);
    }

}
