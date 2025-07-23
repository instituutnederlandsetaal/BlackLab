package nl.inl.blacklab.server.lib;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.Constants;
import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocGroupProperty;
import nl.inl.blacklab.resultproperty.DocGroupPropertySize;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.HitGroupProperty;
import nl.inl.blacklab.resultproperty.HitGroupPropertySize;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.extensions.XFRelations;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.SampleParameters;
import nl.inl.blacklab.search.results.SearchSettings;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternPositionFilter;
import nl.inl.blacklab.searches.SearchCount;
import nl.inl.blacklab.searches.SearchDocGroups;
import nl.inl.blacklab.searches.SearchDocs;
import nl.inl.blacklab.searches.SearchEmpty;
import nl.inl.blacklab.searches.SearchFacets;
import nl.inl.blacklab.searches.SearchHitGroups;
import nl.inl.blacklab.searches.SearchHits;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.jobs.ContextSettings;
import nl.inl.blacklab.server.jobs.DocGroupSettings;
import nl.inl.blacklab.server.jobs.DocGroupSortSettings;
import nl.inl.blacklab.server.jobs.DocSortSettings;
import nl.inl.blacklab.server.jobs.HitGroupSettings;
import nl.inl.blacklab.server.jobs.HitGroupSortSettings;
import nl.inl.blacklab.server.jobs.HitSortSettings;
import nl.inl.blacklab.server.jobs.WindowSettings;
import nl.inl.blacklab.server.lib.results.ApiVersion;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.webservice.WebserviceParameter;

/**
 * Wraps the QueryParams and interprets them to create searches.
 */
public class WebserviceParamsImpl implements WebserviceParams {

    /**
     * Get the search-related parameters from the request object.
     * This ignores stuff like the requested output type, etc.
     * Note also that the request type is not part of the SearchParameters, so from
     * looking at these parameters alone, you can't always tell what type of search
     * we're doing. The RequestHandler subclass will add a jobclass parameter when
     * executing the actual search.
     *
     * @param isDocs    is this a docs operation? influences how the "sort" parameter
     *                  is interpreted
     * @param isDebugMode can this client access debug functionality?
     * @param params parameters sent to webservice
     * @return the unique key
     */
    public static WebserviceParamsImpl get(boolean isDocs, boolean isDebugMode, QueryParams params) {
        return new WebserviceParamsImpl(isDocs, isDebugMode, params);
    }

    private final QueryParams params;

    private final boolean debugMode;

    /** The pattern, if parsed already */
    private TextPattern pattern;

    /** The filter query, if parsed already */
    private Query filterQuery;

    private final boolean isDocsOperation;

    private List<DocProperty> facetProps;

    private String overrideDocPid;

    private String overrideAnnotationName;

    private String fieldName;

    private String inputFormat;

    private WebserviceParamsImpl(boolean isDocsOperation, boolean isDebugMode,
            QueryParams params) {
        this.isDocsOperation = isDocsOperation;
        this.debugMode = isDebugMode;
        this.params = params;
    }

    @Override
    public BlackLabIndex blIndex() {
        try {
            return getSearchManager().getIndexManager().getIndex(getCorpusName()).blIndex();
        } catch (Exception e) {
            throw new InvalidIndex(e);
        }
    }

    @Override
    public SearchManager getSearchManager() {
        return params.getSearchManager();
    }

    @Override
    public User getUser() {
        return params.getUser();
    }

    @Override
    public boolean hasPattern() throws BlsException {
        return pattern().isPresent();
    }

    @Override
    public Optional<TextPattern> pattern() throws BlsException {
        if (pattern == null) {
            pattern = WebserviceParamsUtils.parsePattern(blIndex(), getPattern(), getPattLanguage(), getPattGapData());
        }
        return pattern == null ? Optional.empty() : Optional.of(pattern);
    }

    @Override
    public boolean getAdjustRelationHits() {
        return params.getAdjustRelationHits();
    }

    @Override
    public boolean getWithSpans() {
        return params.getWithSpans();
    }

    /** Pattern, optionally within s if context=s was specified. */
    TextPattern patternWithin;

    @Override
    public Optional<TextPattern> patternWithinContextTag() throws BlsException {
        if (patternWithin == null) {
            if (!pattern().isPresent())
                return Optional.empty();
            patternWithin = pattern;
            String tagName = getContext().inlineTagName();
            if (tagName != null) {
                patternWithin = ensureWithinTag(patternWithin, tagName, XFRelations.DEFAULT_CONTEXT_REL_NAME);
            }
        }
        return patternWithin == null ? Optional.empty() : Optional.of(patternWithin);
    }

    private static TextPattern ensureWithinTag(TextPattern pattern, String tagName, String captureRelsAs) {
        boolean withinTag = pattern instanceof TextPatternPositionFilter &&
                ((TextPatternPositionFilter) pattern).isWithinTag(tagName);
        if (!withinTag) {
            // add "within rcapture(<TAGNAME/>)" to the pattern, so we can produce the requested context later
            return TextPattern.createRelationCapturingWithinQuery(pattern, tagName, captureRelsAs);
        }
        return pattern;
    }

    public void setDocPid(String pid) {
        overrideDocPid = pid;
    }

    @Override
    public String getDocPid() {
        if (overrideDocPid != null)
            return overrideDocPid;
        return params.getDocPid();
    }

    @Override
    public Query filterQuery() throws BlsException {
        if (filterQuery == null) {
            filterQuery = WebserviceParamsUtils.parseFilterQuery(blIndex(), getDocPid(), getDocumentFilterQuery(),
                    getDocumentFilterLanguage());
        }
        return filterQuery;
    }

    @Override
    public void setFilterQuery(Query query) {
        this.filterQuery = query;
    }

    /**
     * @return hits - filtered then sorted then sampled then windowed
     */
    @Override
    public SearchHits hitsWindow() throws BlsException {
        WindowSettings windowSettings = windowSettings();
        if (windowSettings == null)
            return hitsSample();
        return hitsSample().window(windowSettings.first(), windowSettings.size());
    }

    @Override
    public WindowSettings windowSettings() {
        long size = Math.min(Math.max(0, getNumberOfResultsToShow()), configParam().getPageSize().getMax());
        return new WindowSettings(getFirstResultToShow(), size);
    }

    @Override
    public boolean getIncludeGroupContents() {
        return params.getIncludeGroupContents() || configParam().isWriteHitsAndDocsInGroupedHits();
    }

    @Override
    public boolean getOmitEmptyCaptures() {
        return params.getOmitEmptyCaptures() || configParam().isOmitEmptyCaptures();
    }

    private DocGroupSettings docGroupSettings() throws BlsException {
        if (!isDocsOperation)
            return null; // we're doing per-hits stuff, so sort doesn't apply to docs
        Optional<String> groupProps = getGroupProps();
        DocProperty groupProp;
        if (groupProps.isEmpty())
            return null;
        groupProp = DocProperty.deserialize(blIndex(), groupProps.get());
        if (groupProp == null)
            throw new BadRequest("UNKNOWN_GROUP_PROPERTY", "Unknown group property '" + groupProps + "'.");
        return new DocGroupSettings(groupProp);
    }

    private DocGroupSortSettings docGroupSortSettings() {
        DocGroupProperty sortProp = null;
        if (isDocsOperation) {
            Optional<String> groupProps = getGroupProps();
            if (groupProps.isPresent()) {
                Optional<String> sortBy = getSortProps();
                Optional<String> viewGroup = getViewGroup();
                if (sortBy.isPresent() && viewGroup.isEmpty()) {
                    // Sorting refers to results within the group when viewing contents of a group
                    sortProp = DocGroupProperty.deserialize(sortBy.get());
                }
            }
        }
        if (sortProp == null) {
            // By default, show largest group first
            sortProp = new DocGroupPropertySize();
        }
        return new DocGroupSortSettings(sortProp);
    }

    private DocSortSettings docSortSettings() {
        if (!isDocsOperation)
            return null; // we're doing per-hits stuff, so sort doesn't apply to docs

        Optional<String> groupProps = getGroupProps();
        if (groupProps.isPresent()) {
            // looking at groups, or results within a group, don't bother sorting the underlying
            // results themselves (sorting is explicitly ignored anyway in ResultsGrouper::init)
            return null;
        }

        Optional<String> sortBy = getSortProps();
        if (sortBy.isEmpty())
            return null;
        DocProperty sortProp = DocProperty.deserialize(blIndex(), sortBy.get());
        if (sortProp == null)
            return null;
        return new DocSortSettings(sortProp);
    }

    private HitGroupSortSettings hitGroupSortSettings() {
        HitGroupProperty sortProp = null;
        if (!isDocsOperation) {
            // not grouping, so no group sort
            Optional<String> groupProps = getGroupProps();
            if (groupProps.isPresent()) {
                Optional<String> sortBy = getSortProps();
                Optional<String> viewGroup = getViewGroup();
                if (sortBy.isPresent() && viewGroup.isEmpty()) { // Sorting refers to results within the group when viewing contents of a group
                    sortProp = HitGroupProperty.deserialize(sortBy.get());
                }
            }
        }
        if (sortProp == null) {
            // By default, show largest group first
            sortProp = HitGroupPropertySize.get();
        }
        return new HitGroupSortSettings(sortProp);
    }

    private HitGroupSettings hitGroupSettings() {
        if (isDocsOperation)
            return null; // we're doing per-hits stuff, so sort doesn't apply to docs
        Optional<String> groupBy = getGroupProps();
        return groupBy.isEmpty() ? null : new HitGroupSettings(groupBy.get());
    }

    @Override
    public HitSortSettings hitsSortSettings() {
        if (isDocsOperation)
            return null; // we're doing per-docs stuff, so sort doesn't apply to hits

        Optional<String> groupBy = getGroupProps();
        if (groupBy.isPresent() && getViewGroup().isEmpty()) {
            // looking at groups, or results within a group, don't bother sorting the underlying results
            // themselves (sorting is explicitly ignored anyway in ResultsGrouper::init)
            return null;
        }

        Optional<String> sortBy = getSortProps();
        if (sortBy.isEmpty())
            return null;
        HitProperty sortProp = HitProperty.deserialize(blIndex(), getAnnotatedField(),
                sortBy.get(), getContext());
        return new HitSortSettings(sortProp);
    }

    @Override
    public SampleParameters sampleSettings() {
        Optional<Double> sample = getSampleFraction();
        Optional<Integer> sampleNum = getSampleNumber();
        if (sample.isEmpty() && sampleNum.isEmpty())
            return null;
        Optional<Long> sampleSeed = getSampleSeed();
        boolean withSeed = sampleSeed.isPresent();
        SampleParameters p;
        if (sample.isPresent()) {
            double fraction = Math.max(Math.min(sample.get(), 100), 0) / 100.0;
            if (withSeed)
                p = SampleParameters.percentage(fraction, sampleSeed.get());
            else
                p = SampleParameters.percentage(fraction);
        } else {
            if (withSeed) {
                p = SampleParameters.fixedNumber(sampleNum.orElse(0), sampleSeed.get());
            } else
                p = SampleParameters.fixedNumber(sampleNum.orElse(0));
        }
        return p;
    }

    private List<DocProperty> facetProps() {
        if (facetProps == null) {
            Optional<String> facets = getFacetProps();
            if (facets.isEmpty()) {
                facetProps = null;
            } else {
                DocProperty propFacets = DocProperty.deserialize(blIndex(), facets.get());
                if (propFacets == null)
                    facetProps = null;
                else {
                    facetProps = new ArrayList<>();
                    facetProps.addAll(propFacets.propsList());
                }
            }
        }
        return facetProps;
    }

    @Override
    public boolean hasFacets() {
        return facetProps() != null;
    }

    @Override
    public SearchSettings searchSettings() {
        long maxRetrieve = getMaxRetrieve();
        long maxCount = getMaxCount();
        long maxHitsToProcessAllowed = configParam().getProcessHits().getMax();
        if (maxHitsToProcessAllowed >= 0
                && maxRetrieve > maxHitsToProcessAllowed) {
            maxRetrieve = maxHitsToProcessAllowed;
        }
        long maxHitsToCountAllowed = configParam().getCountHits().getMax();
        if (maxHitsToCountAllowed >= 0
                && maxCount > maxHitsToCountAllowed) {
            maxCount = maxHitsToCountAllowed;
        }
        return SearchSettings.get(maxRetrieve, maxCount, forwardIndexMatchFactor());
    }

    @Override
    public ContextSettings contextSettings() {
        ContextSize context = getContext().clampedTo(configParam().getContextSize().getMaxInt());
        return new ContextSettings(context, getConcordanceType());
    }

    @Override
    public boolean useCache() {
        return !debugMode || getUseCache();
    }

    private int forwardIndexMatchFactor() {
        return debugMode ? getForwardIndexMatchFactor() : -1;
    }



    // -------- Create Search instances -----------

    /**
     * @return hits - filtered then sorted then sampled
     */
    @Override
    public SearchHits hitsSample() throws BlsException {
        SampleParameters sampleSettings = sampleSettings();
        if (sampleSettings == null)
            return hitsSorted();
        return hitsSorted().sample(sampleSettings);
    }

    /**
     * @return hits - filtered then sorted
     */
    private SearchHits hitsSorted() throws BlsException {
        HitSortSettings hitsSortSettings = hitsSortSettings();
        if (hitsSortSettings == null)
            return hitsFiltered();
        return hitsFiltered().sort(hitsSortSettings.sortBy());
    }

    private SearchHits hitsFiltered() throws BlsException {
        String hitFilterCrit = getHitFilterCriterium();
        String hitFilterVal = getHitFilterValue();
        if (StringUtils.isEmpty(hitFilterCrit) || StringUtils.isEmpty(hitFilterVal))
            return hits();
        HitProperty prop = HitProperty.deserialize(blIndex(), getAnnotatedField(), hitFilterCrit,
                getContext());
        PropertyValue value = PropertyValue.deserialize(blIndex(), getAnnotatedField(), hitFilterVal);
        return hits().filter(prop, value);
    }

    private SearchHits hits() throws BlsException {
        SearchEmpty search = blIndex().search(getSearchField(), useCache());
        try {
            Query filter = filterQuery();
            Optional<TextPattern> pattern = patternWithinContextTag();
            if (pattern.isEmpty())
                throw new BadRequest("NO_PATTERN_GIVEN", "Text search pattern required. Please specify 'patt' parameter.");

            SearchSettings searchSettings = searchSettings();
            return search.find(pattern.get().toQuery(search.queryInfo(), filter,
                    params.getAdjustRelationHits(), params.getWithSpans()), searchSettings);
        } catch (InvalidQuery e) {
            throw new BadRequest("PATT_SYNTAX_ERROR", "Syntax error in CorpusQL pattern: " + e.getMessage());
        }
    }

    /**
     * Get the annotated field we want to search.
     *
     * Uses the main field if none was specified or the specified field doesn't exist,
     * so we can always return a valid field.
     *
     * Uses the optional "searchfield" parameter if present, or the
     * "field" parameter otherwise.
     *
     * @return the annotated field
     */
    @Override
    public AnnotatedField getSearchField() {
        if (params.getSearchFieldName().isPresent())
            return resolveFieldName(params.getSearchFieldName().get()).orElse(getAnnotatedField());
        return getAnnotatedField();
    }

    /**
     * Get the annotated field for this operation.
     *
     * Uses the main field if none was specified or the specified field doesn't exist,
     * so we can always return a valid field.
     *
     * (see also {@link #getSearchField()}, which also looks at the optional "searchfield"
     *  parameter in addition to the "field" parameter this method looks at)
     *
     * @return the annotated field
     */
    @Override
    public AnnotatedField getAnnotatedField() {
        Optional<AnnotatedField> field = resolveFieldName(getFieldName());
        if (field.isEmpty())
            return blIndex().mainAnnotatedField();
        return field.orElse(null);
    }

    /** Find annotated field by the specified name or version name (parallel).
     *
     * @param fieldName the field name (or field version in a parallel corpus)
     */
    private Optional<AnnotatedField> resolveFieldName(String fieldName) {
        AnnotatedField field = blIndex().annotatedField(fieldName);
        if (field == null) {
            // See if field is actually a different version in a parallel corpus of the main field, e.g. "nl" if the
            // field is "contents__nl"
            String fieldVersion = AnnotatedFieldNameUtil.changeParallelFieldVersion(blIndex().mainAnnotatedField().name(),
                    fieldName);
            field = blIndex().annotatedField(fieldVersion);
        }
        return field == null ? Optional.empty() : Optional.of(field);
    }

    @Override
    public SearchDocs docsWindow() throws BlsException {
        WindowSettings windowSettings = windowSettings();
        if (windowSettings == null)
            return docsSorted();
        return docsSorted().window(windowSettings.first(), windowSettings.size());
    }

    @Override
    public SearchDocs docsSorted() throws BlsException {
        DocSortSettings docSortSettings = docSortSettings();
        if (docSortSettings == null)
            return docs();
        return docs().sort(docSortSettings.sortBy());
    }

    @Override
    public SearchCount docsCount() throws BlsException {
        if (hasPattern())
            return hitsSample().docCount();
        return docs().count();
    }

    @Override
    public SearchDocs docs() throws BlsException {
        if (hasPattern())
            return hitsSample().docs(-1);
        Query docFilterQuery = filterQuery();
        if (docFilterQuery == null) {
            docFilterQuery = blIndex().getAllRealDocsQuery();
        }
        SearchEmpty search = blIndex().search(getAnnotatedField(), useCache());
        return search.findDocuments(docFilterQuery);
    }

    /**
     * Return our subcorpus.
     * <p>
     * The subcorpus is defined as all documents satisfying the metadata query.
     * If no metadata query is given, the subcorpus is all documents in the corpus.
     *
     * @return subcorpus
     */
    @Override
    public SearchDocs subcorpus() throws BlsException {
        Query docFilterQuery = filterQuery();
        if (docFilterQuery == null) {
            docFilterQuery = blIndex().getAllRealDocsQuery();
        }
        SearchEmpty search = blIndex().search(getAnnotatedField(), useCache());
        return search.findDocuments(docFilterQuery);
    }

    @Override
    public SearchHitGroups hitsGroupedStats() throws BlsException {
        return hitsSample()
                .groupStats(getHitGroupProperty(), Constants.RESULTS_NO_LIMIT)
                .sort(hitGroupSortSettings().sortBy());
    }

    @Override
    public SearchHitGroups hitsGroupedWithStoredHits() throws BlsException {
        return hitsSample().groupWithStoredHits(getHitGroupProperty(), Constants.RESULTS_NO_LIMIT)
                .sort(hitGroupSortSettings().sortBy());
    }

    private HitProperty getHitGroupProperty() {
        HitGroupSettings hitGroupSettings = hitGroupSettings();
        assert hitGroupSettings != null;
        String groupProps = hitGroupSettings.groupBy();
        HitProperty prop = HitProperty.deserialize(blIndex(), getAnnotatedField(), groupProps,
                getContext());
        if (prop == null)
            throw new BadRequest("UNKNOWN_GROUP_PROPERTY", "Unknown group property '" + groupProps + "'.");
        return prop;
    }

    @Override
    public SearchDocGroups docsGrouped() throws BlsException {
        DocGroupSettings docGroupSettings = docGroupSettings();
        assert docGroupSettings != null;
        return docs().group(docGroupSettings.groupBy(), Constants.RESULTS_NO_LIMIT).sort(docGroupSortSettings().sortBy());
    }

    @Override
    public SearchFacets facets() throws BlsException {
        return docs().facet(facetProps());
    }

    @Override
    public Map<WebserviceParameter, String> getParameters() {
        return params.getParameters();
    }

    @Override
    public String getCorpusName() {
        return params.getCorpusName();
    }

    @Override
    public String getPattern() {
        return params.getPattern();
    }

    @Override
    public String getPattLanguage() {
        return params.getPattLanguage();
    }

    @Override
    public String getPattGapData() {
        return params.getPattGapData();
    }

    @Override
    public String getDocumentFilterQuery() {
        return params.getDocumentFilterQuery();
    }

    @Override
    public String getDocumentFilterLanguage() {
        return params.getDocumentFilterLanguage();
    }

    @Override
    public String getHitFilterCriterium() {
        return params.getHitFilterCriterium();
    }

    @Override
    public String getHitFilterValue() {
        return params.getHitFilterValue();
    }

    @Override
    public Optional<Double> getSampleFraction() {
        return params.getSampleFraction();
    }

    @Override
    public Optional<Integer> getSampleNumber() {
        return params.getSampleNumber();
    }

    @Override
    public Optional<Long> getSampleSeed() {
        return params.getSampleSeed();
    }

    @Override
    public boolean getUseCache() {
        return params.getUseCache();
    }

    @Override
    public int getForwardIndexMatchFactor() {
        return params.getForwardIndexMatchFactor();
    }

    @Override
    public long getMaxRetrieve() {
        return params.getMaxRetrieve();
    }

    @Override
    public long getMaxCount() {
        return params.getMaxCount();
    }

    @Override
    public long getFirstResultToShow() {
        return params.getFirstResultToShow();
    }

    @Override
    public Optional<Long> optNumberOfResultsToShow() {
        return params.optNumberOfResultsToShow();
    }

    @Override
    public long getNumberOfResultsToShow() {
        return params.getNumberOfResultsToShow();
    }

    @Override
    public ContextSize getContext() {
        return params.getContext();
    }

    @Override
    public ConcordanceType getConcordanceType() {
        return params.getConcordanceType();
    }

    @Override
    public Optional<String> getFacetProps() {
        return params.getFacetProps();
    }

    @Override
    public Optional<String> getGroupProps() {
        return params.getGroupProps();
    }

    @Override
    public Optional<String> getSortProps() {
        return params.getSortProps();
    }

    @Override
    public Optional<String> getViewGroup() {
        return params.getViewGroup();
    }

    @Override
    public Collection<String> getListValuesFor() {
        return params.getListValuesFor();
    }

    @Override
    public Collection<String> getListMetadataValuesFor() {
        return params.getListMetadataValuesFor();
    }

    @Override
    public boolean getWaitForTotal() {
        return params.getWaitForTotal();
    }

    @Override
    public boolean getIncludeSubcorpusSize() {
        return params.getIncludeSubcorpusSize();
    }

    @Override
    public boolean getIncludeCustomInfo() {
        return params.getIncludeCustomInfo();
    }

    @Override
    public boolean getCsvIncludeSummary() {
        return params.getCsvIncludeSummary();
    }

    @Override
    public boolean getCsvDeclareSeparator() {
        return params.getCsvDeclareSeparator();
    }

    @Override
    public String getCsvDescription() {
        return params.getCsvDescription();
    }

    @Override
    public boolean getExplain() {
        return params.getExplain();
    }

    @Override
    public boolean getSensitive(boolean defaultValue) {
        return params.getSensitive(defaultValue);
    }

    @Override
    public int getWordStart() {
        return params.getWordStart();
    }

    @Override
    public int getWordEnd() {
        return params.getWordEnd();
    }

    @Override
    public Optional<Integer> getHitStart() {
        return params.getHitStart();
    }

    @Override
    public int getHitEnd() {
        return params.getHitEnd();
    }

    @Override
    public String getAutocompleteTerm() {
        return params.getAutocompleteTerm();
    }

    @Override
    public String getRelClasses() { return params.getRelClasses(); }

    @Override
    public boolean getRelOnlySpans() { return params.getRelOnlySpans(); }

    @Override
    public boolean getRelSeparateSpans() { return params.getRelSeparateSpans(); }

    @Override
    public long getLimitValues() { return params.getLimitValues(); }

    @Override
    public boolean isCalculateCollocations() {
        return params.isCalculateCollocations();
    }

    @Override
    public String getAnnotationName() {
        if (overrideAnnotationName != null)
            return overrideAnnotationName;
        return params.getAnnotationName();
    }

    public void setAnnotationName(String annotationName) {
        overrideAnnotationName = annotationName;
    }

    @Override
    public String getFieldName() {
        return fieldName == null ? params.getFieldName() : fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    @Override
    public Optional<String> getSearchFieldName() {
        return params.getSearchFieldName();
    }

    @Override
    public Set<String> getTerms() {
        return params.getTerms();
    }

    @Override
    public boolean isIncludeDebugInfo() {
        return params.isIncludeDebugInfo();
    }

    @Override
    public WebserviceOperation getOperation() {
        return params.getOperation();
    }

    @Override
    public ApiVersion apiCompatibility() { return params.apiCompatibility(); }

    @Override
    public Optional<String> getInputFormat() {
        if (inputFormat != null)
            return Optional.of(inputFormat);
        return params.getInputFormat();
    }

    public void setInputFormat(String inputFormat) {
        this.inputFormat = inputFormat;
    }
}
