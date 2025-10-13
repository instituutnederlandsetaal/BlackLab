package nl.inl.blacklab.server.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.search.UserRequest;

/**
 * Use basic HTTP authentication.
 * 
 * Note that you will have to enable this in web.xml for this to work.
 */
public class AuthHttpBasic implements AuthMethod {

    static final Logger logger = LogManager.getLogger(AuthHttpBasic.class);

    final Decoder base64Decoder = Base64.getDecoder();

    public AuthHttpBasic(Map<String, Object> param) {
        // doesn't take any parameters
        if (!param.isEmpty())
            logger.warn("Parameters were passed to " + this.getClass().getName() + ", but it takes no parameters.");
    }

    @Override
    public User determineCurrentUser(UserRequest request) {

        String userId = null;
        String authHeader = request.getHeader("authorization");
        if (authHeader != null) {
            String encodedValue = authHeader.split(" ")[1];
            String decodedValue = new String(base64Decoder.decode(encodedValue), StandardCharsets.UTF_8);
            String[] split = decodedValue.split(":", 2);
            userId = split.length > 0 ? split[0] : null;
        }

        // Return the appropriate User object
        String sessionId = request.getSessionId();
        if (userId == null || userId.isEmpty()) {
            return User.anonymous(sessionId);
        }
        return User.fromIdAndSessionId(userId, sessionId);
    }

}
