package nl.inl.blacklab.server.auth;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.server.search.UserRequest;

/**
 * Authentication system using servlet request attribute/header/parameter
 * for logged-in user id.
 *
 * Can be used, for example, with Shibboleth authentication.
 */
public class AuthRequestValue implements AuthMethod {
    static final Logger logger = LogManager.getLogger(AuthRequestValue.class);

    /** Name of the attribute/parameter/header to read */
    private String valueKey = null;

    protected enum AttributeType {
        ATTRIBUTE,
        HEADER,
        PARAMETER
    }

    private AttributeType type = AttributeType.ATTRIBUTE;

    public AuthRequestValue(Map<String, Object> parameters) {
        Object type = parameters.get("attributeType"); // deprecated, use "type"
        if (type == null) type = parameters.get("type");
        if (type == null) type = "attribute";
        Object parName = parameters.get("attributeName"); // deprecated, use "name"
        if (parName == null) parName = parameters.get("name");
        if (parName == null) {
            logger.error("AuthRequestAttribute: name parameter missing in blacklab-server.json");
            return;
        }

        this.valueKey = parName.toString();
        this.type = AttributeType.valueOf(type.toString().toUpperCase());
        if (parameters.size() > 2) {
            logger.warn("AuthRequestAttribute only takes two parameters (type [attribute, header, parameter] and name), but others were passed.");
        }
    }

    public AuthRequestValue(AttributeType type, String valueKey) {
        this.valueKey = valueKey;
        this.type = type;
    }

    @Override
    public User determineCurrentUser(UserRequest request) {
        String sessionId = request.getSessionId();
        if (valueKey == null) {
            // (not configured correctly)
            logger.warn(
                    "AuthRequestAttribute: cannot determine current user; missing attributeName parameter in blacklab-server.json");
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

    protected String getUserId(UserRequest request) {
        String userId = null;

        // Overridden in URL?
        SearchManager searchMan = request.getSearchManager();
        if (searchMan.config().getAuthentication().isOverrideIp(request.getRemoteAddr()) && request.getParameter("userid") != null) {
            userId = request.getParameter("userid");
        }

        if (userId == null) {
            userId = switch (this.type) {
                case ATTRIBUTE -> request.getAttribute(valueKey).toString();
                case HEADER -> request.getHeader(valueKey);
                case PARAMETER -> request.getParameter(valueKey);
            };
        }

        return userId;
    }
}
