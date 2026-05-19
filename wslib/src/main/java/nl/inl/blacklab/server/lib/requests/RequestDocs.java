package nl.inl.blacklab.server.lib.requests;

import java.util.Collection;
import java.util.List;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.resultproperty.DocGroupProperty;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.results.Results;
import nl.inl.blacklab.search.results.SampleParameters;
import nl.inl.blacklab.searches.SearchCount;
import nl.inl.blacklab.searches.SearchDocGroups;
import nl.inl.blacklab.searches.SearchDocs;
import nl.inl.blacklab.searches.SearchFacets;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.jobs.WindowSettings;
import nl.inl.blacklab.server.lib.ParamsForResponse;
import nl.inl.blacklab.server.lib.QueryParams;
import nl.inl.blacklab.server.lib.WebserviceParams;

public record RequestDocs(
        BlackLabIndex index,
        Query filterQuery,
        RequestHits optHits,
        DocProperty sortBy,
        SampleParameters sampleParams,
        WindowSettings windowSettings,
        DocProperty groupBy,
        DocGroupProperty sortGroupsBy,
        String viewGroup,
        Collection<MetadataField> metadataToInclude,
        String facetDesc,
        boolean waitForTotal,
        boolean includeSubcorpusSize,
        boolean isCsv,
        CsvSettings csvSettings,
        ParamsForResponse params) {
    public static RequestDocs fromParams(QueryParams qpar, boolean isCsv) {
        BlackLabIndex index = WebserviceParams.index(qpar.getCorpusName());
        SampleParameters sampleParams = WebserviceParams.sampleParams(
                qpar.getSampleFraction().orElse(null),
                qpar.getSampleNumber().orElse(null),
                qpar.getSampleSeed().orElse(null));
        return new RequestDocs(
                index,
                WebserviceParams.filterQuery(qpar),
                RequestHits.optFromParams(qpar, isCsv).orElse(null),
                WebserviceParams.docSortProperty(index, qpar.getGroupBy().orElse(null),
                        qpar.getSortBy().orElse(null), qpar.getViewGroup().orElse(null)),
                sampleParams,
                WebserviceParams.windowSettings(qpar, isCsv),
                WebserviceParams.docGroupProperty(index, qpar.getGroupBy().orElse(null)),
                WebserviceParams.docGroupSortProperty(qpar.getGroupBy().orElse(null),
                        qpar.getSortBy().orElse(null), qpar.getViewGroup().orElse(null)),
                qpar.getViewGroup().orElse(null),
                WebserviceParams.getMetadataToInclude(index, qpar.getListMetadataValuesFor()),
                qpar.getFacetProps().orElse(null),
                qpar.getWaitForTotal(),
                qpar.getIncludeSubcorpusSize(),
                isCsv,
                CsvSettings.fromParams(qpar),
                qpar);
    }

    public static SearchDocs docsSearch(BlackLabIndex index, Query docFilterQuery, RequestHits requestHits) throws BlsException {
        if (requestHits != null) {
            return RequestHits.createSearch(requestHits).docs(-1);
        }
        return BlackLabIndex.getSubcorpusSearch(index, docFilterQuery);
    }

    public SearchDocGroups docsGrouped() throws BlsException {
        RequestHits requestHits = optHits();
        DocProperty groupBy = groupBy();
        assert groupBy != null;
        return docsSearch(index(), filterQuery(), requestHits)
                .group(groupBy, Results.NO_LIMIT)
                .sort(sortGroupsBy());
    }

    public SearchDocs docsSorted() throws BlsException {
        SearchDocs searchDocs = docsSearch(index(), filterQuery(),
                optHits());
        return sortBy() == null ? searchDocs : searchDocs.sort(sortBy());
    }

    public SearchCount docsCount() throws BlsException {
        if (optHits() != null)
            return RequestHits.createSearch(optHits()).docCount();
        return docsSearch(index(), filterQuery(), null).count();
    }

    public SearchFacets facets() {
        List<DocProperty> facets = DocProperty.propsFromDesc(index(), facetDesc);
        return facets == null ? null :
                docsSearch(index(), filterQuery(), optHits).facet(facets);
    }
}
