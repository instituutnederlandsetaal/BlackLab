package nl.inl.blacklab.server.auth;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.search.UserRequest;

/**
 * Set a cookie to simulate a logged-in user.
 * 
 * NOTE: this doesn't work properly with BlackLab Frontend at the moment, because
 * BlackLab Frontend performs requests to BLS from both the client and the server.
 * Both of these get assigned different cookies, causing problems. One way to
 * fix this would be to only use BLS from the client, but that would require
 * significant changes to the application.
 */
public class AuthDebugCookie implements AuthMethod {

    static final Logger logger = LogManager.getLogger(AuthDebugCookie.class);

    static final int TEN_YEARS = 10 * 365 * 24 * 60 * 60;

    public AuthDebugCookie(Map<String, Object> param) {
        // doesn't take any parameters
        if (param.size() > 0)
            logger.warn("Parameters were passed to " + this.getClass().getName() + ", but it takes no parameters.");
    }

    @Override
    public User determineCurrentUser(UserRequest request) {
        String userId = request.getPersistedUserId();

        if (userId == null) {
            // No cookie yet. Generate userId based on sessionId. Cookie will be saved in persistUser().
            userId = request.getSessionId();
            if (userId.length() > 6) {
                userId = userId.substring(0, 6);
            }
            userId = "user-" + userId;

        }

        // Return the appropriate User object
        String sessionId = request.getSessionId();
        if (userId.isEmpty()) {
            return User.anonymous(sessionId);
        }
        return User.fromIdAndSessionId(userId, sessionId);
    }

    @Override
    public void persistUser(UserRequest request, User user) {
        request.persistUser(user, TEN_YEARS);
    }

}
