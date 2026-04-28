package nl.inl.blacklab.server.auth;

import nl.inl.blacklab.plugins.AuthMethodProvider;
import nl.inl.blacklab.plugins.param.PluginParams;
import nl.inl.blacklab.server.lib.User;

/**
 * Authentication system used for debugging.
 *
 * Requests from debug IPs (specified in config file) may fake logged-in user by
 * passing "userid" parameter.
 */
public class AuthDebugUrl extends AuthMethodProvider {

    @Override
    public AuthMethod get(PluginParams params) {
        return request -> {
            // URL parameter is already dealt with in AuthManager. If we end up here,
            // there was no userid parameter, so just return an anonymous user.
            return User.anonymous(request.getSessionId());
        };
    }

    @Override
    public boolean isWebSafe() {
        return true;
    }

}
