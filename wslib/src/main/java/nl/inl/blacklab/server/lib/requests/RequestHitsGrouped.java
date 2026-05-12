package nl.inl.blacklab.server.lib.requests;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;

import nl.inl.blacklab.queryParser.corpusql.BcqlQueryLanguageParser;
import nl.inl.blacklab.resultproperty.HitGroupProperty;
import nl.inl.blacklab.resultproperty.HitGroupPropertyScore;
import nl.inl.blacklab.resultproperty.HitGroupPropertySize;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyHitText;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.Results;
import nl.inl.blacklab.search.results.hitresults.ContextSize;
import nl.inl.blacklab.search.results.hitresults.HitGroupScorer;
import nl.inl.blacklab.search.textpattern.CompleteQuery;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.searches.SearchCount;
import nl.inl.blacklab.searches.SearchDocs;
import nl.inl.blacklab.searches.SearchHits;
import nl.inl.blacklab.server.jobs.ContextSettings;
import nl.inl.blacklab.server.jobs.WindowSettings;
import nl.inl.blacklab.server.lib.WebserviceParams;
import nl.inl.blacklab.server.lib.WebserviceParamsImpl;
import nl.inl.util.StringUtil;

/** A request for hits grouped by some property.
 *
 * Can also sort and score the groups if requested.
 *
 * @param hitsToGroup hits search we want to apply grouping to
 * @param groupBy property to group on
 * @param maxHitsToStorePerGroup maximum number of hits to store for each group
 * @param sortGroupsBy how to sort the groups
 * @param groupScorer how to score the groups, or {@link HitGroupScorer#NONE}
 * @param windowSettings window of groups to get
 * @param includeGroupContents whether to include the hits in each group in the response or not
 * @param hitsReponseSettings how to write list of hits, if hits are included
 * @param paramsForResponse original query parameters (only used to echo them in the response)
 */
public record RequestHitsGrouped(
        SearchHits hitsToGroup,
        SearchCount docsCount,
        SearchDocs subcorpus,
        HitProperty groupBy,
        ContextSettings contextSettings,
        long maxHitsToStorePerGroup,
        HitGroupProperty sortGroupsBy,
        HitGroupScorer groupScorer,
        WindowSettings windowSettings,
        boolean includeGroupContents,
        HitsResponseSettings hitsReponseSettings,
        WebserviceParams paramsForResponse) {

    public static @NonNull RequestHitsGrouped fromParams(WebserviceParams params, boolean isCsv) {
        SearchHits searchHits = WebserviceParamsImpl.determineHitsSearch(RequestHits.fromParams(params));
        HitsResponseSettings hitsResponseSettings = HitsResponseSettings.fromParams(params);
        return new RequestHitsGrouped(searchHits,
                params.docsCount(),
                params.subcorpus(),
                ((WebserviceParamsImpl) params).getHitGroupProperty(),
                params.contextSettings(),
                Results.NO_LIMIT,
                ((WebserviceParamsImpl) params).hitGroupSortSettings(HitGroupPropertySize.get()).sortBy(),
                params.getHitGroupScorer(),
                params.windowSettings(isCsv),
                params.getIncludeGroupContents(),
                hitsResponseSettings,
                params);
    }

    public static @NonNull RequestHitsGrouped fromParamsCollocations(WebserviceParams params, boolean isCsv) {
        ContextSize context = params.getContext();
        if (context.isInlineTag())
            throw new UnsupportedOperationException("Collocations with inline context tags are not (yet) supported");
        String annotationName = params.getAnnotationName();
        AnnotatedField annotatedField = params.getAnnotatedField();
        Annotation annotation = StringUtils.isEmpty(annotationName) ? annotatedField.mainAnnotation() : annotatedField.annotation(annotationName);
        String term = params.getTerm();
        String query;
        if (StringUtils.isEmpty(term)) {
            // Pattern given. (must be a 1-token pattern, but we don't check that here)
            query = params.getPattern();
        } else {
            // Term given. Construct a simple [annot="value"] query.
            query = "[" + annotation.name() + "=\"" +
                    StringUtil.escapeQuoteForBcql(term, "\"") + "\"]";
        }
        String collocationQuery = "meet([], " + query + "," + (-context.before()) + "," + context.after() + ")";
        TextPattern pattern = BcqlQueryLanguageParser.parseQuery(collocationQuery);
        CompleteQuery completeQuery = new CompleteQuery(pattern, params.filterQuery());
        SearchHits hitsToGroup = params.blIndex().search(annotatedField).find(completeQuery);

        // Determine group by
        MatchSensitivity sensitivity = params.getSensitive(false) ? MatchSensitivity.SENSITIVE : MatchSensitivity.INSENSITIVE;
        HitProperty groupBy = new HitPropertyHitText(params.blIndex(), annotation, sensitivity);

        // Determine group scorer
        HitGroupScorer groupScorer = HitGroupScorer.fromConfig(annotatedField, Map.of(
                "id", params.getScorer().orElse(HitGroupScorer.DEFAULT_TYPE_ID),
                "term", term,
                "pattern", pattern,
                "annotation", annotation.name(),
                "sensitive", sensitivity == MatchSensitivity.SENSITIVE
        ));

        // Assemble and execute the grouping request and produce the response
        String paramSort = params.getSortProps().orElse(null);
        HitGroupProperty sortBy = paramSort == null ? HitGroupPropertyScore.get() : HitGroupProperty.deserialize(paramSort);
        return new RequestHitsGrouped(hitsToGroup,
                hitsToGroup.docCount(),
                WebserviceParams.getSubcorpusSearch(params),
                groupBy,
                params.contextSettings(),
                Results.NO_LIMIT,
                sortBy,
                groupScorer,
                params.windowSettings(isCsv),
                params.getIncludeGroupContents(),
                HitsResponseSettings.fromParams(params),
                params);
    }

    public BlackLabIndex index() {
        return hitsToGroup.queryInfo().index();
    }
}
