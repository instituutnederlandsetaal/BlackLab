package org.ivdnt.blacklab.proxy.resources;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.ivdnt.blacklab.proxy.backend.Backend;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.requests.RequestCorpusInfo;
import nl.inl.blacklab.server.lib.requests.RequestRelations;
import nl.inl.blacklab.server.lib.results.ApiVersion;

@Path("{corpora:(corpora/)?}{corpusName : (?!input-formats\\b)[^/]+}")
public class CorpusResource {

    private static ApiVersion getApiVersion(String corporaPathPart, String strApiVersion) {
        return strApiVersion.isEmpty() ?
                // Default: v5 if path starts with /corpora; v4 otherwise
                (StringUtils.isEmpty(corporaPathPart) ? ApiVersion.V4_LATEST : ApiVersion.V5_0) :
                ApiVersion.fromValue(strApiVersion);
    }

    /** Object that actually carries out the request */
    private final Backend backend;

    @Inject
    public CorpusResource(Backend backend) {
        this.backend = backend;
    }

    @POST
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response corpusInfo(
            @PathParam("corpora") String corporaPath,
            @PathParam("corpusName") String corpusName) {
        if (corpusName.equals("cache-clear")) {// POST naar /cache-clear : clear cache (not implemented)
            return SimpleResponse.notImplemented("/cache-clear");
        }

        // POST naar /CORPUSNAME ; not supported
        return SimpleResponse.notImplemented("POST to /CORPUSNAME");
    }

    /**
     * Get information about a corpus.
     *
     * @param corpusName corpus name
     * @return corpus information
     */
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response corpusInfo(
            @PathParam("corpora") String corporaPath,
            @QueryParam("api") @DefaultValue ("") String strApiVersion,
            @PathParam("corpusName") String corpusName,
            @QueryParam("custom") @DefaultValue ("false") boolean customInfo,
            @QueryParam("listvalues") @DefaultValue("") String parListValuesFor,
            @QueryParam("limitvalues") @DefaultValue("200") long limitValues,
            @QueryParam("relclasses") @DefaultValue("") String relClasses) {
        // TODO: apiVersion default from config
        Response response = null;
        if (StringUtils.isEmpty(corporaPath)) {
            // Old API: handle endpoints that are not actually corpora
            response = switch (corpusName) {
                case "cache-info" -> SimpleResponse.notImplemented("/cache-info");
                case "help" -> SimpleResponse.notImplemented("/help");
                case "cache-clear" -> SimpleResponse.error(Response.Status.BAD_REQUEST, "WRONG_METHOD",
                        "/cache-clear works only with POST");
                default -> null;
            };
        }
        if (response == null) {
            // Actually a corpus name, so get the corpus info.
            response = getCorpusInfo(corporaPath, strApiVersion, corpusName, customInfo, parListValuesFor, limitValues,
                    relClasses);
        }
        return response;
    }

    private Response getCorpusInfo(String corporaPath, String strApiVersion, String corpusName, boolean customInfo,
            String parListValuesFor, long limitValues, String relClasses) {
        User user = User.anonymous(""); // TODO detect user
        List<String> listValuesFor = parListValuesFor.isEmpty() ? List.of() :
                Arrays.asList(parListValuesFor.split(",", -1));
        RequestRelations reqRel = new RequestRelations(null/*each field*/, limitValues, relClasses, true, false);
        RequestCorpusInfo req = new RequestCorpusInfo(corpusName, listValuesFor, limitValues, customInfo, reqRel);
        return backend.corpusInfo(getApiVersion(corporaPath, strApiVersion), req);
    }

    private Response doField(String corporaPath, String strApiVersion, String corpusName, String fieldName,
            MultivaluedMap<String, String> params, String httpMethod) {
        try {
            return backend.field(getApiVersion(corporaPath, strApiVersion),
                    corpusName, fieldName, params, httpMethod);
        } catch (Exception e) {
            // If the field is not found, we return a 404 Not Found error.
            // This is consistent with how the BlackLab web service behaves.
            StringWriter s = new StringWriter();
            PrintWriter pw = new PrintWriter(s);
            e.printStackTrace(pw);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(s.toString())
                    .type(MediaType.TEXT_PLAIN).build();
        }
    }

    @Path("/fields/{fieldName}")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response getField(
            @PathParam("corpora") String corporaPath,
            @QueryParam("api") @DefaultValue ("") String strApiVersion,
            @PathParam("corpusName") String corpusName,
            @PathParam("fieldName") String fieldName,
            @Context UriInfo uriInfo) {
        MultivaluedMap<String, String> params = uriInfo.getQueryParameters();
        return doField(corporaPath, strApiVersion, corpusName, fieldName, params, HttpMethod.GET);
    }

    @Path("/fields/{fieldName}")
    @POST
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response postField(
            @PathParam("corpora") String corporaPath,
            @QueryParam("api") @DefaultValue ("") String strApiVersion,
            @PathParam("corpusName") String corpusName,
            @PathParam("fieldName") String fieldName,
            MultivaluedMap<String, String> formParams) {
        return doField(corporaPath, strApiVersion, corpusName, fieldName, formParams, HttpMethod.POST);
    }

    @Path("/parse-pattern")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getParsePattern(
            @PathParam("corpora") String corporaPath,
            @QueryParam("api") @DefaultValue ("") String strApiVersion,
            @PathParam("corpusName") String corpusName,
            @Context UriInfo uriInfo) {
        return backend.parsePattern(getApiVersion(corporaPath, strApiVersion),
                corpusName, uriInfo.getQueryParameters(), HttpMethod.GET);
    }

    @Path("/parse-pattern")
    @POST
    @Consumes("application/x-www-form-urlencoded")
    @Produces(MediaType.APPLICATION_JSON)
    public Response postParsePattern(
            @PathParam("corpora") String corporaPath,
            @QueryParam("api") @DefaultValue ("") String strApiVersion,
            @PathParam("corpusName") String corpusName,
            MultivaluedMap<String, String> formParams) {
        return backend.parsePattern(getApiVersion(corporaPath, strApiVersion),
                corpusName, formParams, HttpMethod.POST);
    }

    @Path("/relations")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRelations(
            @PathParam("corpora") String corporaPath,
            @QueryParam("api") @DefaultValue ("") String strApiVersion,
            @PathParam("corpusName") String corpusName,
            @Context UriInfo uriInfo) {
        return backend.relations(getApiVersion(corporaPath, strApiVersion),
                corpusName, uriInfo.getQueryParameters(), HttpMethod.GET);
    }

    @Path("/relations")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response postRelations(
            @PathParam("corpora") String corporaPath,
            @QueryParam("api") @DefaultValue ("") String strApiVersion,
            @PathParam("corpusName") String corpusName,
            @Context UriInfo uriInfo) {
        return backend.relations(getApiVersion(corporaPath, strApiVersion),
                corpusName, uriInfo.getQueryParameters(), HttpMethod.GET);
    }

    @Path("/hits")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, ProxyParamsUtil.MIME_TYPE_CSV })
    public Response getHits(
            @PathParam("corpora") String corporaPath,
            @QueryParam("api") @DefaultValue ("") String strApiVersion,
            @PathParam("corpusName") String corpusName,
            @Context UriInfo uriInfo,
            @Context HttpHeaders headers) {
        return backend.hits(getApiVersion(corporaPath, strApiVersion),
                corpusName, uriInfo.getQueryParameters(), headers, HttpMethod.GET);
    }

    @Path("/hits")
    @POST
    @Consumes("application/x-www-form-urlencoded")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, ProxyParamsUtil.MIME_TYPE_CSV })
    public Response postHits(
            @PathParam("corpora") String corporaPath,
            @QueryParam("api") @DefaultValue ("") String strApiVersion,
            @PathParam("corpusName") String corpusName,
            MultivaluedMap<String, String> formParams,
            @Context HttpHeaders headers) {
        return backend.hits(getApiVersion(corporaPath, strApiVersion),
                corpusName, formParams, headers, HttpMethod.POST);
    }

    @Path("/docs")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, ProxyParamsUtil.MIME_TYPE_CSV })
    public Response getDocs(
            @PathParam("corpora") String corporaPath,
            @QueryParam("api") @DefaultValue ("") String strApiVersion,
            @PathParam("corpusName") String corpusName,
            @Context UriInfo uriInfo,
            @Context HttpHeaders headers) {
        return backend.docs(getApiVersion(corporaPath, strApiVersion),
                corpusName, uriInfo.getQueryParameters(), headers, HttpMethod.GET);
    }

    @Path("/docs")
    @POST
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, ProxyParamsUtil.MIME_TYPE_CSV })
    public Response postDocs(
            @PathParam("corpora") String corporaPath,
            @QueryParam("api") @DefaultValue ("") String strApiVersion,
            @PathParam("corpusName") String corpusName,
            MultivaluedMap<String, String> formParams,
            @Context HttpHeaders headers) {
        return backend.docs(getApiVersion(corporaPath, strApiVersion),
                corpusName, formParams, headers, HttpMethod.POST);
    }

    @Path("/docs/{pid}")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response getDocInfo(
            @PathParam("corpora") String corporaPath,
            @QueryParam("api") @DefaultValue ("") String strApiVersion,
            @PathParam("corpusName") String corpusName,
            @PathParam("pid") String docPid,
            @Context UriInfo uriInfo) {
        return backend.docInfo(getApiVersion(corporaPath, strApiVersion),
                corpusName, docPid, uriInfo.getQueryParameters(), HttpMethod.GET);
    }

    @Path("/docs/{pid}")
    @POST
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response postDocInfo(
            @PathParam("corpora") String corporaPath,
            @QueryParam("api") @DefaultValue ("") String strApiVersion,
            @PathParam("corpusName") String corpusName,
            @PathParam("pid") String docPid,
            MultivaluedMap<String, String> formParams) {
        return backend.docInfo(getApiVersion(corporaPath, strApiVersion),
                corpusName, docPid, formParams, HttpMethod.POST);
    }

    @Path("/docs/{pid}/contents")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response getDocContents(
            @PathParam("corpora") String corporaPath,
            @QueryParam("api") @DefaultValue ("") String strApiVersion,
            @PathParam("corpusName") String corpusName,
            @PathParam("pid") String docPid,
            @Context UriInfo uriInfo) {
        return backend.docContents(getApiVersion(corporaPath, strApiVersion),
                corpusName, docPid, uriInfo.getQueryParameters(), HttpMethod.GET);
    }

    @Path("/docs/{pid}/contents")
    @POST
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response getDocContents(
            @PathParam("corpora") String corporaPath,
            @QueryParam("api") @DefaultValue ("") String strApiVersion,
            @PathParam("corpusName") String corpusName,
            @PathParam("pid") String docPid,
            MultivaluedMap<String, String> formParams) {
        return backend.docContents(getApiVersion(corporaPath, strApiVersion),
                corpusName, docPid, formParams, HttpMethod.POST);
    }

    @Path("/docs/{pid}/snippet")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response getDocSnippet(
            @PathParam("corpora") String corporaPath,
            @QueryParam("api") @DefaultValue ("") String strApiVersion,
            @PathParam("corpusName") String corpusName,
            @PathParam("pid") String docPid,
            @Context UriInfo uriInfo) {
        return backend.docSnippet(getApiVersion(corporaPath, strApiVersion),
                corpusName, docPid, uriInfo.getQueryParameters(), HttpMethod.GET);
    }

    @Path("/docs/{pid}/snippet")
    @POST
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response postDocSnippet(
            @PathParam("corpora") String corporaPath,
            @QueryParam("api") @DefaultValue ("") String strApiVersion,
            @PathParam("corpusName") String corpusName,
            @PathParam("pid") String docPid,
            MultivaluedMap<String, String> formParams) {
        return backend.docSnippet(getApiVersion(corporaPath, strApiVersion),
                corpusName, docPid, formParams, HttpMethod.POST);
    }

    @Path("/termfreq")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response getTermFreq(
            @PathParam("corpora") String corporaPath,
            @QueryParam("api") @DefaultValue ("") String strApiVersion,
            @PathParam("corpusName") String corpusName,
            @Context UriInfo uriInfo) {
        return backend.termFreq(getApiVersion(corporaPath, strApiVersion),
                corpusName, uriInfo.getQueryParameters(), HttpMethod.GET);
    }

    @Path("/termfreq")
    @POST
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response postTermFreq(
            @PathParam("corpora") String corporaPath,
            @QueryParam("api") @DefaultValue ("") String strApiVersion,
            @PathParam("corpusName") String corpusName,
            MultivaluedMap<String, String> formParams) {
        return backend.termFreq(getApiVersion(corporaPath, strApiVersion),
                corpusName, formParams, HttpMethod.POST);
    }

    @Path("/status")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response getStatus(
            @PathParam("corpora") String corporaPath,
            @QueryParam("api") @DefaultValue ("") String strApiVersion,
            @PathParam("corpusName") String corpusName,
            @Context UriInfo uriInfo) {
        return backend.status(getApiVersion(corporaPath, strApiVersion),
                corpusName, uriInfo.getQueryParameters(), HttpMethod.GET);
    }

    @Path("/status")
    @POST
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response postStatus(
            @PathParam("corpora") String corporaPath,
            @QueryParam("api") @DefaultValue ("") String strApiVersion,
            @PathParam("corpusName") String corpusName,
            MultivaluedMap<String, String> formParams) {
        return backend.status(getApiVersion(corporaPath, strApiVersion),
                corpusName, formParams, HttpMethod.POST);
    }

    @Path("/autocomplete/{fieldName}")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response getAutocompleteMetadata(
            @PathParam("corpora") String corporaPath,
            @QueryParam("api") @DefaultValue ("") String strApiVersion,
            @PathParam("corpusName") String corpusName,
            @PathParam("fieldName") String fieldName,
            @Context UriInfo uriInfo) {
        return backend.autocompleteMetadata(getApiVersion(corporaPath, strApiVersion),
                corpusName, fieldName, uriInfo.getQueryParameters(), HttpMethod.GET);
    }

    @Path("/autocomplete/{fieldName}")
    @POST
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response postAutocompleteMetadata(
            @PathParam("corpora") String corporaPath,
            @QueryParam("api") @DefaultValue ("") String strApiVersion,
            @PathParam("corpusName") String corpusName,
            @PathParam("fieldName") String fieldName,
            MultivaluedMap<String, String> formParams) {
        return backend.autocompleteMetadata(getApiVersion(corporaPath, strApiVersion),
                corpusName, fieldName, formParams, HttpMethod.POST);
    }

    @Path("/autocomplete/{fieldName}/{annotationName}")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response getAutocompleteAnnotated(
            @PathParam("corpora") String corporaPath,
            @QueryParam("api") @DefaultValue ("") String strApiVersion,
            @PathParam("corpusName") String corpusName,
            @PathParam("fieldName") String fieldName,
            @PathParam("annotationName") String annotationName,
            @Context UriInfo uriInfo) {
        return backend.autocompleteAnnotated(getApiVersion(corporaPath, strApiVersion),
                corpusName, fieldName, annotationName, uriInfo.getQueryParameters(), HttpMethod.GET);
    }

    @Path("/autocomplete/{fieldName}/{annotationName}")
    @POST
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response postAutocompleteAnnotated(
            @PathParam("corpora") String corporaPath,
            @QueryParam("api") @DefaultValue ("") String strApiVersion,
            @PathParam("corpusName") String corpusName,
            @PathParam("fieldName") String fieldName,
            @PathParam("annotationName") String annotationName,
            MultivaluedMap<String, String> formParams) {
        return backend.autocompleteAnnotated(getApiVersion(corporaPath, strApiVersion),
                corpusName, fieldName, annotationName, formParams, HttpMethod.POST);
    }

    @Path("/sharing")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response getSharing() {
        return SimpleResponse.notImplemented("/sharing");
    }

    @Path("/sharing")
    @POST
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response postSharing() {
        return SimpleResponse.notImplemented("/sharing");
    }

    @Path("/{resource:debug|explain}")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response getErrorNotImplemented(
            @PathParam("resource") String resource) {
        return SimpleResponse.notImplemented("/CORPUS/" + resource);
    }

    @Path("/{resource:debug|explain}")
    @POST
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response postErrorNotImplemented(
            @PathParam("resource") String resource) {
        return SimpleResponse.notImplemented("/CORPUS/" + resource);
    }
}
