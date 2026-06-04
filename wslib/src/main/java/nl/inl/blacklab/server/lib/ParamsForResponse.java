package nl.inl.blacklab.server.lib;

import java.util.Map;

import nl.inl.blacklab.webservice.WsParam;

/**
 * Get a map of parameters for writing the response.
 */
public interface ParamsForResponse {

    Map<WsParam, String> getParameters();

    Map<WsParam, Object> getTypedParameters();
}
