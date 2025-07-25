package nl.inl.blacklab.server.lib.results;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.index.InputFormat;
import nl.inl.blacklab.index.InputFormatWithConfig;
import nl.inl.blacklab.index.annotated.AnnotationSensitivities;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.ResultProperty;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.QueryExplanation;
import nl.inl.blacklab.search.TermFrequency;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.AnnotationGroup;
import nl.inl.blacklab.search.indexmetadata.AnnotationGroups;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.Annotations;
import nl.inl.blacklab.search.indexmetadata.CustomProps;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.indexmetadata.MetadataFields;
import nl.inl.blacklab.search.indexmetadata.RelationUtil;
import nl.inl.blacklab.search.indexmetadata.TruncatableFreqList;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.lucene.RelationLikeInfo;
import nl.inl.blacklab.search.lucene.RelationListInfo;
import nl.inl.blacklab.search.lucene.SpanQueryCaptureRelationsBetweenSpans;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.CorpusSize;
import nl.inl.blacklab.search.results.DocGroup;
import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.Group;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.ResultGroups;
import nl.inl.blacklab.search.results.ResultsStats;
import nl.inl.blacklab.search.results.ResultsStatsSaved;
import nl.inl.blacklab.search.results.SampleParameters;
import nl.inl.blacklab.search.results.WindowStats;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternSerializerCql;
import nl.inl.blacklab.searches.SearchCache;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.index.Index;
import nl.inl.blacklab.server.lib.ConcordanceContext;
import nl.inl.blacklab.server.lib.ResultIndexMetadata;
import nl.inl.blacklab.server.lib.SearchTimings;
import nl.inl.blacklab.server.lib.WebserviceParams;
import nl.inl.blacklab.webservice.WebserviceParameter;

/**
 * For serializing BlackLab response objects.
 * <p>
 * Takes a DataStream and an API version to attempt compatibility with.
 */
public class ResponseStreamer {
    /** Root element to use for XML responses. */
    public static final String BLACKLAB_RESPONSE_ROOT_ELEMENT = "blacklabResponse";
    static final Logger logger = LogManager.getLogger(ResponseStreamer.class);

    private static final String KEY_BLACKLAB_BUILD_TIME = "blacklabBuildTime";
    private static final String KEY_BLACKLAB_VERSION = "blacklabVersion";
    private static final String KEY_BLACKLAB_SCM_REVISION = "blacklabScmRevision";

    public static final String KEY_SUMMARY = "summary";
    public static final String KEY_NUMBER_OF_HITS = "numberOfHits";
    public static final String KEY_NUMBER_OF_DOCS = "numberOfDocs";
    public static final String KEY_NUMBER_OF_GROUPS = "numberOfGroups";
    public static final String KEY_LARGEST_GROUP_SIZE = "largestGroupSize";
    public static final String KEY_SUMMARY_RESULTS_STATS = "resultsStats";
    public static final String KEY_TOKENS_IN_MATCHING_DOCUMENTS = "tokensInMatchingDocuments";
    public static final String KEY_SUBCORPUS_SIZE = "subcorpusSize";

    public static final String KEY_JSON = "json";
    public static final String KEY_BCQL = "bcql";
    public static final String KEY_NAME = "name";

    // resultsStats section
    public static final String KEY_STATS_STATUS = "status";
    public static final String KEY_STATS_STOPPED_TOO_MANY = "stoppedBecauseTooMany";
    public static final String KEY_STATS_COUNT_ONLY = "countOnly";
    public static final String KEY_STATS_NUMBER_OF_HITS = "hits";
    public static final String KEY_STATS_NUMBER_OF_DOCS = "documents";
    public static final String KEY_STATS_TIME_MS = "timeMs";

    public static final String STATS_STATUS_WORKING = "working";
    public static final String STATS_STATUS_FINISHED = "finished";

    /** E.g. index size, document size, field size */
    public static final String KEY_COUNT = "count";

    // Docs
    public static final String KEY_DOC_INFO = "docInfo";
    public static final String KEY_DOC_LENGTH_TOKENS = "lengthInTokens"; // v3/4: main field token count (docInfo)
    public static final String KEY_TOKEN_COUNTS = "tokenCounts";         // v4/5: per-field token counts (both)
    public static final String KEY_TOKEN_COUNT_ITEM = "tokenCount"; // XML element name for item in tokenCounts list
    public static final String KEY_DOCUMENT_VERSION_COUNT = "docVersions"; // v3/4/5: was added to aggregate index response in v4
    public static final String KEY_DOC_MAY_VIEW = "mayView";
    public static final String KEY_DOC_PID = "docPid";
    public static final String KEY_DOC_SNIPPET = "snippet";

    // Spans
    public static final String KEY_SPAN_START = "start";
    public static final String KEY_SPAN_END = "end";
    public static final String KEY_MATCHING_PART_OF_HIT = "match";
    public static final String KEY_MATCH_INFOS = "matchInfos";
    private static final String KEY_MATCH_INFO_TYPE = "type";

    // Fields
    public static final String KEY_ANNOTATED_FIELD = "annotatedField";
    public static final String KEY_ANNOTATED_FIELDS = "annotatedFields";
    public static final String KEY_FIELD_NAME = "fieldName";
    public static final String KEY_TARGET_FIELD = "targetField";
    public static final String KEY_OTHER_FIELDS = "otherFields";
    public static final String KEY_FIELD_IS_ANNOTATED = "isAnnotatedField";
    public static final String KEY_VALUES = "values";
    public static final String KEY_VALUE = "value";
    public static final String KEY_VALUE_LIST_COMPLETE = "valueListComplete";
    public static final String KEY_ATTRIBUTES = "attributes";

    // Sampling
    public static final String KEY_SAMPLE_SEED = "sampleSeed";
    public static final String KEY_SAMPLE_PERCENTAGE = "samplePercentage";
    public static final String KEY_SAMPLE_SIZE = "sampleSize";
    public static final String KEY_GROUP_SIZE = "size";

    /** If an attribute has multiple values, they will be joined together in the response using this separator.
     *  We could make this configurable in the future.
     */
    private static final String SEPARATOR_ATTRIBUTE_MULTIPLE_VALUES = "; ";

    /** Key to use for corpus name (indexName/corpusName) */
    public String KEY_CORPUS_NAME;

    /** Key to use for context before hit (left/before) */
    public final String KEY_BEFORE;

    /** Key to use for context after hit (right/after) */
    public final String KEY_AFTER;

    /** counts for index, field, doc, etc. (tokenCount / tokens) */
    public final String KEY_TOKEN_COUNT;

    /** counts for index, field, doc, etc. (documentCount / documents) */
    public final String KEY_DOCUMENT_COUNT;     // v3/4/5: was added to aggregate index response in v4

    /** Key to use for subcorpus size #docs (documents/numberOfDocs) */
    public final String KEY_SUBCORPUS_SIZE_DOCUMENTS;

    public final String KEY_SUBCORPUS_SIZE_TOKENS;

    public final String KEY_NUMBER_OF_TOKENS;

    public final String KEY_PARAMS;

    private final String KEY_HIT_GROUP;

    private final String KEY_DOC_GROUP;

    /** What version of responses to write. */
    private final ApiVersion apiVersion;

    /** Is this the new, incompatible API? (API v5+) */
    private final boolean isNewApi;

    /** DataStream to write to. */
    private final DataStream ds;

    public static ResponseStreamer get(DataStream ds, ApiVersion v) {
        return new ResponseStreamer(ds, v);
    }

    private ResponseStreamer(DataStream ds, ApiVersion v) {
        this.ds = ds;
        this.apiVersion = v;
        isNewApi = apiVersion.getMajor() >= 5;

        // Some keys are changed in API v5.
        if (isNewApi) {
            KEY_BEFORE ="before";
            KEY_AFTER = "after";
            KEY_CORPUS_NAME = "corpusName";
            KEY_SUBCORPUS_SIZE_DOCUMENTS = KEY_STATS_NUMBER_OF_DOCS;
            KEY_HIT_GROUP = "hitGroup";
            KEY_DOC_GROUP = "docGroup";
            KEY_TOKEN_COUNT = "tokens";
            KEY_DOCUMENT_COUNT = KEY_STATS_NUMBER_OF_DOCS;
            KEY_NUMBER_OF_TOKENS = KEY_SUBCORPUS_SIZE_TOKENS = "tokens";
            KEY_PARAMS = "params";
        } else {
            KEY_BEFORE = "left";
            KEY_AFTER = "right";
            KEY_CORPUS_NAME = "indexName";
            KEY_SUBCORPUS_SIZE_DOCUMENTS = KEY_STATS_NUMBER_OF_DOCS;
            KEY_HIT_GROUP = "hitgroup";
            KEY_DOC_GROUP = "docgroup";
            KEY_TOKEN_COUNT = "tokenCount";
            KEY_DOCUMENT_COUNT = "documentCount";
            KEY_NUMBER_OF_TOKENS = KEY_SUBCORPUS_SIZE_TOKENS = "numberOfTokens";
            KEY_PARAMS = "searchParam";
        }
    }

    /**
     * Add info about the current logged-in user (if any) to the response.
     *
     * @param userInfo user info to show
     */
    public void userInfo(ResultUserInfo userInfo, boolean debugMode) {
        ds.startEntry("user").startMap();
        {
            ds.entry("loggedIn", userInfo.isLoggedIn());
            if (userInfo.isLoggedIn())
                ds.entry("id", userInfo.getUserId());
            ds.entry("canCreateIndex", userInfo.canCreateIndex());
            ds.entry("debugMode", debugMode);
        }
        ds.endMap().endEntry();
    }

    private void stringMap(String key, Map<String, String> docFields1) {
        ds.startEntry(key).startMap();
        for (Map.Entry<String, String> e: docFields1.entrySet()) {
            ds.dynEntry(e.getKey(), e.getValue());
        }
        ds.endMap().endEntry();
    }

    /**
     * Stream document information (metadata, contents authorization)
     *
     * @param docInfos infos to write
     */
    public void documentInfos(Map<String, ResultDocInfo> docInfos) {
        ds.startEntry("docInfos").startMap();
        for (Map.Entry<String, ResultDocInfo> e: docInfos.entrySet()) {
            ds.startAttrEntry(KEY_DOC_INFO, "pid", e.getKey());
            {
                documentInfo(e.getValue());
            }
            ds.endAttrEntry();
        }
        ds.endMap().endEntry();
    }

    /**
     * Stream document information (metadata, contents authorization)
     *
     * @param docInfo info to stream
     */
    public void documentInfo(ResultDocInfo docInfo) {
        ds.startMap();
        {
            if (isNewApi) {
                // New API: separate metadata from other info
                ds.startEntry("metadata").startMap();
                documentMetadataEntries(docInfo);
                ds.endMap().endEntry();
            }
            ds.startEntry(KEY_TOKEN_COUNTS).startList();
            for (Map.Entry<String, Integer> entry: docInfo.getLengthInTokensPerField().entrySet()) {
                ds.startItem(KEY_TOKEN_COUNT_ITEM).startMap();
                ds.entry(KEY_FIELD_NAME, entry.getKey());
                ds.entry(KEY_TOKEN_COUNT, entry.getValue());
                ds.endMap().endItem();
            }
            ds.endList().endEntry();
            if (!isNewApi && docInfo.getLengthInTokens() != null)
                ds.dynEntry(KEY_DOC_LENGTH_TOKENS, docInfo.getLengthInTokens());
            ds.dynEntry(KEY_DOC_MAY_VIEW, docInfo.isMayView());
            if (!isNewApi) {
                // Legacy API: has metadata at the top-level
                documentMetadataEntries(docInfo);
            }
        }
        ds.endMap();
    }

    private void documentMetadataEntries(ResultDocInfo docInfo) {
        for (Map.Entry<String, List<String>> e: docInfo.getMetadata().entrySet()) {
            ds.startDynEntry(e.getKey()).startList();
            {
                for (String v: e.getValue()) {
                    ds.item(KEY_VALUE, v);
                }
            }
            ds.endList().endDynEntry();
        }
    }

    public void metadataGroupInfo(Map<String, List<String>> metadataFieldGroups) {
        ds.startEntry("metadataFieldGroups").startList();
        for (Map.Entry<String, List<String>> e: metadataFieldGroups.entrySet()) {
            ds.startItem("metadataFieldGroup").startMap();
            {
                ds.entry(KEY_NAME, e.getKey());
                ds.startEntry("fields").startList();
                for (String field: e.getValue()) {
                    ds.item("field", field);
                }
                ds.endList().endEntry();
            }
            ds.endMap().endItem();
        }
        ds.endList().endEntry();
    }

    public void facets(Map<String, List<Pair<String, Long>>> facetInfo) {
        ds.startMap();
        for (Map.Entry<String, List<Pair<String,  Long>>> e: facetInfo.entrySet()) {
            String facetBy = e.getKey();
            List<Pair<String,  Long>> facetCounts = e.getValue();
            ds.startAttrEntry("facet", KEY_NAME, facetBy).startList();
            for (Pair<String, Long> count : facetCounts) {
                ds.startItem("item").startMap()
                        .entry(KEY_VALUE, count.getLeft())
                        .entry(KEY_GROUP_SIZE, count.getRight())
                        .endMap().endItem();
            }
            ds.endList().endAttrEntry();
        }
        ds.endMap();
    }

    /**
     * Output most of the fields of the search summary.
     *
     * @param summaryFields info for the fields to write
     */
    public void summaryCommonFields(ResultSummaryCommonFields summaryFields) throws BlsException {
        WebserviceParams params = summaryFields.getSearchParam();

        // Include parameters
        ds.startEntry(KEY_PARAMS).startMap();
        for (Map.Entry<WebserviceParameter, String> e: params.getParameters().entrySet()) {
            ds.dynEntry(e.getKey().value(), e.getValue());
        }
        ds.endMap().endEntry();

        Index.IndexStatus indexStatus = summaryFields.getIndexStatus();
        if (indexStatus != null && indexStatus != Index.IndexStatus.AVAILABLE) {
            ds.entry("indexStatus", indexStatus.toString());
        }

        TextPattern textPattern = summaryFields.getTextPattern();
        if (textPattern != null) {
            // Show how pattern was parsed
            ds.startEntry("pattern").startMap();
            if (ds.getType().equals(KEY_JSON)) {
                // Only include the JSON representation in the JSON response
                // (we don't have a proper XML representation (yet) and don't care as much about that response format)
                ds.entry(KEY_JSON, textPattern);
            }
            try {
                ds.entry(KEY_BCQL, TextPatternSerializerCql.serialize(textPattern));
            } catch (Exception e) {
                // some queries cannot be serialized to CQL;
                // that's okay, just leave it out
            }
            AnnotatedField searchField = summaryFields.getSearchField();
            ds.entry(KEY_FIELD_NAME, searchField.name());
            if (!summaryFields.getOtherFields().isEmpty()) {
                ds.startEntry(KEY_OTHER_FIELDS).startList();
                for (AnnotatedField field: summaryFields.getOtherFields()) {
                    ds.item(KEY_FIELD_NAME, field.name());
                }
                ds.endList().endEntry();
            }
            MatchInfoDefs matchInfoDefs = new MatchInfoDefs(summaryFields.getMatchInfoDefs()
                    .currentListFiltered(d -> !d.getName().endsWith(SpanQueryCaptureRelationsBetweenSpans.TAG_MATCHINFO_TARGET_HIT)));
            if (matchInfoDefs.currentSize() > 0) {
                // Report the match infos in the query, their types, and the field they apply to (if different from
                // main field, i.e. for parallel corpora)
                ds.startEntry(KEY_MATCH_INFOS).startMap();
                for (MatchInfo.Def def: matchInfoDefs.currentList()) {
                    ds.startDynEntry(def.getName()).startMap();
                    {
                        ds.entry(KEY_MATCH_INFO_TYPE, def.getType().jsonName());
                        if (def.getField() != searchField)
                            ds.entry(KEY_FIELD_NAME, def.getField().name());
                        if (def.getTargetField() != null && def.getTargetField() != searchField)
                            ds.entry(KEY_TARGET_FIELD, def.getTargetField().name());
                    }
                    ds.endMap().endDynEntry();
                }
                ds.endMap().endEntry();
            }
            ds.endMap().endEntry();
        }

        // Information about hit sampling
        SampleParameters sample = params.sampleSettings();
        if (sample != null) {
            ds.entry(KEY_SAMPLE_SEED, sample.seed());
            if (sample.isPercentage())
                ds.entry(KEY_SAMPLE_PERCENTAGE, Math.round(sample.percentageOfHits() * 100 * 100) / 100.0);
            else
                ds.entry(KEY_SAMPLE_SIZE, sample.numberOfHitsSet());
        }

        if (!isNewApi) {
            // Legacy API: information about search progress
            // (moved to resultsStats in API v5)
            SearchTimings timings = summaryFields.getTimings();
            ds.entry("searchTime", timings.getProcessingTime());
            ds.entry("countTime", timings.getCountTime());

            // Information about grouping operation
            summaryGroupStats(summaryFields.getGroups());
        }

        // Information about our viewing window
        WindowStats window = summaryFields.getWindow();
        if (window != null) {
            summaryWindow(window);
        }
    }

    private void summaryGroupStats(ResultGroups groups) {
        if (groups != null) {
            ds.entry(KEY_NUMBER_OF_GROUPS, groups.size());
            ds.entry(KEY_LARGEST_GROUP_SIZE, groups.largestGroupSize());
        }
    }

    private void summaryWindow(WindowStats window) {
        if (isNewApi) {
            // New API: group related values
            ds.startEntry("resultWindow").startMap();
            {
                ds.entry("firstResult", window.first());
                ds.entry("requestedSize", window.requestedWindowSize());
                ds.entry("actualSize", window.windowSize());
                ds.entry("hasPrevious", window.hasPrevious());
                ds.entry("hasNext", window.hasNext());
            }
            ds.endMap().endEntry();
        } else {
            // Legacy API
            ds.entry("windowFirstResult", window.first())
                    .entry("requestedWindowSize", window.requestedWindowSize())
                    .entry("actualWindowSize", window.windowSize())
                    .entry("windowHasPrevious", window.hasPrevious())
                    .entry("windowHasNext", window.hasNext());
        }
    }

    private void summaryResultsStats(ResultSummaryNumHits result, ResultGroups groups) {
        // Information about the number of hits/docs, and whether there were too many to retrieve/count
        ResultsStats hitsStats = result.getHitsStats();
        long hitsCounted = result.isCountFailed() ? -1 : (result.isWaitForTotal() ? hitsStats.waitUntil().allCounted() : hitsStats.countedSoFar());
        long hitsProcessed = result.isWaitForTotal() ? hitsStats.waitUntil().allProcessed() : hitsStats.processedSoFar();
        ResultsStats docsStats = result.getDocsStats();
        if (docsStats == null)
            docsStats = ResultsStatsSaved.INVALID;
        long docsCounted = result.isCountFailed() ? -1 : (result.isWaitForTotal() ? docsStats.waitUntil().allCounted() : docsStats.countedSoFar());
        long docsProcessed = result.isWaitForTotal() ? docsStats.waitUntil().allProcessed() : docsStats.processedSoFar();

        CorpusSize subcorpusSize = result.getSubcorpusSize();
        if (isNewApi) {
            // New API v5+: group related values
            boolean limitReached = hitsStats.maxStats().hitsProcessedExceededMaximum();
            ds.startEntry(KEY_SUMMARY_RESULTS_STATS).startMap();
            {
                ds.entry(KEY_STATS_STATUS, !hitsStats.done() && !limitReached ? STATS_STATUS_WORKING :
                        STATS_STATUS_FINISHED);
                ds.entry(KEY_STATS_NUMBER_OF_HITS, hitsProcessed);
                ds.entry(KEY_STATS_NUMBER_OF_DOCS, docsProcessed);
                ds.entry(KEY_STATS_TIME_MS, result.getTimings().getProcessingTime());
                if (limitReached) {
                    ds.entry(KEY_STATS_STOPPED_TOO_MANY, limitReached);
                    ds.startEntry(KEY_STATS_COUNT_ONLY).startMap();
                    {
                        ds.entry(KEY_STATS_STATUS, !hitsStats.done() ? STATS_STATUS_WORKING : STATS_STATUS_FINISHED);
                        ds.entry(KEY_STATS_NUMBER_OF_HITS, hitsCounted);
                        ds.entry(KEY_STATS_NUMBER_OF_DOCS, docsCounted);
                        ds.entry(KEY_STATS_TIME_MS, result.getTimings().getCountTime());
                    }
                    ds.endMap().endEntry();
                }
                summaryGroupStats(groups);
                subcorpusSizeStats(subcorpusSize);
            }
            ds.endMap().endEntry();
        } else {
            // Legacy API
            ds.entry("stillCounting", !hitsStats.done());
            ds.entry(KEY_NUMBER_OF_HITS, hitsCounted)
                    .entry("numberOfHitsRetrieved", hitsProcessed)
                    .entry("stoppedCountingHits", hitsStats.maxStats().hitsCountedExceededMaximum())
                    .entry("stoppedRetrievingHits", hitsStats.maxStats().hitsProcessedExceededMaximum());
            ds.entry(KEY_NUMBER_OF_DOCS, docsCounted)
                    .entry("numberOfDocsRetrieved", docsProcessed);
            subcorpusSizeStats(subcorpusSize);
            if (subcorpusSize != null && subcorpusSize.getTotalCount().getTokens() >= 0)
                ds.entry(KEY_TOKENS_IN_MATCHING_DOCUMENTS, subcorpusSize.getTotalCount().getTokens());
        }
    }

    private void summaryTotalTokens(long totalTokens) {
        if (totalTokens >= 0)
            ds.entry(KEY_TOKENS_IN_MATCHING_DOCUMENTS, totalTokens);
    }

    public void summaryNumDocs(ResultSummaryNumDocs result, ResultGroups groups) {
        // Information about the number of hits/docs, and whether there were too many to retrieve/count
        DocResults docResults = result.getDocResults();
        if (isNewApi) {
            // New API v5+: group related values
            ds.startEntry(KEY_SUMMARY_RESULTS_STATS).startMap();
            boolean limitReached = docResults.resultsStats().maxStats().hitsProcessedExceededMaximum();
            {
                ds.entry(KEY_STATS_STATUS, STATS_STATUS_FINISHED);
                ds.entry(KEY_STATS_NUMBER_OF_HITS, docResults.getNumberOfHits());
                ds.entry(KEY_STATS_NUMBER_OF_DOCS, docResults.size());
                ds.entry(KEY_STATS_TIME_MS, result.getTimings().getProcessingTime());
                if (limitReached) {
                    ds.entry(KEY_STATS_STOPPED_TOO_MANY, limitReached);
                    ds.startEntry(KEY_STATS_COUNT_ONLY).startMap();
                    {
                        ds.entry(KEY_STATS_STATUS, STATS_STATUS_FINISHED);
                        ds.entry(KEY_STATS_NUMBER_OF_HITS, docResults.getNumberOfHits());
                        ds.entry(KEY_STATS_NUMBER_OF_DOCS, docResults.size());
                        ds.entry(KEY_STATS_TIME_MS, result.getTimings().getCountTime());
                    }
                    ds.endMap().endEntry();
                }
                summaryGroupStats(groups);
                subcorpusSizeStats(result.getSubcorpusSize());
            }
            ds.endMap().endEntry();
        } else {
            // Legacy API
            ds.entry("stillCounting", false);
            if (result.isViewDocGroup()) {
                // Viewing single group of documents, possibly based on a hits search.
                // group.getResults().getOriginalHits() returns null in this case,
                // so we have to iterate over the DocResults and sum up the hits ourselves.
                long numberOfHits = docResults.getNumberOfHits();
                ds.entry(KEY_NUMBER_OF_HITS, numberOfHits)
                        .entry("numberOfHitsRetrieved", numberOfHits);

            }
            long numberOfDocsRetrieved = docResults.size();
            long numberOfDocsCounted = numberOfDocsRetrieved;
            if (result.isCountFailed())
                numberOfDocsCounted = -1;
            ds.entry(KEY_NUMBER_OF_DOCS, numberOfDocsCounted);
            ds.entry("numberOfDocsRetrieved", numberOfDocsRetrieved);
            subcorpusSizeStats(result.getSubcorpusSize());
        }
    }

    public void subcorpusSizeStats(CorpusSize subcorpusSize) {
        if (subcorpusSize != null) {
            CorpusSize.Count totalCount = subcorpusSize.getTotalCount();
            ds.startEntry(KEY_SUBCORPUS_SIZE).startMap();
            ds.entry(KEY_SUBCORPUS_SIZE_DOCUMENTS, totalCount.getDocuments());
            if (subcorpusSize.getDocumentVersions() > totalCount.getDocuments())
                ds.entry(KEY_DOCUMENT_VERSION_COUNT, subcorpusSize.getDocumentVersions());
            if (totalCount.hasTokenCount())
                ds.entry("tokens", totalCount.getTokens()); // always use "tokens" here
            if (subcorpusSize.getTokensPerField().size() > 1) {
                // Multiple annotated fields. Show size per field.
                ds.startEntry(KEY_ANNOTATED_FIELDS).startList();
                for (Map.Entry<String, CorpusSize.Count> entry : subcorpusSize.getTokensPerField().entrySet()) {
                    ds.startItem(KEY_ANNOTATED_FIELD).startMap();
                    ds.entry(KEY_FIELD_NAME, entry.getKey());
                    ds.entry(KEY_SUBCORPUS_SIZE_DOCUMENTS, entry.getValue().getDocuments());
                    ds.entry("tokens", entry.getValue().getTokens()); // always use "tokens" here
                    ds.endMap().endItem();
                }
                ds.endList().endEntry();
            }
            ds.endMap().endEntry();
        }
    }

    public void listOfHits(ResultListOfHits result) throws BlsException {
        nl.inl.blacklab.server.lib.WebserviceParams params = result.getParams();
        Hits hits = result.getHits();

        ds.startEntry("hits").startList();
        for (Hit hit : hits) {
            ds.startItem("hit");
            {
                String docPid = result.getDocIdToPid().get(hit.doc());
                Map<String, MatchInfo> matchInfos = null;
                if (hits.hasMatchInfo()) {
                    matchInfos = Hits.getMatchInfoMap(hits, hit, params.getOmitEmptyCaptures());
                    if (matchInfos == null && logger != null)
                        logger.warn(
                                "MISSING CAPTURE GROUP: " + docPid + ", query: " + params.getPattern());
                }

                hit(docPid, hit, hits.queryInfo().field(), matchInfos, params.contextSettings().size(), result.getConcordanceContext(),
                        result.getAnnotationsToWrite());
            }
            ds.endItem();
        }
        ds.endList().endEntry();
    }

    private void hit(String docPid, Hit hit, AnnotatedField searchField, Map<String, MatchInfo> matchInfo, ContextSize context, ConcordanceContext concordanceContext,
            Collection<Annotation> annotationsToList) {
        boolean isSnippet = false;

        outputHitOrSnippet(docPid, hit, searchField, matchInfo, context, concordanceContext, annotationsToList,
                isSnippet);
    }

    /**
     * Output a hit (or just a document fragment with no hit in it)
     *
     * @param result hit to output
     */
    public void snippet(ResultDocSnippet result) {
        String docPid = result.getParams().getDocPid();
        Hits hits = result.getHits();
        if (!hits.resultsStats().waitUntil().processedAtLeast(1))
            throw new IllegalStateException("Hit for snippet not found");
        Hit hit = hits.get(0);
        hits = hits.size() > 1 ? hits.window(hit) : hits; // make sure we only have 1 hit
        Map<String, MatchInfo> matchInfo = Hits.getMatchInfoMap(hits, hit, false);
        ContextSize context = result.getContext();
        ConcordanceContext concordanceContext = result.isOrigContent() ?
                ConcordanceContext.concordances(hits.concordances(context, ConcordanceType.CONTENT_STORE)) :
                ConcordanceContext.kwics(hits.kwics(context));
        List<Annotation> annotationsToList = result.getAnnotsToWrite();
        //boolean includeContext = result.isHit(); // i.e. did we specify hitstart/hitend (include context) or
                                                 // wordstart/wordend (no context, just the snippet)
        boolean isSnippet = true;

        AnnotatedField searchField = result.getHits().queryInfo().field();
        outputHitOrSnippet(docPid, hit, searchField, matchInfo, context, concordanceContext, annotationsToList,
                isSnippet);
    }

    private void outputHitOrSnippet(String docPid, Hit hit, AnnotatedField searchField, Map<String, MatchInfo> matchInfos,
            ContextSize context, ConcordanceContext concordanceContext, Collection<Annotation> annotationsToList,
            boolean isSnippet) {
        boolean includeContext = context.inlineTagName() != null || context.before() > 0 || context.after() > 0;
        ds.startMap();
        if (!StringUtils.isEmpty(docPid)) { // (should never be empty..?)
            // Add basic hit info
            ds.entry(KEY_DOC_PID, docPid);
            ds.entry(KEY_SPAN_START, hit.start());
            ds.entry(KEY_SPAN_END, hit.end());
        }

        // If any groups were captured, include them in the response
        // (legacy, replaced by matchInfos)
        if (!isNewApi) {
            Set<Map.Entry<String, MatchInfo>> capturedGroups = filterMatchInfo(matchInfos,
                    m -> m.getType() == MatchInfo.Type.SPAN || m.getType() == MatchInfo.Type.INLINE_TAG);
            if (!capturedGroups.isEmpty()) {
                ds.startEntry("captureGroups").startList();
                for (Map.Entry<String, MatchInfo> capturedGroup: capturedGroups) {
                    ds.startItem("group");
                    legacyCapturedGroup(ds, capturedGroup);
                    ds.endItem();
                }
                ds.endList().endEntry();
            }
        }

        // If there's any match info for this field, include it here
        optMatchInfos(matchInfos, mi -> mi.getField().equals(searchField));

        if (concordanceContext.isConcordances()) {
            // Add concordance from original XML
            Concordance c = concordanceContext.getConcordance(hit);
            if (includeContext) {
                ds.startEntry(KEY_BEFORE).xmlFragment(c.left()).endEntry()
                        .startEntry(KEY_MATCHING_PART_OF_HIT).xmlFragment(c.match()).endEntry()
                        .startEntry(KEY_AFTER).xmlFragment(c.right()).endEntry();
            } else {
                if (isSnippet) {
                    if (isNewApi) {
                        ds.startEntry(KEY_MATCHING_PART_OF_HIT).xmlFragment(c.match()).endEntry();
                    } else {
                        ds.xmlFragment(c.match());
                    }
                } else {
                    ds.startEntry(KEY_MATCHING_PART_OF_HIT).xmlFragment(c.match()).endEntry();
                }
            }
        } else {
            // Add KWIC info
            Kwic c = concordanceContext.getKwic(hit);
            if (includeContext) {
                ds.startEntry(KEY_BEFORE).contextList(c.annotations(), annotationsToList, c.before()).endEntry()
                        .startEntry(KEY_MATCHING_PART_OF_HIT).contextList(c.annotations(), annotationsToList, c.match()).endEntry()
                        .startEntry(KEY_AFTER).contextList(c.annotations(), annotationsToList, c.after()).endEntry();
            } else {
                if (isSnippet) {
                    ds.startEntry(KEY_MATCHING_PART_OF_HIT).contextList(c.annotations(), annotationsToList, c.tokens()).endEntry();
                } else {
                    ds.startEntry(KEY_MATCHING_PART_OF_HIT).contextList(c.annotations(), annotationsToList, c.match()).endEntry();
                }
            }
            Map<AnnotatedField, Kwic> foreignKwics = concordanceContext.getForeignKwics(hit);
            if (foreignKwics != null) {
                ds.startEntry(KEY_OTHER_FIELDS).startMap();
                for (Map.Entry<AnnotatedField, Kwic> e: foreignKwics.entrySet()) {
                    AnnotatedField field = e.getKey();
                    Kwic kwic = e.getValue();
                    ds.startDynEntry(field.name()).startMap();
                    {
                        ds.entry(KEY_SPAN_START, kwic.hitStart() + kwic.fragmentStartInDoc());
                        ds.entry(KEY_SPAN_END, kwic.hitEnd() + kwic.fragmentStartInDoc());
                        optMatchInfos(matchInfos, mi -> mi.getField().equals(field));
                        ds.startEntry(KEY_BEFORE);
                        ds.contextList(kwic.annotations(), annotationsToList, kwic.before());
                        ds.endEntry();
                        ds.startEntry(KEY_MATCHING_PART_OF_HIT);
                        ds.contextList(kwic.annotations(), annotationsToList, kwic.match());
                        ds.endEntry();
                        ds.startEntry(KEY_AFTER);
                        ds.contextList(kwic.annotations(), annotationsToList, kwic.after());
                        ds.endEntry();
                    }
                    ds.endMap().endDynEntry();
                }
                ds.endMap().endEntry();
            }
        }
        ds.endMap();
    }

    private void optMatchInfos(Map<String, MatchInfo> matchInfos, Predicate<MatchInfo> include) {
        Set<Map.Entry<String, MatchInfo>> filtered = matchInfos == null ? Collections.emptySet() :
                matchInfos.entrySet().stream()
                        .filter(e ->
                                // don't include the autogenerated "foreign hit" match infos
                                !e.getKey().endsWith(SpanQueryCaptureRelationsBetweenSpans.TAG_MATCHINFO_TARGET_HIT) &&
                                        // make sure we should include this match info here
                                        e.getValue() != null && include.test(e.getValue()))
                        .collect(Collectors.toSet());

        if (!filtered.isEmpty()) {
            ds.startEntry(KEY_MATCH_INFOS).startMap();
            for (Map.Entry<String, MatchInfo> e: filtered) {
                ds.startElEntry(e.getKey());
                matchInfo(ds, e.getValue());
                ds.endElEntry();
            }
            ds.endMap().endEntry();
        }
    }

    private static void legacyCapturedGroup(DataStream ds, Map.Entry<String, MatchInfo> capturedGroup) {
        ds.startMap();
        {
            ds.entry(KEY_NAME, capturedGroup.getKey());
            ds.entry(KEY_SPAN_START, capturedGroup.getValue().getSpanStart());
            ds.entry(KEY_SPAN_END, capturedGroup.getValue().getSpanEnd());
        }
        ds.endMap();
    }

    private static void matchInfo(DataStream ds, MatchInfo matchInfo) {
        if (matchInfo == null)
            return;
        switch (matchInfo.getType()) {
        case INLINE_TAG:
            matchInfoInlineTag(ds, (RelationInfo) matchInfo);
            break;
        case RELATION:
            matchInfoRelation(ds, (RelationInfo) matchInfo);
            break;
        case LIST_OF_RELATIONS:
            matchInfoListOfRelations(ds, (RelationListInfo) matchInfo);
            break;
        default:
            matchInfoCapturedGroup(ds, matchInfo);
            break;
        }
    }

    private static void matchInfoListOfRelations(DataStream ds, RelationListInfo listOfRelations) {
        ds.startMap();
        {
            ds.entry(KEY_MATCH_INFO_TYPE, MatchInfo.Type.LIST_OF_RELATIONS.jsonName());
            // report min/max of source target of list items? disabled for now
            //matchInfoRelationSourceTarget(ds, listOfRelations);
            ds.entry(KEY_SPAN_START, listOfRelations.getSpanStart());
            ds.entry(KEY_SPAN_END, listOfRelations.getSpanEnd());
            ds.startEntry("infos").startList();
            {
                for (RelationInfo relationInfo: listOfRelations.getRelations()) {
                    if (relationInfo != null) {
                        ds.startItem("info");
                        matchInfo(ds, relationInfo);
                        ds.endItem();
                    }
                }
            }
            ds.endList().endEntry();
        }
        ds.endMap();
    }

    private static void matchInfoCapturedGroup(DataStream ds, MatchInfo capturedGroup) {
        ds.startMap();
        {
            ds.entry(KEY_MATCH_INFO_TYPE, MatchInfo.Type.SPAN.jsonName());
            ds.entry(KEY_SPAN_START, capturedGroup.getSpanStart());
            ds.entry(KEY_SPAN_END, capturedGroup.getSpanEnd());
        }
        ds.endMap();
    }

    private static void matchInfoInlineTag(DataStream ds, RelationInfo inlineTag) {
        ds.startMap();
        {
            String fullRelationType = inlineTag.getFullRelationType();
            String tagName = RelationUtil.typeFromFullType(fullRelationType);
            ds.entry(KEY_MATCH_INFO_TYPE, MatchInfo.Type.INLINE_TAG.jsonName());
            ds.entry("tagName", tagName);
            optAttributes(ds, inlineTag);
            ds.entry(KEY_SPAN_START, inlineTag.getSourceStart());
            ds.entry(KEY_SPAN_END, inlineTag.getTargetStart());
        }
        ds.endMap();
    }

    /** If attribute values are avaiable, include those in the response. */
    private static void optAttributes(DataStream ds, RelationInfo inlineTag) {
        if (!inlineTag.getAttributes().isEmpty()) {
            ds.startEntry(KEY_ATTRIBUTES).startMap();
            for (Map.Entry<String, List<String>> attr: inlineTag.getAttributes().entrySet()) {
                ds.elEntry(attr.getKey(), attr.getValue());
            }
            ds.endMap().endEntry();
        }
    }

    private static void matchInfoRelation(DataStream ds, RelationInfo relationInfo) {
        ds.startMap();
        {
            ds.entry(KEY_MATCH_INFO_TYPE, MatchInfo.Type.RELATION.jsonName());
            ds.entry("relClass", relationInfo.getRelationClass());
            ds.entry("relType", relationInfo.getRelationType());
            optAttributes(ds, relationInfo);
            matchInfoRelationSourceTarget(ds, relationInfo);
            ds.entry(KEY_SPAN_START, relationInfo.getSpanStart());
            ds.entry(KEY_SPAN_END, relationInfo.getSpanEnd());

            if (!relationInfo.getField().equals(relationInfo.getTargetField())) {
                // Report targetField for cross-field relations
                ds.entry(KEY_TARGET_FIELD, relationInfo.getTargetField());
            }
        }
        ds.endMap();
    }

    private static void matchInfoRelationSourceTarget(DataStream ds, RelationLikeInfo relationInfo) {
        if (!relationInfo.isRoot()) {
            ds.entry("sourceStart", relationInfo.getSourceStart());
            ds.entry("sourceEnd", relationInfo.getSourceEnd());
        }
        ds.entry("targetStart", relationInfo.getTargetStart());
        ds.entry("targetEnd", relationInfo.getTargetEnd());
    }

    private static Set<Map.Entry<String, MatchInfo>> filterMatchInfo(Map<String, MatchInfo> matchInfo, Predicate<MatchInfo> predicate) {
        return matchInfo == null ? Collections.emptySet() :
                matchInfo.entrySet().stream()
                        .filter(e -> e.getValue() != null && predicate.test(e.getValue()))
                        .collect(Collectors.toSet());
    }

    public void indexProgress(ResultIndexStatus progress)
            throws BlsException {
        if (progress.getIndexStatus().equals(Index.IndexStatus.INDEXING)) {
            ds.startEntry("indexProgress").startMap()
                    .entry("filesProcessed", progress.getFiles())
                    .entry("docsDone", progress.getDocs())
                    .entry("tokensProcessed", progress.getTokens());
            ds.endMap().endEntry();
        }
    }

    public void metadataField(ResultMetadataField metadataField, boolean includeCustom) {
        String indexName = metadataField.getIndexName();
        MetadataField fd = metadataField.getFieldDesc();
        boolean listValues = metadataField.isListValues();
        Map<String, Long> fieldValuesInOrder = metadataField.getFieldValues();
        boolean isValueListComplete = metadataField.isValueListComplete();

        ds.startMap();

        // Assemble response
        if (indexName != null)
            ds.entry(KEY_CORPUS_NAME, indexName);
        ds.entry(KEY_FIELD_NAME, fd.name());
        ds.entry(KEY_FIELD_IS_ANNOTATED, false);
        ds.entry("type", fd.type().toString());
        ds.entry("analyzer", fd.analyzerName());
        if (isNewApi) {
            if (includeCustom)
                customInfoEntry(fd.custom());
        } else {
            // Legacy fields, now moved to custom section
            ds.entry("displayName", fd.custom().get("displayName", ""))
                    .entry("description", fd.custom().get("description", ""))
                    .entry("uiType", fd.custom().get("uiType"));
            Object unknownCondition = fd.custom().get("unknownCondition");
            if (unknownCondition != null)
                ds.entry("unknownCondition", unknownCondition.toString().toUpperCase());
            ds.entry("unknownValue", fd.custom().get("unknownValue"));
        }
        if (listValues) {
            if (!isNewApi) {
                // Legacy API. Include displayValues (now moved to custom section)
                final Map<String, String> displayValues = fd.custom().get("displayValues",
                        Collections.emptyMap());
                ds.startEntry("displayValues").startMap();
                for (Map.Entry<String, String> e: displayValues.entrySet()) {
                    ds.attrEntry("displayValue", KEY_VALUE, e.getKey(), e.getValue());
                }
                ds.endMap().endEntry();
            }

            // Show values in display order (if defined)
            // If not all values are mentioned in display order, show the rest at the end,
            // sorted by their displayValue (or regular value if no displayValue specified)
            ds.startEntry("fieldValues").startMap();
            for (Map.Entry<String, Long> e: fieldValuesInOrder.entrySet()) {
                ds.attrEntry(KEY_VALUE, "text", e.getKey(), e.getValue());
            }
            ds.endMap().endEntry();

            // (we report false for ValueListComplete.UNKNOWN - this usually means there's no values either way)
            ds.entry(KEY_VALUE_LIST_COMPLETE, isValueListComplete);
        }
        ds.endMap();
    }

    public void annotatedField(ResultAnnotatedField annotatedField, boolean includeCustom) {
        String indexName = annotatedField.getIndexName();
        AnnotatedField fieldDesc = annotatedField.getFieldDesc();
        Map<String, ResultAnnotationInfo> annotInfos = annotatedField.getAnnotInfos();

        ds.startMap();
        if (indexName != null)
            ds.entry(KEY_CORPUS_NAME, indexName);
        Annotations annotations = fieldDesc.annotations();
        ds
                .entry(KEY_FIELD_NAME, fieldDesc.name())
                .entry(KEY_FIELD_IS_ANNOTATED, true);
        fieldCount(annotatedField.getCount());
        if (isNewApi) {
            if (includeCustom)
                customInfoEntry(fieldDesc.custom());
        } else {
            // Legacy fields, now moved to custom section
            ds.entry("displayName", fieldDesc.custom().get("displayName", ""))
                    .entry("description", fieldDesc.custom().get("description", ""));
        }
        ds.entry("hasContentStore", fieldDesc.hasContentStore());
        if (!isNewApi)
            ds.entry("hasXmlTags", fieldDesc.hasRelationAnnotation());
        ds.entry("mainAnnotation", annotations.main().name());
        if (!isNewApi) {
            // Moved to custom
            ds.startEntry("displayOrder").startList();
            annotations.stream()
                    .filter(a -> !a.isInternal())
                    .map(Annotation::name)
                    .forEach(id -> ds.item(KEY_FIELD_NAME, id));
            ds.endList().endEntry();
        }

        ds.startEntry("annotations").startMap();
        for (Map.Entry<String, ResultAnnotationInfo> annotEntry: annotInfos.entrySet()) {
            if (annotEntry.getKey().equals(AnnotatedFieldNameUtil.RELATIONS_ANNOT_NAME))
                continue; // don't include _relation, may not be used in queries
            ds.startAttrEntry("annotation", KEY_NAME, annotEntry.getKey()).startMap();
            ResultAnnotationInfo ai = annotEntry.getValue();
            Annotation annotation = ai.getAnnotation();
            AnnotationSensitivity offsetsSensitivity = annotation.offsetsSensitivity();
            String offsetsAlternative = offsetsSensitivity == null ? "" :
                    offsetsSensitivity.sensitivity().luceneFieldSuffix();
            AnnotationSensitivities annotationSensitivities = annotation.sensitivitySetting();
            String sensitivity = annotationSensitivities == null ? "" : annotationSensitivities.stringValueForResponse();
            if (isNewApi) {
                if (includeCustom)
                    customInfoEntry(annotation.custom());
            } else {
                // Legacy fields, now moved to custom section
                ds
                        .entry("displayName", annotation.custom().get("displayName", ""))
                        .entry("description", annotation.custom().get("description", ""))
                        .entry("uiType", annotation.custom().get("uiType", ""));
            }
            ds.entry("hasForwardIndex", annotation.hasForwardIndex())
                    .entry("sensitivity", sensitivity)
                    .entry("offsetsAlternative", offsetsAlternative)
                    .entry("isInternal", annotation.isInternal());
            if (ai.isShowValues()) {
                TruncatableFreqList terms = ai.getTerms();
                // Return both terms AND their frequencies
                ds.startEntry("terms").startMap();
                for (Map.Entry<String, Long> termEntry: terms.getValues().entrySet()) {
                    ds.dynEntry(termEntry.getKey(), termEntry.getValue());
                }
                ds.endMap().endEntry();
                if (!isNewApi) {
                    // Return the list of terms
                    ds.startEntry(KEY_VALUES).startList();
                    for (String term: terms.getValues().keySet()) {
                        ds.item(KEY_VALUE, term);
                    }
                    ds.endList().endEntry();
                }
                ds.entry(KEY_VALUE_LIST_COMPLETE, !terms.isTruncated());
            }
            if (!annotation.subannotationNames().isEmpty()) {
                ds.startEntry("subannotations").startList();
                for (String name: annotation.subannotationNames()) {
                    ds.item("subannotation", name);
                }
                ds.endList().endEntry();
            }
            if (annotation.isSubannotation()) {
                ds.entry("parentAnnotation", annotation.parentAnnotation().name());
            }
            ds.endMap().endAttrEntry();
        }
        ds.endMap().endEntry();
        ds.endMap();
    }

    private void fieldCount(CorpusSize.Count count) {
        if (isNewApi)
            ds.startEntry(KEY_COUNT).startMap();
        ds.entry(KEY_TOKEN_COUNT, count.getTokens());
        ds.entry(KEY_DOCUMENT_COUNT, count.getDocuments());
        if (isNewApi)
            ds.endMap().endEntry();
    }

    public void collocationsResponse(TermFrequencyList tfl) {
        ds.startMap().startEntry("tokenFrequencies").startMap();
        for (TermFrequency tf : tfl) {
            ds.attrEntry("token", "text", tf.term, tf.frequency);
        }
        ds.endMap().endEntry().endMap();
    }

    public void hitsResponse(ResultHits resultHits)
            throws InvalidQuery {
        WebserviceParams params = resultHits.getParams();
        BlackLabIndex index = params.blIndex();
        // Search time should be time user (originally) had to wait for the response to this request.
        // Count time is the time it took (or is taking) to iterate through all the results to count the total.
        ResultSummaryCommonFields summaryFields = resultHits.getSummaryCommonFields();

        ds.startMap();

        // The summary
        ds.startEntry(KEY_SUMMARY).startMap();
        {
            summaryCommonFields(summaryFields);
            ResultSummaryNumHits result = resultHits.getSummaryNumHits();
            summaryResultsStats(result, null);
            if (!isNewApi)
                summaryTotalTokens(resultHits.getTotalTokens());

            // Write docField (pidField, titleField, etc.) and metadata display names
            // (this information is not specific to this request and can be found elsewhere,
            //  so it probably shouldn't be here - hence the API differences)
            if (!isNewApi) {
                stringMap("docFields", resultHits.getDocFields());
                stringMap("metadataFieldDisplayNames", resultHits.getMetaDisplayNames());
            }

            // Include explanation of how the query was executed?
            if (params.getExplain()) {
                TextPattern tp = params.patternWithinContextTag().orElseThrow();
                try {
                    BLSpanQuery q = tp.toQuery(QueryInfo.create(index, params.getAnnotatedField()));
                    QueryExplanation explanation = index.explain(q);
                    ds.startEntry("explanation").startMap()
                            .entry("originalQuery", explanation.originalQuery())
                            .entry("rewrittenQuery", explanation.rewrittenQuery())
                            .endMap().endEntry();
                } catch (InvalidQuery e) {
                    throw new BadRequest("INVALID_QUERY", e.getMessage());
                }
            }
        }
        ds.endMap().endEntry();

        // Hits and docInfos
        listOfHits(resultHits.getListOfHits());
        documentInfos(resultHits.getDocInfos());

        // Facets (if requested)
        if (resultHits.hasFacets()) {
            // Now, group the docs according to the requested facets.
            ds.startEntry("facets");
            {
                facets(resultHits.getFacetInfo());
            }
            ds.endEntry();
        }

        ds.endMap();
    }

    public void hitsGroupedResponse(ResultHitsGrouped hitsGrouped) {
        ds.startMap();
        {
            ds.startEntry(KEY_SUMMARY).startMap();
            {
                summaryCommonFields(hitsGrouped.getSummaryFields());
                ResultSummaryNumHits result = hitsGrouped.getSummaryNumHits();
                summaryResultsStats(result, hitsGrouped.getGroups());
            }
            ds.endMap().endEntry();

            ds.startEntry("hitGroups").startList();
            {
                List<ResultHitGroup> groupInfos = hitsGrouped.getGroupInfos();
                for (ResultHitGroup groupInfo: groupInfos) {
                    ds.startItem(KEY_HIT_GROUP).startMap();
                    {
                        groupStats(groupInfo.getGroup(), groupInfo.getProperties());
                        ds.entry(KEY_NUMBER_OF_DOCS, groupInfo.getNumberOfDocsInGroup());
                        if (hitsGrouped.getMetadataGroupProperties() != null)
                            subcorpusSizeStats(groupInfo.getSubcorpusSize());
                        if (groupInfo.getListOfHits() != null)
                            listOfHits(groupInfo.getListOfHits());
                    }
                    ds.endMap().endItem();
                }
            }
            ds.endList().endEntry();

            if (hitsGrouped.getParams().getIncludeGroupContents()) {
                documentInfos(hitsGrouped.getDocInfos());
            }
        }
        ds.endMap();
    }

    private void groupStats(Group group, Map<ResultProperty, PropertyValue> properties) {
        ds
                .entry("identity", group.identity().serialize())
                .entry("identityDisplay", group.identity().toString())
                .entry(KEY_GROUP_SIZE, group.size());

        ds.startEntry("properties").startList();
        for (Map.Entry<ResultProperty, PropertyValue> p: properties.entrySet()) {
            ds.startItem("property").startMap();
            {
                ds.entry(KEY_NAME, p.getKey().serialize());
                ds.entry(KEY_VALUE, p.getValue().toString());
            }
            ds.endMap().endItem();
        }
        ds.endList().endEntry();
    }

    public void docsGroupedResponse(ResultDocsGrouped result) {
        DocGroups groups = result.getGroups();
        ds.startMap();
        {
            // The summary
            ds.startEntry(KEY_SUMMARY).startMap();
            {
                summaryCommonFields(result.getSummaryFields());
                if (result.getNumResultDocs() != null)
                    summaryNumDocs(result.getNumResultDocs(), groups);
                else {
                    ResultSummaryNumHits result1 = result.getNumResultHits();
                    summaryResultsStats(result1, groups);
                }
            }
            ds.endMap().endEntry();

            ds.startEntry("docGroups").startList();
            {
                /* Gather group values per property:
                 * In the case we're grouping by multiple values, the DocPropertyMultiple and PropertyValueMultiple will
                 * contain the sub properties and values in the same order.
                 */
                Iterator<CorpusSize> it = result.getCorpusSizes().iterator();
                List<DocProperty> prop = groups.groupCriteria().propsList();
                WindowStats ourWindow = result.getOurWindow();
                for (long i = ourWindow.first(); i <= ourWindow.last(); ++i) {
                    DocGroup group = groups.get(i);
                    ds.startItem(KEY_DOC_GROUP).startMap();
                    groupStats(group, group.getGroupProperties(prop));
                    ds.entry(KEY_NUMBER_OF_TOKENS, group.totalTokens());
                    if (result.getParams().hasPattern()) {
                        subcorpusSizeStats(it.next());
                    }
                    ds.endMap().endItem();
                }
            }
            ds.endList().endEntry();
        }
        ds.endMap();
    }

    public void docsResponse(ResultDocsResponse result) {
        ds.startMap();
        {
            // The summary
            ds.startEntry(KEY_SUMMARY).startMap();
            {
                summaryCommonFields(result.getSummaryFields());
                if (result.getNumResultDocs() != null) {
                    summaryNumDocs(result.getNumResultDocs(), null);
                } else {
                    ResultSummaryNumHits result1 = result.getNumResultHits();
                    summaryResultsStats(result1, null);
                }
                if (!isNewApi)
                    summaryTotalTokens(result.getTotalTokens());

                boolean includeDeprecatedFieldInfo = !isNewApi;
                if (includeDeprecatedFieldInfo) {
                    // (this information is not specific to this request and can be found elsewhere,
                    //  so it probably shouldn't be here)
                    Map<String, String> docFields = result.getDocFields();
                    Map<String, String> metaDisplayNames = result.getMetaDisplayNames();
                    stringMap("docFields", docFields);
                    stringMap("metadataFieldDisplayNames", metaDisplayNames);
                }
            }
            ds.endMap().endEntry();

            // The hits and document info
            ds.startEntry("docs").startList();
            for (ResultDocResult docResult: result.getDocResults()) {
                docResult(docResult);
            }
            ds.endList().endEntry();
            if (result.getFacetInfo() != null) {
                // Now, group the docs according to the requested facets.
                ds.startEntry("facets");
                {
                    facets(result.getFacetInfo());
                }
                ds.endEntry();
            }
        }
        ds.endMap();
    }

    public void docResult(ResultDocResult result) {
        ds.startItem("doc").startMap();
        {
            // Combine all
            ds.entry(KEY_DOC_PID, result.getPid());
            if (result.numberOfHits() > 0)
                ds.entry(KEY_NUMBER_OF_HITS, result.numberOfHits());

            // Doc info (metadata, etc.)
            ds.startEntry(KEY_DOC_INFO);
            {
                documentInfo(result.getDocInfo());
            }
            ds.endEntry();

            // Snippets
            Collection<Annotation> annotationsToList = result.getAnnotationsToList();
            if (result.numberOfHitsToShow() > 0) {
                ds.startEntry("snippets").startList();
                if (!result.hasConcordances()) {
                    // KWICs
                    for (Kwic k: result.getKwicsToShow()) {
                        ds.startItem(KEY_DOC_SNIPPET).startMap();
                        {
                            // Add KWIC info
                            ds.startEntry(KEY_BEFORE).contextList(k.annotations(), annotationsToList, k.before())
                                    .endEntry();
                            ds.startEntry(KEY_MATCHING_PART_OF_HIT).contextList(k.annotations(), annotationsToList, k.match())
                                    .endEntry();
                            ds.startEntry(KEY_AFTER).contextList(k.annotations(), annotationsToList, k.after())
                                    .endEntry();
                        }
                        ds.endMap().endItem();
                    }
                } else {
                    // Concordances from original content
                    for (Concordance c: result.getConcordancesToShow()) {
                        ds.startItem(KEY_DOC_SNIPPET).startMap();
                        {
                            // Add concordance from original XML
                            ds.startEntry(KEY_BEFORE).xmlFragment(c.left()).endEntry()
                                    .startEntry(KEY_MATCHING_PART_OF_HIT).xmlFragment(c.match()).endEntry()
                                    .startEntry(KEY_AFTER).xmlFragment(c.right()).endEntry();
                        }
                        ds.endMap().endItem();
                    }
                }
                ds.endList().endEntry();
            } // if snippets

        }
        ds.endMap().endItem();
    }

    public void serverInfo(ResultServerInfo result) {
        ds.startMap();
        {
            ds.entry("apiVersion", apiVersion.toString());
            ds.entry(KEY_BLACKLAB_BUILD_TIME, BlackLab.buildTime())
                    .entry(KEY_BLACKLAB_VERSION, BlackLab.version());
            String buildScmRevision = BlackLab.getBuildScmRevision();
            if (StringUtils.isEmpty(buildScmRevision))
                buildScmRevision = "UNKNOWN";
            ds.entry(KEY_BLACKLAB_SCM_REVISION, buildScmRevision);

            ds.startEntry("corpora").startMap();
            for (ResultIndexStatus corpusInfo: result.getIndexStatuses()) {
                corpusInfoEntry(corpusInfo, result.getParams().getIncludeCustomInfo());
            }
            ds.endMap().endEntry();
            if (!isNewApi) {
                ds.startEntry("indices").startMap();
                for (ResultIndexStatus indexStatus: result.getIndexStatuses()) {
                    legacyIndexInfo(indexStatus);
                }
                ds.endMap().endEntry();
            }

            userInfo(result.getUserInfo(), result.isDebugMode());
        }
        ds.endMap();
    }

    /** New API corpus info */
    public void corpusInfoEntry(ResultIndexStatus corpusStatus, boolean includeCustom) {
        Index index = corpusStatus.getIndex();
        IndexMetadata indexMetadata = corpusStatus.getMetadata();
        ds.startElEntry(index.getId()).startMap();
        {
            if (includeCustom)
                customInfoEntry(indexMetadata.custom(), List.of("displayName", "description"));
            ds.entry(KEY_STATS_STATUS, index.getStatus());
            String formatIdentifier = indexMetadata.documentFormat();
            if (formatIdentifier != null && !formatIdentifier.isEmpty())
                ds.entry("documentFormat", formatIdentifier);
            ds.entry("timeModified", indexMetadata.timeModified());
            indexSize(indexMetadata);
            indexProgress(corpusStatus);
            if (corpusStatus.getIndex().isUserIndex()) {
                ds.entry("owner", corpusStatus.getIndex().getUserId());
            }
        }
        ds.endMap().endElEntry();
    }

    private void customInfoEntry(CustomProps custom) {
        customInfoEntry(custom, Collections.emptyList());
    }

    /** New API custom props for corpus/field (e.g. displayName, description, etc.) */
    private void customInfoEntry(CustomProps custom, List<String> includeKeys) {
        if (includeKeys == null)
            includeKeys = Collections.emptyList();
        ds.startEntry("custom");
        ds.startMap();
        for (Map.Entry<String, Object> prop: custom.asMap().entrySet()) {
            if (includeKeys.isEmpty() || includeKeys.contains(prop.getKey()))
                ds.elEntry(prop.getKey(), prop.getValue());
        }
        ds.endMap();
        ds.endEntry();
    }

    public void legacyIndexInfo(ResultIndexStatus progress) {
        Index index = progress.getIndex();
        IndexMetadata indexMetadata = progress.getMetadata();
        ds.startAttrEntry("index", KEY_NAME, index.getId());
        {
            ds.startMap();
            {
                ds.entry("displayName", indexMetadata.custom().get("displayName", ""));
                ds.entry("description", indexMetadata.custom().get("description", ""));
                ds.entry(KEY_STATS_STATUS, index.getStatus());
                String formatIdentifier = indexMetadata.documentFormat();
                if (formatIdentifier != null && !formatIdentifier.isEmpty())
                    ds.entry("documentFormat", formatIdentifier);
                ds.entry("timeModified", indexMetadata.timeModified());
                ds.entry(KEY_TOKEN_COUNT, indexMetadata.tokenCount());
                indexProgress(progress);
            }
            ds.endMap();
        }
        ds.endAttrEntry();
    }

    public void corpusMetadataResponse(ResultIndexMetadata result, boolean includeCustom) {
        IndexMetadata metadata = result.getMetadata();
        ds.startMap();
        {
            ds.entry(KEY_CORPUS_NAME, result.getProgress().getIndex().getId());

            if (isNewApi) {
                if (includeCustom)
                    customInfoEntry(metadata.custom());
            } else {
                // Legacy fields, now moved to custom section
                ds.entry("displayName", metadata.custom().get("displayName", ""));
                ds.entry("description", metadata.custom().get("description", ""));
                ds.entry("textDirection", metadata.custom().get("textDirection", "ltr"));
            }
            ds.entry(KEY_STATS_STATUS, result.getProgress().getIndexStatus());
            ds.entry("contentViewable", metadata.contentViewable());

            String formatIdentifier = metadata.documentFormat();
            if (formatIdentifier != null && !formatIdentifier.isEmpty())
                ds.entry("documentFormat", formatIdentifier);
            indexSize(metadata);
            indexProgress(result.getProgress());

            ds.startEntry("versionInfo").startMap()
                    .entry(KEY_BLACKLAB_BUILD_TIME, metadata.indexBlackLabBuildTime())
                    .entry(KEY_BLACKLAB_VERSION, metadata.indexBlackLabVersion());
            ds.entry(KEY_BLACKLAB_SCM_REVISION, metadata.indexBlackLabScmRevision());
            ds.entry("indexFormat", metadata.indexFormat())
                    .entry("timeCreated", metadata.timeCreated())
                    .entry("timeModified", metadata.timeModified())
                    .endMap().endEntry();

            // New API; all except pidField moved to custom
            MetadataField pidField = metadata.metadataFields().pidField();
            ds.entry(MetadataFields.SPECIAL_FIELD_SETTING_PID, pidField == null ? "" : pidField.name());
            if (!isNewApi) {
                // Legacy API
                ds.startEntry("fieldInfo").startMap()
                        .entry(MetadataFields.SPECIAL_FIELD_SETTING_PID, metadata.metadataFields().pidField() == null ?
                                "" :
                                metadata.metadataFields().pidField())
                        .entry("titleField", metadata.custom().get("titleField", ""))
                        .entry("authorField", metadata.custom().get("authorField", ""))
                        .entry("dateField", metadata.custom().get("dateField", ""))
                        .endMap().endEntry();
            }

            ds.entry("mainAnnotatedField", result.getMainAnnotatedField() == null ? "" : result.getMainAnnotatedField());
            ds.startEntry(KEY_ANNOTATED_FIELDS).startMap();
            for (ResultAnnotatedField annotatedField: result.getAnnotatedFields()) {
                // internal fields.
                // TODO figure out how to prevent this.
                // This happens when we have linked metadata, a dummy annotatedField is written, but it's required for the contentstore (apparently?).
                if (annotatedField.getAnnotInfos().isEmpty())
                    continue;
                ds.startAttrEntry(KEY_ANNOTATED_FIELD, KEY_NAME, annotatedField.getFieldDesc().name());
                {
                    annotatedField(annotatedField, includeCustom);
                }
                ds.endAttrEntry();
            }
            ds.endMap().endEntry();

            ds.startEntry("metadataFields").startMap();
            for (ResultMetadataField metadataField: result.getMetadataFields()) {
                ds.startAttrEntry("metadataField", KEY_NAME, metadataField.getFieldDesc().name());
                {
                    metadataField(metadataField, includeCustom);
                }
                ds.endAttrEntry();
            }
            ds.endMap().endEntry();

            if (!isNewApi) {
                // Part of custom info in v5
                metadataGroupInfo(result.getMetadataFieldGroups());
                annotationGroups(metadata);
            }
        }
        ds.endMap();
    }

    private void annotationGroups(IndexMetadata metadata) {
        ds.startEntry("annotationGroups").startMap();
        for (AnnotatedField f: metadata.annotatedFields()) {
            AnnotationGroups groups = metadata.annotatedFields().annotationGroups(f.name());
            if (groups != null) {
                @SuppressWarnings("FuseStreamOperations") // LinkedHashSet - preserve order!
                Set<Annotation> annotationsNotInGroups = new LinkedHashSet<>(
                        f.annotations().stream().collect(Collectors.toList()));
                for (AnnotationGroup group: groups) {
                    for (String annotationName: group) {
                        Annotation annotation = f.annotation(annotationName);
                        annotationsNotInGroups.remove(annotation);
                    }
                }
                ds.startAttrEntry(KEY_ANNOTATED_FIELD, KEY_NAME, f.name()).startList();
                boolean addedRemainingAnnots = false;
                for (AnnotationGroup group: groups) {
                    ds.startItem("annotationGroup").startMap();
                    ds.entry(KEY_NAME, group.groupName());
                    ds.startEntry("annotations").startList();
                    for (String annotation: group) {
                        ds.item("annotation", annotation);
                    }
                    if (!addedRemainingAnnots && group.addRemainingAnnotations()) {
                        addedRemainingAnnots = true;
                        for (Annotation annotation: annotationsNotInGroups) {
                            if (!annotation.isInternal())
                                ds.item("annotation", annotation.name());
                        }
                    }
                    ds.endList().endEntry();
                    ds.endMap().endItem();
                }
                ds.endList().endAttrEntry();
            }
        }
        ds.endMap().endEntry();
    }

    public void corpusStatusResponse(ResultIndexStatus progress, boolean includeCustom) {
        IndexMetadata metadata = progress.getMetadata();
        ds.startMap();
        {
            ds.entry(KEY_CORPUS_NAME, progress.getIndex().getId());
            if (isNewApi) {
                if (includeCustom)
                    customInfoEntry(metadata.custom(), List.of("displayName", "description"));
            } else {
                // Legacy fields, now moved to custom section
                ds.entry("displayName", metadata.custom().get("displayName", ""));
                ds.entry("description", metadata.custom().get("description", ""));
            }
            ds.entry(KEY_STATS_STATUS, progress.getIndexStatus());
            ds.entry("timeModified", metadata.timeModified());
            indexSize(metadata);
            String formatIdentifier = metadata.documentFormat();
            if (!StringUtils.isEmpty(formatIdentifier))
                ds.entry("documentFormat", formatIdentifier);
            indexProgress(progress);
        }
        ds.endMap();
    }

    private void indexSize(IndexMetadata metadata) {
        if (isNewApi)
            ds.startEntry(KEY_COUNT).startMap();
        ds.entry(KEY_TOKEN_COUNT, metadata.tokenCount());
        ds.entry(isNewApi ? KEY_SUBCORPUS_SIZE_DOCUMENTS : KEY_DOCUMENT_COUNT, metadata.documentCount());
        if (metadata.documentVersionCount() > metadata.documentCount())
            ds.entry(KEY_DOCUMENT_VERSION_COUNT, metadata.documentVersionCount());
        if (isNewApi)
            ds.endMap().endEntry();
    }

    public String getDocContentsResponsePlain(ResultDocContents resultDocContents) {
        StringBuilder b = new StringBuilder();

        if (resultDocContents.needsXmlDeclaration()) {
            // We haven't outputted an XML declaration yet, and there's none in the document. Do so now.
            b.append(DataStream.XML_PROLOG);
        }

        // Output root element and namespaces if necessary
        // (i.e. when we're not returning the full document, only part of it)
        if (!resultDocContents.isFullDocument()) {
            // Surround with root element and make sure it has the required namespaces
            b.append(DataStream.XML_PROLOG);
            b.append("<" + BLACKLAB_RESPONSE_ROOT_ELEMENT);
            for (String ns: resultDocContents.getNamespaces()) {
                b.append(" ").append(ns);
            }
            for (String anon: resultDocContents.getAnonNamespaces()) {
                b.append(" ").append(anon);
            }
            b.append(">");
        }

        // Output (part of) the document
        b.append(resultDocContents.getContent());

        if (!resultDocContents.isFullDocument()) {
            // Close the root el we opened
            b.append("</" + BLACKLAB_RESPONSE_ROOT_ELEMENT + ">");
        }

        return b.toString();
    }

    public void docContentsResponseAsCdata(ResultDocContents result) {
        ds.startMap();
        ds.entry("contents", getDocContentsResponsePlain(result));
        ds.endMap();
    }

    public void docContentsResponsePlain(ResultDocContents resultDocContents) {
        ds.plain(getDocContentsResponsePlain(resultDocContents));
    }

    public void docInfoResponse(ResultDocInfo docInfo, Map<String, List<String>> metadataFieldGroups,
            Map<String, String> docFields, Map<String, String> metaDisplayNames) {
        ds.startMap().entry(KEY_DOC_PID, docInfo.getPid());
        {
            ds.startEntry(KEY_DOC_INFO);
            {
                documentInfo(docInfo);
            }
            ds.endEntry();

            boolean includeDeprecatedFieldInfo = !isNewApi;
            if (includeDeprecatedFieldInfo) {
                // (this information is not specific to this document and can be found elsewhere,
                //  so it probably shouldn't be here)
                metadataGroupInfo(metadataFieldGroups);
                stringMap("docFields", docFields);
                stringMap("metadataFieldDisplayNames", metaDisplayNames);
            }
        }
        ds.endMap();
    }

    public void termFreqResponse(TermFrequencyList tfl) {
        // Assemble all the parts
        ds.startMap();
        ds.startEntry("termFreq").startMap();
        //DataObjectMapAttribute termFreq = new DataObjectMapAttribute("term", "text");
        for (TermFrequency tf : tfl) {
            ds.attrEntry("term", "text", tf.term, tf.frequency);
        }
        ds.endMap().endEntry();
        ds.endMap();
    }

    public void autoComplete(ResultAutocomplete result) {
        ds.startList();
        result.getTerms().forEach((v) -> ds.item("term", v));
        ds.endList();
    }

    public void listFormatsResponse(ResultListInputFormats result) {

        ds.startMap();
        {
            userInfo(result.getUserInfo(), result.isDebugMode());

            // List supported input formats
            // Formats from other users are hidden in the master list, but are considered public for all other purposes (if you know the name)
            ds.startEntry("supportedInputFormats").startMap();
            for (InputFormat inputFormat: result.getFormats()) {
                String displayName = null;
                try {
                    displayName = inputFormat.getDisplayName();
                } catch (InvalidInputFormatConfig e) {
                    // Invalid input format shouldn't break the response; just log and skip it
                    logger.error(e.getMessage());
                    continue;
                }
                ds.startAttrEntry("format", KEY_NAME, inputFormat.getIdentifier());
                {
                    ds.startMap();
                    {
                        ds.entry("displayName", displayName)
                                .entry("description", inputFormat.getDescription())
                                .entry("helpUrl", inputFormat.getHelpUrl())
                                .entry("configurationBased", inputFormat instanceof InputFormatWithConfig)
                                .entry("isVisible", inputFormat.isVisible());
                    }
                    ds.endMap();
                }
                ds.endAttrEntry();
            }
            ds.endMap().endEntry();
        }
        ds.endMap();
    }

    public void formatInfoResponse(ResultInputFormat result) {
        ds.startMap()
                .entry("formatName", result.getConfig().getName())
                .entry("configFileType", result.getConfig().getConfigFileType())
                .entry("configFile", result.getFileContents())
                .endMap();
    }

    public void cacheInfo(SearchCache blackLabCache, boolean includeDebugInfo) {
        ds.startMap()
                .startEntry("cacheStatus");
        ds.value(blackLabCache.getStatus());
        ds.endEntry()
            .startEntry("cacheContents");
        ds.value(blackLabCache.getContents(includeDebugInfo));
        ds.endEntry()
                .endMap();
    }

    public void formatXsltResponse(ResultInputFormat result) {
        ds.xslt(result.getXslt());
    }

    public DataStream getDataStream() {
        return ds;
    }
}
