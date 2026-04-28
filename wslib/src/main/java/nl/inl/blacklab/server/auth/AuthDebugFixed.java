package nl.inl.blacklab.server.auth;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.plugins.AuthMethodProvider;
import nl.inl.blacklab.plugins.param.PString;
import nl.inl.blacklab.plugins.param.PluginParam;
import nl.inl.blacklab.plugins.param.PluginParams;
import nl.inl.blacklab.server.lib.User;

/**
 * Authentication system used for debugging.
 *
 * Requests from debug IPs (specified in config file) are automatically logged
 * in as the specified userId.
 */
public class AuthDebugFixed extends AuthMethodProvider {

    static final Logger logger = LogManager.getLogger(AuthDebugFixed.class);

    private PluginParam parUserId;

    @Override
    public void initialize() throws PluginException {
        parUserId = addParam(PString.any("userId"));
    }

    @Override
    public AuthMethod get(PluginParams params) {
        String userId = params.getString(parUserId, "DEBUG-USER");
        return request -> User.fromIdAndSessionId(userId, request.getSessionId());
    }

    @Override
    public boolean isWebSafe() {
        return true;
    }
}
