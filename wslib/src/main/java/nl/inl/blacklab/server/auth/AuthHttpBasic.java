package nl.inl.blacklab.server.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Base64.Decoder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.plugins.AuthMethodProvider;
import nl.inl.blacklab.plugins.param.PluginParams;
import nl.inl.blacklab.server.lib.User;

/**
 * Use basic HTTP authentication.
 * 
 * Note that you will have to enable this in web.xml for this to work.
 */
public class AuthHttpBasic extends AuthMethodProvider {

    static final Logger logger = LogManager.getLogger(AuthHttpBasic.class);

    @Override
    public AuthMethod get(PluginParams config) {
        Decoder base64Decoder = Base64.getDecoder();
        return request -> {
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
        };
    }

}
