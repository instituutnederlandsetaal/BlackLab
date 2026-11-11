package nl.inl.blacklab.server.auth;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.plugins.AuthMethodProvider;
import nl.inl.blacklab.plugins.param.PEnum;
import nl.inl.blacklab.plugins.param.PString;
import nl.inl.blacklab.plugins.param.PluginParam;
import nl.inl.blacklab.plugins.param.PluginParams;
import nl.inl.blacklab.server.BlsMain;
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

    private PluginParam parType;
    private PluginParam parName;
    private PluginParam parAttributeType; // deprecated, use "type"
    private PluginParam parAttributeName; // deprecated, use "name"

    enum AttributeType {
        ATTRIBUTE,
        HEADER,
        PARAMETER
    }

    @Override
    public void initialize() throws PluginException {
        List<String> typeOptions = List.of("attribute", "header", "parameter");
        parAttributeType = addParam(PEnum.of("attributeType", typeOptions)); // deprecated, use "type"
        parType = addParam(PEnum.of("type", typeOptions));
        parAttributeName = addParam(PString.identifier("attributeName")); // deprecated, use "name"
        parName = addParam(PString.identifier("name"));
    }

    @Override
    public AuthMethod get(PluginParams config) {
        String type = config.getString(parAttributeType, null); // deprecated, use "type"
        if (type == null)
            type = config.getString(parType, "attribute");
        AttributeType attType = AttributeType.valueOf(type.toUpperCase());
        // Name of the attribute/parameter/header to read
        String name = config.getString(parAttributeName, null); // deprecated, use "name"
        if (name == null)
            name = config.getString(parName, null);
        if (name == null)
            logger.error("AuthRequestAttribute: name parameter missing in blacklab-server.yaml");
        String valueKey = StringUtils.isEmpty(name) ? null : name;

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
                SearchManager searchMan = BlsMain.get().getSearchManager();
                if (searchMan.config().getAuthentication().isOverrideIp(request.getRemoteAddr()) && request.getParameter("userid") != null) {
                    userId = request.getParameter("userid");
                }

                if (userId == null) {
                    userId = switch (attType) {
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
