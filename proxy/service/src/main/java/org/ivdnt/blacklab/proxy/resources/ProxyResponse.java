package org.ivdnt.blacklab.proxy.resources;

import jakarta.ws.rs.core.Response;

import org.ivdnt.blacklab.proxy.representation.ErrorResponse;

public class ProxyResponse {
    public static Response error(Response.Status status, String code, String message) {
        return error(status, code, message, null);
    }

    public static Response error(Response.Status status, String code, String message, String stackTrace) {
        ErrorResponse error = new ErrorResponse(status.getStatusCode(), code, message, stackTrace);
        return Response.status(status).entity(error).build();
    }

    static Response notImplemented(String resource) {
        return error(Response.Status.NOT_IMPLEMENTED, "NOT_IMPLEMENTED", "The " + resource + " resource hasn't been implemented on the proxy.");
    }

    public static Response success(Object entity) {
        return Response.ok().entity(entity).build();
    }
}
