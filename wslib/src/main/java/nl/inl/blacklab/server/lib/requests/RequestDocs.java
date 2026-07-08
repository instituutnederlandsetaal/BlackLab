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
import nl.inl.blacklab.server.lib.ParamUtil;
import nl.inl.blacklab.server.lib.ParamsForResponse;
import nl.inl.blacklab.server.lib.QueryParams;
import nl.inl.blacklab.webservice.WsParam;

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
        BlackLabIndex index = ParamUtil.index(qpar.getCorpusName());
        SampleParameters sampleParams = ParamUtil.sampleParams(
                qpar.optDouble(WsParam.SAMPLE).orElse(null),
                qpar.optLong(WsParam.SAMPLE_NUMBER).orElse(null),
                qpar.optLong(WsParam.SAMPLE_SEED).orElse(null));
        return new RequestDocs(
                index,
                ParamUtil.filterQuery(qpar),
                RequestHits.optFromParams(qpar, isCsv, null).orElse(null),
                ParamUtil.docSortProperty(index, qpar.opt(WsParam.GROUP_BY).orElse(null),
                        qpar.opt(WsParam.SORT_BY).orElse(null), qpar.opt(WsParam.VIEW_GROUP).orElse(null)),
                sampleParams,
                ParamUtil.windowSettings(qpar, isCsv),
                ParamUtil.docGroupProperty(index, qpar.opt(WsParam.GROUP_BY).orElse(null)),
                ParamUtil.docGroupSortProperty(qpar.opt(WsParam.GROUP_BY).orElse(null),
                        qpar.opt(WsParam.SORT_BY).orElse(null), qpar.opt(WsParam.VIEW_GROUP).orElse(null)),
                qpar.opt(WsParam.VIEW_GROUP).orElse(null),
                ParamUtil.getMetadataToInclude(index, qpar.getList(WsParam.LIST_VALUES_FOR_METADATA_FIELDS)),
                qpar.opt(WsParam.FACETS).orElse(null),
                qpar.getBool(WsParam.WAIT_FOR_TOTAL_COUNT),
                ParamUtil.includeSubcorpusSize(qpar),
                isCsv,
                CsvSettings.fromParams(qpar),
                qpar);
    }

    public static SearchDocs docsSearch(BlackLabIndex index, Query docFilterQuery, RequestHits requestHits) throws BlsException {
        if (requestHits != null) {
            return requestHits.getSearch().docs(-1);
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
            return optHits().getSearch().docCount();
        return docsSearch(index(), filterQuery(), null).count();
    }

    public SearchFacets facets() {
        List<DocProperty> facets = DocProperty.propsFromDesc(index(), facetDesc);
        return facets == null ? null :
                docsSearch(index(), filterQuery(), optHits).facet(facets);
    }
}
