package nl.inl.blacklab.server.lib.requests;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.results.SampleParameters;
import nl.inl.blacklab.search.results.SearchSettings;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.server.lib.WebserviceParams;
import nl.inl.blacklab.server.lib.WebserviceParamsImpl;

/** A request for a hits search.
 * <p>
 * Searches the given pattern in the documents determined by the filterQuery, if any.
 * <p>
 * Can also filter hits by a property and value, sort the hits, and sample them,
 * if requested.
 *
 * @param searchField Which annotated field we're searching
 * @param pattern Pattern to search for
 * @param adjustHits Adjust hits to include all matched relations or not? (adjusts the pattern)
 * @param withSpans Automatically capture any spans overlapping with the hit or not? (adjusts the pattern)
 * @param filterQuery Search only in documents matching this query, or null for all
 * @param searchSettings Some settings that influence query optimization and maximum # of hits processed
 * @param useCache Use the results cache or ignore it for this query?
 * @param propFilter Optional property/value hit filter
 * @param sortBy Optional property to sort by
 * @param sampleParams Optional sample parameters
 */
public record RequestHits(
        AnnotatedField searchField,
        TextPattern pattern,
        boolean adjustHits,
        boolean withSpans,
        Query filterQuery,
        SearchSettings searchSettings,
        boolean useCache,
        WebserviceParamsImpl.RequestPropFilter propFilter,
        HitProperty sortBy,
        SampleParameters sampleParams) {
    public static RequestHits fromParams(WebserviceParams params1) {
        if (!(params1 instanceof WebserviceParamsImpl params))
            throw new IllegalArgumentException();
        HitProperty sortBy = params.hitsSortSettings() == null ? null : params.hitsSortSettings().sortBy();
        return new RequestHits(params.getSearchField(), params.pattern().orElse(null),
                params.getAdjustRelationHits(), params.getWithSpans(), params.filterQuery(), params.searchSettings(),
                params.useCache(), WebserviceParamsImpl.RequestPropFilter.fromParams(params),
                sortBy, params.sampleSettings());
    }

    public BlackLabIndex index() {
        return searchField.index();
    }
}
