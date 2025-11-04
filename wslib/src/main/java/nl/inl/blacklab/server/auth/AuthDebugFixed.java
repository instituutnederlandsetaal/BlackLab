package nl.inl.blacklab.server.auth;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.plugins.AuthMethodProvider;
import nl.inl.blacklab.server.lib.User;

/**
 * Authentication system used for debugging.
 *
 * Requests from debug IPs (specified in config file) are automatically logged
 * in as the specified userId.
 */
public class AuthDebugFixed extends AuthMethodProvider {

    static final Logger logger = LogManager.getLogger(AuthDebugFixed.class);

    @Override
    public AuthMethod get(Map<String, Object> config) {
        boolean hasUserId = config.containsKey("userId");
        int expectedParameters = hasUserId ? 1 : 0;
        if (config.size() > expectedParameters)
            logger.warn("AuthDebugFixed only takes one parameter (userId), but other config were passed.");
        Object u = config.get("userId");
        String userId = u != null ? u.toString() : "DEBUG-USER";
        return request -> User.fromIdAndSessionId(userId, request.getSessionId());
    }
}
