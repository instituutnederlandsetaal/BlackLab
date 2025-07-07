package nl.inl.blacklab.server.lib.results;

import java.util.List;
import java.util.concurrent.ExecutionException;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.HitGroup;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.searches.SearchCacheEntry;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.lib.WebserviceParams;

public class ResultHitsCsv {

    private final WebserviceParams params;

    private Hits hits;

    private HitGroups groups;

    private final DocResults subcorpusResults;

    private final boolean isViewGroup;

    private final List<Annotation> annotationsToWrite;

    /**
     * Get the hits (and the groups from which they were extracted - if applicable)
     * or the groups for this request. Exceptions cleanly mapping to http error
     * responses are thrown if any part of the request cannot be fulfilled. Sorting
     * is already applied to the hits.
     */
    ResultHitsCsv(WebserviceParams params) throws InvalidQuery {
        super();
        this.params = params;

        // TODO share with regular RequestHandlerHits, allow configuring windows, totals, etc ?

        // Might be null
        String groupBy = params.getGroupProps().orElse(null);
        String viewGroup = params.getViewGroup().orElse(null);
        isViewGroup = viewGroup != null;
        String sortBy = params.getSortProps().orElse(null);

        SearchCacheEntry<?> cacheEntry;
        groups = null;
        subcorpusResults = params.subcorpus().execute();

        try {
            if (!StringUtils.isEmpty(groupBy)) {
                hits = params.hitsSample().execute();
                groups = params.hitsGroupedWithStoredHits().execute();

                if (viewGroup != null) {
                    PropertyValue groupId = PropertyValue.deserialize(params.blIndex(), params.getAnnotatedField(), viewGroup);
                    if (groupId == null)
                        throw new BadRequest("ERROR_IN_GROUP_VALUE", "Cannot deserialize group value: " + viewGroup);
                    HitGroup group = groups.get(groupId);
                    if (group == null)
                        throw new BadRequest("GROUP_NOT_FOUND", "Group not found: " + viewGroup);

                    hits = group.storedResults();

                    // NOTE: sortBy is automatically applied to regular results, but not to results within groups
                    // See ResultsGrouper::init (uses hits.getByOriginalOrder(i)) and DocResults::constructor
                    // Also see SearchParams (hitsSortSettings, docSortSettings, hitGroupsSortSettings, docGroupsSortSettings)
                    // There is probably no reason why we can't just sort/use the sort of the input results, but we need some more testing to see if everything is correct if we change this
                    if (sortBy != null) {
                        HitProperty sortProp = HitProperty.deserialize(hits, sortBy, params.getContext());
                        if (sortProp == null)
                            throw new BadRequest("ERROR_IN_SORT_VALUE", "Cannot deserialize sort value: " + sortBy);
                        hits = hits.sort(sortProp);
                    }
                }
            } else {
                // Use a regular search for hits, so that not all hits are actually retrieved yet, we'll have to construct a pagination view on top of the hits manually
                cacheEntry = params.hitsSample().executeAsync();
                hits = (Hits) cacheEntry.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // preserve interrupted status
            throw WebserviceOperations.translateSearchException(e);
        } catch (ExecutionException e) {
            throw WebserviceOperations.translateSearchException(e);
        }

        // apply window settings
        // Different from the regular results, if no window settings are provided, we export the maximum amount automatically
        // The max for CSV exports is also different from the default pagesize maximum.
        if (hits != null) {
            long first = Math.max(0, params.getFirstResultToShow()); // Defaults to 0
            if (!hits.hitsStats().processedAtLeast(first))
                first = 0;

            long number = params.getSearchManager().config().getSearch().getMaxHitsToRetrieve();
            if (number < 0)
                number = Long.MAX_VALUE;
            if (params.optNumberOfResultsToShow().isPresent()) {
                long requested = params.optNumberOfResultsToShow().get();
                if (requested < 0)
                    requested = Long.MAX_VALUE;
                number = Math.min(requested, number);
            }

            if (number >= 0)
                hits = hits.window(first, number);
        }

        annotationsToWrite = WebserviceOperations.getAnnotationsToWrite(params);
    }

    public Hits getHits() {
        return hits;
    }

    public HitGroups getGroups() {
        return groups;
    }

    public DocResults getSubcorpusResults() {
        return subcorpusResults;
    }

    public boolean isViewGroup() {
        return isViewGroup;
    }

    public List<Annotation> getAnnotationsToWrite() {
        return annotationsToWrite;
    }

    public WebserviceParams getParams() {
        return params;
    }
}
