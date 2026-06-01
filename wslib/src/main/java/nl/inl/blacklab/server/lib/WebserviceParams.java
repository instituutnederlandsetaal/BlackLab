package nl.inl.blacklab.server.lib;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.Query;

import com.fasterxml.jackson.core.JsonProcessingException;

import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.queryParser.corpusql.BcqlQueryLanguageParser;
import nl.inl.blacklab.resultproperty.DocGroupProperty;
import nl.inl.blacklab.resultproperty.DocGroupPropertySize;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.HitGroupProperty;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.SingleDocIdFilter;
import nl.inl.blacklab.search.extensions.XFRelations;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.indexmetadata.MetadataFields;
import nl.inl.blacklab.search.results.SampleParameters;
import nl.inl.blacklab.search.results.SearchSettings;
import nl.inl.blacklab.search.results.hitresults.ContextSize;
import nl.inl.blacklab.search.results.hitresults.HitGroupCollocationScorer;
import nl.inl.blacklab.search.results.hitresults.HitGroupScorer;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternPositionFilter;
import nl.inl.blacklab.server.config.BLSConfig;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.NotFound;
import nl.inl.blacklab.server.index.IndexManager;
import nl.inl.blacklab.server.jobs.ContextSettings;
import nl.inl.blacklab.server.jobs.WindowSettings;
import nl.inl.blacklab.server.lib.results.WebserviceOperations;
import nl.inl.blacklab.server.util.BlsUtils;
import nl.inl.blacklab.server.util.GapFiller;
import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.webservice.WebserviceParameter;
import nl.inl.util.Json;

/**
 * Utility methods for webservice requests interpreting query parameters.
 * <p>
 * Offers e.g. methods that instantiate Search objects.
 */
public class WebserviceParams {

    /** utility class */
    private WebserviceParams() {}

    /** Resolve the index a request wants to access */
    public static BlackLabIndex index(String corpusName) {
        try {
            return IndexManager.get().getIndex(corpusName).blIndex();
        } catch (Exception e) {
            throw new InvalidIndex(e);
        }
    }

    /**
     * The pattern as passed by the user.
     *
     * This excludes the (optionally added) within clause for context.
     *
     * E.g. if the user has passed context=s, we add within &lt;s/&gt; to the
     * query so we can capture the relevant sentence span for the requested
     * context. This is a separate method because we don't want to report
     * this query with the additional clause in the response.
     *
     * Optionally fill in gaps in the patt parameter using the pattGapData provided.
     *
     * @param index index we're searching
     * @param pattLang pattern language (usually corpusql)
     * @param pattern pattern string
     * @param pattGapData optional pattern gap data if the pattern string contains gaps
     * @return original query without the optionally added within clause
     * @throws BlsException
     */
    public static Optional<TextPattern> patternNoWithinContextTag(BlackLabIndex index, String pattLang,
            String pattern, String pattGapData) throws BlsException {
        TextPattern textPattern = null;
        if (!StringUtils.isBlank(pattern)) {
            if (pattLang.matches("default|corpusql") && !StringUtils.isBlank(pattGapData) && GapFiller.hasGaps(pattern)) {
                // CQL query with gaps, and TSV data to put in the gaps
                try {
                    textPattern = GapFiller.parseBcqlGapQuery(index, pattern, pattGapData);
                } catch (InvalidQuery e) {
                    throw new BadRequest("PATT_SYNTAX_ERROR",
                            "Syntax error in gapped CorpusQL pattern: " + e.getMessage());
                }
            } else {
                textPattern = BlsUtils.parsePatt(index, pattern, pattLang);
            }
        }
        return Optional.ofNullable(textPattern);
    }

    /**
     * The pattern to find in the corpus.
     *
     * This includes the (optionally added) within clause for context.
     * E.g. if the user has passed context=s, we add within &lt;s/&gt; to the
     * query so we can capture the relevant sentence span for the requested
     * context. This is a separate method because we don't want to report
     * this query with the additional clause in the response.
     *
     * @param index index we're searching
     * @param pattLang pattern language (usually corpusql)
     * @param pattern pattern string
     * @param pattGapData optional pattern gap data if the pattern string contains gaps
     * @return query with optionally added within clause
     * @throws BlsException
     */
    public static Optional<TextPattern> pattern(BlackLabIndex index, String pattLang,
            String pattern, String pattGapData, String withinTag) throws BlsException {
        Optional<TextPattern> textPattern = patternNoWithinContextTag(index, pattLang, pattern, pattGapData);
        if (textPattern.isEmpty())
            return Optional.empty();
        TextPattern patternWithin = textPattern.get();
        if (withinTag != null) {
            patternWithin = ensureWithinTag(patternWithin, withinTag, XFRelations.DEFAULT_CONTEXT_REL_NAME);
        }
        return patternWithin == null ? Optional.empty() : Optional.of(patternWithin);
    }

    private static TextPattern ensureWithinTag(TextPattern pattern, String tagName, String captureRelsAs) {
        boolean withinTag = pattern instanceof TextPatternPositionFilter &&
                ((TextPatternPositionFilter) pattern).isWithinTag(tagName);
        if (!withinTag) {
            // add "within rcapture(<TAGNAME/>)" to the pattern, so we can produce the requested context later
            // NOTE: actually, we use a special operation so this works even if the match spans multiple sentences
            //       (for the example of context=s)
            return TextPattern.createRelationCapturingWithinQuery(pattern, tagName, captureRelsAs);
        }
        return pattern;
    }

    public static ContextSize getContext(String contextParam, BLSConfig config) {
        int maxContextSize = config.getParameters().getContextSize().getMaxInt();
        int maxSnippetLength = ContextSize.maxSnippetLengthFromMaxContextSize(maxContextSize);
        return ContextSize.fromContextDef(contextParam, maxSnippetLength);
    }

    public static boolean getIncludeGroupContents(Boolean includeGroupContents, BLSConfig config) {
        boolean defVal = config.getParameters().isWriteHitsAndDocsInGroupedHits();
        return includeGroupContents == null ? defVal : includeGroupContents;
    }

    /**
     * Returns a list of metadata fields to write out.
     * <p>
     * By default, all metadata fields are returned.
     * Special fields (pidField, titleField, etc...) are always returned.
     *
     * @return a list of metadata fields to write out, as specified by the "listmetadatavalues" query parameter.
     */
    public static List<MetadataField> getMetadataToInclude(BlackLabIndex index, List<String> requestedFields) {
        boolean includeAllFields = requestedFields.isEmpty() || requestedFields.contains("*");
        if (includeAllFields)
            return index.metadataFields().toList();
        List<MetadataField> ret = new ArrayList<>();
        MetadataFields fields = index.metadataFields();
        for (String field: requestedFields) {
            ret.add(fields.get(field));
        }
        return ret;
    }

    public static WindowSettings windowSettings(QueryParams qpar, boolean isCsv) {
        return windowSettings(qpar.getFirstResultToShow(), qpar.getNumberOfResultsToShow(), qpar.config(), isCsv);
    }

    public static WindowSettings windowSettings(long firstResultToShow, long numberOfResultsToShow, BLSConfig config, boolean isCsv) {
        long maxSize = WebserviceOperations.getMaxWindowSize(config, isCsv);
        if (numberOfResultsToShow < 0)
            numberOfResultsToShow = isCsv ? maxSize :
                    WebserviceParameter.defaultLong(WebserviceParameter.NUMBER_OF_RESULTS);
        long size = Math.min(numberOfResultsToShow, maxSize);
        if (firstResultToShow < 0)
            firstResultToShow = 0;
        return new WindowSettings(firstResultToShow, size);
    }

    public static DocProperty docGroupProperty(BlackLabIndex index, String groupBy) throws BlsException {
        DocProperty groupProp;
        if (groupBy == null)
            return null;
        groupProp = DocProperty.deserialize(index, groupBy);
        if (groupProp == null)
            throw new BadRequest("UNKNOWN_GROUP_PROPERTY", "Unknown group property '" + groupBy + "'.");
        return groupProp;
    }

    public static DocGroupProperty docGroupSortProperty(String groupBy, String sortBy, String viewGroup) {
        DocGroupProperty sortProp = null;
        if (groupBy != null) {
            if (sortBy != null && viewGroup == null) {
                // Sorting refers to results within the group when viewing contents of a group
                sortProp = DocGroupProperty.deserialize(sortBy);
            }
        }
        if (sortProp == null) {
            // By default, show largest group first
            sortProp = new DocGroupPropertySize();
        }
        return sortProp;
    }

    public static DocProperty docSortProperty(BlackLabIndex index, String groupBy, String sortBy, String viewGroup) {
        if (groupBy != null && viewGroup == null) {
            // looking at groups; don't bother sorting the underlying
            // results themselves (sorting is explicitly ignored anyway in ResultsGrouper::init)
            return null;
        }
        return sortBy == null ? null : DocProperty.deserialize(index, sortBy);
    }

    public static HitGroupProperty hitGroupSortProperty(WebserviceOperation operation, String groupBy, String sortBy, String viewGroup, HitGroupProperty defaultSortBy) {
        if (operation.isDocsOperation())
            return defaultSortBy;
        HitGroupProperty sortProp = null;
        if (groupBy != null) {
            if (sortBy != null && viewGroup == null) { // Sorting refers to results within the group when viewing contents of a group
                sortProp = HitGroupProperty.deserialize(sortBy);
            }
        }
        if (sortProp == null) {
            // By default, show largest group first
            sortProp = defaultSortBy;
        }
        return sortProp;
    }

    public static HitProperty hitsSortProperty(WebserviceOperation operation, AnnotatedField field, String groupBy, String viewGroup, String sortBy, ContextSize contextSize) {
        if (operation.isDocsOperation())
            return null;
        if (groupBy != null && viewGroup == null) {
            // looking at groups, or results within a group, don't bother sorting the underlying results
            // themselves (sorting is explicitly ignored anyway in ResultsGrouper::init)
            return null;
        }
        return sortBy == null ? null : HitProperty.deserialize(field, sortBy, contextSize);
    }

    public static SampleParameters sampleParams(Double sampleFraction, Long sampleNum, Long sampleSeed) {
        if (sampleFraction == null && sampleNum == null)
            return null;
        boolean withSeed = sampleSeed != null;
        SampleParameters p;
        if (sampleFraction != null) {
            double fraction = Math.max(Math.min(sampleFraction, 100), 0) / 100.0;
            if (withSeed)
                p = SampleParameters.percentage(fraction, sampleSeed);
            else
                p = SampleParameters.percentage(fraction);
        } else {
            if (withSeed) {
                p = SampleParameters.fixedNumber(sampleNum, sampleSeed);
            } else
                p = SampleParameters.fixedNumber(sampleNum);
        }
        return p;
    }

    public static SearchSettings searchSettings(long maxRetrieve, long maxCount, int fiMatchFactor, BLSConfig config) {
        long maxHitsToProcessAllowed = config.getParameters().getProcessHits().getMax();
        if (maxHitsToProcessAllowed >= 0
                && maxRetrieve > maxHitsToProcessAllowed) {
            maxRetrieve = maxHitsToProcessAllowed;
        }
        long maxHitsToCountAllowed = config.getParameters().getCountHits().getMax();
        if (maxHitsToCountAllowed >= 0
                && maxCount > maxHitsToCountAllowed) {
            maxCount = maxHitsToCountAllowed;
        }
        return SearchSettings.get(maxRetrieve, maxCount, fiMatchFactor);
    }

    public static ContextSettings contextSettings(ContextSize context, ConcordanceType concType, BLSConfig config) {
        context = context.clampedTo(
                config.getParameters().getContextSize().getMaxInt());
        return new ContextSettings(context, concType);
    }

    public static boolean useCache(boolean useCache, boolean debugMode) {
        return !debugMode || useCache;
    }



    // -------- Create Search instances -----------

    /**
     * Get the annotated field we want to search.
     *
     * Uses the main field if none was specified or the specified field doesn't exist,
     * so we can always return a valid field.
     *
     * Uses the optional "searchfield" parameter if present, or the
     * "field" parameter otherwise. This is only relevant for document contents (snippets)
     * requests on parallel corpora, where you may ask for a snippet from a different annotated
     * field than was searched.
     *
     * @param index corpus we're searching
     * @param fieldName field to find
     * @param overrideFieldName if set, try to find this field first; only use fieldName if not found
     * @return the annotated field
     */
    public static AnnotatedField getSearchField(BlackLabIndex index, String fieldName, String overrideFieldName) {
        AnnotatedField annotatedField = null;
        if (overrideFieldName != null)
            annotatedField = resolveFieldName(index, overrideFieldName).orElse(null);
        if (annotatedField == null) {
            Optional<AnnotatedField> field = resolveFieldName(index, fieldName);
            annotatedField = field.orElseGet(index::mainAnnotatedField);
        }
        return annotatedField;
    }

    /**
     * Get the annotated field for this operation.
     *
     * Uses the main field if none was specified or the specified field doesn't exist,
     * so we can always return a valid field.
     *
     * (see also {@link #getSearchField(BlackLabIndex, String, String)}, which also looks at the optional "searchfield"
     *  parameter in addition to the "field" parameter this method looks at)
     *
     * @return the annotated field
     */
    public static AnnotatedField getAnnotatedField(BlackLabIndex index, String fieldName) {
        return getSearchField(index, fieldName, null);
    }

    /** Find annotated field by the specified name or version name (parallel).
     *
     * @param fieldName the field name (or field version in a parallel corpus)
     */
    private static Optional<AnnotatedField> resolveFieldName(BlackLabIndex index, String fieldName) {
        AnnotatedField field = index.annotatedField(fieldName);
        if (field == null) {
            // See if field is actually a different version in a parallel corpus of the main field, e.g. "nl" if the
            // field is "contents__nl"
            String fieldVersion = AnnotatedFieldNameUtil.changeParallelFieldVersion(index.mainAnnotatedField().name(),
                    fieldName);
            field = index.annotatedField(fieldVersion);
        }
        return field == null ? Optional.empty() : Optional.of(field);
    }

    /**
     * Given a JSON config, instantiate the HitGroupScorer.
     *
     * @param field field we're searching
     * @param jsonScorerConfig JSON object with name, type and parameters for the scorer
     * @return scorer
     */
    private static HitGroupScorer getHitGroupScorerFromJsonParam(AnnotatedField field, String jsonScorerConfig) {
        try {
            Map<String, Object> config = (Map<String, Object>)Json.getJsonObjectMapper().readValue(jsonScorerConfig,
                    Map.class);

            // HitGroupScorer expects Query and TextPattern, so parse those from the string values in the config if needed
            Map<String, Object> configParsed = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : config.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (key.equals(HitGroupCollocationScorer.KEY_DOC_FILTER) && value instanceof String) {
                    // Parse doc filter query into a Query object
                    value = BlsUtils.parseFilter(field.index(), value.toString(), WebserviceParameter.defaultString(WebserviceParameter.FILTER_LANGUAGE));
                } else if (key.equals(HitGroupCollocationScorer.KEY_PATTERN) && value instanceof String) {
                    // Parse BCQL pattern into a TextPattern object
                    value = BcqlQueryLanguageParser.parseQuery(value.toString());
                }
                configParsed.put(key, value);
            }

            return HitGroupScorer.fromConfig(field, configParsed);
        } catch (JsonProcessingException e) {
            throw new BadRequest("INVALID_SCORER",
                    "The scorer parameter does not have the correct JSON structure, please consult the documentation: "
                            + e.getMessage(), e);
        }
    }

    public static HitGroupScorer getHitGroupScorer(AnnotatedField field, String scorer) {
        return Optional.ofNullable(scorer)
                .map(config -> getHitGroupScorerFromJsonParam(field, config))
                .orElse(HitGroupScorer.NONE);
    }

    public static HitProperty getHitsGroupProperty(WebserviceOperation operation, String groupBy,
            AnnotatedField annotatedField, ContextSize context) {
        if (groupBy == null || operation.isDocsOperation()) {
            // No grouping requested or not a hits grouping
            return null;
        }
        HitProperty prop = HitProperty.deserialize(annotatedField, groupBy, context);
        if (prop == null)
            throw new BadRequest("UNKNOWN_GROUP_PROPERTY", "Unknown group property '" + groupBy + "'.");
        return prop;
    }

    public static Query filterQuery(QueryParams qpar) throws BlsException {
        return filterQuery(index(qpar.getCorpusName()), qpar.getDocumentFilterLanguage(),
                qpar.getDocumentFilterQuery(),
                qpar.getDocPid(), qpar.getFallbackFilterQuery());
    }

    /**
     * Parse the filter parameter (document filter query).
     *
     * Uses docPid (if specified), otherwise filter/filterLang.
     *
     * @param index index we're searching
     * @param filterLang filter query language (e.g. "lucene")
     * @param filterQuery filter query string
     * @param docPid filter on this specific document (ignore filterQuery)
     * @param fallbackFilterQuery optional filter query to use if no filter query or docPid
     * @return document filter query
     */
    public static Query filterQuery(BlackLabIndex index, String filterLang, String filterQuery, String docPid, Query fallbackFilterQuery) throws BlsException {
        Query result;
        if (!StringUtils.isEmpty(docPid)) {
            // Only hits in 1 doc (for highlighting)
            int luceneDocId = index.getDocIdFromPid(docPid);
            if (luceneDocId < 0)
                throw new NotFound("DOC_NOT_FOUND", "Document with pid '" + docPid + "' not found.");
            result = new SingleDocIdFilter(luceneDocId);
        } else if (!StringUtils.isEmpty(filterQuery)) {
            result = BlsUtils.parseFilter(index, filterQuery, filterLang);
        } else
            result = fallbackFilterQuery;
        return result;
    }

}
