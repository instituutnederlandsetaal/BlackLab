package nl.inl.blacklab.server.lib.results;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.results.DocGroup;
import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.lib.WebserviceParams;

public class ResultDocsCsv {

    private DocResults docs;
    private DocGroups groups;
    private final DocResults subcorpusResults;
    private final boolean isViewGroup;

    /**
     * Get the docs (and the groups from which they were extracted - if applicable)
     * or the groups for this request. Exceptions cleanly mapping to http error
     * responses are thrown if any part of the request cannot be fulfilled. Sorting
     * is already applied to the results.
     *
     * @return Docs if looking at ungrouped results, Docs+Groups if looking at
     *         results within a group, Groups if looking at groups but not within a
     *         specific group.
     */
    ResultDocsCsv(WebserviceParams params) throws InvalidQuery {
        super();

        // TODO share with regular RequestHandlerHits

        // Might be null
        String groupBy = params.getGroupProps().orElse(null);
        String viewGroup = params.getViewGroup().orElse(null);
        isViewGroup = viewGroup != null;
        String sortBy = params.getSortProps().orElse(null);

        groups = null;
        subcorpusResults = params.subcorpus().execute();

        if (groupBy != null) {
            groups = params.docsGrouped().execute();
            docs = params.docs().execute();

            if (viewGroup != null) {
                PropertyValue groupId = PropertyValue.deserialize(groups.queryInfo().index(), groups.queryInfo().field(), viewGroup);
                if (groupId == null)
                    throw new BadRequest("ERROR_IN_GROUP_VALUE", "Cannot deserialize group value: " + viewGroup);
                DocGroup group = groups.get(groupId);
                if (group == null)
                    throw new BadRequest("GROUP_NOT_FOUND", "Group not found: " + viewGroup);

                docs = group.storedResults();

                // NOTE: sortBy is automatically applied to regular results, but not to results within groups
                // See ResultsGrouper::init (uses hits.getByOriginalOrder(i)) and DocResults::constructor
                // Also see SearchParams (hitsSortSettings, docSortSettings, hitGroupsSortSettings, docGroupsSortSettings)
                // There is probably no reason why we can't just sort/use the sort of the input results, but we need some more testing to see if everything is correct if we change this
                if (sortBy != null) {
                    DocProperty sortProp = DocProperty.deserialize(params.blIndex(), sortBy);
                    if (sortProp == null)
                        throw new BadRequest("ERROR_IN_SORT_VALUE", "Cannot deserialize sort value: " + sortBy);
                    docs = docs.sort(sortProp);
                }
            }
        } else {
            // Don't use JobDocsAll, as we only might not need them all.
            docs = params.docsSorted().execute();
        }

        // apply window settings
        // Different from the regular results, if no window settings are provided, we export the maximum amount automatically
        // The max for CSV exports is also different from the default pagesize maximum.
        if (docs != null) {
            long first = Math.max(0, params.getFirstResultToShow()); // Defaults to 0
            if (!docs.resultsStats().waitUntil().processedAtLeast(first))
                first = 0;

            long number = params.getSearchManager().config().getSearch().getMaxHitsToRetrieve();
            if (params.optNumberOfResultsToShow().isPresent()) {
                long requested = params.optNumberOfResultsToShow().get();
                if (number >= 0 || requested >= 0) { // clamp
                    number = Math.min(requested, number);
                }
            }

            if (number >= 0)
                docs = docs.window(first, number);
        }
    }

    public DocResults getDocs() {
        return docs;
    }

    public DocGroups getGroups() {
        return groups;
    }

    public DocResults getSubcorpusResults() {
        return subcorpusResults;
    }

    public boolean isViewGroup() {
        return isViewGroup;
    }
}
