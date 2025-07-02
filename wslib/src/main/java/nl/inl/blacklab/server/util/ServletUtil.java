package nl.inl.blacklab.server.util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.server.datastream.DataFormat;

/**
 * Servlet-specific utilities.
 */
public class ServletUtil {
    private static final Logger logger = LogManager.getLogger(ServletUtil.class);

    /**
     * Returns the value of a servlet parameter, or the default value
     * 
     * @param request the request object
     *
     * @param name name of the parameter
     * @param defaultValue default value
     * @return value of the paramater
     */
    public static boolean getParameter(HttpServletRequest request, String name, boolean defaultValue) {
        String defStr = defaultValue ? "true" : "false";
        String value = getParameterSafe(request, name, defStr);
        if (value.toLowerCase().matches("true|yes|1"))
            return true;
        if (value.toLowerCase().matches("false|no|0"))
            return false;

        logger.warn("Illegal value '" + value + "' given for boolean parameter '" + name
                + "'. Using default (" + defStr + ")");
        return defaultValue;
    }

    /**
     * Returns the value of a servlet parameter, or the default value
     * 
     * @param request the request object
     * @param name name of the parameter
     * @param defaultValue default value
     * @return value of the paramater
     */
    public static String getParameter(HttpServletRequest request, String name, String defaultValue) {
        String value = request.getParameter(name);
        if (value == null || value.isEmpty())
            value = defaultValue; // default action
        return value;
    }

    /**
     * Get a parameter value, but replace line endings with underscores.
     *
     * This is to make it safe for logging. Only use this method if the parameter
     * does not need to contain line endings, which should be most of them.
     *
     * @param request the request object
     * @param name name of the parameter
     * @param defaultValue default value
     * @return value of the paramater
     */
    public static String getParameterSafe(HttpServletRequest request, String name, String defaultValue) {
        String value = getParameter(request, name, defaultValue);
        if (value != null)
            value = value.replaceAll("[\n\r]", "_");
        return value;
    }

    /**
     * Returns the type of content the user would like as output (HTML, CSV, ...)
     * This is based on the "outputformat" parameter. If "outputparameter" has an
     * unknown value or is missing, null is returned.
     *
     * @param request the request object
     * @return the type of content the user would like, or null if unknown
     */
    public static DataFormat getOutputType(HttpServletRequest request) {
        // See if there was an explicit outputformat parameter. If so, use that.
        String outputTypeString = getParameter(request, "outputformat", "").toLowerCase();
        if (outputTypeString.length() > 0) {
            return DataFormat.fromString(outputTypeString, null);
        }

        // No explicit parameter. Check if the Accept header contains either json or xml
        String accept = request.getHeader("Accept");
        //logger.debug("Accept: " + accept);
        if (accept != null && accept.length() > 0) {
            if (accept.contains("json"))
                return DataFormat.JSON;
            if (accept.contains("xml"))
                return DataFormat.XML;
            if (accept.contains("javascript"))
                return DataFormat.JSON;
            if (accept.contains("csv"))
                return DataFormat.CSV;
        }

        return null;
    }

    /** The HTTP date format, to use for the cache header */
    static final DateFormat httpDateFormat;

    // Initialize the HTTP date format
    static {
        httpDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        httpDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    /**
     * Write cache headers for the configured cache time.
     * 
     * @param response the response object to write the headers to
     * @param cacheTimeSeconds how long to cache the response
     */
    public static void writeCacheHeaders(HttpServletResponse response, int cacheTimeSeconds) {
        if (cacheTimeSeconds > 0) {
            // Cache page for specified time
            GregorianCalendar cal = new GregorianCalendar();
            cal.add(Calendar.SECOND, cacheTimeSeconds);
            String expires;
            synchronized (httpDateFormat) {
                expires = httpDateFormat.format(cal.getTime());
            }
            response.setHeader("Expires", expires);
            response.setHeader("Cache-Control", "PUBLIC, max-age=" + cacheTimeSeconds);
        } else {
            // Don't cache this page
            response.setHeader("Expires", "0");
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setHeader("Pragma", "no-cache");
        }
    }

    /**
     * Returns the path info and query string (if any) of the request URL
     *
     * @param request the servlet request
     *
     * @return the path and query string (if any)
     */
    public static String getPathAndQueryString(HttpServletRequest request) {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null)
            pathInfo = "";
        String queryString = request.getQueryString();
        if (queryString == null)
            queryString = "";
        else
            queryString = "?" + queryString;
        return request.getServletPath() + pathInfo + queryString;
    }

    /**
     * Get the originating address.
     *
     * This is either the "normal" remote address or, in the case of
     * a reverse proxy setup, the value of the X-Forwarded-For header.
     *
     * @param request request
     * @return originating address
     */
    public static String getOriginatingAddress(HttpServletRequest request) {
        String remoteAddr = request.getHeader("X-Forwarded-For");
        if (StringUtils.isEmpty(remoteAddr))
            remoteAddr = request.getRemoteAddr();
        return remoteAddr;
    }
}
