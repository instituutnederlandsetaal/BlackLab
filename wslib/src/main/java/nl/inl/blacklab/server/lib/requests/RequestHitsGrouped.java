package nl.inl.blacklab.server.lib.requests;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
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
import nl.inl.blacklab.search.results.hitresults.HitGroupScorer;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.searches.SearchDocs;
import nl.inl.blacklab.server.jobs.ContextSettings;
import nl.inl.blacklab.server.jobs.WindowSettings;
import nl.inl.blacklab.server.lib.ParamsForResponse;
import nl.inl.blacklab.server.lib.QueryParams;
import nl.inl.blacklab.server.lib.WebserviceParams;
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
        BlackLabIndex index = WebserviceParams.index(qpar.getCorpusName());
        AnnotatedField annotatedField = WebserviceParams.getAnnotatedField(index, qpar.getFieldName());
        Annotation annotation = StringUtils.isEmpty(qpar.getAnnotationName()) ? annotatedField.mainAnnotation() :
                annotatedField.annotation(qpar.getAnnotationName());
        String bcqlQuery = getCollocationQuery(
                WebserviceParams.getContext(qpar.getContextParam(), qpar.config()),
                qpar.getTerm(), qpar.getPattern(), annotation);
        TextPattern pattern = BcqlQueryLanguageParser.parseQuery(bcqlQuery);

        // Determine group by
        MatchSensitivity sensitivity = qpar.optSensitive().orElse(false) ? MatchSensitivity.SENSITIVE :
                MatchSensitivity.INSENSITIVE;
        HitProperty groupBy = new HitPropertyHitText(index, annotation, sensitivity);

        // Determine group scorer
        HitGroupScorer groupScorer = HitGroupScorer.fromConfig(annotatedField, Map.of(
                "id", qpar.getScorer().orElse(HitGroupScorer.DEFAULT_TYPE_ID),
                "term", qpar.getTerm(),
                "pattern", pattern,
                "annotation", annotation.name(),
                "sensitive", sensitivity == MatchSensitivity.SENSITIVE
        ));

        // Assemble and execute the grouping request and produce the response
        HitGroupProperty sortBy = qpar.getSortBy().isEmpty() ? HitGroupPropertyScore.get() :
                HitGroupProperty.deserialize(qpar.getSortBy().get());

        RequestHits requestHits = RequestHits.fromParams(qpar, isCsv).withPattern(pattern);

        return new RequestHitsGrouped(requestHits,
                groupBy,
                Results.NO_LIMIT,
                groupScorer,
                sortBy,
                WebserviceParams.getIncludeGroupContents(qpar.optIncludeGroupContents().orElse(null), qpar.config())
        );
    }

    private static @NonNull String getCollocationQuery(ContextSize context, String term, String pattern, Annotation annotation) {
        if (context.isInlineTag())
            throw new UnsupportedOperationException("Collocations with inline context tags are not (yet) supported");
        String query;
        if (StringUtils.isEmpty(term)) {
            // Pattern given. (must be a 1-token pattern, but we don't check that here)
            query = pattern;
        } else {
            // Term given. Construct a simple [annot="value"] query.
            query = "[" + annotation.name() + "=\"" +
                    StringUtil.escapeQuoteForBcql(term, "\"") + "\"]";
        }
        return "meet([], " + query + "," + (-context.before()) + "," + context.after() + ")";
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
