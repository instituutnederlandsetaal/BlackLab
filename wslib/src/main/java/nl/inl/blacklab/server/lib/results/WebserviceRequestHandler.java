package nl.inl.blacklab.server.lib.results;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.results.hitresults.HitResults;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternSerializerBcql;
import nl.inl.blacklab.searches.SearchCache;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.index.IndexManager;
import nl.inl.blacklab.server.lib.Response;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.requests.RequestAutocomplete;
import nl.inl.blacklab.server.lib.requests.RequestCorpusInfo;
import nl.inl.blacklab.server.lib.requests.RequestCorpusStatus;
import nl.inl.blacklab.server.lib.requests.RequestDocContents;
import nl.inl.blacklab.server.lib.requests.RequestDocInfo;
import nl.inl.blacklab.server.lib.requests.RequestDocSnippet;
import nl.inl.blacklab.server.lib.requests.RequestDocs;
import nl.inl.blacklab.server.lib.requests.RequestFieldInfo;
import nl.inl.blacklab.server.lib.requests.RequestHits;
import nl.inl.blacklab.server.lib.requests.RequestHitsGrouped;
import nl.inl.blacklab.server.lib.requests.RequestOldCollocations;
import nl.inl.blacklab.server.lib.requests.RequestParsePattern;
import nl.inl.blacklab.server.lib.requests.RequestRelations;
import nl.inl.blacklab.server.lib.requests.RequestServerInfo;
import nl.inl.blacklab.server.lib.requests.RequestTermFrequencies;
import nl.inl.blacklab.webservice.WsParam;
import nl.inl.util.JsonSchemaUtil;

/**
 * Handle all the different webservice requests, given the requested operation,
 * parameters and output stream.
 * <p>
 * This is used for both the BLS and Solr webservices.
 */
public class WebserviceRequestHandler {

    private WebserviceRequestHandler() {
    }

    /**
     * Show information about a field in a corpus.
     *
     * @param request parameters
     * @param rs output stream
     */
    public static void opFieldInfo(RequestFieldInfo request, ResponseStreamer rs) {
        IndexMetadata indexMetadata = request.index().metadata();
        String fieldName = request.fieldName();
        boolean includeCustomInfo = request.includeCustomInfo();
        long limitValues = request.limitValues();
        if (indexMetadata.annotatedFields().exists(fieldName)) {
            // Annotated field
            AnnotatedField fieldDesc = indexMetadata.annotatedField(fieldName);
            RequestRelations reqRel = new RequestRelations(fieldDesc, limitValues, request.relClasses(),
                    request.relSeparateSpans(), request.relOnlySpans());
            ResultRelations relations = new ResultRelations(reqRel);
            ResultAnnotatedField resultAnnotatedField = WebserviceOperations.annotatedField(fieldDesc,
                    request.listValuesFor(), limitValues, true, relations);
            rs.annotatedField(resultAnnotatedField, includeCustomInfo);
        } else if (indexMetadata.metadataFields().exists(fieldName)) {
            // Metadata field
            MetadataField fieldDesc = indexMetadata.metadataField(fieldName);
            ResultMetadataField metadataField = WebserviceOperations.metadataField(limitValues, fieldDesc,
                    fieldDesc.index().name());
            rs.metadataField(metadataField, includeCustomInfo);
        } else {
            // Unknown field
            throw new BadRequest("UNKNOWN_FIELD", "Field '" + fieldName + "' not found in index.");
        }
    }

    /**
     * Show information about a corpus.
     *
     * @param params parameters
     * @param rs output stream
     */
    public static void opCorpusInfo(RequestCorpusInfo req, ResponseStreamer rs) {
        ResultCorpusInfo corpusInfo = WebserviceOperations.corpusInfo(req);
        rs.corpusMetadataResponse(corpusInfo, req.customInfo());
    }

    /**
     * Show (indexing) status of a corpus.
     *
     * @param params parameters
     * @param rs output stream
     */
    public static void opCorpusStatus(RequestCorpusStatus req, ResponseStreamer rs) {
        ResultIndexStatus corpusStatus = WebserviceOperations.resultIndexStatus(req.index());
        rs.corpusStatusResponse(corpusStatus, req.includeCustomInfo());
    }

    /**
     * Show server information.
     *
     * @param request parameters
     * @param rs output stream
     */
    public static void opServerInfo(RequestServerInfo request, ResponseStreamer rs) {
        ResultServerInfo serverInfo = new ResultServerInfo(request);
        rs.serverInfo(serverInfo);
    }

    /**
     * Find or group hits.
     *
     * @param params parameters
     * @param rs output stream
     */
    public static void opHits(RequestHits reqHits, ResponseStreamer rs, boolean isCsv) throws InvalidQuery {
        if (reqHits.calculateCollocations()) {
            if (isCsv) {
                // CSV collocations request
                throw new UnsupportedOperationException("CSV collocations currently not implemented");
            } else {
                // Collocations request
                RequestOldCollocations request = RequestOldCollocations.fromHitsRequest(reqHits);
                opOldCollocations(request, rs);
            }
        } else {
            // Hits request
            if (shouldReturnListOfGroups(reqHits.groupBy() != null, reqHits.viewGroup())) {
                // We're returning a list of groups
                RequestHitsGrouped reqGroup = RequestHitsGrouped.fromHitsRequestParams(reqHits);
                opHitsGrouped(reqGroup, rs, isCsv);
            } else {
                // We're returning a list of results (ungrouped, or viewing single group)
                ResultHits resultHits = WebserviceOperations.hits(reqHits);
                rs.hitsResponse(resultHits, isCsv);
            }
        }
    }

    private static void opOldCollocations(RequestOldCollocations request, ResponseStreamer rs) {
        HitResults hits = WebserviceOperations.hits(request.requestHits()).getHits();
        TermFrequencyList tfl = WebserviceOperations.getCollocations(request, hits);
        rs.collocationsResponse(tfl);
    }

    public static void opHitsGrouped(RequestHitsGrouped reqGroup, ResponseStreamer rs, boolean isCsv) {
        ResultHitsGrouped hitsGrouped = new ResultHitsGrouped(reqGroup);
        rs.hitsGroupedResponse(hitsGrouped, isCsv);
    }

    /**
     * Find or group documents.
     *
     * @param params parameters
     * @param rs output stream
     */
    public static void opDocs(RequestDocs requestDocs, ResponseStreamer rs, boolean isCsv) throws InvalidQuery {
        ResultDocs result = WebserviceOperations.docs(requestDocs);
        String viewGroup = requestDocs.viewGroup();
        if (shouldReturnListOfGroups(requestDocs.groupBy() != null, viewGroup)) {
            // We're returning a list of groups
            rs.docsGroupedResponse(result, isCsv);
        } else {
            // We're returning a list of results (ungrouped, or viewing single group)
            rs.docsResponse(result, isCsv);
        }
    }

    /**
     * Is this a request for a list of groups?
     * <p>
     * If not, it's either a regular request for (hits or docs) results,
     * or a request for viewing the results in a single group.
     *
     * @param requestHits parameters
     * @return true if we should return a list of groups
     */
    private static boolean shouldReturnListOfGroups(boolean hasGroupBy, String viewGroup) {
        boolean hasViewGroup = !StringUtils.isEmpty(viewGroup);
        if (!hasGroupBy && hasViewGroup) {
            // "viewgroup" parameter without "group" parameter; error.
            throw new BadRequest("ERROR_IN_GROUP_VALUE",
                    "Parameter 'viewgroup' specified, but required 'group' parameter is missing.");
        }
        return hasGroupBy && !hasViewGroup;
    }

    /**
     * Return the original contents of a document.
     *
     * @param request parameters
     * @param rs output stream
     */
    public static void opDocContents(RequestDocContents request, ResponseStreamer rs) throws InvalidQuery {
        ResultDocContents result = new ResultDocContents(request);
        rs.docContentsResponseAsCdata(result);
    }

    /**
     * Return metadata for a document.
     *
     * @param req parameters
     * @param rs output stream
     */
    public static void opDocInfo(RequestDocInfo req, ResponseStreamer rs) {
        BlackLabIndex index = req.index();
        ResultDocInfo docInfo = new ResultDocInfo(index, req.docPid(), null, req.metadataToInclude());
        Map<String, List<String>> metadataFieldGroups = WebserviceOperations.getMetadataFieldGroupsWithRest(index);
        Map<String, String> docFields = WebserviceOperations.getDocFields(index);
        Map<String, String> metaDisplayNames = WebserviceOperations.getMetaDisplayNames(index);

        // Document info
        rs.docInfoResponse(docInfo, metadataFieldGroups, docFields, metaDisplayNames);
    }

    /**
     * Return a snippet from a document.
     *
     * @param params parameters
     * @param rs output stream
     */
    public static void opDocSnippet(RequestDocSnippet request, ResponseStreamer rs) {
        ResultDocSnippet result = new ResultDocSnippet(request);
        rs.snippet(result);
    }

    /**
     * Calculate term frequencies.
     *
     * @param reqTermFreq parameters
     * @param rs output stream
     */
    public static void opTermFreq(RequestTermFrequencies reqTermFreq, ResponseStreamer rs) {
        TermFrequencyList tfl = WebserviceOperations.getTermFrequencies(reqTermFreq);
        rs.termFreqResponse(tfl);
    }


    /**
     * Return autocomplete results for metadata or annotated field.
     *
     * @param request parameters
     * @param rs output stream
     */
    public static void opAutocomplete(RequestAutocomplete request, ResponseStreamer rs) {
        ResultAutocomplete result = new ResultAutocomplete(request);
        rs.autoComplete(result);
    }

    public static void opInputFormatInfo(String inputFormat, ResponseStreamer rs) {
        if (StringUtils.isEmpty(inputFormat))
            throw new BadRequest("NO_INPUT_FORMAT", "No input format specified (" + WsParam.INPUT_FORMAT.value() + ")");
        ResultInputFormat result = new ResultInputFormat(inputFormat);
        rs.formatInfoResponse(result);
    }

    public static void opListInputFormats(User user, IndexManager indexMan, ResponseStreamer rs, boolean debugMode) {
        ResultListInputFormats result = new ResultListInputFormats(user, indexMan, debugMode);
        rs.listFormatsResponse(result);
    }

    public static void opListPlugins(ResponseStreamer rs) {
        ResultListPlugins result = new ResultListPlugins();
        rs.pluginsResponse(result);
    }

    public static void opCacheInfo(SearchCache blackLabCache, boolean includeDebugInfo, ResponseStreamer rs) {
        rs.cacheInfo(blackLabCache, includeDebugInfo);
    }

    public static int opClearCache(SearchCache cache, ResponseStreamer rs, boolean debugMode) {
        if (!debugMode) {
            return Response.forbidden(rs);
        } else {
            cache.clear(false);
            return Response.status(rs, "SUCCESS", "Cache cleared succesfully.", HttpURLConnection.HTTP_OK);
        }
    }

    public static void opInputFormatXslt(String inputFormat, ResponseStreamer rs) {
        if (StringUtils.isEmpty(inputFormat))
            throw new BadRequest("NO_INPUT_FORMAT", "No input format specified (" + WsParam.INPUT_FORMAT.value() + ")");
        ResultInputFormat result = new ResultInputFormat(inputFormat);
        rs.formatXsltResponse(result);
    }

    public static void opParsePattern(RequestParsePattern request, ResponseStreamer rs) {
        if (!rs.getDataStream().getType().equals("json"))
            throw new UnsupportedOperationException("/parse-pattern only supports JSON output");
        // Write response
        DataStream ds = rs.getDataStream();
        ds.startMap();
        {
            ds.startEntry(rs.KEY_PARAMS).startMap();
            {
                ds.entry(WsParam.PATTERN.value(), request.bcqlQuery());
                ds.entry(WsParam.PATTERN_LANGUAGE.value(), request.queryLanguage());
            }
            ds.endMap().endEntry();
            ds.startEntry("parsed").startMap();
            {
                try {
                    TextPattern tp = request.textPattern();
                    try {
                        ds.entry(ResponseStreamer.KEY_BCQL, TextPatternSerializerBcql.serialize(tp));
                    } catch (Exception e) {
                        ds.entry("corpusql-error", e.getMessage());
                    }
                    ds.entry(ResponseStreamer.KEY_JSON, tp);
                } catch (Exception e) {
                    ds.entry("error", e.getMessage());
                }
            }
            ds.endMap().endEntry();
        }
        ds.endMap();
    }

    public static void opRelations(RequestRelations request, ResponseStreamer rs) {
        ResultRelations result = new ResultRelations(request);
        rs.relations(result);
    }

    final static List<String> AVAILABLE_SCHEMAS = List.of(
            "input-format",
            "bcql"
    );

    public static void opListSchemas(ResponseStreamer rs) {
        DataStream ds = rs.getDataStream();
        ds.startList();
        for (String schema: AVAILABLE_SCHEMAS)
            ds.startItem("schema").value(schema).endItem();
        ds.endList();
    }

    public static void opSchema(String urlResource, ResponseStreamer rs) {
        switch (urlResource) {
        case "input-format":
            rs.getDataStream().plain(JsonSchemaUtil.getJsonSchema(ConfigInputFormat.class));
            break;
        case "bcql":
            // TODO
            break;
        default:
            throw new BadRequest("UNKNOWN_SCHEMA", "Unknown schema '" + urlResource + "'.");
        }
    }
}
