package nl.inl.blacklab.server.lib.requests;

import org.jspecify.annotations.NonNull;

import nl.inl.blacklab.resultproperty.HitGroupProperty;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.results.Results;
import nl.inl.blacklab.search.results.hitresults.HitGroupScorer;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.searches.SearchDocs;
import nl.inl.blacklab.server.jobs.ContextSettings;
import nl.inl.blacklab.server.jobs.WindowSettings;
import nl.inl.blacklab.server.lib.ParamsForResponse;

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
