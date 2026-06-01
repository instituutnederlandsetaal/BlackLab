package nl.inl.blacklab.server.lib;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.server.config.BLSConfig;
import nl.inl.blacklab.server.lib.results.ApiVersion;
import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.webservice.WebserviceParameter;

/**
 * Abstract implementation of QueryParams that uses request parameters.
 * This is used for both BLS and Solr.
 */
public abstract class QueryParamsAbstract implements QueryParams {

    protected final String corpusName;

    /** Config, for determining some default values */
    private final BLSConfig config;

    /** Is this a debug request? If not, we may not see cache info or override the FI match factor. */
    boolean debugMode;

    /** Get config, for determining some default values */
    @Override
    public BLSConfig config() {
        return config;
    }

    @Override
    public boolean debugMode() {
        return debugMode;
    }

    protected QueryParamsAbstract(String corpusName, BLSConfig config, boolean debugMode) {
        this.corpusName = corpusName;
        this.config = config;
        this.debugMode = debugMode;
    }

    protected static double parseDouble(String value) {
        if (value != null) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                // ok, just return default
            }
        }
        return 0.0;
    }

    private static int parseInt(String value) {
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // ok, just return default
            }
        }
        return 0;
    }

    protected static long parseLong(String value) {
        if (value != null) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                // ok, just return default
            }
        }
        return 0;
    }

    protected static boolean parseBoolean(String value) {
        if (value != null) {
            switch (value) {
            case "true":
            case "1":
            case "yes":
            case "on":
                return true;
            case "false":
            case "0":
            case "no":
            case "off":
            default:
                return false;
            }
        }
        return false;
    }

    /**
     * Was a value for this parameter explicitly passed?
     *
     * This disregards any default values configured for the parameter,
     * and only checks if this request included a value for it.
     *
     * @param par parameter type
     * @return true if this request included an explicit value for the parameter
     */
    protected abstract boolean has(WebserviceParameter par);

    /**
     * Get the parameter value.
     *
     * If this request didn't include an explicit value, use the configured default value.
     *
     * @param par parameter type
     * @return value
     */
    protected abstract String get(WebserviceParameter par);

    /**
     * Get parameter value as a boolean.
     *
     * If not explicitly set, uses the configured default value, or false if none configured.
     *
     * @param par parameter type
     * @return value
     */
    protected boolean getBool(WebserviceParameter par) {
        String value = get(par);
        return parseBoolean(value);
    }

    /**
     * Get parameter value as an integer.
     *
     * If not explicitly set, uses the configured default value, or 0 if none configured.
     *
     * @param par parameter type
     * @return value
     */
    protected int getInt(WebserviceParameter par) {
        return QueryParamsAbstract.parseInt(get(par));
    }

    /**
     * Get parameter value as a long.
     *
     * If not explicitly set, uses the configured default value, or 0 if none configured.
     *
     * @param par parameter type
     * @return value
     */
    protected long getLong(WebserviceParameter par) {
        return QueryParamsAbstract.parseLong(get(par));
    }

    /**
     * Get parameter value as a set of strings.
     *
     * If not explicitly set, uses the configured default value, or an empty set if none configured.
     *
     * @param par parameter type
     * @return value
     */
    protected Set<String> getSet(WebserviceParameter par) {
        return new LinkedHashSet<>(getList(par));
    }

    /**
     * Get parameter value as a list of strings.
     *
     * If not explicitly set, uses the configured default value, or an empty set if none configured.
     *
     * @param par parameter type
     * @return value
     */
    protected List<String> getList(WebserviceParameter par) {
        String val = get(par).trim();
        if (StringUtils.isEmpty(val))
            return List.of();
        return Arrays.stream(val.split(",")).map(String::trim).toList();
    }

    /**
     * Get parameter value if it was explicitly passed with the request.
     *
     * If not explicitly set, will return an empty Optional.
     *
     * @param par parameter type
     * @return value if set
     */
    protected Optional<String> opt(WebserviceParameter par) {
        return has(par) ? Optional.of(get(par)) : Optional.empty();
    }

    /**
     * Get parameter value if it was explicitly passed with the request.
     *
     * If not explicitly set, will return an empty Optional.
     *
     * @param par parameter type
     * @return value if set
     */
    protected Optional<Double> optDouble(WebserviceParameter par) {
        return opt(par).map(QueryParamsAbstract::parseDouble);
    }

    /**
     * Get parameter value if it was explicitly passed with the request.
     *
     * If not explicitly set, will return an empty Optional.
     *
     * @param par parameter type
     * @return value if set
     */
    protected Optional<Integer> optInteger(WebserviceParameter par) {
        return opt(par).map(QueryParamsAbstract::parseInt);
    }

    /**
     * Get parameter value if it was explicitly passed with the request.
     *
     * If not explicitly set, will return an empty Optional.
     *
     * @param par parameter type
     * @return value if set
     */
    protected Optional<Long> optLong(WebserviceParameter par) {
        return opt(par).map(QueryParamsAbstract::parseLong);
    }

    /**
     * Get parameter value if it was explicitly passed with the request.
     *
     * If not explicitly set, will return an empty Optional.
     *
     * @param par parameter type
     * @return value if set
     */
    protected Optional<Boolean> optBool(WebserviceParameter par) {
        return opt(par).map(QueryParamsAbstract::parseBoolean);
    }

    @Override
    public String getPattern() { return get(WebserviceParameter.PATTERN); }

    @Override
    public String getPattLanguage() { return get(WebserviceParameter.PATTERN_LANGUAGE); }

    @Override
    public String getPattGapData() { return get(WebserviceParameter.PATTERN_GAP_DATA); }

    @Override
    public Optional<String> getCollocatePattern() {
        return opt(WebserviceParameter.COLLOCATE_PATTERN);
    }

    @Override
    public Optional<CollocationType> getCollocationType() {
        String s = get(WebserviceParameter.COLLOCATION_TYPE);
        if (!StringUtils.isEmpty(s))
            return Optional.of(CollocationType.fromStringValue(s));
        else
            return Optional.empty();
    }

    @Override
    public Optional<String> getRelationType() {
        return opt(WebserviceParameter.RELATION_TYPE);
    }

    @Override
    public String getDocPid() { return get(WebserviceParameter.DOC_PID); }

    @Override
    public String getDocumentFilterQuery() { return get(WebserviceParameter.FILTER); }

    @Override
    public String getDocumentFilterLanguage() { return get(WebserviceParameter.FILTER_LANGUAGE); }

    @Override
    public String getHitFilterCriterium() { return get(WebserviceParameter.HIT_FILTER_CRITERIUM); }

    @Override
    public String getHitFilterValue() { return get(WebserviceParameter.HIT_FILTER_VALUE); }

    @Override
    public Optional<Double> getSampleFraction() { return optDouble(WebserviceParameter.SAMPLE); }

    @Override
    public Optional<Long> getSampleNumber() { return optLong(WebserviceParameter.SAMPLE_NUMBER); }

    @Override
    public Optional<Long> getSampleSeed() { return optLong(WebserviceParameter.SAMPLE_SEED); }

    @Override
    public boolean getUseCache() { return getBool(WebserviceParameter.USE_CACHE); }

    @Override
    public int getForwardIndexMatchFactor() { return getInt(WebserviceParameter.FORWARD_INDEX_MATCHING_SETTING); }

    @Override
    public long getMaxRetrieve() { return getLong(WebserviceParameter.MAX_HITS_TO_RETRIEVE); }

    @Override
    public long getMaxCount() { return getLong(WebserviceParameter.MAX_HITS_TO_COUNT); }

    @Override
    public long getFirstResultToShow() { return getLong(WebserviceParameter.FIRST_RESULT); }

    @Override
    public long getNumberOfResultsToShow() {
        // NOTE: this is NOT the same as optNumberOfResultsToShow.orElse(0L) because
        //       getLong() sets the configured default value if "number" param is not set
        //       (yes, this is smelly)
        return getLong(WebserviceParameter.NUMBER_OF_RESULTS);
    }

    @Override
    public String getContextParam() {
        // ("wordsaroundhit" is deprecated, now called "context")
        WebserviceParameter par = has(WebserviceParameter.WORDS_AROUND_HIT) ?
                WebserviceParameter.WORDS_AROUND_HIT :
                WebserviceParameter.CONTEXT;
        return get(par);
    }

    @Override
    public ConcordanceType getConcordanceType() {
        return get(WebserviceParameter.CREATE_CONCORDANCES_FROM).equals("orig") ? ConcordanceType.CONTENT_STORE :
                ConcordanceType.FORWARD_INDEX;
    }

    @Override
    public Optional<Boolean> optIncludeGroupContents() {
        return optBool(WebserviceParameter.INCLUDE_GROUP_CONTENTS);
    }

    @Override
    public Optional<Boolean> optOmitEmptyCaptures() {
        return optBool(WebserviceParameter.OMIT_EMPTY_CAPTURES);
    }

    @Override
    public Optional<String> getFacetProps() { return opt(WebserviceParameter.FACETS); }

    @Override
    public Optional<String> getGroupBy() { return opt(WebserviceParameter.GROUP_BY); }

    @Override
    public Optional<String> getSortBy() { return opt(WebserviceParameter.SORT_BY); }

    @Override
    public Optional<String> getViewGroup() { return opt(WebserviceParameter.VIEW_GROUP); }

    /**
     * Which annotations to list actual or available values for in hit results/hit exports/indexmetadata requests.
     * IDs are not validated and may not actually exist!
     *
     * @return which annotations to list
     */
    @Override
    public List<String> getListValuesFor() { return getList(WebserviceParameter.LIST_VALUES_FOR_ANNOTATIONS); }

    /**
     * Which metadata fields to list actual or available values for in search results/result exports/indexmetadata requests.
     * IDs are not validated and may not actually exist!
     *
     * @return which metadata fields to list
     */
    @Override
    public List<String> getListMetadataValuesFor() { return getList(WebserviceParameter.LIST_VALUES_FOR_METADATA_FIELDS); }

    /**
     * Which metadata fields to list actual or available values for in search results/result exports/indexmetadata requests.
     * IDs are not validated and may not actually exist!
     *
     * @return which metadata fields to list
     */
    @Override
    public List<String> getListSpanAttributes() { return getList(WebserviceParameter.LIST_VALUES_FOR_SPAN_ATTR); }

    @Override
    public boolean getWaitForTotal() { return getBool(WebserviceParameter.WAIT_FOR_TOTAL_COUNT); }

    @Override
    public boolean getIncludeSubcorpusSize() {
        return getBool(WebserviceParameter.SUBCORPUS_SIZE);
    }

    @Override
    public boolean getIncludeCustomInfo() {
        return getBool(WebserviceParameter.INCLUDE_CUSTOM_INFO);
    }

    @Override
    public boolean getCsvIncludeSummary() {
        return getBool(WebserviceParameter.CSV_INCLUDE_SUMMARY);
    }

    @Override
    public boolean getCsvDeclareSeparator() {
        return getBool(WebserviceParameter.CSV_DECLARE_SEPARATOR);
    }

    @Override
    public String getCsvDescription() {
        return get(WebserviceParameter.CSV_DESCRIPTION);
    }

    @Override
    public boolean getExplain() { return getBool(WebserviceParameter.EXPLAIN_QUERY_REWRITE); }

    @Override
    public Optional<Boolean> optSensitive() {
        return optBool(WebserviceParameter.SENSITIVE);
    }

    @Override
    public int getWordStart() { return getInt(WebserviceParameter.WORD_START); }

    @Override
    public int getWordEnd() { return getInt(WebserviceParameter.WORD_END); }

    @Override
    public Optional<Integer> getHitStart() { return optInteger(WebserviceParameter.HIT_START); }

    @Override
    public int getHitEnd() { return getInt(WebserviceParameter.HIT_END); }

    @Override
    public String getTerm() { return get(WebserviceParameter.TERM); }

    @Override
    public String getAutocompleteType() { return get(WebserviceParameter.AUTOCOMPLETE_TYPE); }

    @Override
    public String getRelClasses() { return get(WebserviceParameter.REL_CLASSES); }

    @Override
    public boolean getRelOnlySpans() { return getBool(WebserviceParameter.REL_ONLY_SPANS); }

    @Override
    public boolean getRelSeparateSpans() { return getBool(WebserviceParameter.REL_SEPARATE_SPANS); }

    @Override
    public long getLimitValues() { return getLong(WebserviceParameter.LIMIT_VALUES); }

    @Override
    public boolean getAdjustRelationHits() { return getBool(WebserviceParameter.REL_ADJUST_HITS); }

    @Override
    public boolean getWithSpans() { return getBool(WebserviceParameter.WITH_SPANS); }

    @Override
    public boolean isCalculateCollocations() { return get(WebserviceParameter.CALCULATE_STATS).equals("colloc"); }

    @Override
    public String getAnnotationName() {
        return get(WebserviceParameter.ANNOTATION);
    }

    @Override
    public Set<String> getTerms() { return getSet(WebserviceParameter.TERMS); }

    @Override
    public boolean isIncludeDebugInfo() { return getBool(WebserviceParameter.DEBUG); }

    @Override
    public String getFieldName() { return get(WebserviceParameter.FIELD); }

    @Override
    public Optional<String> getSearchFieldName() { return opt(WebserviceParameter.SEARCH_FIELD); }

    @Override
    public WebserviceOperation getOperation() {
        String strOp = get(WebserviceParameter.OPERATION);
        WebserviceOperation op = WebserviceOperation.fromValue(strOp)
                .orElseThrow(() -> new UnsupportedOperationException("Unsupported operation '" + strOp + "'"));

        // BLS has /hits and /docs paths for both ungrouped and grouped operations, so the two WebserviceOperations
        // are kind of interchangeable at the moment (the proxy will only send op=hits or op=docs, even for grouped
        // requests).
        // Here we make sure we send the specific value appropriate to the rest of the parametesr, so responses are
        // consistent (important for CI testing, among other things)
        boolean isGroupResponse = has(WebserviceParameter.GROUP_BY) && !has(WebserviceParameter.VIEW_GROUP);
        if (op == WebserviceOperation.DOCS || op == WebserviceOperation.DOCS_GROUPED) {
            op = isGroupResponse ? WebserviceOperation.DOCS_GROUPED : WebserviceOperation.DOCS;
        } else if (op == WebserviceOperation.HITS || op == WebserviceOperation.HITS_GROUPED) {
            op = isGroupResponse ? WebserviceOperation.HITS_GROUPED : WebserviceOperation.HITS;
        }

        return op;
    }

    @Override
    public Optional<String> getInputFormat() { return opt(WebserviceParameter.INPUT_FORMAT); }

    @Override
    public Optional<String> getConverters() {
        return opt(WebserviceParameter.CONVERTERS);
    }

    @Override
    public Optional<String> getScorer() {
        return opt(WebserviceParameter.SCORER);
    }

    @Override
    public Optional<String> getScorerType() {
        return opt(WebserviceParameter.SCORER_TYPE);
    }

    @Override
    public ApiVersion apiCompatibility() {
        ApiVersion apiVersion = ApiVersion.fromValue(get(WebserviceParameter.API_VERSION));
        if (apiVersion.getMajor() < 3)
            throw new UnsupportedOperationException("API version " + apiVersion + " is no longer supported");
        return apiVersion;
    }

    @Override
    public String getCorpusName() { return corpusName; }
}
