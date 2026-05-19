package nl.inl.blacklab.server.lib;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.server.config.BLSConfig;
import nl.inl.blacklab.server.lib.results.ApiVersion;
import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.webservice.WebserviceParameter;

/** BlackLab API endpoint parameters.
 * <p>
 * This only manages the "plain" parameters (i.e. string, int, enum value, etc.),
 * not any complex objects (such as TextPattern or Search instances) derived from them.
 */
public interface QueryParams extends ParamsForResponse {

    /** Config, for determing some parameter defaults */
    BLSConfig config();

    /** Is this a debug request? If not, we may not see cache info or override the FI match factor. */
    boolean debugMode();

    /** Filter query to use if filter parameter not specified, if any. Used with Solr.  */
    default Query getFallbackFilterQuery() { return null; }

    String getCorpusName();

    /** Get the BCQL query passed in the "patt" parameter */
    String getPattern();

    String getPattLanguage();

    String getPattGapData();

    String getDocPid();

    String getDocumentFilterQuery();

    String getDocumentFilterLanguage();

    String getHitFilterCriterium();

    String getHitFilterValue();

    Optional<Double> getSampleFraction();

    Optional<Long> getSampleNumber();

    Optional<Long> getSampleSeed();

    boolean getUseCache();

    int getForwardIndexMatchFactor();

    long getMaxRetrieve();

    long getMaxCount();

    long getFirstResultToShow();

    long getNumberOfResultsToShow();

    String getContextParam();

    ConcordanceType getConcordanceType();

    Optional<Boolean> optIncludeGroupContents();

    Optional<Boolean> optOmitEmptyCaptures();

    Optional<String> getFacetProps();

    Optional<String> getGroupBy();

    Optional<String> getSortBy();

    Optional<String> getViewGroup();

    /**
     * Which annotations to list actual or available values for in hit results/hit exports/indexmetadata requests.
     * IDs are not validated and may not actually exist!
     *
     * @return which annotations to list
     */
    List<String> getListValuesFor();

    /**
     * Which metadata fields to list actual or available values for in search results/result exports/indexmetadata requests.
     * IDs are not validated and may not actually exist!
     *
     * @return which metadata fields to list
     */
    List<String> getListMetadataValuesFor();

    List<String> getListSpanAttributes();

    boolean getWaitForTotal();

    boolean getIncludeSubcorpusSize();

    boolean getIncludeCustomInfo();

    boolean getCsvIncludeSummary();

    boolean getCsvDeclareSeparator();

    String getCsvDescription();

    boolean getExplain();

    Optional<Boolean> optSensitive();

    int getWordStart();

    int getWordEnd();

    Optional<Integer> getHitStart();

    int getHitEnd();

    String getTerm();
    
    String getAutocompleteType();

    String getRelClasses();

    boolean getRelOnlySpans();

    boolean getRelSeparateSpans();

    long getLimitValues();

    boolean isCalculateCollocations();

    String getAnnotationName();

    Set<String> getTerms();

    boolean isIncludeDebugInfo();

    String getFieldName();

    Optional<String> getSearchFieldName();

    /**
     * Get the operation, for webservices that pass operation via a parameter.
     * <p>
     * For example, BLS chooses an operation based on the URL path, and doesn't use this method.
     *
     * @return requested operation
     */
    WebserviceOperation getOperation();

    Optional<String> getInputFormat();

    /** Get extra converters specification (JSON) */
    Optional<String> getConverters();

    /** Get scorer specification (JSON) */
    Optional<String> getScorer();

    /**
     * Should the responses include deprecated field information?
     * <p>
     * A few requests would always include information that was not specific to that request,
     * and available elsewhere, like metadata field groups, special fields, and metadata display names.
     * This toggle is for applications that rely on these deprecated parts of the response.
     * Caution, this will be removed in the future.
     *
     * @return should we include deprecated field info?
     */
    ApiVersion apiCompatibility();

    /**
     * Should relations queries automatically be adjusted so the hit covers all words involved in the relation?
     *
     * @return should we auto-adjust relations?
     */
    boolean getAdjustRelationHits();

    /**
     * Should we include all overlapping spans in the response?
     *
     * @return true if we should include all overlapping spans
     */
    boolean getWithSpans();

    /** Override some of the parameters.
     *
     * @param overrides parameters to override
     * @return new QueryParams with the given parameters overridden
     */
    default QueryParams withOverrides(Map<WebserviceParameter, Object> overrides) {
        Map<WebserviceParameter, Object> typedParams = new LinkedHashMap<>(getTypedParameters());
        typedParams.putAll(overrides);
        return new QueryParamsMap(getCorpusName(), null, typedParams, config(), debugMode());
    }


}
