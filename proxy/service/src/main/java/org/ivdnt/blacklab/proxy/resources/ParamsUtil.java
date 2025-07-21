package org.ivdnt.blacklab.proxy.resources;

import java.util.Map;
import java.util.stream.Collectors;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.webservice.WebserviceParameter;

public class ParamsUtil {
    public static final String MIME_TYPE_CSV = "text/csv";
    private static final MediaType MEDIA_TYPE_CSV = MediaType.valueOf(MIME_TYPE_CSV);

    public static Map<WebserviceParameter, String> get(MultivaluedMap<String, String> parameters, String corpusName,
            WebserviceOperation op) {
        Map<WebserviceParameter, String> params = get(parameters, op);
        params.put(WebserviceParameter.CORPUS_NAME, corpusName);
        return params;
    }

    public static Map<WebserviceParameter, String> get(MultivaluedMap<String,String> parameters, WebserviceOperation op) {
        Map<WebserviceParameter, String> params = parameters.entrySet().stream()
                .filter(e -> WebserviceParameter.fromValue(e.getKey()).isPresent()) // keep only known parameters
                .map(e -> Map.entry(WebserviceParameter.fromValue(e.getKey()).orElse(null),
                        StringUtils.join(e.getValue(), ",")))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        params.put(WebserviceParameter.OPERATION, op.value());
        return params;
    }

    /**
     * Does this request accept a CSV response?
     *
     * @param headers HTTP headers
     * @return true if CSV is accepted
     */
    public static boolean isCsvRequest(HttpHeaders headers) {
        return headers.getAcceptableMediaTypes().stream().anyMatch(m -> m.equals(MEDIA_TYPE_CSV));
    }
}
