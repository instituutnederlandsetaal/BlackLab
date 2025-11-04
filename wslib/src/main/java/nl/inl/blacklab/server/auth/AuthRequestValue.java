package nl.inl.blacklab.server.auth;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.plugins.AuthMethodProvider;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.server.search.UserRequest;

/**
 * Authentication system using servlet request attribute/header/parameter
 * for logged-in user id.
 *
 * Can be used, for example, with Shibboleth authentication.
 */
public class AuthRequestValue extends AuthMethodProvider {
    static final Logger logger = LogManager.getLogger(AuthRequestValue.class);

    enum AttributeType {
        ATTRIBUTE,
        HEADER,
        PARAMETER
    }

    @Override
    public AuthMethod get(Map<String, Object> config) {
        Object typeName = config.get("attributeType"); // deprecated, use "type"
        if (typeName == null)
            typeName = config.get("type");
        if (typeName == null)
            typeName = "attribute";
        Object parName = config.get("attributeName"); // deprecated, use "name"
        if (parName == null) parName = config.get("name");
        if (parName == null)
            logger.error("AuthRequestAttribute: name parameter missing in blacklab-server.yaml");

        // Name of the attribute/parameter/header to read
        String valueKey = parName == null ? null : parName.toString();
        AttributeType type = AttributeType.valueOf(typeName.toString().toUpperCase());
        if (config.size() > 2)
            logger.warn("AuthRequestAttribute only takes two parameters " +
                    "(type [attribute, header, parameter] and name), but others were passed.");

        return new AuthMethod() {
            @Override
            public User determineCurrentUser(UserRequest request) {
                String sessionId = request.getSessionId();
                if (valueKey == null) {
                    // (not configured correctly)
                    logger.warn(
                            "AuthRequestAttribute: cannot determine current user; missing 'name' parameter " +
                                    "in blacklab-server.yaml");
                    return User.anonymous(sessionId);
                }

                // See if there's a logged-in user or not
                String userId = getUserId(request);

                // Return the appropriate User object
                if (userId == null || userId.isEmpty()) {
                    return User.anonymous(sessionId);
                }
                return User.fromIdAndSessionId(userId, sessionId);
            }

            private String getUserId(UserRequest request) {
                String userId = null;

                // Overridden in URL?
                SearchManager searchMan = request.getSearchManager();
                if (searchMan.config().getAuthentication().isOverrideIp(request.getRemoteAddr()) && request.getParameter("userid") != null) {
                    userId = request.getParameter("userid");
                }

                if (userId == null) {
                    userId = switch (type) {
                        case ATTRIBUTE -> request.getAttribute(valueKey).toString();
                        case HEADER -> request.getHeader(valueKey);
                        case PARAMETER -> request.getParameter(valueKey);
                    };
                }

                return userId;
            }
        };
    }

}
