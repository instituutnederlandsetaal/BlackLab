package nl.inl.blacklab.server;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.IndexFormatTooOldException;

import com.fasterxml.jackson.core.JacksonException;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.IndexVersionMismatch;
import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.instrumentation.MetricsProvider;
import nl.inl.blacklab.instrumentation.RequestInstrumentationProvider;
import nl.inl.blacklab.instrumentation.impl.PrometheusMetricsProvider;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.server.config.BLSConfig;
import nl.inl.blacklab.server.config.BLSConfigDebug;
import nl.inl.blacklab.server.config.ConfigFileReader;
import nl.inl.blacklab.server.datastream.DataFormat;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.datastream.DataStreamAbstract;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.ConfigurationException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.lib.Response;
import nl.inl.blacklab.server.lib.results.ApiVersion;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.server.requesthandlers.RequestHandler;
import nl.inl.blacklab.server.requesthandlers.UserRequestBls;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.server.util.ServletUtil;
import nl.inl.blacklab.server.util.WebserviceUtil;
import nl.inl.blacklab.webservice.WebserviceParameter;

public class BlackLabServer extends HttpServlet {

    private static final Logger logger = LogManager.getLogger(BlackLabServer.class);

    private static final Charset REQUEST_ENCODING = StandardCharsets.UTF_8;

    private static final Charset OUTPUT_ENCODING = StandardCharsets.UTF_8;

    private static final String CONFIG_FILE_NAME = "blacklab-server";

    /** Pretty-print the response? */
    public static final String PARAM_PRETTYPRINT = "prettyprint";

    /** Include XML fragments from document escaped as CDATA or not (i.e. as part of the XML structure)? */
    public static final String PARAM_ESCAPE_XML_FRAGMENT = "escapexmlfragment";

    /** Manages all our searches */
    private SearchManager searchManager;

    private RequestInstrumentationProvider requestInstrumentationProvider = null;

    /** Default output type to use if none given. */
    private DataFormat defaultOutputType;

    @Override
    public void init() throws ServletException {
        logger.info("Starting BlackLab Server...");
        super.init();
        logger.info("BlackLab Server ready.");
    }

    private synchronized void ensureSearchManagerAvailable() throws BlsException {
        if (searchManager == null) {
            try {
                BLSConfig config = readConfig();

                // Create our search manager (main webservice class)
                searchManager = new SearchManager(config, true);

                // Set defaults from config in ParameterDefaults
                config.getParameters().setParameterDefaults();

                // Configure metrics provider (e.g Prometheus)
                setMetricsProvider(config);
                getInstrumentationProvider(); // ensure creation

                checkExpectedDebugAddresses(config);

                // Determine default output type.
                defaultOutputType = DataFormat.fromString(searchManager.config().getProtocol().getDefaultOutputType(),
                        DataFormat.XML);
            } catch (JacksonException e) {
                throw new ConfigurationException("Invalid JSON in configuration file", e);
            } catch (IOException e) {
                throw new ConfigurationException("Error reading configuration file", e);
            }
        }
    }

    /** Check if localhost addresses are in the list of debug addresses. If not, warn about it.
     * This may help to debug issues in some cases.
     */
    public static void checkExpectedDebugAddresses(BLSConfig config) {
        List<String> addresses = config.getDebug().getAddresses();
        Set<String> missingLocalhosts = new HashSet<>(BLSConfigDebug.DEBUG_ADDRESSES_LOCALHOST);
        for (String address: addresses)
            missingLocalhosts.remove(address);
        if (!missingLocalhosts.isEmpty()) {
            logger.info("NOTE: debug.addresses has been overridden and no longer contains these expected localhost values: " +
                    StringUtils.join(missingLocalhosts, "; "));
        }
    }

    private BLSConfig readConfig() throws IOException, ConfigurationException {
        File servletPath = new File(getServletContext().getRealPath("."));
        logger.debug("Running from dir: " + servletPath);
        List<File> searchDirs = new ArrayList<>();
        searchDirs.add(servletPath.getAbsoluteFile().getParentFile().getCanonicalFile());
        searchDirs.addAll(BlackLab.defaultConfigDirs());
        return ConfigFileReader.getBlsConfig(searchDirs, CONFIG_FILE_NAME);
    }

    private void setMetricsProvider(BLSConfig config) throws ConfigurationException {
        String registryProviderClassName = config.getDebug().getMetricsProvider();
        if (StringUtils.isBlank(registryProviderClassName)) {
            return;
        }

        String fqClassName = registryProviderClassName.startsWith("nl.inl.blacklab.instrumentation")
            ? registryProviderClassName
            : String.format("nl.inl.blacklab.instrumentation.impl.%s", registryProviderClassName);

        try {
            MetricsProvider meterRegistryProvider = (MetricsProvider)
                Class.forName(fqClassName).getDeclaredConstructor().newInstance();
            MeterRegistry registry = meterRegistryProvider.getRegistry();
            Metrics.addRegistry(registry);
        } catch (Exception ex) {
            throw new ConfigurationException("Can not create metrics provider with class" + fqClassName);
        }
    }

    public synchronized RequestInstrumentationProvider getInstrumentationProvider() {
        if (requestInstrumentationProvider == null) {
            BLSConfig config = searchManager.config();
            requestInstrumentationProvider = WebserviceUtil.createInstrumentationProvider(config);
        }
        return requestInstrumentationProvider;
    }

    /**
     * Process POST requests (add data to index)
     *
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse responseObject) {
        handleRequest(request, responseObject);
    }

    /**
     * Process PUT requests (create index)
     *
     */
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse responseObject) {
        handleRequest(request, responseObject);
    }

    /**
     * Process DELETE requests (create a index, add data to one)
     *
     */
    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse responseObject) {
        handleRequest(request, responseObject);
    }

    /**
     * Process GET requests (information retrieval)
     *
     * @param request HTTP request object
     * @param responseObject where to write our response
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse responseObject) {
        handleRequest(request, responseObject);
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try {
            super.doOptions(request, response);
        } catch (ServletException|IOException e) {
            DataFormat outputType = ServletUtil.getOutputType(request);
            if (outputType == null)
                outputType = defaultOutputType;
            ApiVersion api = ApiVersion.CURRENT;
            DataStream es = DataStreamAbstract.create(outputType, true, api);
            es.outputProlog();
            ResponseStreamer errorWriter = ResponseStreamer.get(es, api);
            int httpCode = Response.error(errorWriter, "INTERNAL_ERROR",
                    e.getMessage(), null, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e);
            response.setStatus(httpCode);
            response.setCharacterEncoding(OUTPUT_ENCODING.name().toLowerCase());
            response.setContentType(outputType.getContentType());
            optAddAllowOriginHeader(response);
            try {
                Writer out = new OutputStreamWriter(response.getOutputStream(), OUTPUT_ENCODING);
                out.write(es.getOutput());
                out.flush();
            } catch (IOException ex) {
                logger.error("Error writing response for OPTIONS request", ex);
            }
            return;
        }
        String allowOrigin = optAddAllowOriginHeader(response);
        if (allowOrigin != null) {
            response.addHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers"));
        	response.addHeader("Access-Control-Allow-Methods", "GET, HEAD, POST, PUT, DELETE, TRACE, OPTIONS");
        }
    }

    private String optAddAllowOriginHeader(HttpServletResponse responseObject) {
        String allowOrigin = searchManager == null ? "*" : searchManager.config().getProtocol().getAccessControlAllowOrigin();
        if (allowOrigin != null)
            responseObject.addHeader("Access-Control-Allow-Origin", allowOrigin);
        return allowOrigin;
    }

    private void handleRequest(HttpServletRequest request, HttpServletResponse responseObject) {
        DataFormat outputType = ServletUtil.getOutputType(request);
        try {
            request.setCharacterEncoding(REQUEST_ENCODING.name());
        } catch (UnsupportedEncodingException ex) {
            logger.error(ex);
        }

        try {
            ensureSearchManagerAvailable();
        } catch (BlackLabRuntimeException | BlsException e) {
            boolean prettyPrint = ServletUtil.getParameter(request, PARAM_PRETTYPRINT, true);
            String strApiVersion = ServletUtil.getParameter(request, WebserviceParameter.API_VERSION.value(),
                    ApiVersion.CURRENT.toString());
            ApiVersion apiVersion = ApiVersion.fromValue(strApiVersion);
            initializationErrorResponse(responseObject, e, outputType, apiVersion, prettyPrint);
            return;
        }

        if (PrometheusMetricsProvider.handlePrometheus(Metrics.globalRegistry, request, responseObject, OUTPUT_ENCODING.name())) {
            return;
        }

        // === Create RequestHandler object

        // The outputType handling is a bit iffy:
        // For some urls the dataType is required to determined the correct RequestHandler to instance
        // (the /docs/ and /hits/, because of how CSV is handled)
        // For some other urls, the RequestHandler can only output a single type of data
        // and for the rest of the urls, it doesn't matter, so we should just use the default if no explicit type
        // was requested.
        // As long as we're careful not to have urls in multiple of these categories there is never any ambiguity
        // about which handler to use
        // Note that only some requests support CSV output (hits/docs); requesting it should return an error on
        // requests that don't support it.
        UserRequestBls userRequest = new UserRequestBls(this, request, responseObject);
        int httpCode;
        RequestHandler requestHandler = null;
        int cacheTime = 0;
        ApiVersion api = ApiVersion.CURRENT;
        boolean prettyPrint = ServletUtil.getParameter(request, PARAM_PRETTYPRINT, userRequest.isDebugMode());
        DataStream ds = DataStreamAbstract.create(outputType, prettyPrint, api);
        ds.setOmitEmptyAnnotations(searchManager.config().getProtocol().isOmitEmptyProperties());
        if (request.getParameterMap().containsKey(PARAM_ESCAPE_XML_FRAGMENT)) {
            // We want to override whether XML fragments are output as CDATA or not
            // (defaults to true for v5, false before)
            boolean escapeXmlFragment = ServletUtil.getParameter(request, PARAM_ESCAPE_XML_FRAGMENT, true);
            ds.setEscapeXmlFragment(escapeXmlFragment);
        }
        DataStream es = DataStreamAbstract.create(outputType, prettyPrint, api);
        es.outputProlog();
        ResponseStreamer errorWriter = ResponseStreamer.get(es, api);
        int errorBufLengthBefore = es.length();
        try {
            requestHandler = RequestHandler.create(userRequest, outputType);
            if (outputType == null)
                outputType = requestHandler.getOverrideType();
            if (outputType == null)
                outputType = defaultOutputType;

            // For some auth systems, we need to persist the logged-in user, e.g. by setting a cookie
            searchManager.getAuthSystem().persistUser(userRequest, requestHandler.getUser());

            cacheTime = requestHandler.isCacheAllowed() ? searchManager.config().getCache().getClientCacheTimeSec() : 0;

            String rootEl = requestHandler.omitBlackLabResponseRootElement() ? null : ResponseStreamer.BLACKLAB_RESPONSE_ROOT_ELEMENT;
            ds.startDocument(rootEl);

            // === Handle the request
            if (!api.equals(requestHandler.apiCompatibility())) {
                api = requestHandler.apiCompatibility();
                ds.setVersion(api);
                es.setVersion(api);
            }
            ResponseStreamer dstream = ResponseStreamer.get(ds, api);
            httpCode = requestHandler.handle(dstream);
        } catch (IndexVersionMismatch e) {
            if (e.getCause() instanceof IndexFormatTooOldException)
                httpCode = Response.error(errorWriter, "INDEX_TOO_OLD", "Index too old for this BlackLab version: " + e.getCause().getMessage(), null, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            else
                httpCode = Response.error(errorWriter, "INDEX_TOO_NEW", "Index was created with a newer BlackLab version: " + e.getCause().getMessage(), null, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (ErrorOpeningIndex e) {
            httpCode = Response.internalError(errorWriter, e, userRequest.isDebugMode(), "ERROR_OPENING_INDEX");
        } catch (InvalidQuery e) {
            httpCode = Response.error(errorWriter, "INVALID_QUERY", e.getMessage(), null, HttpServletResponse.SC_BAD_REQUEST);
        } catch (InternalServerError e) {
            String msg = WebserviceUtil.internalErrorMessage(e, userRequest.isDebugMode(), e.getInternalErrorCode());
            httpCode = Response.error(errorWriter, e.getBlsErrorCode(), msg, e.getInfo(), e.getHttpStatusCode(), e);
        } catch (BlsException e) {
            httpCode = Response.error(errorWriter, e.getBlsErrorCode(), e.getMessage(), e.getInfo(), e.getHttpStatusCode());
        } catch (InterruptedSearch e) {
            httpCode = Response.error(errorWriter, "INTERRUPTED", e.getMessage(), null, HttpServletResponse.SC_SERVICE_UNAVAILABLE, e);
        } catch (RuntimeException e) {
            if (errorWriter != null)
                httpCode = Response.internalError(errorWriter, e, userRequest.isDebugMode(), "INTERR_HANDLING_REQUEST");
            else
                throw e;
        } finally {
            if (requestHandler != null)
                requestHandler.cleanup(); // close logger
        }
        ds.endDocument();

        // === Write the response headers

        // Write HTTP headers (status code, encoding, content type and cache)
        responseObject.setStatus(httpCode);
        responseObject.setCharacterEncoding(OUTPUT_ENCODING.name().toLowerCase());
        responseObject.setContentType(outputType.getContentType());
        optAddAllowOriginHeader(responseObject);
        ServletUtil.writeCacheHeaders(responseObject, cacheTime);

        // === Write the response that was captured in buf
        try {
            Writer realOut = new OutputStreamWriter(responseObject.getOutputStream(), OUTPUT_ENCODING);
            if (es.length() > errorBufLengthBefore) {
                // an error occurred
                realOut.write(es.getOutput());
            } else {
                realOut.write(ds.getOutput());
            }
            realOut.flush();
        } catch (IOException e) {
            // Client cancelled the request midway through.
            // This is okay, don't raise the alarm.
            logger.debug("(couldn't send response, client probably cancelled the request)");
        }
    }

    private void initializationErrorResponse(HttpServletResponse responseObject, Exception e, DataFormat outputType,
            ApiVersion api, boolean prettyPrint) {
        if (outputType == null)
            outputType = DataFormat.XML;
        // Write HTTP headers (status code, encoding, content type and cache)
        responseObject.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        responseObject.setCharacterEncoding(OUTPUT_ENCODING.name().toLowerCase());
        responseObject.setContentType(outputType.getContentType());
        optAddAllowOriginHeader(responseObject);
        ServletUtil.writeCacheHeaders(responseObject, 0);

        // === Write the response that was captured in buf
        try {
            DataStream es = DataStreamAbstract.create(outputType, prettyPrint, api);
            es.outputProlog();
            es.error("INTERNAL_ERROR", e.getMessage(), null, e);
            Writer realOut = new OutputStreamWriter(responseObject.getOutputStream(), OUTPUT_ENCODING);
            realOut.write(es.getOutput());
            realOut.flush();
        } catch (IOException e2) {
            // Client cancelled the request midway through.
            // This is okay, don't raise the alarm.
            logger.debug("(couldn't send response, client probably cancelled the request)");
        }
    }

    @Override
    public void destroy() {

        // Stops the load management thread
        if (searchManager != null)
            searchManager.cleanup();

        super.destroy();
    }

    /**
     * Provides a short description of this servlet.
     *
     * @return the description
     */
    @Override
    public String getServletInfo() {
        return "Provides corpus search services on one or more BlackLab indices.\n"
                + "Source available at https://github.com/instituutnederlandsetaal/BlackLab\n"
                + "(C) 2013-" + Calendar.getInstance().get(Calendar.YEAR)
                + " Dutch Language Institute (https://ivdnt.org/)\n"
                + "Licensed under the Apache License v2.\n";
    }

    public SearchManager getSearchManager() {
        return searchManager;
    }
}
