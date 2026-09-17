package nl.inl.blacklab.server.lib.requests;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.Query;
import org.jspecify.annotations.NonNull;

import nl.inl.blacklab.queryParser.corpusql.BcqlQueryLanguageParser;
import nl.inl.blacklab.resultproperty.HitGroupProperty;
import nl.inl.blacklab.resultproperty.HitGroupPropertyScore;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyHitText;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.Results;
import nl.inl.blacklab.search.results.hitresults.ContextSize;
import nl.inl.blacklab.search.results.hitresults.HitGroupCollocationScorer;
import nl.inl.blacklab.search.results.hitresults.HitGroupScorer;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.searches.SearchDocs;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.jobs.ContextSettings;
import nl.inl.blacklab.server.jobs.WindowSettings;
import nl.inl.blacklab.server.lib.ParamUtil;
import nl.inl.blacklab.server.lib.ParamsForResponse;
import nl.inl.blacklab.server.lib.QueryParams;
import nl.inl.blacklab.webservice.WsParam;
import nl.inl.util.StringUtil;

/**
 * A request for hits grouped by some property.
 * Can also sort and score the groups if requested.
 *
 * @param requestHits            hits search we want to apply grouping to
 * @param groupBy                property to group on
 * @param maxHitsToStorePerGroup maximum number of hits to store for each group
 * @param groupScorer            how to score the groups, or {@link HitGroupScorer#NONE}
 * @param sortGroupsBy           how to sort the groups
 * @param includeGroupContents   whether to include the hits in each group in the response or not
 */
public record RequestHitsGrouped(
        // The original search we want to group
        RequestHits requestHits,

        // how to group
        HitProperty groupBy,
        long maxHitsToStorePerGroup,

        // sort groups/window of groups
        HitGroupScorer groupScorer,
        HitGroupProperty sortGroupsBy,

        // what to include in response
        boolean includeGroupContents
    ) {

    public static @NonNull RequestHitsGrouped fromHitsRequestParams(RequestHits requestHits) {
        return new RequestHitsGrouped(requestHits,
                requestHits.groupBy(),
                Results.NO_LIMIT,
                requestHits.groupScorer(),
                requestHits.sortGroupsBy(),
                requestHits.includeGroupContents()
                );
    }

    /** /collocations endpoint is an alternative way to group hits, useful for easily finding and scoring collocations.
     *
     * @param qpar parameters from the request
     * @param isCsv whether this is for a CSV response or not (some parameters are interpreted differently for CSV)
     * @return object representing the collocations request
     */
    public static @NonNull RequestHitsGrouped fromParamsCollocations(QueryParams qpar, boolean isCsv) {
        BlackLabIndex index = ParamUtil.index(qpar.getCorpusName());
        AnnotatedField annotatedField = ParamUtil.getAnnotatedField(index, qpar.get(WsParam.FIELD));
        Annotation annotation;
        if (StringUtils.isEmpty(qpar.get(WsParam.ANNOTATION))) {
            annotation = annotatedField.mainAnnotation();
        } else {
            annotation = annotatedField.annotation(qpar.get(WsParam.ANNOTATION));
        }

        // Determine what we're finding collocations for
        // (e.g. collocations for "schip", or for [lemma = "bla.*" & pos="N"])
        String findQuery = qpar.get(WsParam.PATTERN);
        if (findQuery.isEmpty())
            throw new BadRequest("NO_PATTERN_GIVEN", "Missing required parameter: patt (pattern to find collocations for)");
        String collocateQuery = qpar.opt(WsParam.COLLOCATE_PATTERN).orElse("[]");
        HitGroupCollocationScorer.CollocationType collocationType = qpar.opt(WsParam.COLLOCATION_TYPE,
                        HitGroupCollocationScorer.CollocationType::fromStringValue)
                .orElse(HitGroupCollocationScorer.CollocationType.PROXIMITY);
        boolean findRelations = collocationType != HitGroupCollocationScorer.CollocationType.PROXIMITY;
        String relationTypeRegex = qpar.opt(WsParam.RELATION_TYPE).orElse(StringUtil.REGEX_ANY_VALUE);

        // Construct and parse the query that will yield the collocations
        String within = qpar.has(WsParam.WITHIN) ? "<" + qpar.get(WsParam.WITHIN) + "/>" : "";
        ContextSize context = qpar.has(WsParam.CONTEXT) ? ParamUtil.getContext(qpar) : ContextSize.ZERO;
        String bcqlQuery = getCollocationQuery(context, findQuery, collocateQuery, collocationType, relationTypeRegex, within);
        TextPattern textPattern = BcqlQueryLanguageParser.parseQuery(bcqlQuery);

        // Determine group by
        MatchSensitivity sensitivity = qpar.optBool(WsParam.SENSITIVE).orElse(false) ? MatchSensitivity.SENSITIVE :
                MatchSensitivity.INSENSITIVE;
        HitProperty groupBy = new HitPropertyHitText(index, annotation, sensitivity);

        // Determine group scorer
        Query filter = ParamUtil.filterQuery(qpar);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put(HitGroupScorer.KEY_ID, qpar.opt(WsParam.SCORER_TYPE).orElse(HitGroupScorer.DEFAULT_TYPE_ID));
        config.put(HitGroupCollocationScorer.KEY_DOC_FILTER, filter);
        config.put(HitGroupCollocationScorer.KEY_PATTERN, BcqlQueryLanguageParser.parseQuery(findQuery));
        config.put(HitGroupCollocationScorer.KEY_ANNOTATION, annotation.name());
        config.put(HitGroupCollocationScorer.KEY_SENSITIVITY, sensitivity.toString());
        config.put(HitGroupCollocationScorer.KEY_REL_TYPE, findRelations ? relationTypeRegex : null);
        HitGroupScorer groupScorer = HitGroupScorer.fromConfig(annotatedField, config);

        // Assemble and execute the grouping request and produce the response
        HitGroupProperty sortBy;
        if (qpar.opt(WsParam.SORT_BY).isEmpty()) {
            sortBy = HitGroupPropertyScore.get();
        } else {
            sortBy = HitGroupProperty.deserialize(qpar.opt(WsParam.SORT_BY).get());
        }

        RequestHits requestHits = RequestHits.fromParams(qpar, isCsv, textPattern);

        return new RequestHitsGrouped(requestHits,
                groupBy,
                Results.NO_LIMIT,
                groupScorer,
                sortBy,
                ParamUtil.getIncludeGroupContents(qpar.optBool(WsParam.INCLUDE_GROUP_CONTENTS).orElse(null), qpar.config())
        );
    }

    /** Determine the query that will yield the collocations we're looking for. */
    private static @NonNull String getCollocationQuery(ContextSize context, String findQuery, String collocateQuery,
            HitGroupCollocationScorer.CollocationType collocationType, String relTypeRegex, String within) {
        if (findQuery.isEmpty())
            throw new IllegalArgumentException("Missing findQuery (pattern to find collocations for)");
        if (context.isInlineTag()) {
            if (within != null)
                throw new IllegalArgumentException("Both within and a tag context specified! If you specify within, context may be omitted or must be in number of tokens");
            // You can either specify within=s (optionally combined with e.g. context=3:5), or you can specify
            // context=s to just find collocations within sentences without any proximity restriction.
            within = context.inlineTagName();
            context = ContextSize.ZERO;
        }
        if (collocationType == HitGroupCollocationScorer.CollocationType.PROXIMITY) {
            // Proximity-based collocations.
            int lower, upper;
            if (context == ContextSize.ZERO) {
                lower = -1;
                upper = 1;
            } else {
                lower = context.before() == 0 ? 1 : -context.before();
                upper = context.after() == 0 ? -1 : context.after();
            }
            String optLowerUpper = "," + lower + "," + upper;
            if (!within.isEmpty() && context == ContextSize.ZERO) {
                // We only care about within (e.g. within <s/>), not about proximity.
                optLowerUpper = "";
            }
            return within.isEmpty() ?
                    "meet(" + collocateQuery + ", " + findQuery + optLowerUpper + ")" :
                    "meet_within(" + collocateQuery + ", " + findQuery + ", " + within + optLowerUpper + ")";
        } else {
            if (within != null)
                throw new UnsupportedOperationException("Relational and within not (yet) implemented");
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

    public BlackLabIndex index() {
        return requestHits.index();
    }

    public SearchDocs subcorpus() {
        return BlackLabIndex.getSubcorpusSearch(index(), requestHits.filterQuery());
    }

    // Delegates to requestHits

    public ContextSettings contextSettings() {
        return requestHits.contextSettings();
    }

    public HitsResponseSettings hitsResponseSettings() {
        return requestHits.hitsResponseSettings();
    }

    public WindowSettings windowSettings() {
        return requestHits.windowSettings();
    }

    public TextPattern patternOriginal() {
        return requestHits.patternOriginal();
    }

    public ParamsForResponse paramsForResponse() {
        return requestHits.paramsForResponse();
    }

}
