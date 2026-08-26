package nl.inl.blacklab.server;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.jul.Log4jBridgeHandler;
import org.apache.lucene.index.IndexFormatTooNewException;
import org.apache.lucene.index.IndexFormatTooOldException;

import io.micrometer.core.instrument.Metrics;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.IndexVersionMismatch;
import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.instrumentation.impl.PrometheusMetricsProvider;
import nl.inl.blacklab.server.datastream.DataFormat;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.datastream.DataStreamAbstract;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.lib.Response;
import nl.inl.blacklab.server.lib.results.ApiVersion;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.server.requesthandlers.RequestHandler;
import nl.inl.blacklab.server.requesthandlers.UserRequestBls;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.server.util.ServletUtil;
import nl.inl.blacklab.server.util.WebserviceUtil;
import nl.inl.blacklab.webservice.WsParam;

public class BlackLabServer extends HttpServlet {

    private static final Logger logger = LogManager.getLogger(BlackLabServer.class);

    private static final Charset REQUEST_ENCODING = StandardCharsets.UTF_8;

    private static final Charset OUTPUT_ENCODING = StandardCharsets.UTF_8;

    /** Pretty-print the response? */
    public static final String PARAM_PRETTYPRINT = "prettyprint";

    /** Include XML fragments from document escaped as CDATA or not (i.e. as part of the XML structure)? */
    public static final String PARAM_ESCAPE_XML_FRAGMENT = "escapexmlfragment";

    /** If a startup error occurred, save it so we can produce an error response later. */
    private Exception initializationException;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        Log4jBridgeHandler.install(true, null, true);

        File servletPath = new File(config.getServletContext().getRealPath("."));
        logger.debug("Running from dir: " + servletPath);

        try {
            BlsMain.get();
        } catch (Exception e) {
            initializationException = e;
        }
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
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        try {
            super.doOptions(request, response);
        } catch (ServletException|IOException e) {
            DataFormat outputType = ServletUtil.getOutputType(request);
            if (outputType == null)
                outputType = BlsMain.get().getDefaultOutputType();
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
        String allowOrigin = BlsMain.getInstance() == null ? "*" :
                BlsMain.getInstance().getSearchManager().config().getProtocol().getAccessControlAllowOrigin();
        if (allowOrigin != null)
            responseObject.addHeader("Access-Control-Allow-Origin", allowOrigin);
        return allowOrigin;
    }

    private void handleRequest(HttpServletRequest request, HttpServletResponse responseObject) {
        if (!ensureInitialized(request, responseObject))
            return;

        DataFormat outputType = ServletUtil.getOutputType(request);
        try {
            request.setCharacterEncoding(REQUEST_ENCODING.name());
        } catch (UnsupportedEncodingException ex) {
            logger.error(ex);
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
        UserRequestBls userRequest = new UserRequestBls(request);
        int httpCode;
        RequestHandler requestHandler = null;
        int cacheTime = 0;
        ApiVersion api = ApiVersion.CURRENT;
        boolean prettyPrint = ServletUtil.getParameter(request, PARAM_PRETTYPRINT, userRequest.isDebugMode());
        DataStream ds = DataStreamAbstract.create(outputType, prettyPrint, api);
        ds.setOmitEmptyAnnotations(BlsMain.get().getSearchManager().config().getProtocol().isOmitEmptyProperties());
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
            if (requestHandler.getOverrideType() != null && outputType != null && requestHandler.getOverrideType() != outputType) {
                // Requested output type is not supported for this request
                throw new BadRequest("OUTPUT_TYPE_NOT_SUPPORTED", "This request doesn't support requested type " + outputType.getContentType() + ", only " + requestHandler.getOverrideType().getContentType());
            }
            if (outputType == null)
                outputType = BlsMain.get().getDefaultOutputType();

            // For some auth systems, we need to persist the logged-in user, e.g. by setting a cookie
            BlsMain.get().getSearchManager().getAuthSystem().persistUser(userRequest, requestHandler.getUser());

            cacheTime = requestHandler.isCacheAllowed() ? BlsMain.get().getSearchManager().config().getCache().getClientCacheTimeSec() : 0;

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
            String msg = e.getCause() == null ? e.getMessage() : e.getCause().getMessage();
            if (e.getCause() instanceof IndexFormatTooOldException)
                httpCode = Response.error(errorWriter, "INDEX_TOO_OLD", "Index too old for this BlackLab version: " + msg, null, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            else if (e.getCause() instanceof IndexFormatTooNewException)
                httpCode = Response.error(errorWriter, "INDEX_TOO_NEW", "Index was created with a newer BlackLab version: " + msg, null, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            else
                httpCode = Response.error(errorWriter, "INDEX_VERSION_MISMATCH", "Index version mismatch: " + msg, null, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e);
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

    private boolean ensureInitialized(HttpServletRequest request, HttpServletResponse responseObject) {
        if (initializationException != null) {
            boolean prettyPrint = ServletUtil.getParameter(request, PARAM_PRETTYPRINT, true);
            String strApiVersion = ServletUtil.getParameter(request, WsParam.API.value(),
                    ApiVersion.CURRENT.toString());
            ApiVersion apiVersion = ApiVersion.fromValue(strApiVersion);
            DataFormat outputType = ServletUtil.getOutputType(request);
            initializationErrorResponse(responseObject, initializationException, outputType,
                    apiVersion, prettyPrint);
            return false;
        }
        return true;
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
        // Cleans up search manager
        BlsMain.get().cleanup();
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

    public synchronized SearchManager getSearchManager() {
        return BlsMain.get().getSearchManager();
    }
}
