package nl.inl.blacklab.server.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.instrumentation.RequestInstrumentationProvider;
import nl.inl.blacklab.server.config.BLSConfig;
import nl.inl.blacklab.server.exceptions.ConfigurationException;
import nl.inl.blacklab.server.exceptions.InternalServerError;

/** Reusable utilities for implementing BlackLab webservice. */
public class WebserviceUtil {
    private static final Logger logger = LogManager.getLogger(WebserviceUtil.class);

    private WebserviceUtil() {
    }

    public static String internalErrorMessage(String code) {
        return "An internal error occurred. Please contact the administrator. Error code: " + code + ".";
    }

    public static String internalErrorMessage(Exception e, boolean debugMode, String code) {
        if (debugMode) {
            if (e instanceof InternalServerError)
                return internalErrorMessage(e.getMessage(), true, code);
            return internalErrorMessage(e.getClass().getName() + ": " + e.getMessage(), true, code);
        }
        return internalErrorMessage(code);
    }

    public static String internalErrorMessage(String message, boolean debugMode, String code) {
        if (debugMode) {
            return message + " (Internal error code " + code + ")";
        }
        return internalErrorMessage(code);
    }

    public static String shortenIpv6(String longAddress) {
        return longAddress.replaceFirst("(^|:)(0+(:|$)){2,8}", "::").replaceAll("(:|^)0+([0-9A-Fa-f])", "$1$2");
    }

    public static RequestInstrumentationProvider createInstrumentationProvider(BLSConfig config) {
        String provider = config.getDebug().getRequestInstrumentationProvider();
        if (StringUtils.isBlank(provider)) {
            return RequestInstrumentationProvider.noOpProvider();
        }

        String fqClassName = provider.startsWith("nl.inl.blacklab.instrumentation")
            ? provider
            : String.format("nl.inl.blacklab.instrumentation.impl.%s", provider);

        try {
            return (RequestInstrumentationProvider)
                    Class.forName(fqClassName).getDeclaredConstructor().newInstance();

        } catch (Exception ex) {
            throw new ConfigurationException("Can not create request instrumentation provider with class" + fqClassName);
        }
    }
}
