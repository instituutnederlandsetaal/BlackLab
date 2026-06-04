package org.ivdnt.blacklab.proxy.resources;

import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.webservice.WsParam;

public class ProxyParamsUtil {
    public static final String MIME_TYPE_CSV = "text/csv";
    private static final MediaType MEDIA_TYPE_CSV = MediaType.valueOf(MIME_TYPE_CSV);

    public static Map<WsParam, String> get(MultivaluedMap<String, String> parameters, String corpusName,
            WebserviceOperation op) {
        Map<WsParam, String> params = get(parameters, op);
        params.put(WsParam.CORPUS_NAME, corpusName);
        return params;
    }

    public static Map<WsParam, String> get(MultivaluedMap<String,String> parameters, WebserviceOperation op) {
        Map<WsParam, String> params = parameters.entrySet().stream()
                .filter(e -> WsParam.fromValue(e.getKey()).isPresent()) // keep only known parameters
                .map(e -> Map.entry(WsParam.fromValue(e.getKey()).orElse(null),
                        StringUtils.join(e.getValue(), ",")))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        params.put(WsParam.OPERATION, op.value());
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
