package nl.inl.blacklab.server.lib.requests;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.queryParser.corpusql.BcqlQueryLanguageParser;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.HitGroupProperty;
import nl.inl.blacklab.resultproperty.HitGroupPropertyScore;
import nl.inl.blacklab.resultproperty.HitGroupPropertySize;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyHitText;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.results.SampleParameters;
import nl.inl.blacklab.search.results.SearchSettings;
import nl.inl.blacklab.search.results.hitresults.ContextSize;
import nl.inl.blacklab.search.results.hitresults.HitGroupCollocationScorer;
import nl.inl.blacklab.search.results.hitresults.HitGroupScorer;
import nl.inl.blacklab.search.textpattern.CompleteQuery;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.searches.SearchFacets;
import nl.inl.blacklab.searches.SearchHits;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.jobs.ContextSettings;
import nl.inl.blacklab.server.jobs.WindowSettings;
import nl.inl.blacklab.server.lib.ParamUtil;
import nl.inl.blacklab.server.lib.ParamsForResponse;
import nl.inl.blacklab.server.lib.QueryParams;
import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.webservice.WsParam;
import nl.inl.util.StringUtil;

/**
 * A request for a hits search.
 * <p>
 * Searches the given pattern in the documents determined by the filterQuery, if any.
 * <p>
 * Can also filter hits by a property and value, sort the hits, and sample them,
 * if requested.
 */
public final class RequestHits {
    private final AnnotatedField searchField;
    private final TextPattern pattern;
    private final TextPattern patternOriginal;
    private final boolean adjustHits;
    private final boolean withSpans;
    private final Query filterQuery;
    private final SearchSettings searchSettings;
    private final HitPropFilter propFilter;
    private final String viewGroup;
    private final SampleParameters sampleParams;
    private final ContextSettings contextSettings;
    private final HitProperty sortBy;
    private final HitProperty groupBy;
    private final HitGroupScorer groupScorer;
    private final HitGroupProperty sortGroupsBy;
    private final boolean includeGroupContents;
    private final WindowSettings windowSettings;
    private final String facetDesc;
    private final boolean calculateCollocations;
    private final Boolean sensitive;
    private final boolean useCache;
    private final boolean waitForTotal;
    private final boolean includeSubcorpusSize;
    private final boolean explain;
    private final HitsResponseSettings hitsResponseSettings;
    private final List<MetadataField> metadataToInclude;
    private final boolean isCsv;
    private final CsvSettings csvSettings;
    private final ParamsForResponse paramsForResponse;

    /**
     * @param searchField    Which annotated field we're searching
     * @param pattern        Pattern to search for
     * @param patternOriginal Original pattern before any adjustments
     * @param adjustHits     Adjust hits to include all matched relations or not? (adjusts the pattern)
     * @param withSpans      Automatically capture any spans overlapping with the hit or not? (adjusts the pattern)
     * @param filterQuery    Search only in documents matching this query, or null for all
     * @param searchSettings Some settings that influence query optimization and maximum # of hits processed
     * @param propFilter     Optional property/value hit filter
     * @param viewGroup      Identity of the single group from grouped hits to view, if any
     * @param sampleParams   Optional sample parameters
     * @param contextSettings How many words around a hit to retrieve
     * @param sortBy         Optional property to sort by
     * @param groupBy        Optional property to group by
     * @param groupScorer    How to score each group (optional)
     * @param sortGroupsBy   Optional property to sort groups by
     * @param includeGroupContents Whether to include the contents of each group
     * @param windowSettings Settings for the window of hits to return
     * @param facetDesc      Description of the facets to calculate
     * @param calculateCollocations Whether to calculate collocations
     * @param sensitive      Whether collocations should be case-sensitive
     * @param useCache       Use the results cache or ignore it for this query?
     * @param waitForTotal   Wait for the total count to be calculated before returning results?
     * @param includeSubcorpusSize Whether to include the size of the subcorpus
     * @param explain        Whether to include explanation of how query was rewritten
     * @param hitsResponseSettings Settings for what to include in the hits response (e.g. which annotations)
     * @param metadataToInclude List of metadata fields to include in the response
     * @param isCsv          Whether the response should be in CSV format
     * @param csvSettings    Settings for CSV output
     * @param paramsForResponse Query parameters to be echoed in the response
     */
    public RequestHits(
            AnnotatedField searchField,
            TextPattern pattern,
            TextPattern patternOriginal,
            boolean adjustHits,
            boolean withSpans,
            Query filterQuery,
            SearchSettings searchSettings,
            HitPropFilter propFilter,
            String viewGroup,
            SampleParameters sampleParams,
            ContextSettings contextSettings,
            HitProperty sortBy,
            HitProperty groupBy,
            HitGroupScorer groupScorer,
            HitGroupProperty sortGroupsBy,
            boolean includeGroupContents,
            WindowSettings windowSettings,
            String facetDesc,
            boolean calculateCollocations, // (OLD collocations)
            Boolean sensitive, // (OLD collocations)
            boolean useCache,
            boolean waitForTotal,
            boolean includeSubcorpusSize,
            boolean explain,
            HitsResponseSettings hitsResponseSettings,
            List<MetadataField> metadataToInclude,
            boolean isCsv,
            CsvSettings csvSettings,
            ParamsForResponse paramsForResponse) {
        this.searchField = searchField;
        this.pattern = pattern;
        this.patternOriginal = patternOriginal;
        this.adjustHits = adjustHits;
        this.withSpans = withSpans;
        this.filterQuery = filterQuery;
        this.searchSettings = searchSettings;
        this.propFilter = propFilter;
        this.viewGroup = viewGroup;
        this.sampleParams = sampleParams;
        this.contextSettings = contextSettings;
        this.sortBy = sortBy;
        this.groupBy = groupBy;
        this.groupScorer = groupScorer;
        this.sortGroupsBy = sortGroupsBy;
        this.includeGroupContents = includeGroupContents;
        this.windowSettings = windowSettings;
        this.facetDesc = facetDesc;
        this.calculateCollocations = calculateCollocations;
        this.sensitive = sensitive;
        this.useCache = useCache;
        this.waitForTotal = waitForTotal;
        this.includeSubcorpusSize = includeSubcorpusSize;
        this.explain = explain;
        this.hitsResponseSettings = hitsResponseSettings;
        this.metadataToInclude = metadataToInclude;
        this.isCsv = isCsv;
        this.csvSettings = csvSettings;
        this.paramsForResponse = paramsForResponse;
    }

    /** /collocations endpoint is an alternative way to group hits, useful for easily finding and scoring collocations.
     *
     * @param qpar parameters from the request
     * @param isCsv whether this is for a CSV response or not (some parameters are interpreted differently for CSV)
     * @return object representing the collocations request
     */
    public static RequestHits fromParamsCollocations(QueryParams qpar, boolean isCsv) {
        BlackLabIndex index = ParamUtil.index(qpar.getCorpusName());
        AnnotatedField annotatedField = ParamUtil.getAnnotatedField(index, qpar.get(WsParam.FIELD));
        Annotation annotation;
        if (StringUtils.isEmpty(qpar.get(WsParam.ANNOTATION))) {
            annotation = annotatedField.mainAnnotation();
        } else {
            annotation = annotatedField.annotation(qpar.get(WsParam.ANNOTATION));
            if (annotation == null)
                throw new BadRequest("UNKNOWN_ANNOTATION",
                        "Annotation '" + qpar.get(WsParam.ANNOTATION) + "' not found in field '" +
                                annotatedField.name() + "'.");
        }

        // Determine what we're finding collocations for
        // (e.g. collocations for "schip", or for [lemma = "bla.*" & pos="N"])
        String findQuery = qpar.get(WsParam.PATTERN);
        if (StringUtils.isBlank(findQuery))
            throw new BadRequest("NO_PATTERN_GIVEN", "Missing required parameter: patt (pattern to find collocations for)");
        String collocateQuery = qpar.opt(WsParam.COLLOCATE_PATTERN).orElse("[]");
        HitGroupCollocationScorer.CollocationType collocationType;
        try {
            collocationType = qpar.opt(WsParam.COLLOCATION_TYPE,
                            HitGroupCollocationScorer.CollocationType::fromStringValue)
                    .orElse(HitGroupCollocationScorer.CollocationType.PROXIMITY);
        } catch (IllegalArgumentException e) {
            throw new BadRequest("INVALID_COLLOCATION_TYPE", e.getMessage(), e);
        }
        boolean findRelations = collocationType != HitGroupCollocationScorer.CollocationType.PROXIMITY;
        String relationTypeRegex = qpar.opt(WsParam.RELATION_TYPE).orElse(StringUtil.REGEX_ANY_VALUE);

        // Construct and parse the query that will yield the collocations
        String within = qpar.has(WsParam.WITHIN) ? "<" + qpar.get(WsParam.WITHIN) + "/>" : "";
        ContextSize context;
        try {
            context = qpar.has(WsParam.CONTEXT) ? ParamUtil.getContext(qpar) : ContextSize.ZERO;
        } catch (IllegalArgumentException e) {
            throw new BadRequest("INVALID_CONTEXT", e.getMessage(), e);
        }
        String bcqlQuery = getCollocationQuery(context, findQuery, collocateQuery, collocationType, relationTypeRegex, within);
        TextPattern textPattern = parseCollocationPattern(bcqlQuery);

        // Determine group by
        MatchSensitivity sensitivity = qpar.optBool(WsParam.SENSITIVE).orElse(false) ? MatchSensitivity.SENSITIVE :
                MatchSensitivity.INSENSITIVE;
        HitProperty groupBy = new HitPropertyHitText(index, annotation, sensitivity);

        // Determine group scorer
        Query filter = ParamUtil.filterQuery(qpar);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put(HitGroupScorer.KEY_ID, qpar.opt(WsParam.SCORER_TYPE).orElse(HitGroupScorer.DEFAULT_TYPE_ID));
        config.put(HitGroupCollocationScorer.KEY_DOC_FILTER, filter);
        config.put(HitGroupCollocationScorer.KEY_PATTERN, parseCollocationPattern(findQuery));
        config.put(HitGroupCollocationScorer.KEY_ANNOTATION, annotation.name());
        config.put(HitGroupCollocationScorer.KEY_SENSITIVITY, sensitivity.toString());
        config.put(HitGroupCollocationScorer.KEY_COLL_TYPE, collocationType.toString());
        config.put(HitGroupCollocationScorer.KEY_REL_TYPE, findRelations ? relationTypeRegex : null);
        HitGroupScorer groupScorer;
        try {
            groupScorer = HitGroupScorer.fromConfig(annotatedField, config);
        } catch (InvalidQuery e) {
            throw BadRequest.pattSyntaxError(e);
        } catch (IllegalArgumentException e) {
            throw new BadRequest("INVALID_SCORER", e.getMessage(), e);
        }

        return optFromParams(qpar, isCsv, textPattern, groupBy, groupScorer, HitGroupPropertyScore.get())
                .orElseThrow();
    }

    /** Determine the query that will yield the collocations we're looking for. */
    static String getCollocationQuery(ContextSize context, String findQuery, String collocateQuery,
            HitGroupCollocationScorer.CollocationType collocationType, String relTypeRegex, String within) {
        if (StringUtils.isBlank(findQuery))
            throw new BadRequest("NO_PATTERN_GIVEN", "Missing required parameter: patt (pattern to find collocations for)");
        if (context.isInlineTag()) {
            if (!within.isEmpty())
                throw new BadRequest("INVALID_CONTEXT",
                        "Both within and a tag context specified! If you specify within, context may be omitted or " +
                                "must be in number of tokens");
            // You can either specify within=s (optionally combined with e.g. context=3:5), or you can specify
            // context=s to just find collocations within sentences without any proximity restriction.
            within = "<" + context.inlineTagName() + "/>";
            context = ContextSize.ZERO;
        }
        if (collocationType == HitGroupCollocationScorer.CollocationType.PROXIMITY) {
            // Proximity-based collocations.
            int lower, upper;
            if (context.equals(ContextSize.ZERO)) {
                lower = -1;
                upper = 1;
            } else {
                lower = context.before() == 0 ? 1 : -context.before();
                upper = context.after() == 0 ? -1 : context.after();
            }
            String optLowerUpper = "," + lower + "," + upper;
            if (!within.isEmpty() && context.equals(ContextSize.ZERO)) {
                // We only care about within (e.g. within <s/>), not about proximity.
                optLowerUpper = "";
            }
            return within.isEmpty() ?
                    "meet(" + collocateQuery + ", " + findQuery + optLowerUpper + ")" :
                    "meet_within(" + collocateQuery + ", " + findQuery + ", " + within + optLowerUpper + ")";
        } else {
            if (!within.isEmpty())
                throw new BadRequest("INVALID_CONTEXT", "Parameter within is not supported for relation-based collocations");
            // Relation-based collocations.
            String optRelTypeFilter = StringUtils.isEmpty(relTypeRegex) ||
                    relTypeRegex.equals(StringUtil.REGEX_ANY_VALUE) ? "" :
                    "(" + relTypeRegex + ")";
            if (collocationType == HitGroupCollocationScorer.CollocationType.RELATION_TARGETS) {
                // Find all targets for specified source and relation type
                return "rspan(" + findQuery + " -" + optRelTypeFilter + "-> " + collocateQuery + ", \"target\")";
            } else {
                // Find all sources for specified target and relation type
                return collocateQuery + " -" + optRelTypeFilter + "-> " + findQuery;
            }
        }
    }

    private static TextPattern parseCollocationPattern(String bcqlQuery) {
        try {
            return BcqlQueryLanguageParser.parseQuery(bcqlQuery);
        } catch (InvalidQuery e) {
            throw BadRequest.pattSyntaxError(e);
        }
    }

    public static RequestHits fromParams(QueryParams params, boolean isCsv, TextPattern pattern) {
        return optFromParams(params, isCsv, pattern).orElseThrow(
                () -> new IllegalArgumentException("No pattern specified"));
    }

    public static Optional<RequestHits> optFromParams(QueryParams qpar, boolean isCsv, TextPattern overridePattern) {
        return optFromParams(qpar, isCsv, overridePattern, null, null, HitGroupPropertySize.get());
    }

    private static Optional<RequestHits> optFromParams(QueryParams qpar, boolean isCsv, TextPattern overridePattern,
            HitProperty overrideGroupBy, HitGroupScorer overrideGroupScorer, HitGroupProperty defaultGroupSort) {
        BlackLabIndex index = ParamUtil.index(qpar.getCorpusName());
        ContextSize contextSize = ParamUtil.getContext(qpar);
        String optContextTag = contextSize.inlineTagName();
        TextPattern pattern;
        pattern = overridePattern == null ?
                ParamUtil.pattern(index, qpar.get(WsParam.PATTERN_LANGUAGE), qpar.get(WsParam.PATTERN),
                        qpar.get(WsParam.PATTERN_GAP_DATA),
                        optContextTag).orElse(null) :
                overridePattern;
        if (pattern == null)
            return Optional.empty(); // pattern is required
        String groupBy = qpar.opt(WsParam.GROUP_BY).orElse(null);
        String viewGroup = qpar.opt(WsParam.VIEW_GROUP).orElse(null);
        String sortBy = qpar.opt(WsParam.SORT_BY).orElse(null);
        AnnotatedField annotatedField = ParamUtil.getAnnotatedField(index, qpar.get(WsParam.FIELD));
        AnnotatedField searchField = ParamUtil.getSearchField(index, qpar.get(WsParam.FIELD),
                qpar.opt(WsParam.SEARCH_FIELD).orElse(null));
        WebserviceOperation operation = ParamUtil.getOperation(qpar);
        HitProperty hitsGroupProperty = overrideGroupBy == null ?
                ParamUtil.getHitsGroupProperty(operation, groupBy, annotatedField, contextSize) : overrideGroupBy;
        HitGroupScorer hitGroupScorer = overrideGroupBy == null ?
                ParamUtil.getHitGroupScorer(annotatedField, qpar.opt(WsParam.SCORER).orElse(null)) : overrideGroupScorer;
        TextPattern patternOriginal = ParamUtil.patternNoWithinContextTag(index,
                qpar.get(WsParam.PATTERN_LANGUAGE),
                qpar.get(WsParam.PATTERN), qpar.get(WsParam.PATTERN_GAP_DATA)).orElse(null);
        HitProperty hitsSortProperty = ParamUtil.hitsSortProperty(operation, annotatedField, hitsGroupProperty != null,
                viewGroup, sortBy, contextSize);
        HitGroupProperty sortGroupsBy = ParamUtil.hitGroupSortProperty(operation, hitsGroupProperty != null, sortBy,
                viewGroup, defaultGroupSort);
        boolean includeGroupContents = ParamUtil.getIncludeGroupContents(
                qpar.optBool(WsParam.INCLUDE_GROUP_CONTENTS).orElse(null), qpar.config());
        SampleParameters sampleParams = ParamUtil.sampleParams(
                qpar.optDouble(WsParam.SAMPLE).orElse(null),
                qpar.optLong(WsParam.SAMPLE_NUMBER).orElse(null),
                qpar.optLong(WsParam.SAMPLE_SEED).orElse(null));
        SearchSettings searchSettings = ParamUtil.searchSettings(qpar.getLong(WsParam.MAX_HITS_TO_RETRIEVE),
                qpar.getLong(WsParam.MAX_HITS_TO_COUNT),
                qpar.debugMode() ? qpar.getInt(WsParam.FORWARD_INDEX_MATCHING_SETTING) : -1, qpar.config());
        ConcordanceType concordanceType = ParamUtil.getConcordanceType(qpar.get(WsParam.USE_CONTENT));
        return Optional.of(new RequestHits(
                searchField,
                pattern,
                patternOriginal,
                qpar.getBool(WsParam.REL_ADJUST_HITS),
                qpar.getBool(WsParam.WITH_SPANS),
                ParamUtil.filterQuery(qpar),
                searchSettings,
                HitPropFilter.fromParams(qpar),
                viewGroup,
                sampleParams,
                ParamUtil.contextSettings(contextSize, concordanceType, qpar.config()),
                hitsSortProperty,
                hitsGroupProperty,
                hitGroupScorer,
                sortGroupsBy,
                includeGroupContents,
                ParamUtil.windowSettings(qpar, isCsv),
                qpar.opt(WsParam.FACETS).orElse(null),
                qpar.get(WsParam.CALCULATE_STATS).equals("colloc"),
                qpar.optBool(WsParam.SENSITIVE).orElse(null),
                ParamUtil.useCache(qpar.getBool(WsParam.USE_CACHE), qpar.debugMode()),
                qpar.getBool(WsParam.WAIT_FOR_TOTAL_COUNT),
                ParamUtil.includeSubcorpusSize(qpar),
                qpar.getBool(WsParam.EXPLAIN_QUERY_REWRITE),
                HitsResponseSettings.fromParams(qpar),
                ParamUtil.getMetadataToInclude(index, qpar.getList(WsParam.LIST_VALUES_FOR_METADATA_FIELDS)),
                isCsv,
                CsvSettings.fromParams(qpar),
                qpar)
        );
    }

    public BlackLabIndex index() {
        return searchField.index();
    }

    public SearchFacets facets() {
        List<DocProperty> facets = DocProperty.propsFromDesc(index(), facetDesc);
        return facets == null ? null :
                RequestDocs.docsSearch(index(), filterQuery(), this).facet(facets);
    }

    public RequestHits withPattern(TextPattern pattern) {
        return new RequestHits(
                searchField, pattern, pattern,
                adjustHits, withSpans, filterQuery, searchSettings, propFilter, viewGroup, sampleParams,
                contextSettings, sortBy, groupBy, groupScorer, sortGroupsBy,
                includeGroupContents, windowSettings, facetDesc, calculateCollocations, sensitive, useCache,
                waitForTotal, includeSubcorpusSize, explain, hitsResponseSettings, metadataToInclude,
                isCsv, csvSettings, paramsForResponse);
    }

    SearchHits searchHits = null;

    /**
     * @return hits - filtered then sorted then sampled
     */
    public synchronized SearchHits getSearch() throws BlsException {
        if (searchHits == null) {
            // Find hits
            TextPattern pattern = pattern();
            if (pattern == null)
                throw new BadRequest("NO_PATTERN_GIVEN",
                        "Text search pattern required. Please specify 'patt' parameter.");
            if (adjustHits() || withSpans())
                pattern = pattern.adjustTextPattern(adjustHits(), withSpans());
            try {
                CompleteQuery cp = new CompleteQuery(pattern, filterQuery());
                searchHits = index().search(searchField(), useCache())
                        .find(cp, searchSettings());
            } catch (InvalidQuery e) {
                throw BadRequest.pattSyntaxError(e);
            }

            // Optionally filter by property and value
            HitPropFilter filter = propFilter();
            if (filter != null) {
                searchHits = searchHits.filter(filter.prop(), filter.value());
            }

            // Optionally sort
            if (sortBy() != null)
                searchHits = searchHits.sort(sortBy());

            // Optionally sample
            if (sampleParams() != null)
                searchHits = searchHits.sample(sampleParams());
        }
        return searchHits;
    }

    public AnnotatedField searchField() {
        return searchField;
    }

    public TextPattern pattern() {
        return pattern;
    }

    public TextPattern patternOriginal() {
        return patternOriginal;
    }

    public boolean adjustHits() {
        return adjustHits;
    }

    public boolean withSpans() {
        return withSpans;
    }

    public Query filterQuery() {
        return filterQuery;
    }

    public SearchSettings searchSettings() {
        return searchSettings;
    }

    public HitPropFilter propFilter() {
        return propFilter;
    }

    public String viewGroup() {
        return viewGroup;
    }

    public SampleParameters sampleParams() {
        return sampleParams;
    }

    public ContextSettings contextSettings() {
        return contextSettings;
    }

    public HitProperty sortBy() {
        return sortBy;
    }

    public HitProperty groupBy() {
        return groupBy;
    }

    public HitGroupScorer groupScorer() {
        return groupScorer;
    }

    public HitGroupProperty sortGroupsBy() {
        return sortGroupsBy;
    }

    public boolean includeGroupContents() {
        return includeGroupContents;
    }

    public WindowSettings windowSettings() {
        return windowSettings;
    }

    public String facetDesc() {
        return facetDesc;
    }

    public boolean calculateCollocations() {
        return calculateCollocations;
    }

    public Boolean sensitive() {
        return sensitive;
    }

    public boolean useCache() {
        return useCache;
    }

    public boolean waitForTotal() {
        return waitForTotal;
    }

    public boolean includeSubcorpusSize() {
        return includeSubcorpusSize;
    }

    public boolean explain() {
        return explain;
    }

    public HitsResponseSettings hitsResponseSettings() {
        return hitsResponseSettings;
    }

    public List<MetadataField> metadataToInclude() {
        return metadataToInclude;
    }

    public boolean isCsv() {
        return isCsv;
    }

    public CsvSettings csvSettings() {
        return csvSettings;
    }

    public ParamsForResponse paramsForResponse() {
        return paramsForResponse;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        var that = (RequestHits) obj;
        return Objects.equals(this.searchField, that.searchField) &&
                Objects.equals(this.pattern, that.pattern) &&
                Objects.equals(this.patternOriginal, that.patternOriginal) &&
                this.adjustHits == that.adjustHits &&
                this.withSpans == that.withSpans &&
                Objects.equals(this.filterQuery, that.filterQuery) &&
                Objects.equals(this.searchSettings, that.searchSettings) &&
                Objects.equals(this.propFilter, that.propFilter) &&
                Objects.equals(this.viewGroup, that.viewGroup) &&
                Objects.equals(this.sampleParams, that.sampleParams) &&
                Objects.equals(this.contextSettings, that.contextSettings) &&
                Objects.equals(this.sortBy, that.sortBy) &&
                Objects.equals(this.groupBy, that.groupBy) &&
                Objects.equals(this.groupScorer, that.groupScorer) &&
                Objects.equals(this.sortGroupsBy, that.sortGroupsBy) &&
                this.includeGroupContents == that.includeGroupContents &&
                Objects.equals(this.windowSettings, that.windowSettings) &&
                Objects.equals(this.facetDesc, that.facetDesc) &&
                this.calculateCollocations == that.calculateCollocations &&
                Objects.equals(this.sensitive, that.sensitive) &&
                this.useCache == that.useCache &&
                this.waitForTotal == that.waitForTotal &&
                this.includeSubcorpusSize == that.includeSubcorpusSize &&
                this.explain == that.explain &&
                Objects.equals(this.hitsResponseSettings, that.hitsResponseSettings) &&
                Objects.equals(this.metadataToInclude, that.metadataToInclude) &&
                this.isCsv == that.isCsv &&
                Objects.equals(this.csvSettings, that.csvSettings) &&
                Objects.equals(this.paramsForResponse, that.paramsForResponse);
    }

    @Override
    public int hashCode() {
        return Objects.hash(searchField, pattern, patternOriginal, adjustHits, withSpans, filterQuery, searchSettings,
                propFilter, viewGroup, sampleParams, contextSettings, sortBy, groupBy, groupScorer, sortGroupsBy,
                includeGroupContents, windowSettings, facetDesc, calculateCollocations, sensitive, useCache,
                waitForTotal, includeSubcorpusSize, explain, hitsResponseSettings, metadataToInclude, isCsv,
                csvSettings, paramsForResponse);
    }

    @Override
    public String toString() {
        return "RequestHits[" +
                "searchField=" + searchField + ", " +
                "pattern=" + pattern + ", " +
                "patternOriginal=" + patternOriginal + ", " +
                "adjustHits=" + adjustHits + ", " +
                "withSpans=" + withSpans + ", " +
                "filterQuery=" + filterQuery + ", " +
                "searchSettings=" + searchSettings + ", " +
                "propFilter=" + propFilter + ", " +
                "viewGroup=" + viewGroup + ", " +
                "sampleParams=" + sampleParams + ", " +
                "contextSettings=" + contextSettings + ", " +
                "sortBy=" + sortBy + ", " +
                "groupBy=" + groupBy + ", " +
                "groupScorer=" + groupScorer + ", " +
                "sortGroupsBy=" + sortGroupsBy + ", " +
                "includeGroupContents=" + includeGroupContents + ", " +
                "windowSettings=" + windowSettings + ", " +
                "facetDesc=" + facetDesc + ", " +
                "calculateCollocations=" + calculateCollocations + ", " +
                "sensitive=" + sensitive + ", " +
                "useCache=" + useCache + ", " +
                "waitForTotal=" + waitForTotal + ", " +
                "includeSubcorpusSize=" + includeSubcorpusSize + ", " +
                "explain=" + explain + ", " +
                "hitsResponseSettings=" + hitsResponseSettings + ", " +
                "metadataToInclude=" + metadataToInclude + ", " +
                "isCsv=" + isCsv + ", " +
                "csvSettings=" + csvSettings + ", " +
                "paramsForResponse=" + paramsForResponse + ']';
    }

}
