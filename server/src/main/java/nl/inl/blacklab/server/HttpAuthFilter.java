package nl.inl.blacklab.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import nl.inl.blacklab.server.auth.AuthHttpBasic;
import nl.inl.blacklab.server.search.SearchManager;

/**
 * Servlet filter that enforces HTTP Basic Authentication when the environment
 * variables {@code BLACKLAB_HTTP_AUTH_USER} and {@code BLACKLAB_HTTP_AUTH_PASSWORD}
 * are both set. If either variable is absent or empty the filter is a no-op,
 * so no changes to {@code web.xml} are required to enable/disable it.
 */
public class HttpAuthFilter implements Filter {

    static final Logger logger = LogManager.getLogger(HttpAuthFilter.class);

    private static final String REALM = "BlackLab";
    public static final String AUTH_HEADER_PREFIX_BASIC = "Basic ";

    /** Is HTTP authentication enabled? (i.e. we're using AuthHttpBasic) */
    private boolean authEnabled = false;

    /** If improperly configured, deny all requests */
    private boolean denyAll = false;

    /** HTTP auth user name */
    private String requiredUser;

    /** HTTP auth password */
    private String requiredPassword;

    /** Have we read the config? */
    private boolean configRead;

    /** If no user/password supplied, is that okay? */
    private boolean okayWithoutLogin;

    @Override
    public void init(FilterConfig filterConfig) {
    }

    private synchronized void initConfig() {
        if (configRead)
            return;
        configRead = true;
        Map<String, String> authCfg = SearchManager.get().config().getAuthentication().getSystem();
        if (authCfg.get("class").endsWith("AuthHttpBasic")) {
            authEnabled = true;
            // Dirty hack - grab user/password from the BLS config directly
            // (the AuthHttpBasic doesn't get the config, the AuthMethod it produces does, and we don't have
            //  access to that here)
            requiredUser = authCfg.get("userId");
            requiredPassword = authCfg.get("password");
            okayWithoutLogin = authCfg.get("required") == null || !Boolean.parseBoolean(authCfg.get("required"));
            if (!StringUtils.isEmpty(requiredUser) && !StringUtils.isEmpty(requiredPassword)) {
                logger.info("HttpAuthFilter: HTTP Basic Authentication is ENABLED (user='{}')", requiredUser);
            } else {
                requiredUser = null;
                requiredPassword = null;
                denyAll = true;
                logger.warn("HttpAuthFilter: HTTP Basic Authentication is DISABLED "
                        + "(correctly configure AuthHttpBasic with userId and password)");
            }
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        // If auth is not configured, pass the request straight through.
        initConfig();
        if (authEnabled) {
            if (request instanceof HttpServletRequest httpRequest) {
                if (!isAuthorized(httpRequest)) {
                    // Not authorized, respond with error
                    sendUnauthorized(response);
                    return;
                }
            } else {
                throw new IOException("HttpAuthFilter: request is not HttpServletRequest");
            }
        }
        // Authorized, or authorization disabled; pass the request through
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        // NOP
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Valid login, or no login but login is optional? */
    private boolean isAuthorized(HttpServletRequest request) {
        if (denyAll)
            return false;
        String authHeader = request.getHeader(AuthHttpBasic.HTTP_HEADER_AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(AUTH_HEADER_PREFIX_BASIC)) {
            return okayWithoutLogin;
        }

        try {
            String encoded = authHeader.substring(AUTH_HEADER_PREFIX_BASIC.length()).trim();
            String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            // Split on the first ':' only — passwords may contain ':'
            int colonIdx = decoded.indexOf(':');
            if (colonIdx < 0) {
                return okayWithoutLogin;
            }
            String user = decoded.substring(0, colonIdx);
            String password = decoded.substring(colonIdx + 1);
            if (!requiredUser.isEmpty())
                return requiredUser.equals(user) && requiredPassword.equals(password);
            else
                return okayWithoutLogin;
        } catch (IllegalArgumentException e) {
            // Invalid Base64
            logger.warn("HttpAuthFilter: invalid Base64 in Authorization header", e);
            return okayWithoutLogin;
        }
    }

    private void sendUnauthorized(ServletResponse response) throws IOException {
        if (response instanceof HttpServletResponse httpResponse) {
            httpResponse.setHeader("WWW-Authenticate", "Basic realm=\"" + REALM + "\"");
            httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
        } else
            throw new IOException("HttpAuthFilter: response is not an HttpServletResponse");
    }
}



