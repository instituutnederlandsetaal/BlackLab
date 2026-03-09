package nl.inl.blacklab.server.lib.results;

import java.net.HttpURLConnection;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternSerializerBcql;
import nl.inl.blacklab.searches.SearchCache;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.index.Index;
import nl.inl.blacklab.server.index.IndexManager;
import nl.inl.blacklab.server.lib.Response;
import nl.inl.blacklab.server.lib.WebserviceParams;
import nl.inl.blacklab.server.lib.requests.RequestCorpusInfo;
import nl.inl.blacklab.server.lib.requests.RequestRelations;
import nl.inl.blacklab.webservice.WebserviceParameter;
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
     * @param params parameters
     * @param rs output stream
     */
    public static void opFieldInfo(WebserviceParams params, ResponseStreamer rs) {
        BlackLabIndex index = params.blIndex();
        IndexMetadata indexMetadata = index.metadata();
        String fieldName = params.getFieldName();
        boolean includeCustomInfo = params.getIncludeCustomInfo();
        if (indexMetadata.annotatedFields().exists(fieldName)) {
            // Annotated field
            AnnotatedField fieldDesc = indexMetadata.annotatedField(fieldName);
            RequestRelations reqRel = new RequestRelations(params.getCorpusName(), fieldName,
                    params.getLimitValues(), params.getRelClasses(),
                    params.getRelSeparateSpans(), params.getRelOnlySpans());
            ResultRelations relations = new ResultRelations(reqRel);
            ResultAnnotatedField resultAnnotatedField = WebserviceOperations.annotatedField(index, fieldDesc,
                    params.getListValuesFor(), params.getLimitValues(), true, relations);
            rs.annotatedField(resultAnnotatedField, includeCustomInfo);
        } else if (indexMetadata.metadataFields().exists(fieldName)) {
            // Metadata field
            MetadataField fieldDesc = indexMetadata.metadataField(fieldName);
            ResultMetadataField metadataField = WebserviceOperations.metadataField(params.getLimitValues(), fieldDesc, params.getCorpusName());
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
    public static void opCorpusInfo(WebserviceParams params, ResponseStreamer rs) {
        RequestRelations reqRel = new RequestRelations(params.getCorpusName(), null/*each field*/,
                params.getLimitValues(), params.getRelClasses(),
                params.getRelSeparateSpans(), params.getRelOnlySpans());
        RequestCorpusInfo req = new RequestCorpusInfo(params.getCorpusName(),
                params.getUser(), params.getListValuesFor(), params.getLimitValues(), params.getIncludeCustomInfo(),
                reqRel);
        ResultCorpusInfo corpusInfo = WebserviceOperations.corpusInfo(req);
        rs.corpusMetadataResponse(corpusInfo, params.getIncludeCustomInfo());
    }

    /**
     * Show (indexing) status of a corpus.
     *
     * @param params parameters
     * @param rs output stream
     */
    public static void opCorpusStatus(WebserviceParams params, ResponseStreamer rs) {
        IndexManager indexManager = params.getIndexManager();
        String corpusName = params.getCorpusName();
        Index index = indexManager.getIndex(corpusName);
        ResultIndexStatus corpusStatus = WebserviceOperations.resultIndexStatus(index, params.getUser());
        rs.corpusStatusResponse(corpusStatus, params.getIncludeCustomInfo());
    }

    /**
     * Show server information.
     *
     * @param params parameters
     * @param rs output stream
     */
    public static void opServerInfo(WebserviceParams params, boolean debugMode, ResponseStreamer rs) {
        ResultServerInfo serverInfo = WebserviceOperations.serverInfo(params, debugMode);
        rs.serverInfo(serverInfo);
    }

    /**
     * Find or group hits.
     *
     * @param params parameters
     * @param rs output stream
     */
    public static void opHits(WebserviceParams params, ResponseStreamer rs, boolean isCsv) throws InvalidQuery {
        if (params.isCalculateCollocations()) {
            if (isCsv) {
                // CSV collocations request
                throw new UnsupportedOperationException("CSV collocations not (yet) implemented");
            } else {
                // Collocations request
                TermFrequencyList tfl = WebserviceOperations.calculateCollocations(params);
                rs.collocationsResponse(tfl);
            }
        } else {
            // Hits request
            if (shouldReturnListOfGroups(params)) {
                // We're returning a list of groups
                ResultHitsGrouped hitsGrouped = WebserviceOperations.hitsGrouped(params, isCsv);
                rs.hitsGroupedResponse(hitsGrouped, isCsv);
            } else {
                // We're returning a list of results (ungrouped, or viewing single group)
                ResultHits resultHits = WebserviceOperations.hits(params, isCsv);
                rs.hitsResponse(resultHits, isCsv);
            }
        }
    }

    /**
     * Find or group documents.
     *
     * @param params parameters
     * @param rs output stream
     */
    public static void opDocs(WebserviceParams params, ResponseStreamer rs, boolean isCsv) throws InvalidQuery {
        ResultDocs result = WebserviceOperations.docs(params, isCsv);
        if (shouldReturnListOfGroups(params)) {
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
     * @param params parameters
     * @return true if we should return a list of groups
     */
    private static boolean shouldReturnListOfGroups(WebserviceParams params) {
        Optional<String> viewgroup = params.getViewGroup();
        boolean returnListOfGroups = false;
        if (params.getGroupProps().isPresent()) {
            // This is a grouping operation
            if (viewgroup.isEmpty()) {
                // We want the list of groups, not the contents of a single group
                returnListOfGroups = true;
            }
        } else if (viewgroup.isPresent()) {
            // "viewgroup" parameter without "group" parameter; error.
            throw new BadRequest("ERROR_IN_GROUP_VALUE",
                    "Parameter 'viewgroup' specified, but required 'group' parameter is missing.");
        }
        return returnListOfGroups;
    }

    /**
     * Return the original contents of a document.
     *
     * @param params parameters
     * @param rs output stream
     */
    public static void opDocContents(WebserviceParams params, ResponseStreamer rs) throws InvalidQuery {
        ResultDocContents result = WebserviceOperations.docContents(params);
        rs.docContentsResponseAsCdata(result);
    }

    /**
     * Return metadata for a document.
     *
     * @param params parameters
     * @param rs output stream
     */
    public static void opDocInfo(WebserviceParams params, ResponseStreamer rs) {
        Collection<MetadataField> metadataToWrite = WebserviceOperations.getMetadataToWrite(params);
        BlackLabIndex index = params.blIndex();
        ResultDocInfo docInfo = WebserviceOperations.docInfo(index, params.getDocPid(), null, metadataToWrite);

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
    public static void opDocSnippet(WebserviceParams params, ResponseStreamer rs) {
        ResultDocSnippet result = WebserviceOperations.docSnippet(params);
        rs.snippet(result);
    }

    /**
     * Calculate term frequencies.
     *
     * @param params parameters
     * @param rs output stream
     */
    public static void opTermFreq(WebserviceParams params, ResponseStreamer rs) {
        TermFrequencyList tfl = WebserviceOperations.getTermFrequencies(params);
        rs.termFreqResponse(tfl);
    }


    /**
     * Return autocomplete results for metadata or annotated field.
     *
     * @param params parameters
     * @param rs output stream
     */
    public static void opAutocomplete(WebserviceParams params, ResponseStreamer rs) {
        ResultAutocomplete result = WebserviceOperations.autocomplete(params);
        rs.autoComplete(result);
    }

    public static void opInputFormatInfo(WebserviceParams params, ResponseStreamer rs) {
        Optional<String> inputFormat = params.getInputFormat();
        if (!inputFormat.isPresent())
            throw new BadRequest("NO_INPUT_FORMAT", "No input format specified (" + WebserviceParameter.INPUT_FORMAT.value() + ")");
        ResultInputFormat result = WebserviceOperations.inputFormat(inputFormat.get());
        rs.formatInfoResponse(result);
    }

    public static void opListInputFormats(WebserviceParams params, ResponseStreamer rs, boolean debugMode) {
        ResultListInputFormats result = WebserviceOperations.listInputFormats(params, debugMode);
        rs.listFormatsResponse(result);
    }

    public static void opCacheInfo(WebserviceParams params, ResponseStreamer rs) {
        boolean includeDebugInfo = params.isIncludeDebugInfo();
        SearchCache blackLabCache = params.getSearchManager().getBlackLabCache();
        rs.cacheInfo(blackLabCache, includeDebugInfo);
    }

    public static int opClearCache(WebserviceParams params, ResponseStreamer rs, boolean debugMode) {
        if (!debugMode) {
            return Response.forbidden(rs);
        } else {
            params.getSearchManager().getBlackLabCache().clear(false);
            return Response.status(rs, "SUCCESS", "Cache cleared succesfully.", HttpURLConnection.HTTP_OK);
        }
    }

    public static void opInputFormatXslt(WebserviceParams params, ResponseStreamer rs) {
        Optional<String> inputFormat = params.getInputFormat();
        if (!inputFormat.isPresent())
            throw new BadRequest("NO_INPUT_FORMAT", "No input format specified (" + WebserviceParameter.INPUT_FORMAT.value() + ")");
        ResultInputFormat result = WebserviceOperations.inputFormat(inputFormat.get());
        rs.formatXsltResponse(result);
    }

    public static void opParsePattern(WebserviceParams params, ResponseStreamer rs) {
        if (!rs.getDataStream().getType().equals("json"))
            throw new UnsupportedOperationException("/parse-pattern only supports JSON output");
        // Write response
        DataStream ds = rs.getDataStream();
        ds.startMap();
        {
            ds.startEntry("params").startMap();
            {
                ds.entry("patt", params.getPattern());
                ds.entry("pattlang", params.getPattLanguage());
            }
            ds.endMap().endEntry();
            ds.startEntry("parsed").startMap();
            {
                try {
                    TextPattern tp = params.pattern().orElse(null);
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

    public static void opRelations(WebserviceParams params, ResponseStreamer rs) {
        String corpusName = params.getCorpusName();
        String annotatedFieldName = params.getFieldName().isEmpty() ? null : params.getFieldName();
        long limitValues = params.getLimitValues();
        String parRelClasses = params.getRelClasses();
        boolean separateSpans = params.getRelSeparateSpans();
        boolean onlySpans = params.getRelOnlySpans();
        RequestRelations request = new RequestRelations(corpusName, annotatedFieldName, limitValues,
                parRelClasses, separateSpans, onlySpans);

        ResultRelations result = new ResultRelations(request);

        // Write response
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
