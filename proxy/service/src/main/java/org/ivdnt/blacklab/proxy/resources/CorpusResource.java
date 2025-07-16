package org.ivdnt.blacklab.proxy.resources;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

import org.ivdnt.blacklab.proxy.logic.Requests;
import org.ivdnt.blacklab.proxy.representation.Corpus;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.webservice.WebserviceParameter;

@Path("/{corpusName : (?!input-formats\\b)[^/]+}")
public class CorpusResource {

    /** REST client */
    private final Client client;

    @Inject
    public CorpusResource(Client client) {
        this.client = client;
    }

    @POST
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response corpusInfo(@PathParam("corpusName") String corpusName) {

        if (corpusName.equals("cache-clear")) {// POST naar /cache-clear : clear cache (not implemented)
            return ProxyResponse.notImplemented("/cache-clear");
        }

        // POST naar /CORPUSNAME ; not supported
        return ProxyResponse.notImplemented("POST to /CORPUSNAME");
    }

    /**
     * Get information about a corpus.
     *
     * @param corpusName corpus name
     * @return corpus information
     */
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response corpusInfo(@PathParam("corpusName") String corpusName, @Context UriInfo uriInfo) {
        return switch (corpusName) {
            case "cache-info" -> ProxyResponse.notImplemented("/cache-info");
            case "help" -> ProxyResponse.notImplemented("/help");
            case "cache-clear" -> ProxyResponse.error(Response.Status.BAD_REQUEST, "WRONG_METHOD",
                    "/cache-clear works only with POST");
            default -> {
                // Actually a corpus name (we assume), so get the corpus info.
                Map<WebserviceParameter, String> params = ParamsUtil.get(uriInfo.getQueryParameters(), corpusName,
                        WebserviceOperation.CORPUS_INFO);
                yield ProxyResponse.success(Requests.get(client, params, Corpus.class));
            }
        };
    }

    @Path("/parse-pattern")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getParsePattern(
            @PathParam("corpusName") String corpusName,
            @Context UriInfo uriInfo) {
        Response hits = ProxyRequest.parsePattern(client, corpusName, uriInfo.getQueryParameters(), HttpMethod.GET);
        return hits;
    }

    @Path("/parse-pattern")
    @POST
    @Consumes("application/x-www-form-urlencoded")
    @Produces(MediaType.APPLICATION_JSON)
    public Response postParsePattern(
            @PathParam("corpusName") String corpusName,
            MultivaluedMap<String, String> formParams) {
        return ProxyRequest.parsePattern(client, corpusName, formParams, HttpMethod.POST);
    }

    @Path("/relations")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRelations(
            @PathParam("corpusName") String corpusName,
            @Context UriInfo uriInfo) {
        Response hits = ProxyRequest.relations(client, corpusName, uriInfo.getQueryParameters(), HttpMethod.GET);
        return hits;
    }

    @Path("/relations")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response postRelations(
            @PathParam("corpusName") String corpusName,
            @Context UriInfo uriInfo) {
        Response hits = ProxyRequest.relations(client, corpusName, uriInfo.getQueryParameters(), HttpMethod.GET);
        return hits;
    }

    @Path("/hits")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, ParamsUtil.MIME_TYPE_CSV })
    public Response getHits(
            @PathParam("corpusName") String corpusName,
            @Context UriInfo uriInfo,
            @Context HttpHeaders headers) {
        Response hits = ProxyRequest.hits(client, corpusName, uriInfo.getQueryParameters(), headers, HttpMethod.GET);
        return hits;
    }

    @Path("/hits")
    @POST
    @Consumes("application/x-www-form-urlencoded")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, ParamsUtil.MIME_TYPE_CSV })
    public Response postHits(
            @PathParam("corpusName") String corpusName,
            MultivaluedMap<String, String> formParams,
            @Context HttpHeaders headers) {
        return ProxyRequest.hits(client, corpusName, formParams, headers, HttpMethod.POST);
    }

    @Path("/docs")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, ParamsUtil.MIME_TYPE_CSV })
    public Response getDocs(
            @PathParam("corpusName") String corpusName,
            @Context UriInfo uriInfo,
            @Context HttpHeaders headers) {
        return ProxyRequest.docs(client, corpusName, uriInfo.getQueryParameters(), headers, HttpMethod.GET);
    }

    @Path("/docs")
    @POST
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, ParamsUtil.MIME_TYPE_CSV })
    public Response postDocs(
            @PathParam("corpusName") String corpusName,
            MultivaluedMap<String, String> formParams,
            @Context HttpHeaders headers) {
        return ProxyRequest.docs(client, corpusName, formParams, headers, HttpMethod.POST);
    }

    @Path("/docs/{pid}")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response getDocInfo(
            @PathParam("corpusName") String corpusName,
            @PathParam("pid") String docPid,
            @Context UriInfo uriInfo) {
        return ProxyRequest.docInfo(client, corpusName, docPid, uriInfo.getQueryParameters(), HttpMethod.GET);
    }

    @Path("/docs/{pid}")
    @POST
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response postDocInfo(
            @PathParam("corpusName") String corpusName,
            @PathParam("pid") String docPid,
            MultivaluedMap<String, String> formParams) {
        return ProxyRequest.docInfo(client, corpusName, docPid, formParams, HttpMethod.POST);
    }

    @Path("/docs/{pid}/contents")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response getDocContents(
            @PathParam("corpusName") String corpusName,
            @PathParam("pid") String docPid,
            @Context UriInfo uriInfo) {
        return ProxyRequest.docContents(client, corpusName, docPid, uriInfo.getQueryParameters(), HttpMethod.GET);
    }

    @Path("/docs/{pid}/contents")
    @POST
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response getDocContents(
            @PathParam("corpusName") String corpusName,
            @PathParam("pid") String docPid,
            MultivaluedMap<String, String> formParams) {
        return ProxyRequest.docContents(client, corpusName, docPid, formParams, HttpMethod.POST);
    }

    @Path("/docs/{pid}/snippet")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response getDocSnippet(
            @PathParam("corpusName") String corpusName,
            @PathParam("pid") String docPid,
            @Context UriInfo uriInfo) {
        return ProxyRequest.docSnippet(client, corpusName, docPid, uriInfo.getQueryParameters(), HttpMethod.GET);
    }

    @Path("/docs/{pid}/snippet")
    @POST
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response postDocSnippet(
            @PathParam("corpusName") String corpusName,
            @PathParam("pid") String docPid,
            MultivaluedMap<String, String> formParams) {
        return ProxyRequest.docSnippet(client, corpusName, docPid, formParams, HttpMethod.POST);
    }

    @Path("/termfreq")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response getTermFreq(@PathParam("corpusName") String corpusName, @Context UriInfo uriInfo) {
        return ProxyRequest.termFreq(client, corpusName, uriInfo.getQueryParameters(), HttpMethod.GET);
    }

    @Path("/termfreq")
    @POST
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response postTermFreq(@PathParam("corpusName") String corpusName, MultivaluedMap<String, String> formParams) {
        return ProxyRequest.termFreq(client, corpusName, formParams, HttpMethod.POST);
    }

    @Path("/fields/{fieldName}")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response getField(
            @PathParam("corpusName") String corpusName,
            @PathParam("fieldName") String fieldName,
            @Context UriInfo uriInfo) {
        try {
            return ProxyRequest.field(client, corpusName, fieldName, uriInfo.getQueryParameters(), HttpMethod.GET);
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
    @POST
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response postField(
            @PathParam("corpusName") String corpusName,
            @PathParam("fieldName") String fieldName,
            MultivaluedMap<String, String> formParams) {
        return ProxyRequest.field(client, corpusName, fieldName, formParams, HttpMethod.POST);
    }

    @Path("/status")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response getStatus(
            @PathParam("corpusName") String corpusName,
            @Context UriInfo uriInfo) {
        return ProxyRequest.status(client, corpusName, uriInfo.getQueryParameters(), HttpMethod.GET);
    }

    @Path("/status")
    @POST
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response postStatus(
            @PathParam("corpusName") String corpusName,
            MultivaluedMap<String, String> formParams) {
        return ProxyRequest.status(client, corpusName, formParams, HttpMethod.POST);
    }

    @Path("/autocomplete/{fieldName}")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response getAutocompleteMetadata(
            @PathParam("corpusName") String corpusName,
            @PathParam("fieldName") String fieldName,
            @Context UriInfo uriInfo) {
        return ProxyRequest.autocompleteMetadata(client, corpusName, fieldName, uriInfo.getQueryParameters(), HttpMethod.GET);
    }

    @Path("/autocomplete/{fieldName}")
    @POST
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response postAutocompleteMetadata(
            @PathParam("corpusName") String corpusName,
            @PathParam("fieldName") String fieldName,
            MultivaluedMap<String, String> formParams) {
        return ProxyRequest.autocompleteMetadata(client, corpusName, fieldName, formParams, HttpMethod.POST);
    }

    @Path("/autocomplete/{fieldName}/{annotationName}")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response getAutocompleteAnnotated(
            @PathParam("corpusName") String corpusName,
            @PathParam("fieldName") String fieldName,
            @PathParam("annotationName") String annotationName,
            @Context UriInfo uriInfo) {
        return ProxyRequest.autocompleteAnnotated(client, corpusName, fieldName, annotationName, uriInfo.getQueryParameters(),
                HttpMethod.GET);
    }

    @Path("/autocomplete/{fieldName}/{annotationName}")
    @POST
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response postAutocompleteAnnotated(
            @PathParam("corpusName") String corpusName,
            @PathParam("fieldName") String fieldName,
            @PathParam("annotationName") String annotationName,
            MultivaluedMap<String, String> formParams) {
        return ProxyRequest.autocompleteAnnotated(client, corpusName, fieldName, annotationName, formParams, HttpMethod.POST);
    }

    @Path("/sharing")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response getSharing() {
        return ProxyResponse.notImplemented("/sharing");
    }

    @Path("/sharing")
    @POST
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response postSharing() {
        return ProxyResponse.notImplemented("/sharing");
    }

    @Path("/{resource:debug|explain}")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response getErrorNotImplemented(@PathParam("resource") String resource) {
        return ProxyResponse.notImplemented("/CORPUS/" + resource);
    }

    @Path("/{resource:debug|explain}")
    @POST
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response postErrorNotImplemented(@PathParam("resource") String resource) {
        return ProxyResponse.notImplemented("/CORPUS/" + resource);
    }
}
