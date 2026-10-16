package nl.inl.blacklab.server.util;

import nl.inl.blacklab.server.exceptions.InternalServerError;

/** Reusable utilities for implementing BlackLab webservice. */
public class WebserviceUtil {

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
}
