package org.ivdnt.blacklab.proxy.resources;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

@Path("")
public class RootResource {

    /** REST client */
    private final Client client;

    @Inject
    public RootResource(Client client) {
        this.client = client;
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response getServerInfo(@DefaultValue ("") @QueryParam("api") String apiVersion) {
        return ProxyRequest.serverInfo(client, apiVersion, HttpMethod.GET);
    }

    @POST
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response postServerInfo(@DefaultValue ("") @FormParam("api") String apiVersion) {
        return ProxyRequest.serverInfo(client, apiVersion, HttpMethod.POST);
    }

    @Path("/input-formats")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public Response getFormats(
            @Context UriInfo uriInfo,
            @Context HttpHeaders headers) {
        return ProxyRequest.listInputFormats(client, uriInfo.getQueryParameters(), HttpMethod.GET);
    }

    @Path("/input-formats")
    @POST
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public Response postFormats(
            MultivaluedMap<String, String> formParams,
            @Context HttpHeaders headers) {
        return ProxyRequest.listInputFormats(client, formParams, HttpMethod.POST);
    }

    @Path("/input-formats/{formatName}")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public Response getFormat(
            @PathParam("formatName") String formatName,
            @Context UriInfo uriInfo,
            @Context HttpHeaders headers) {
        return ProxyRequest.inputFormat(client, formatName, uriInfo.getQueryParameters(), HttpMethod.GET);
    }

    @Path("/input-formats/{formatName}")
    @POST
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public Response postFormat(
            @PathParam("formatName") String formatName,
            MultivaluedMap<String, String> formParams,
            @Context HttpHeaders headers) {
        return ProxyRequest.inputFormat(client, formatName, formParams, HttpMethod.POST);
    }

    @Path("/input-formats/{formatName}/xslt")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public Response getFormatXslt(
            @PathParam("formatName") String formatName,
            @Context UriInfo uriInfo,
            @Context HttpHeaders headers) {
        return ProxyRequest.inputFormatXslt(client, formatName, uriInfo.getQueryParameters(), HttpMethod.GET);
    }

    @Path("/input-formats/{formatName}/xslt")
    @POST
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public Response postFormatXslt(
            @PathParam("formatName") String formatName,
            MultivaluedMap<String, String> formParams,
            @Context HttpHeaders headers) {
        return ProxyRequest.inputFormatXslt(client, formatName, formParams, HttpMethod.POST);
    }

}
