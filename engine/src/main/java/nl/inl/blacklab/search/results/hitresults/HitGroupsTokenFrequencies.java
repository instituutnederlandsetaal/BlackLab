package nl.inl.blacklab.search.results.hitresults;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.SimpleCollector;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.FieldForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.DocPropertyAnnotatedFieldLength;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyHitText;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.PropertyValueContextWords;
import nl.inl.blacklab.resultproperty.PropertyValueDoc;
import nl.inl.blacklab.resultproperty.PropertyValueMultiple;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexAbstract;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SearchSettings;
import nl.inl.blacklab.search.results.docs.DocResult;
import nl.inl.blacklab.search.results.stats.MaxStats;
import nl.inl.blacklab.search.results.stats.ResultsStats;
import nl.inl.blacklab.search.results.stats.ResultsStatsSaved;
import nl.inl.blacklab.searches.SearchHits;
import nl.inl.util.BlockTimer;

/**
 * Determine token frequencies for (a subset of) a corpus, given a HitProperty to group on.
 *
 * Allows us to e.g. find lemma frequencies, or lemma frequencies per year.
 * This implementation is faster than finding all hits, then grouping those.
 */
public class HitGroupsTokenFrequencies {

    private static final Logger logger = LogManager.getLogger(HitGroupsTokenFrequencies.class);

    private HitGroupsTokenFrequencies() {
    }

    /** Precalculated hashcode for group id, to save time while grouping and sorting. */
    private static class GroupIdHash {
        private final int[] tokenIds;

        private final int[] tokenSortPositions;

        private final PropertyValue[] metadataValues;

        /** Precalculated hash code. */
        private final int hash;

        /**
         *
         * @param tokenIds token term id for each token in the group id
         * @param tokenSortPositions sort position for each token in the group id
         * @param metadataValues relevant metadatavalues
         * @param metadataValuesHash since many tokens per document, precalculate md hash for that thing
         */
        public GroupIdHash(int[] tokenIds, int[] tokenSortPositions, PropertyValue[] metadataValues, int metadataValuesHash) {
            this.tokenIds = tokenIds;
            this.tokenSortPositions = tokenSortPositions;
            this.metadataValues = metadataValues;
            hash = Arrays.hashCode(this.tokenSortPositions) ^ metadataValuesHash;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        // Assume only called with other instances of IdHash (faster for large groupings)
        @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
        @Override
        public boolean equals(Object obj) {
            if (((GroupIdHash) obj).hash != this.hash)
                return false;
            if (!Arrays.deepEquals(((GroupIdHash) obj).metadataValues, this.metadataValues))
                return false;
            return Arrays.equals(((GroupIdHash) obj).tokenSortPositions, this.tokenSortPositions);
        }

        public GroupIdHash toGlobalTermIds(LeafReaderContext lrc, List<AnnotInfo> hitProperties) {
            int[] globalTermIds = new int[tokenIds.length];
            int[] globalSortPositions = new int[tokenIds.length];
            for (int i = 0; i < tokenIds.length; i++) {
                // Convert segment-local term ids to global term ids
                // (this is necessary because the same term can have different ids in different segments)
                AnnotInfo hitProp = hitProperties.get(i);
                Terms globalTerms = hitProp.getTerms();
                globalTermIds[i] = globalTerms.toGlobalTermId(lrc, tokenIds[i]);
                globalSortPositions[i] = globalTerms.idToSortPosition(globalTermIds[i],
                        hitProp.getMatchSensitivity());
            }
            return new GroupIdHash(globalTermIds, globalSortPositions,
                    metadataValues, Arrays.hashCode(metadataValues));
        }
    }

    /**
     * Can the given grouping request be answered using the faster codepath in this classs?
     *
     * If not, it will be handled by {@link HitGroups}, see {@link nl.inl.blacklab.searches.SearchHitGroupsFromHits}.
     *
     * @param mustStoreHits do we need stored hits? if so, we can't use this path
     * @param hitsSearch hits search to group. Must be any token query
     * @param property property to group on. Must consist of DocProperties or HitPropertyHitText
     * @return true if this path can be used
     */
    public static boolean canUse(boolean mustStoreHits, SearchHits hitsSearch, HitProperty property) {
        return !mustStoreHits && hitsSearch.isAnyTokenQuery() && property.isDocPropOrHitText();
    }

    /** Counts of hits and docs while grouping. */
    private static final class OccurrenceCounts {
        // volatile just to be safe, as these objects are at times added together from different threads.
        public volatile long hits;
        public volatile int docs;

        public OccurrenceCounts(long hits, int docs) {
            this.hits = hits;
            this.docs = docs;
        }
    }

    /**
     * Info about doc and hit properties while grouping.
     */
    private record PropInfo(boolean docProperty, int indexInList) {
        public static PropInfo doc(int index) {
            return new PropInfo(true, index);
        }
        public static PropInfo hit(int index) {
            return new PropInfo(false, index);
        }
    }

    /** Info about an annotation we're grouping on. */
    private static final class AnnotInfo {
        private final Annotation annotation;

        private final MatchSensitivity matchSensitivity;

        private final Terms terms;

        public Annotation getAnnotation() {
            return annotation;
        }

        public MatchSensitivity getMatchSensitivity() {
            return matchSensitivity;
        }

        public Terms getTerms() {
            return terms;
        }

        public AnnotInfo(Annotation annotation, MatchSensitivity matchSensitivity, Terms terms) {
            this.annotation = annotation;
            this.matchSensitivity = matchSensitivity;
            this.terms = terms;
        }
    }

    /**
     * Get the token frequencies for the given query and hit property.
     *
     * @param source query to find token frequencies for
     * @param requestedGroupingProperty what to group on
     * @return token frequencies
     */
    public static HitGroups get(SearchHits source, HitProperty requestedGroupingProperty) {

        QueryInfo queryInfo = source.queryInfo();
        Query filterQuery = source.getFilterQuery();
        SearchSettings searchSettings = source.searchSettings();

        try {
            // This is where we store our groups while we're computing/gathering them. Maps from group Id to number of hits and number of docs
            final ConcurrentHashMap<GroupIdHash, OccurrenceCounts> globalOccurrences = new ConcurrentHashMap<>();

            final BlackLabIndex index = queryInfo.index();

            /*
             * Document properties that are used in the grouping. (e.g. for query "all tokens, grouped by lemma + document year", will contain DocProperty("document year")
             * This is not necessarily limited to just metadata, can also contain any other DocProperties such as document ID, document length, etc.
             */
            final List<DocProperty> docProperties = new ArrayList<>();

            // Token properties that need to be grouped on, with sensitivity (case-sensitive grouping or not) and Terms */
            final List<AnnotInfo> hitProperties = new ArrayList<>();

            /*
             * Stores the original index every (doc|hit)property has in the original interleaved/intertwined list.
             * The requestedGroupingProperty sometimes represents more than one property (in the form of HitPropertyMultiple) such as 3 properties: [token text, document year, token lemma]
             * The groups always get an id that is (roughly) the concatenation of the properties (in the example case [token text, document year, token lemma]),
             * and it's important this id contains the respective values in the same order.
             * We need to keep this list because otherwise we'd potentially change the order.
             *
             * Integer contains index in the source list (docProperties or hitProperties, from just above)
             * Boolean is true when origin list was docProperties, false for hitProperties.
             */
            final List<PropInfo> originalOrderOfUnpackedProperties = new ArrayList<>();

            // Unpack the requestedGroupingProperty into its constituents and sort those into the appropriate categories: hit and doc properties.
            {
                List<HitProperty> props = requestedGroupingProperty.propsList();
                for (HitProperty p : props) {
                    final DocProperty asDocPropIfApplicable = p.docPropsOnly();
                    if (asDocPropIfApplicable != null) { // property can be converted to docProperty (applies to the document instead of the token/hit)
                        if (asDocPropIfApplicable.isCompound()) {
                            throw new IllegalStateException("Nested PropertyMultiples detected, should never happen");
                        }
                        final int positionInUnpackedList = docProperties.size();
                        docProperties.add(asDocPropIfApplicable);
                        originalOrderOfUnpackedProperties.add(PropInfo.doc(positionInUnpackedList));
                    } else { // Property couldn't be converted to DocProperty (is null). The current property is an actual HitProperty (applies to annotation/token/hit value)
                        assert p instanceof HitPropertyHitText : "HitProperty should be HitPropertyHitText, should never happen";
                        Annotation annotation = ((HitPropertyHitText)p).getAnnotation();
                        final int positionInUnpackedList = hitProperties.size();
                        final MatchSensitivity sensitivity = ((HitPropertyHitText) p).getSensitivity();
                        Terms terms = index.forwardIndex(annotation).terms();
                        hitProperties.add(new AnnotInfo(annotation, sensitivity, terms));
                        originalOrderOfUnpackedProperties.add(PropInfo.hit(positionInUnpackedList));
                    }
                }
            }

            final int numAnnotations = hitProperties.size();
            long numberOfDocsProcessed;
            final AtomicLong numberOfHitsProcessed = new AtomicLong();
            final AtomicBoolean hitMaxHitsToCount = new AtomicBoolean(false);

            try (final BlockTimer c = BlockTimer.create("Top Level")) {

                // Collect all doc ids that match the given filter (or all docs if no filter specified)
                final Map<LeafReaderContext, List<Integer>> docIds = new HashMap<>();
                try (BlockTimer ignored = c.child("Gathering documents")) {
                    index.searcher().search(filterQuery == null ? index.getAllRealDocsQuery() : filterQuery, new SimpleCollector() {
                        private LeafReaderContext context;

                        @Override
                        protected void doSetNextReader(LeafReaderContext context) throws IOException {
                            this.context = context;
                            super.doSetNextReader(context);
                        }

                        @Override
                        public void collect(int segmentDocId) {
                            docIds.compute(context, (k, v) -> {
                                if (v == null)
                                    v = new ArrayList<>();
                                v.add(segmentDocId);
                                return v;
                            });
                        }

                        @Override
                        public ScoreMode scoreMode() {
                            return ScoreMode.COMPLETE_NO_SCORES;
                        }
                    });
                }

                // Start actually calculating the requests frequencies.
                if (hitProperties.isEmpty()) {
                    // Matched all tokens but not grouping by a specific annotation, only metadata
                    // This requires a different approach because we never retrieve the individual tokens if there's no annotation
                    // e.g. match '*' group by document year --
                    // What we do instead is for every document just retrieve how many tokens it contains (from its metadata), and add that count to the appropriate group
                    numberOfDocsProcessed = docIds.values().stream().map(List::size).reduce(0, Integer::sum);
                    try (BlockTimer ignored = c.child("Grouping documents (metadata only path)")) {
                        String fieldName = queryInfo.field().name();
                        DocPropertyAnnotatedFieldLength fieldLength = new DocPropertyAnnotatedFieldLength(index, fieldName);
                        final int[] emptyTokenValuesArray = new int[0];

                        docIds.entrySet().parallelStream().forEach(entry -> {
                            LeafReaderContext lrc = entry.getKey();
                            List<Integer> docIdsInSegment = entry.getValue();
                            for (int docId: docIdsInSegment) {
                                int globalDocId = docId + lrc.docBase;
                                final int docLength = (int) fieldLength.get(globalDocId); // excludes dummy closing token!
                                final DocResult synthesizedDocResult = DocResult.fromDoc(queryInfo,
                                        new PropertyValueDoc(globalDocId), 0, docLength);
                                final PropertyValue[] metadataValuesForGroup = new PropertyValue[docProperties.size()];
                                for (int i = 0; i < docProperties.size(); ++i) {
                                    metadataValuesForGroup[i] = docProperties.get(i).get(synthesizedDocResult);
                                }
                                // precompute, it's the same for all hits in document
                                final int metadataValuesHash = Arrays.hashCode(metadataValuesForGroup);

                                numberOfHitsProcessed.addAndGet(docLength);

                                // Add all tokens in document to the group.
                                final GroupIdHash groupId = new GroupIdHash(emptyTokenValuesArray,
                                        emptyTokenValuesArray, metadataValuesForGroup, metadataValuesHash);
                                globalOccurrences.compute(groupId, (__, groupSizes) -> {
                                    if (groupSizes != null) {
                                        // Issue #379 (https://github.com/instituutnederlandsetaal/BlackLab/issues/379) discussed the
                                        // thread safety of the below 2 lines. It should be safe because globalOccurrences
                                        // is a ConcurrentHashMap, and its compute() operation is atomic according to the
                                        // documentation:
                                        // https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ConcurrentHashMap.html#compute-K-java.util.function.BiFunction-
                                        groupSizes.hits += docLength;
                                        groupSizes.docs += 1;
                                        return groupSizes;
                                    } else {
                                        return new OccurrenceCounts(docLength, 1);
                                    }
                                });
                            }
                        });
                    }
                } else {
                    // We do have hit properties, so we need to use both document metadata and the tokens from the forward index to
                    // calculate the frequencies.
                    // OPT: maybe we don't need to respect the maxHitsToCount setting here? The whole point of this
                    //       code is that it can perform this operation faster and using less memory, and the setting
                    //       exists to manage server load, so maybe we can ignore it here? I guess then we might need
                    //       another setting that can limit this operation as well.
                    final long maxHitsToCount = searchSettings.maxHitsToCount();
                    //final IntUnaryOperator incrementUntilMax = (v) -> v < maxHitsToCount ? v + 1 : v;
                    final String fieldName = queryInfo.field().name();
                    final String lengthTokensFieldName = AnnotatedFieldNameUtil.lengthTokensField(fieldName);

                    // Determine all the fields we want to be able to load, so we don't need to load the entire document
                    final Set<String> fieldsToLoad = new HashSet<>();
                    fieldsToLoad.add(lengthTokensFieldName);

                    numberOfDocsProcessed = docIds.entrySet().parallelStream().map(entry -> {
                        LeafReaderContext lrc = entry.getKey();
                        List<Integer> segmentDocIds = entry.getValue();

                        // Create a forward index reader for each hit property, so we can retrieve the token values
                        AnnotationForwardIndex[] forwardIndexes = new FieldForwardIndex[hitProperties.size()];
                        int hitPropIndex = 0;
                        for (AnnotInfo annot : hitProperties) {
                            String luceneField = annot.annotation.forwardIndexSensitivity().luceneField();
                            forwardIndexes[hitPropIndex] = FieldForwardIndex.get(lrc, luceneField);
                            hitPropIndex++;
                        }

                        // Keep track of term occurrences in this segment; later we'll merge it with the global term frequencies
                        Map<GroupIdHash, OccurrenceCounts> occsInSegment = new HashMap<>();

                        int docsDone = 0;
                        for (int segmentDocId: segmentDocIds) {
                            int globalDocId = segmentDocId + lrc.docBase;
                            Map<GroupIdHash, OccurrenceCounts> occsInDoc = new HashMap<>();

                            // If we've already exceeded the maximum, skip this doc
                            if (numberOfHitsProcessed.get() >= maxHitsToCount)
                                return docsDone;
                            docsDone++;

                            try {

                                // Step 1: read all values for the to-be-grouped annotations for this document
                                // This will create one int[] for every annotation, containing ids that map to the values for this document for this annotation

                                final Document doc = lrc.reader().storedFields().document(segmentDocId, fieldsToLoad);
                                final List<int[]> tokenValuesPerAnnotation = new ArrayList<>();
                                final List<int[]> sortValuesPerAnnotation = new ArrayList<>();

                                try (BlockTimer ignored = c.child("Read annotations from forward index")) {
                                    hitPropIndex = 0;
                                    for (AnnotInfo annot : hitProperties) {
                                        AnnotationForwardIndex forwardIndex = forwardIndexes[hitPropIndex];
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
                                                    annot.getMatchSensitivity());
                                        }
                                        sortValuesPerAnnotation.add(sortValues);
                                        hitPropIndex++;
                                    }
                                }

                                // Step 2: retrieve the to-be-grouped metadata for this document
                                int docLength = Integer.parseInt(doc.get(lengthTokensFieldName))
                                        - BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN;
                                final DocResult synthesizedDocResult = DocResult.fromDoc(queryInfo,
                                        new PropertyValueDoc(globalDocId), 0, docLength);
                                final PropertyValue[] metadataValuesForGroup = !docProperties.isEmpty() ?
                                        new PropertyValue[docProperties.size()] :
                                        null;
                                for (int i = 0; i < docProperties.size(); ++i)
                                    metadataValuesForGroup[i] = docProperties.get(i).get(synthesizedDocResult);
                                // precompute, it's the same for all hits in document
                                final int metadataValuesHash = Arrays.hashCode(metadataValuesForGroup);

                                // now we have all values for all relevant annotations for this document
                                // iterate again and pair up the nth entries for all annotations, then store that as a group.

                                try (BlockTimer ignored = c.child("Group tokens")) {

                                    for (int tokenIndex = 0; tokenIndex < docLength; ++tokenIndex) {
                                        int[] annotationValuesForThisToken = new int[numAnnotations];
                                        int[] sortPositions = new int[numAnnotations];

                                        // Unfortunate fact: token ids are case-sensitive, and in order to group on a token's values case and diacritics insensitively,
                                        // we need to actually group by their "sort positions" - which is just the index the term would have if all terms would have been sorted
                                        // so in essence it's also an "id", but a case-insensitive one.
                                        // we could further optimize to not do this step when grouping sensitively by making a specialized instance of the GroupIdHash class
                                        // that hashes the token ids instead of the sortpositions in that case.
                                        for (int annotationIndex = 0;
                                             annotationIndex < numAnnotations; ++annotationIndex) {
                                            int[] tokenValues = tokenValuesPerAnnotation.get(annotationIndex);
                                            if (tokenIndex >= tokenValues.length) {
                                                logger.debug("### ERROR");
                                                logger.debug("docLength = " + docLength);
                                                logger.debug("tokenValues.length = " + tokenValues.length);
                                                logger.debug("tokenIndex = " + tokenIndex);
                                                logger.debug("annotationIndex = " + annotationIndex);
                                                logger.debug("annotation = " + (hitProperties.get(annotationIndex)
                                                        .getAnnotation().name()));
                                            }
                                            annotationValuesForThisToken[annotationIndex] = tokenValues[tokenIndex];
                                            int[] sortValuesThisAnnotation = sortValuesPerAnnotation.get(annotationIndex);
                                            sortPositions[annotationIndex] = sortValuesThisAnnotation[tokenIndex];
                                        }
                                        final GroupIdHash groupId = new GroupIdHash(annotationValuesForThisToken,
                                                sortPositions, metadataValuesForGroup, metadataValuesHash);

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

                                    // If we exceeded maxHitsToCount, remember that and don't process more docs.
                                    // (NOTE: we don't care if we don't get exactly maxHitsToCount in this case; just that
                                    //  we stop the operation before the server is overloaded)
                                    if (numberOfHitsProcessed.getAndUpdate(i -> i + docLength) >= maxHitsToCount) {
                                        hitMaxHitsToCount.set(true);
                                    }

                                }
                            } catch (IOException e) {
                                throw BlackLabException.wrapRuntime(e);
                            }
                            // Merge the document occurrences into the segment's occurrences.
                            // (we do this extra step so that the docs count is correct;
                            //  if we do all docs in one map, we don't know when to increment docs)
                            occsInDoc.forEach((groupId, occInDoc) ->
                                    occsInSegment.compute(groupId, (__, occInSeg) -> {
                                if (occInSeg == null) {
                                    // First time this segment we found this group.
                                    occInSeg = occInDoc;
                                } else {
                                    // Merge counts
                                    occInSeg.hits += occInDoc.hits;
                                    occInSeg.docs += occInDoc.docs;
                                }
                                return occInSeg;
                            }));
                        }
                        // Merge occurrences in this doc with global occurrences
                        // (the group ids are converted to their string representation here)
                        occsInSegment.forEach(
                                (groupId, occurrenceInSegment) -> globalOccurrences.compute(groupId.toGlobalTermIds(lrc, hitProperties),
                                        (__, globalGroup) -> {
                                            if (globalGroup != null) {
                                                // Merge local & global counts
                                                globalGroup.hits += occurrenceInSegment.hits;
                                                globalGroup.docs += occurrenceInSegment.docs;
                                                return globalGroup;
                                            } else {
                                                return occurrenceInSegment; // first time we found this group.
                                            }
                                        }));

                        return docsDone;
                    }).reduce(0, Integer::sum);
                    logger.trace("Number of processed docs: " + numberOfDocsProcessed);
                }
            }

            List<HitGroup> groups;
            try (final BlockTimer ignored = BlockTimer.create("Resolve string values for tokens")) {
                final int numMetadataValues = docProperties.size();
                groups = globalOccurrences.entrySet().parallelStream().map(e -> {
                    final long groupSizeHits = e.getValue().hits;
                    final int groupSizeDocs = e.getValue().docs;
                    final int[] annotationValues = e.getKey().tokenIds;
                    final int[] annotationSortValues = e.getKey().tokenSortPositions;
                    final PropertyValue[] metadataValues = e.getKey().metadataValues;
                    // allocate new - is not copied when moving into propertyvaluemultiple
                    final PropertyValue[] groupIdAsList = new PropertyValue[numAnnotations + numMetadataValues];

                    // Convert all raw values (integers) into their appropriate PropertyValues
                    // Taking care to preserve the order of the resultant PropertyValues with the order of the input HitProperties
                    int indexInOutput = 0;
                    for (PropInfo p : originalOrderOfUnpackedProperties) {
                        final int indexInInput = p.indexInList();
                        if (p.docProperty()) {
                            // is docprop, add PropertyValue as-is
                            groupIdAsList[indexInOutput++] = metadataValues[indexInInput];
                        } else {
                             // is hitprop, convert value to PropertyValue.
                            AnnotInfo annotInfo = hitProperties.get(indexInInput);
                            Annotation annot = annotInfo.getAnnotation();
                            MatchSensitivity sens = annotInfo.getMatchSensitivity();
                            Terms terms = annotInfo.getTerms();
                            groupIdAsList[indexInOutput++] = new PropertyValueContextWords(annot, sens, terms,
                                    new int[] {annotationValues[indexInInput]},
                                    new int[] {annotationSortValues[indexInInput]}, false, null);
                        }
                    }

                    PropertyValue groupId = groupIdAsList.length > 1 ? new PropertyValueMultiple(groupIdAsList) : groupIdAsList[0];

                    return HitGroup.withoutResults(queryInfo, groupId, groupSizeHits,
                            groupSizeDocs, MaxStats.NOT_EXCEEDED);
                }).map(g -> (HitGroup)g).toList();
            }
            logger.debug("fast path used for grouping");

            ResultsStats hitsStats = new ResultsStatsSaved(numberOfHitsProcessed.get(), numberOfHitsProcessed.get(), MaxStats.get(hitMaxHitsToCount.get(), hitMaxHitsToCount.get()));
            ResultsStats docsStats = new ResultsStatsSaved((int) numberOfDocsProcessed, (int) numberOfDocsProcessed, MaxStats.get(hitMaxHitsToCount.get(), hitMaxHitsToCount.get()));
            return HitGroups.fromList(queryInfo, groups, requestedGroupingProperty, null, null, hitsStats, docsStats);
        } catch (IOException e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }
}
