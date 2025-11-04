package nl.inl.blacklab.server.auth;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.plugins.AuthMethodProvider;
import nl.inl.blacklab.server.lib.User;

/**
 * Authentication system used for debugging.
 *
 * Requests from debug IPs (specified in config file) may fake logged-in user by
 * passing "userid" parameter.
 */
public class AuthDebugUrl extends AuthMethodProvider {

    static final Logger logger = LogManager.getLogger(AuthDebugUrl.class);

    @Override
    public AuthMethod get(Map<String, Object> param) {
        // doesn't take any parameters
        if (!param.isEmpty())
            logger.warn("Parameters were passed to " + this.getClass().getName() + ", but it takes no parameters.");
        return request -> {
            // URL parameter is already dealt with in AuthManager. If we end up here,
            // there was no userid parameter, so just return an anonymous user.
            return User.anonymous(request.getSessionId());
        };
    }

}
