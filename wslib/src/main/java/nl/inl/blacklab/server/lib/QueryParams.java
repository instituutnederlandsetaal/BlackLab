package nl.inl.blacklab.server.lib;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.server.config.BLSConfigParameters;
import nl.inl.blacklab.server.index.IndexManager;
import nl.inl.blacklab.server.lib.results.ApiVersion;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.webservice.WebserviceParameter;

/** API-independent interface to BlackLab operation parameters.
 *
 * <p>In addition to parameters, also knows about SearchManager, IndexManager and User,
 * which are useful to pass around with the parameters.
 *
 * <p>This class only includes "plain" parameters, not any objects derived from them.
 */
public interface QueryParams {

    /**
     * Get a view of the parameters.
     *
     * @return the view
     */
    Map<WebserviceParameter, String> getParameters();

    String getCorpusName();

    String getPattern();

    String getPattLanguage();

    String getPattGapData();

    SearchManager getSearchManager();

    default BLSConfigParameters configParam() {
        return getSearchManager().config().getParameters();
    }

    default IndexManager getIndexManager() { return getSearchManager().getIndexManager(); }

    User getUser();

    String getDocPid();

    String getDocumentFilterQuery();

    String getDocumentFilterLanguage();

    String getHitFilterCriterium();

    String getHitFilterValue();

    Optional<Double> getSampleFraction();

    Optional<Integer> getSampleNumber();

    Optional<Long> getSampleSeed();

    boolean getUseCache();

    int getForwardIndexMatchFactor();

    long getMaxRetrieve();

    long getMaxCount();

    long getFirstResultToShow();

    Optional<Long> optNumberOfResultsToShow();

    long getNumberOfResultsToShow();

    ContextSize getContext();

    ConcordanceType getConcordanceType();

    boolean getIncludeGroupContents();

    boolean getOmitEmptyCaptures();

    Optional<String> getFacetProps();

    Optional<String> getGroupProps();

    Optional<String> getSortProps();

    Optional<String> getViewGroup();

    /**
     * Which annotations to list actual or available values for in hit results/hit exports/indexmetadata requests.
     * IDs are not validated and may not actually exist!
     *
     * @return which annotations to list
     */
    Collection<String> getListValuesFor();

    /**
     * Which metadata fields to list actual or available values for in search results/result exports/indexmetadata requests.
     * IDs are not validated and may not actually exist!
     *
     * @return which metadata fields to list
     */
    Collection<String> getListMetadataValuesFor();

    boolean getWaitForTotal();

    boolean getIncludeTokenCount();

    boolean getIncludeCustomInfo();

    boolean getCsvIncludeSummary();

    boolean getCsvDeclareSeparator();

    String getCsvDescription();

    boolean getExplain();

    boolean getSensitive();

    int getWordStart();

    int getWordEnd();

    Optional<Integer> getHitStart();

    int getHitEnd();

    String getAutocompleteTerm();

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
    default WebserviceOperation getOperation() { return WebserviceOperation.NONE; }

    default Optional<String> getInputFormat() { return Optional.empty(); }

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
}
