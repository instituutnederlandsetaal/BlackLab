package nl.inl.blacklab.server.requesthandlers;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.ThreadContext;

import com.fasterxml.jackson.core.JsonProcessingException;

import nl.inl.blacklab.exceptions.InvalidConfiguration;
import nl.inl.blacklab.instrumentation.RequestInstrumentationProvider;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.auth.AuthMethod;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.lib.QueryParams;
import nl.inl.blacklab.server.lib.QueryParamsJson;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.WebserviceParams;
import nl.inl.blacklab.server.lib.WebserviceParamsImpl;
import nl.inl.blacklab.server.lib.results.ApiVersion;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.server.search.UserRequest;
import nl.inl.blacklab.server.util.ServletUtil;
import nl.inl.blacklab.webservice.WebserviceOperation;

/** Represents a servlet request to the webservice. */
public class UserRequestBls implements UserRequest {

    private final BlackLabServer servlet;

    private final HttpServletRequest request;

    private final HttpServletResponse response;

    /** Newly added encdpoint that always uses v5 conventions for response, etc.? */
    private boolean newEndpoint = false;

    /** Corpus name from the URL path */
    private String corpusName;

    /** Resource from the URL path, e.g. "hits" */
    private final String urlResource;

    /** Any info after the resource, e.g. document PID */
    private final String urlPathInfo;

    private User user;

    public UserRequestBls(BlackLabServer servlet, HttpServletRequest request, HttpServletResponse response) {
        this.servlet = servlet;
        this.request = request;
        this.response = response;

        // Pass requestId to instrumentationProvider
        RequestInstrumentationProvider instrumentationProvider = getInstrumentationProvider();
        ThreadContext.put("requestId", instrumentationProvider.getRequestID(request).orElse(""));

        // Parse the URL path
        String servletPath = StringUtils.strip(StringUtils.trimToEmpty(request.getPathInfo()), "/");
        if (servletPath.startsWith("corpora/")) {
            // Strip "corpora/" prefix, but remember it (new API)
            servletPath = servletPath.substring("corpora/".length());
            this.newEndpoint = true;
        }
        String[] parts = servletPath.split("/", 3);
        if (parts.length > 1 && parts[1].equals("relations")) {
            this.newEndpoint = true;
        }
        corpusName = parts.length >= 1 ? parts[0] : "";
        if (corpusName.startsWith(":")) {
            // Private index. Prefix with user id.
            corpusName = user.getId() + corpusName;
        }
        urlResource = parts.length >= 2 ? parts[1] : "";
        urlPathInfo = parts.length >= 3 ? parts[2] : "";
    }

    public boolean isNewEndpoint() {
        return newEndpoint;
    }

    @Override
    public synchronized User getUser() {
        if (user == null) {
            AuthMethod authObj = getSearchManager().getAuthSystem().getAuthObject();

            // If no auth system is configured, all users are anonymous
            if (authObj == null) {
                user = User.anonymous(request.getSession().getId());
            } else {

                // Is client on debug IP and is there a userid parameter?
                if (servlet.getSearchManager().config().getAuthentication().isOverrideIp(request.getRemoteAddr())
                        && request.getParameter("userid") != null) {
                    user = User.fromIdAndSessionId(request.getParameter("userid"), request.getSession().getId());
                } else {
                    // Let auth system determine the current user.
                    try {
                        user = authObj.determineCurrentUser(this);
                    } catch (Exception e) {
                        throw new InvalidConfiguration("Error determining current user", e);
                    }
                }
            }

            // Override via HTTP header? (insecure, normally disabled)
            String debugHttpHeaderToken = getSearchManager().config().getAuthentication().getDebugHttpHeaderAuthToken();
            if (!user.isLoggedIn() && !StringUtils.isEmpty(debugHttpHeaderToken)) {
                String xBlackLabAccessToken = request.getHeader("X-BlackLabAccessToken");
                if (xBlackLabAccessToken != null && xBlackLabAccessToken.equals(debugHttpHeaderToken)) {
                    user = User.fromIdAndSessionId(request.getHeader("X-BlackLabUserId"), request.getSession().getId());
                }
            }
        }
        return user;
    }

    public BlackLabServer getServlet() {
        return servlet;
    }

    public HttpServletRequest getRequest() {
        return request;
    }

    public HttpServletResponse getResponse() {
        return response;
    }

    @Override
    public SearchManager getSearchManager() {
        return servlet.getSearchManager();
    }

    @Override
    public String getSessionId() {
        return request.getSession().getId();
    }

    @Override
    public String getRemoteAddr() {
        return ServletUtil.getOriginatingAddress(request);
    }

    @Override
    public String getPersistedUserId() {
        // Is there a cookie yet?
        String userId = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            // Check if we have a session cookie
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals("autosearch-debug-user")) {
                    userId = cookie.getValue();
                    break;
                }
            }
        }
        return userId;
    }

    @Override
    public void persistUser(User user, int durationSec) {
        Cookie cookie = new Cookie("autosearch-debug-user", user.getId());
        cookie.setPath("/");
        cookie.setMaxAge(durationSec);
        response.addCookie(cookie);
    }

    @Override
    public String getHeader(String name) {
        return request.getHeader(name);
    }

    @Override
    public String getParameter(String name) {
        return request.getParameter(name);
    }

    @Override
    public Object getAttribute(String name) {
        return request.getAttribute(name);
    }

    @Override
    public WebserviceParams getParams(BlackLabIndex index, WebserviceOperation operation) {
        String jsonRequest = getRequest().getParameter("req");
        QueryParams blsParams;
        if (jsonRequest != null) {
            // Request was passed as a JSON structure. Parse that.
            try {
                blsParams = new QueryParamsJson(corpusName, getSearchManager(), getUser(), jsonRequest, operation);
            } catch (JsonProcessingException e) {
                throw new BadRequest("INVALID_JSON", "Error parsing req parameter (JSON request)", e);
            }
        } else {
            // Request was passed as separate bl.* parameters. Parse them.
            blsParams = new QueryParamsBlackLabServer(corpusName, getSearchManager(), getUser(), getRequest(), operation);
        }
        return WebserviceParamsImpl.get(operation.isDocsOperation(), isDebugMode(), blsParams);
    }

    @Override
    public boolean isDebugMode() {
        return getSearchManager().isDebugMode(ServletUtil.getOriginatingAddress(request));
    }

    @Override
    public RequestInstrumentationProvider getInstrumentationProvider() {
        return servlet.getInstrumentationProvider();
    }

    @Override
    public String getCorpusName() {
        return corpusName;
    }

    public String getUrlResource() {
        return urlResource;
    }

    public String getUrlPathInfo() {
        return urlPathInfo;
    }

    @Override
    public ApiVersion apiVersion() {
        String paramApi = request.getParameter("api");
        return paramApi == null ? servlet.getSearchManager().config().getParameters().getApi() :
                ApiVersion.fromValue(paramApi);
    }
}
