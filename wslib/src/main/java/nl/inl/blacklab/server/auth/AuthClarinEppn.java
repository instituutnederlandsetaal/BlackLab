package nl.inl.blacklab.server.auth;

import java.util.Map;

import nl.inl.blacklab.plugins.AuthMethodProvider;
import nl.inl.blacklab.plugins.PluginManager;
import nl.inl.blacklab.plugins.param.PluginParams;
import nl.inl.blacklab.server.lib.User;

/**
 * Used for CLARIN login (Shibboleth), which passes userid in an attribute
 * called "eppn". Special class because for unknown reasons, we (sometimes?) get
 * the same userid twice in the attribute (i.e.
 * user@domain.com;user@domain.com). We detect and correct this anomaly here.
 */
public class AuthClarinEppn extends AuthMethodProvider {

    @Override
    public AuthMethod get(PluginParams param) {
        // Use a regular AuthRequestValue that looks at the eppn attribute.
        // We'll check and optionally fix the user id, see below.
        AuthRequestValue authRequestValue = (AuthRequestValue)PluginManager.type(AuthMethodProvider.class).get("AuthRequestValue");
        PluginParams config = authRequestValue.descriptor().validate(
                Map.of("type", "attribute", "name", "eppn"));
        AuthMethod wrapped = authRequestValue.get(config);

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

    @Override
    public boolean isWebSafe() {
        return true;
    }
}
