package nl.inl.blacklab.server.auth;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.plugins.AuthMethodProvider;
import nl.inl.blacklab.server.lib.User;

/**
 * Used for CLARIN login (Shibboleth), which passes userid in an attribute
 * called "eppn". Special class because for unknown reasons, we (sometimes?) get
 * the same userid twice in the attribute (i.e.
 * user@domain.com;user@domain.com). We detect and correct this anomaly here.
 */
public class AuthClarinEppn extends AuthMethodProvider {

    private static final Logger logger = LogManager.getLogger(AuthClarinEppn.class);

    @Override
    public AuthMethod get(Map<String, Object> param) {
        if (!param.isEmpty())
            logger.warn("Parameters were passed to " + this.getClass().getName() + ", but it takes no parameters.");

        // Use a regular AuthRequestValue that looks at the eppn attribute.
        // We'll check and optionally fix the user id, see below.
        Map<String, Object> config = Map.of("type", "attribute", "name", "eppn");
        AuthMethod wrapped = new AuthRequestValue().get(config);

        return request -> {
            User user = wrapped.determineCurrentUser(request);
            String userId = user.getId();
            if (userId != null) {
                String[] parts = userId.split(";", 2);
                if (parts.length == 2 && parts[0].equals(parts[1])) {
                    // The user id string is of the form "USERID;USERID".
                    // Only return it once.
                    return User.fromIdAndSessionId(parts[0], user.getSessionId());
                }
            }
            return user;
        };
    }
}
