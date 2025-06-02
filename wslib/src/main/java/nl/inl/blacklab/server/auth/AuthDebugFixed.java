package nl.inl.blacklab.server.auth;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.search.UserRequest;

/**
 * Authentication system used for debugging.
 *
 * Requests from debug IPs (specified in config file) are automatically logged
 * in as the specified userId.
 */
public class AuthDebugFixed implements AuthMethod {

    static final Logger logger = LogManager.getLogger(AuthDebugFixed.class);

    private final String userId;

    @SuppressWarnings("unused") // reflection in AuthManager
    public AuthDebugFixed(Map<String, Object> parameters) {
        boolean hasUserId = parameters.containsKey("userId");
        int expectedParameters = hasUserId ? 1 : 0;
        if (parameters.size() > expectedParameters)
            logger.warn("AuthDebugFixed only takes one parameter (userId), but other parameters were passed.");
        Object u = parameters.get("userId");
        this.userId = u != null ? u.toString() : "DEBUG-USER";
    }

    public User determineCurrentUser(UserRequest request) {
        return User.fromIdAndSessionId(userId, request.getSessionId());
    }

}
