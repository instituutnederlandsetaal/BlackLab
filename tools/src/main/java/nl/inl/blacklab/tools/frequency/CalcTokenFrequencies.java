package nl.inl.blacklab.tools.frequency;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.FieldForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLabIndexAbstract;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.util.BlockTimer;

/**
 * More optimized version of HitGroupsTokenFrequencies.
 *
 * Takes shortcuts to be able to process huge corpora without
 * running out of memory, at the expense of genericity.
 *
 * Major changes:
 * - store metadata values as strings, not PropertyValue
 * - always group on annotations first, then metadata fields
 * - don't create HitGroups, return Map with counts directly
 * - don't check if we exceed maxHitsToCount
 * - always process all documents (no document filter query)
 * - return sorted map, so we can perform sub-groupings and merge them later
 *   (uses ConcurrentSkipListMap, or alternatively wraps a TreeMap at the end;
 *    note that using ConcurrentSkipListMap has consequences for the compute() method, see there)
 */
@SuppressWarnings("DuplicatedCode") // see above
class CalcTokenFrequencies {

    private CalcTokenFrequencies() {
    }

    /**
     * Get the token frequencies for the given query and hit property.
     *
     * @param annotations annotations to group on
     * @param metadataFields metadata fields to group on
     * @param segmentDocIds (segment-local) document ids to process
     * @param ngramSize size of ngrams to group on (1 for single tokens
     */
    public static Map<GroupIdHash, OccurrenceCounts> get(
            LeafReaderContext lrc,
            List<Annotation> annotations,
            List<String> metadataFields,
            List<Integer> segmentDocIds,
            int ngramSize
        ) {

        /*
         * Document properties that are used in the grouping. (e.g. for query "all tokens, grouped by lemma + document year", will contain DocProperty("document year")
         * This is not necessarily limited to just metadata, can also contain any other DocProperties such as document ID, document length, etc.
         */

        // Token properties that need to be grouped on, with sensitivity (case-sensitive grouping or not) and Terms
        final List<AnnotInfo> hitProperties = annotations.stream()
                .map(ann -> new AnnotInfo(ann, MatchSensitivity.INSENSITIVE))
                .toList();
        final List<String> docProperties = new ArrayList<>(metadataFields);

        final int numAnnotations = hitProperties.size();

        // Keep track of term occurrences in this segment; later we'll merge it with the global term frequencies
        Map<GroupIdHash, OccurrenceCounts> occsInSegment = new HashMap<>();

        try (final BlockTimer c = BlockTimer.create("Top Level")) {

            // Start actually calculating the requests frequencies.

            // We do have hit properties, so we need to use both document metadata and the tokens from the forward index to
            // calculate the frequencies.
            //final IntUnaryOperator incrementUntilMax = (v) -> v < maxHitsToCount ? v + 1 : v;
            final String fieldName = annotations.get(0).field().name();
            final String lengthTokensFieldName = AnnotatedFieldNameUtil.lengthTokensField(fieldName);

            // Determine all the fields we want to be able to load, so we don't need to load the entire document
            final Set<String> fieldsToLoad = new HashSet<>();
            fieldsToLoad.add(lengthTokensFieldName);
            fieldsToLoad.addAll(metadataFields);

            for (int segmentDocId: segmentDocIds) {
                int globalDocId = segmentDocId + lrc.docBase;
                Map<GroupIdHash, OccurrenceCounts> occsInDoc = new HashMap<>();

                try {

                    // Step 1: read all values for the to-be-grouped annotations for this document
                    // This will create one int[] for every annotation, containing ids that map to the values for this document for this annotation

                    final Document doc = lrc.reader().storedFields().document(segmentDocId, fieldsToLoad);
                    final List<int[]> tokenValuesPerAnnotation = new ArrayList<>();
                    final List<int[]> sortValuesPerAnnotation = new ArrayList<>();

                    try (BlockTimer ignored = c.child("Read annotations from forward index")) {
                        for (AnnotInfo annot : hitProperties) {
                            String luceneField = annot.annotation().forwardIndexSensitivity().luceneField();
                            AnnotationForwardIndex forwardIndex = FieldForwardIndex.get(lrc, luceneField);
                            final int[] tokenValues = forwardIndex.retrieveParts(globalDocId - lrc.docBase,
                                            new int[] { -1 }, new int[] { -1 })[0];
                            Terms segmentTerms = forwardIndex.terms();
                            tokenValuesPerAnnotation.add(tokenValues);

                            // Look up sort values
                            // NOTE: tried moving this to a TermsReader.arrayOfIdsToSortPosition() method,
                            //       but that was slower...
                            int docLength = tokenValues.length;
                            int[] sortValues = new int[docLength];
                            for (int tokenIndex = 0; tokenIndex < docLength; ++tokenIndex) {
                                final int segmentTermId = tokenValues[tokenIndex];
                                sortValues[tokenIndex] = segmentTerms.idToSortPosition(segmentTermId,
                                        annot.matchSensitivity());
                            }
                            sortValuesPerAnnotation.add(sortValues);
                        }

                    }

                    // Step 2: retrieve the to-be-grouped metadata for this document
                    int docLength = Integer.parseInt(doc.get(lengthTokensFieldName)) - BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN;
                    //final DocResult synthesizedDocResult = DocResult.fromDoc(queryInfo, new PropertyValueDoc(queryInfo.index(), docId), 0, docLength);
                    final String[] metadataValuesForGroup = !docProperties.isEmpty() ? new String[docProperties.size()] : null;
                    for (int i = 0; i < docProperties.size(); ++i)
                        metadataValuesForGroup[i] = doc.get(docProperties.get(i));
                    // precompute, it's the same for all hits in document
                    final int metadataValuesHash = Arrays.hashCode(metadataValuesForGroup);

                    // now we have all values for all relevant annotations for this document
                    // iterate again and pair up the nth entries for all annotations, then store that as a group.

                    try (BlockTimer ignored = c.child("Group tokens")) {
                        // We can't get an ngram for the last ngramSize-1 tokens
                        for (int tokenIndex = 0; tokenIndex < docLength - (ngramSize - 1); ++ tokenIndex) {
                            int[] annotationValuesForThisToken = new int[numAnnotations * ngramSize];
                            int[] sortPositions = new int[numAnnotations * ngramSize];

                            // Unfortunate fact: token ids are case-sensitive, and in order to group on a token's values case and diacritics insensitively,
                            // we need to actually group by their "sort positions" - which is just the index the term would have if all terms would have been sorted
                            // so in essence it's also an "id", but a case-insensitive one.
                            // we could further optimize to not do this step when grouping sensitively by making a specialized instance of the GroupIdHash class
                            // that hashes the token ids instead of the sortpositions in that case.
                            for (int annotationIndex = 0, arrIndex = 0; annotationIndex < numAnnotations; ++annotationIndex, arrIndex += ngramSize) {
                                // get array slices of ngramSize
                                int[] tokenValues = tokenValuesPerAnnotation.get(annotationIndex);
                                System.arraycopy(tokenValues, tokenIndex, annotationValuesForThisToken, arrIndex, ngramSize);
                                int[] sortValuesThisAnnotation = sortValuesPerAnnotation.get(annotationIndex);
                                System.arraycopy(sortValuesThisAnnotation, tokenIndex, sortPositions, arrIndex, ngramSize);
                            }
                            final GroupIdHash groupId = new GroupIdHash(ngramSize, annotationValuesForThisToken, sortPositions, metadataValuesForGroup, metadataValuesHash);

                            // Count occurrence in this doc
                            occsInDoc.compute(groupId, (__, occurrenceInDoc) -> {
                                if (occurrenceInDoc == null) {
                                    occurrenceInDoc = new OccurrenceCounts(1, 1);
                                } else {
                                    occurrenceInDoc.hits++;
                                }
                                return occurrenceInDoc;
                            });
                        }
                    }
                } catch (IOException e) {
                    throw BlackLabException.wrapRuntime(e);
                }
                // Merge occurrences in this doc with segment occurrences
                occsInDoc.forEach((groupId, occurrenceInDocument) ->
                    occsInSegment.compute(groupId,
                        (__, segmentGroup) -> {
                            if (segmentGroup != null) {
                                // Merge local & global counts
                                segmentGroup.hits += occurrenceInDocument.hits;
                                segmentGroup.docs += occurrenceInDocument.docs;
                                return segmentGroup;
                            } else {
                                return occurrenceInDocument; // first time we found this group.
                            }
                        }));
            }
        }
        return occsInSegment;
    }
}
