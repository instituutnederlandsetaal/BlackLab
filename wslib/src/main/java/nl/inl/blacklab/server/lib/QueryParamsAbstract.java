package nl.inl.blacklab.server.lib;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.server.lib.results.ApiVersion;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.webservice.WebserviceParameter;

/**
 * Abstract implementation of QueryParams that uses request parameters.
 * This is used for both BLS and Solr.
 */
public abstract class QueryParamsAbstract implements QueryParams {

    protected final SearchManager searchMan;

    protected final User user;

    protected final String corpusName;

    public QueryParamsAbstract(String corpusName, SearchManager searchMan, User user) {
        this.searchMan = searchMan;
        this.user = user;
        this.corpusName = corpusName;
    }

    private static double parseDouble(String value) {
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

    private static long parseLong(String value) {
        if (value != null) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                // ok, just return default
            }
        }
        return 0;
    }

    private static boolean parseBoolean(String value) {
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
        String val = get(par).trim();
        return StringUtils.isEmpty(val) ?
                Collections.emptySet() :
                new HashSet<>(Arrays.asList(val.split("\\s*,\\s*")));
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

    @Override
    public String getPattern() { return get(WebserviceParameter.PATTERN); }

    @Override
    public String getPattLanguage() { return get(WebserviceParameter.PATTERN_LANGUAGE); }

    @Override
    public String getPattGapData() { return get(WebserviceParameter.PATTERN_GAP_DATA); }

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
    public Optional<Integer> getSampleNumber() { return optInteger(WebserviceParameter.SAMPLE_NUMBER); }

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
    public Optional<Long> optNumberOfResultsToShow() { return optLong(WebserviceParameter.NUMBER_OF_RESULTS); }

    @Override
    public long getNumberOfResultsToShow() {
        // NOTE: this is NOT the same as optNumberOfResultsToShow.orElse(0L) because
        //       getLong() sets the configured default value if "number" param is not set
        //       (yes, this is smelly)
        return getLong(WebserviceParameter.NUMBER_OF_RESULTS);
    }

    public ContextSize getContext() {
        // ("wordsaroundhit" is deprecated, now called "context")
        WebserviceParameter par = has(WebserviceParameter.WORDS_AROUND_HIT) ?
                WebserviceParameter.WORDS_AROUND_HIT :
                WebserviceParameter.CONTEXT;
        String str = get(par);
        int before = 0, after = 0;
        String inlineTagName = null;
        if (str.matches("\\d+")) {
            before = after = Integer.parseInt(str);
        } else if (str.matches("\\d+:\\d+")) {
            String[] parts = str.split(":");
            before = Integer.parseInt(parts[0]);
            after = Integer.parseInt(parts[1]);
        } else if (str.matches("\\w+")) {
            inlineTagName = str;
        } else {
            throw new IllegalArgumentException("Invalid context value: " + str);
        }

        int maxContextSize = getSearchManager().config().getParameters().getContextSize().getMaxInt();

        return ContextSize.get(before, after, true, inlineTagName, ContextSize.maxSnippetLengthFromMaxContextSize(maxContextSize));
    }

    @Override
    public ConcordanceType getConcordanceType() {
        return get(WebserviceParameter.CREATE_CONCORDANCES_FROM).equals("orig") ? ConcordanceType.CONTENT_STORE :
                ConcordanceType.FORWARD_INDEX;
    }

    @Override
    public boolean getIncludeGroupContents() { return getBool(WebserviceParameter.INCLUDE_GROUP_CONTENTS); }

    @Override
    public boolean getOmitEmptyCaptures() { return getBool(WebserviceParameter.OMIT_EMPTY_CAPTURES); }

    @Override
    public Optional<String> getFacetProps() { return opt(WebserviceParameter.INCLUDE_FACETS); }

    public Optional<String> getGroupProps() { return opt(WebserviceParameter.GROUP_BY); }

    @Override
    public Optional<String> getSortProps() { return opt(WebserviceParameter.SORT_BY); }

    @Override
    public Optional<String> getViewGroup() { return opt(WebserviceParameter.VIEW_GROUP); }

    /**
     * Which annotations to list actual or available values for in hit results/hit exports/indexmetadata requests.
     * IDs are not validated and may not actually exist!
     *
     * @return which annotations to list
     */
    @Override
    public Set<String> getListValuesFor() { return getSet(WebserviceParameter.LIST_VALUES_FOR_ANNOTATIONS); }

    /**
     * Which metadata fields to list actual or available values for in search results/result exports/indexmetadata requests.
     * IDs are not validated and may not actually exist!
     *
     * @return which metadata fields to list
     */
    @Override
    public Set<String> getListMetadataValuesFor() { return getSet(WebserviceParameter.LIST_VALUES_FOR_METADATA_FIELDS); }

    @Override
    public boolean getWaitForTotal() { return getBool(WebserviceParameter.WAIT_FOR_TOTAL_COUNT); }

    @Override
    public boolean getIncludeTokenCount() {
        return getBool(WebserviceParameter.INCLUDE_TOKEN_COUNT);
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
    public boolean getSensitive(boolean defaultValue) {
        return has(WebserviceParameter.SENSITIVE) ? getBool(WebserviceParameter.SENSITIVE) : defaultValue;
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
    public String getAutocompleteTerm() { return get(WebserviceParameter.TERM); }

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
        String annotName = get(WebserviceParameter.ANNOTATION);
        if (annotName.length() == 0 && has(WebserviceParameter.PROPERTY))
            annotName = get(WebserviceParameter.PROPERTY); // old parameter name, deprecated
        return annotName;
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
    public ApiVersion apiCompatibility() {
        ApiVersion apiVersion = ApiVersion.fromValue(get(WebserviceParameter.API_VERSION));
        if (apiVersion.getMajor() < 3)
            throw new UnsupportedOperationException("API version " + apiVersion + " is no longer supported");
        return apiVersion;
    }

    @Override
    public SearchManager getSearchManager() { return searchMan; }

    @Override
    public User getUser() { return user; }

    @Override
    public String getCorpusName() { return corpusName; }
}
