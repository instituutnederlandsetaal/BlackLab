package nl.inl.blacklab.instrumentation;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

/**
 * RequestInstrumentationProvider provides information used
 * to augment logging messages and other instrumentation information.
 */
public interface RequestInstrumentationProvider {

    /**
     * Returns a request id that will be logged in each blacklab
     * request
     * @return the request id
     */
    Optional<String> getRequestID(HttpServletRequest request);

    /**
     * A RequestIdProvider that does not create a request id
     */
    static RequestInstrumentationProvider noOpProvider() {
        return request -> Optional.empty();
    }
}
