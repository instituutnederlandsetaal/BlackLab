package org.ivdnt.blacklab.proxy.logic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.ivdnt.blacklab.proxy.ProxyConfig;
import org.ivdnt.blacklab.proxy.helper.ErrorReadingResponse;
import org.ivdnt.blacklab.proxy.representation.EntityWithSummary;
import org.ivdnt.blacklab.proxy.representation.ErrorResponse;
import org.ivdnt.blacklab.proxy.representation.JsonCsvResponse;
import org.ivdnt.blacklab.proxy.representation.SolrGeneralErrorResponse;
import org.ivdnt.blacklab.proxy.representation.SolrResponse;
import org.ivdnt.blacklab.proxy.resources.ParamsUtil;
import org.ivdnt.blacklab.proxy.resources.ProxyResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.webservice.WebserviceParameter;
import nl.inl.util.Json;

/** Performs requests to the BLS nodes we're proxying */
public class Requests {

    private static final String BL_PAR_NAME_PREFIX = "bl" + ".";

    private static final int MAX_GROUPS_TO_GET = Integer.MAX_VALUE - 10;

    /** Is the given value the default value for this parameter?
     *
     * Used to omit some default values for more readable URLs.
     */
    private static boolean isParamDefault(String key, String value) {
        if ("usecache".equals(key)) {
            return value.equals("true") || value.equals("yes");
        }
        return false;
    }

    public static <T> T get(Client client, Map<WebserviceParameter, String> queryParams, Class<T> entityType) {
        return (T)request(client, queryParams, "GET", List.of(entityType));
    }

    public static Object get(Client client, Map<WebserviceParameter, String> queryParams, List<Class<?>> entityTypes) {
        return request(client, queryParams, "GET", entityTypes);
    }

    public static <T> T request(Client client, Map<WebserviceParameter, String> queryParams, String method,
            Class<T> entityType) {
        return (T)request(client, queryParams, method, List.of(entityType));
    }

    public static Object request(Client client, Map<WebserviceParameter, String> queryParams, String method, List<Class<?>> entityTypes) {
        ProxyConfig.ProxyTarget proxyTarget = ProxyConfig.get().getProxyTarget();
        String url = proxyTarget.getUrl();
        WebTarget target = client.target(url);
        boolean isSolr = proxyTarget.getProtocol().equalsIgnoreCase("solr");
        if (isSolr && !queryParams.containsKey(WebserviceParameter.CORPUS_NAME)) {
            // Solr always needs a corpus name even for "server-wide" requests.
            if (proxyTarget.getDefaultCorpusName().isEmpty())
                throw new IllegalStateException("No corpus name. Please specify proxyTarget.defaultCorpusName in proxy config file");
            queryParams = new HashMap<>(queryParams);
            queryParams.put(WebserviceParameter.CORPUS_NAME, proxyTarget.getDefaultCorpusName());
        }
        return isSolr ?
                requestSolr(target, queryParams, method, entityTypes) :
                requestBls(target, queryParams, method, entityTypes);
    }

    private static Object requestBls(WebTarget target, Map<WebserviceParameter, String> queryParams, String method, List<Class<?>> entityTypes) {
        if (queryParams != null) {
            String corpusName = queryParams.get(WebserviceParameter.CORPUS_NAME);
            if (corpusName != null)
                target = target.path(corpusName);
            String operation = queryParams.get(WebserviceParameter.OPERATION);
            if (operation != null) {
                WebserviceOperation op = WebserviceOperation.fromValue(operation).orElseThrow();
                target = target.path(op.path());
            }
            for (Map.Entry<WebserviceParameter, String> e: queryParams.entrySet()) {
                WebserviceParameter key = e.getKey();
                if (key != WebserviceParameter.CORPUS_NAME && key != WebserviceParameter.OPERATION) {
                    target = target.queryParam(e.getKey().value(), escapeBraces(e.getValue()));
                }
            }
        }
        if (entityTypes.size() == 1) {
            // Just one option for the response type. Use that.
            // (the loop below correctly reduces to this in the case of size() == 1, but we've kept this
            //  'special case' for clarity)
            return target.request(MediaType.APPLICATION_JSON_TYPE).method(method).readEntity(entityTypes.get(0));
        } else {
            // Try mapping response to each of the supplied options.
            // (mainly used for /fields/NAME, where the proxy doesn't know in advance if the field is an annotated
            //  or metadata field; we could of course ask once and keep track of this, but we'd rather avoid that
            //  complexity)
            for (int i = 0; i < entityTypes.size(); i++) {
                Class<?> entityType = entityTypes.get(i);
                try {
                    return target.request(MediaType.APPLICATION_JSON_TYPE).method(method).readEntity(entityType);
                } catch (ProcessingException e) {
                    if (i != entityTypes.size() - 1) {
                        // Couldn't map to this class. Try the next one.
                    } else {
                        // Couldn't map to any of the supplied classes. Throw the final exception.
                        String classes = entityTypes.stream().map(c -> c.getName()).collect(Collectors.joining(" / "));
                        throw new ErrorReadingResponse("Couldn't interpret the response as the given entity class(es): " + classes, e);
                    }
                }
            }
            throw new IllegalStateException("Code should never get here");
        }
    }

    /** Escape { and } in parameter values or they will be interpreted by Jersey as template slots
     * (no, this shouldn't be necessary, but it is)
     */
    private static String escapeBraces(String value) {
        return value.replaceAll("\\{", "%7B").replaceAll("\\}", "%7D");
    }

    private static Object requestSolr(WebTarget target, Map<WebserviceParameter, String> queryParams, String method, List<Class<?>> entityTypes) {
        if (queryParams != null) {
            String corpusName = queryParams.get(WebserviceParameter.CORPUS_NAME);
            if (corpusName != null)
                target = target.path(corpusName);
            target = target.path("select");
            for (Map.Entry<WebserviceParameter, String> e: queryParams.entrySet()) {
                WebserviceParameter key = e.getKey();
                if (key != WebserviceParameter.CORPUS_NAME) {
                    target = target.queryParam(BL_PAR_NAME_PREFIX + key, escapeBraces(e.getValue()));
                }
            }
        }
        Response response = target.request(MediaType.APPLICATION_JSON_TYPE).method(method);
        int status = response.getStatus();
        response.bufferEntity(); // so we can call readEntity() again if first call fails
        SolrResponse solrResponse = null;
        try {
            solrResponse = response.readEntity(SolrResponse.class);
        } catch (Exception e) {
            // Not a regular response; try to read error entity
            SolrGeneralErrorResponse err = response.readEntity(SolrGeneralErrorResponse.class);
            throw new BlsRequestException(Response.Status.fromStatusCode(status),
                    new ErrorResponse(500, "INTERNAL_ERROR", "(" + err.getServlet() + ") " + err.getStatus() + " " + err.getMessage() + ": " + err.getUrl(), ""));
        }

        JsonNode blacklab = solrResponse.getBlacklab();
        ObjectMapper objectMapper = Json.getJsonObjectMapper();
        for (int i = 0; i < entityTypes.size(); i++) {
            Class<?> entityType = entityTypes.get(i);
            try {
                return objectMapper.treeToValue(blacklab, entityType);
            } catch (JsonProcessingException e) {
                if (i < entityTypes.size() - 1) {
                    // Couldn't map to this class. Try the next one.
                } else {
                    // Couldn't map to any of the supplied classes. See if it's a BLS error.
                    try {
                        e.printStackTrace();
                        ErrorResponse err = objectMapper.treeToValue(blacklab, ErrorResponse.class);
                        // Yes. Throw it so it will be handled by the GenericExceptionMapper.
                        throw new BlsRequestException(Response.Status.fromStatusCode(err.getError().getHttpStatusCode()), err);
                    } catch (JsonProcessingException e2) {
                        // Error didn't work either. Fail.
                        String classes = entityTypes.stream().map(c -> c.getName()).collect(Collectors.joining(" / "));
                        throw new ErrorReadingResponse(
                                "Couldn't interpret the response as the given entity class(es): " + classes, e);
                    }
                }
            }
        }
        throw new IllegalStateException("Code should never get here");
    }

    /**
     * Process a request that could return a CSV response.
     *
     * @param corpusName  corpus we're querying
     * @param parameters  parameters to the request
     * @param op          operation to perform
     * @param resultTypes what types the result entity could be
     * @param isXml
     * @return response
     */
    public static Response requestWithPossibleCsvResponse(Client client, String method, String corpusName,
            MultivaluedMap<String, String> parameters, WebserviceOperation op, List<Class<?>> resultTypes,
            boolean isXml) {
        Object entity = request(client, ParamsUtil.get(parameters, corpusName, op), method, resultTypes);
        if (isXml && entity instanceof EntityWithSummary) {
            // Don't try to serialize the pattern to XML, this induces headaches.
            ((EntityWithSummary) entity).getSummary().pattern = null;
        }
        if (entity instanceof JsonCsvResponse) {
            // Return actual CSV contents instead of JSON
            String csv = ((JsonCsvResponse) entity).csv;
            return Response.ok().type(ParamsUtil.MIME_TYPE_CSV).entity(csv).build();
        } else {
            return ProxyResponse.success(entity);
        }
    }

    /** How to create the BLS request to a node */
    public interface NodeRequestFactory {
        WebTarget get(String nodeUrl);
    }

    /** Thrown when BLS returns an error response */
    public static class BlsRequestException extends RuntimeException {

        private final Response.Status status;

        private final ErrorResponse response;

        public BlsRequestException(Response.Status status, ErrorResponse response) {
            super(response.getMessage());
            this.status = status;
            this.response = response;
        }

        public Response.Status getStatus() {
            return status;
        }

        public ErrorResponse getResponse() {
            return response;
        }
    }

}
