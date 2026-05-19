package nl.inl.blacklab.server.lib;

import java.util.Map;

import nl.inl.blacklab.webservice.WebserviceParameter;

/**
 * Get a map of parameters for writing the response.
 */
public interface ParamsForResponse {

    Map<WebserviceParameter, String> getParameters();

    Map<WebserviceParameter, Object> getTypedParameters();
}
