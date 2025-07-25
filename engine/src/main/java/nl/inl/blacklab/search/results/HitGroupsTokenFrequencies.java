package nl.inl.blacklab.search.results;

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
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.SimpleCollector;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
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
import nl.inl.blacklab.searches.SearchHits;
import nl.inl.util.BlockTimer;

/**
 * Determine token frequencies for (a subset of) a corpus, given a HitProperty to group on.
 *
 * Allows us to e.g. find lemma frequencies, or lemma frequencies per year.
 * This implementation is faster than finding all hits, then grouping those.
 */
public class HitGroupsTokenFrequencies {

    /** When true, performs extra checking that we never overwrite a previously created group */
    public static final boolean DEBUG_DUPLICATE_GROUPS = false;

    private static final Logger logger = LogManager.getLogger(HitGroupsTokenFrequencies.class);

    private HitGroupsTokenFrequencies() {
    }

    /** Precalculated hashcode for group id, to save time while grouping and sorting. */
    private static class GroupIdHash {
        private final int[] tokenIds;
        private final int[] tokenSortPositions;
        private final PropertyValue[] metadataValues;
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
            hash = Arrays.hashCode(tokenSortPositions) ^ metadataValuesHash;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        // Assume only called with other instances of IdHash (faster for large groupings)
        @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
        @Override
        public boolean equals(Object obj) {
            return ((GroupIdHash) obj).hash == this.hash &&
                   Arrays.equals(((GroupIdHash) obj).tokenSortPositions, this.tokenSortPositions) &&
                   Arrays.deepEquals(((GroupIdHash) obj).metadataValues, this.metadataValues);
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
        private final AnnotationForwardIndex annotationForwardIndex;

        private final MatchSensitivity matchSensitivity;

        private final Terms terms;

        public AnnotationForwardIndex getAnnotationForwardIndex() {
            return annotationForwardIndex;
        }

        public MatchSensitivity getMatchSensitivity() {
            return matchSensitivity;
        }

        public Terms getTerms() {
            return terms;
        }

        public AnnotInfo(AnnotationForwardIndex annotationForwardIndex, MatchSensitivity matchSensitivity) {
            this.annotationForwardIndex = annotationForwardIndex;
            this.matchSensitivity = matchSensitivity;
            this.terms = annotationForwardIndex.terms();
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
                        final AnnotationForwardIndex annotationFI = index.annotationForwardIndex(annotation);
                        final MatchSensitivity sensitivity = ((HitPropertyHitText) p).getSensitivity();
                        hitProperties.add(new AnnotInfo(annotationFI, sensitivity));
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
                final List<Integer> docIds = new ArrayList<>();
                try (BlockTimer ignored = c.child("Gathering documents")) {
                    index.searcher().search(filterQuery == null ? index.getAllRealDocsQuery() : filterQuery, new SimpleCollector() {
                        private int docBase;

                        @Override
                        protected void doSetNextReader(LeafReaderContext context) throws IOException {
                            docBase = context.docBase;
                            super.doSetNextReader(context);
                        }

                        @Override
                        public void collect(int docId) {
                            int globalDocId = docId + docBase;
                            docIds.add(globalDocId);
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
                    numberOfDocsProcessed = docIds.size();
                    try (BlockTimer ignored = c.child("Grouping documents (metadata only path)")) {
                        String fieldName = queryInfo.field().name();
                        DocPropertyAnnotatedFieldLength propTokens = new DocPropertyAnnotatedFieldLength(index, fieldName);
                        final int[] emptyTokenValuesArray = new int[0];

                        docIds.parallelStream().forEach(docId -> {
                            final int docLength = (int) propTokens.get(docId); // already excludes dummy closing token!
                            final DocResult synthesizedDocResult = DocResult.fromDoc(queryInfo, new PropertyValueDoc(
                                    docId), 0, docLength);
                            final PropertyValue[] metadataValuesForGroup = new PropertyValue[docProperties.size()];
                            for (int i = 0; i < docProperties.size(); ++i) { metadataValuesForGroup[i] = docProperties.get(i).get(synthesizedDocResult); }
                            final int metadataValuesHash = Arrays.hashCode(metadataValuesForGroup); // precompute, it's the same for all hits in document

                            numberOfHitsProcessed.addAndGet(docLength);

                            // Add all tokens in document to the group.
                            final GroupIdHash groupId = new GroupIdHash(emptyTokenValuesArray, emptyTokenValuesArray, metadataValuesForGroup, metadataValuesHash);
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
                        });
                    }
                } else {
                    // We do have hit properties, so we need to use both document metadata and the tokens from the forward index to
                    // calculate the frequencies.
                    // OPT: maybe we don't need to respect the maxHitsToCount setting here? The whole point of this
                    //       code is that it can perform this operation faster and using less memory, and the setting
                    //       exists to manage server load, so maybe we can ignore it here? I guess then we might need
                    //       another setting that can limit this operation as well.
                    final long maxHitsToCount = searchSettings.maxHitsToCount() > 0 ? searchSettings.maxHitsToCount() : Long.MAX_VALUE;
                    //final IntUnaryOperator incrementUntilMax = (v) -> v < maxHitsToCount ? v + 1 : v;
                    final String fieldName = queryInfo.field().name();
                    final String lengthTokensFieldName = AnnotatedFieldNameUtil.lengthTokensField(fieldName);

                    // Determine all the fields we want to be able to load, so we don't need to load the entire document
                    final Set<String> fieldsToLoad = new HashSet<>();
                    fieldsToLoad.add(lengthTokensFieldName);

                    final IndexReader reader = index.reader();

                    numberOfDocsProcessed = docIds.parallelStream().filter(docId -> {

                        // If we've already exceeded the maximum, skip this doc
                        if (numberOfHitsProcessed.get() >= maxHitsToCount)
                            return false;

                        try {

                            // Step 1: read all values for the to-be-grouped annotations for this document
                            // This will create one int[] for every annotation, containing ids that map to the values for this document for this annotation

                            final Document doc = reader.document(docId, fieldsToLoad);
                            final List<int[]> tokenValuesPerAnnotation = new ArrayList<>();
                            final List<int[]> sortValuesPerAnnotation = new ArrayList<>();

                            try (BlockTimer ignored = c.child("Read annotations from forward index")) {
                                for (AnnotInfo annot : hitProperties) {
                                    final AnnotationForwardIndex afi = annot.getAnnotationForwardIndex();
                                    final int[] tokenValues = afi.getDocument(docId);
                                    tokenValuesPerAnnotation.add(tokenValues);

                                    // Look up sort values
                                    // NOTE: tried moving this to a TermsReader.arrayOfIdsToSortPosition() method,
                                    //       but that was slower...
                                    int docLength = tokenValues.length;
                                    int[] sortValues = new int[docLength];
                                    for (int tokenIndex = 0; tokenIndex < docLength; ++tokenIndex) {
                                        final int termId = tokenValues[tokenIndex];
                                        sortValues[tokenIndex] = annot.getTerms().idToSortPosition(termId, annot.getMatchSensitivity());
                                    }
                                    sortValuesPerAnnotation.add(sortValues);
                                }

                            }

                            // Step 2: retrieve the to-be-grouped metadata for this document
                            int docLength = Integer.parseInt(doc.get(lengthTokensFieldName)) - BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN;
                            final DocResult synthesizedDocResult = DocResult.fromDoc(queryInfo, new PropertyValueDoc(
                                    docId), 0, docLength);
                            final PropertyValue[] metadataValuesForGroup = !docProperties.isEmpty() ? new PropertyValue[docProperties.size()] : null;
                            for (int i = 0; i < docProperties.size(); ++i)
                                metadataValuesForGroup[i] = docProperties.get(i).get(synthesizedDocResult);
                            final int metadataValuesHash = Arrays.hashCode(metadataValuesForGroup); // precompute, it's the same for all hits in document

                            // now we have all values for all relevant annotations for this document
                            // iterate again and pair up the nth entries for all annotations, then store that as a group.

                            // Keep track of term occurrences in this document; later we'll merge it with the global term frequencies
                            Map<GroupIdHash, OccurrenceCounts> occsInDoc = new HashMap<>();

                            try (BlockTimer ignored = c.child("Group tokens")) {

                                for (int tokenIndex = 0; tokenIndex < docLength; ++ tokenIndex) {
                                    int[] annotationValuesForThisToken = new int[numAnnotations];
                                    int[] sortPositions = new int[numAnnotations];

                                    // Unfortunate fact: token ids are case-sensitive, and in order to group on a token's values case and diacritics insensitively,
                                    // we need to actually group by their "sort positions" - which is just the index the term would have if all terms would have been sorted
                                    // so in essence it's also an "id", but a case-insensitive one.
                                    // we could further optimize to not do this step when grouping sensitively by making a specialized instance of the GroupIdHash class
                                    // that hashes the token ids instead of the sortpositions in that case.
                                    for (int annotationIndex = 0; annotationIndex < numAnnotations; ++annotationIndex) {
                                        int[] tokenValues = tokenValuesPerAnnotation.get(annotationIndex);
                                        if (tokenIndex >= tokenValues.length) {
                                            logger.debug("### ERROR");
                                            logger.debug("docLength = " + docLength);
                                            logger.debug("tokenValues.length = " + tokenValues.length);
                                            logger.debug("tokenIndex = " + tokenIndex);
                                            logger.debug("annotationIndex = " + annotationIndex);
                                            logger.debug("annotation = " + (hitProperties.get(annotationIndex).getAnnotationForwardIndex().annotation().name()));
                                        }
                                        annotationValuesForThisToken[annotationIndex] = tokenValues[tokenIndex];
                                        int[] sortValuesThisAnnotation = sortValuesPerAnnotation.get(annotationIndex);
                                        sortPositions[annotationIndex] = sortValuesThisAnnotation[tokenIndex];
                                    }
                                    final GroupIdHash groupId = new GroupIdHash(annotationValuesForThisToken, sortPositions, metadataValuesForGroup, metadataValuesHash);

                                    // Count occurrence in this doc
                                    OccurrenceCounts occ = occsInDoc.get(groupId);
                                    if (occ == null) {
                                        occ = new OccurrenceCounts(1, 1);
                                        occsInDoc.put(groupId, occ);
                                    } else {
                                        occ.hits++;
                                    }


                                }

                                // Merge occurrences in this doc with global occurrences
                                occsInDoc.forEach((groupId, occurrenceInDocument) -> globalOccurrences.compute(groupId, (__, globalGroup) -> {
                                    if (globalGroup != null) {
                                        // Merge local & global counts
                                        globalGroup.hits += occurrenceInDocument.hits;
                                        globalGroup.docs += occurrenceInDocument.docs;
                                        return globalGroup;
                                    } else {
                                        return occurrenceInDocument; // first time we found this group.
                                    }
                                }));


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
                        return true;
                    }).count();
                    logger.trace("Number of processed docs: " + numberOfDocsProcessed);
                }
            }

            Set<PropertyValue> duplicateGroupsDebug = DEBUG_DUPLICATE_GROUPS ? new HashSet<>() : null;

            List<HitGroup> groups;
            try (final BlockTimer ignored = BlockTimer.create("Resolve string values for tokens")) {
                final int numMetadataValues = docProperties.size();
                groups = globalOccurrences.entrySet().parallelStream().map(e -> {
                    final long groupSizeHits = e.getValue().hits;
                    final int groupSizeDocs = e.getValue().docs;
                    final int[] annotationValues = e.getKey().tokenIds;
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
                            Annotation annot = annotInfo.getAnnotationForwardIndex().annotation();
                            MatchSensitivity sens = annotInfo.getMatchSensitivity();
                            groupIdAsList[indexInOutput++] = new PropertyValueContextWords(index, annot, sens,
                                    new int[] {annotationValues[indexInInput]}, null, false);
                        }
                    }

                    PropertyValue groupId = groupIdAsList.length > 1 ? new PropertyValueMultiple(groupIdAsList) : groupIdAsList[0];
                    if (DEBUG_DUPLICATE_GROUPS) {
                        synchronized (duplicateGroupsDebug) {
                            if (!duplicateGroupsDebug.add(groupId)) {
                                throw new IllegalStateException("Identical groups - should never happen");
                            }
                        }
                    }

                    return new HitGroupWithoutResults(queryInfo, groupId, groupSizeHits, groupSizeDocs, false, false);
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
