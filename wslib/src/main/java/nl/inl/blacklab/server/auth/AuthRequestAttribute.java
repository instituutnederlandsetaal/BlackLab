package nl.inl.blacklab.server.auth;

import java.util.Map;

/**
 * Authentication system using servlet request attributes for logged-in user id.
 *
 * Can be used, for example, with Shibboleth authentication.
 * @deprecated use AuthRequestValue instead
 */
@Deprecated
public class AuthRequestAttribute extends AuthRequestValue {
    public AuthRequestAttribute(Map<String, Object> parameters) {
        super(parameters);
    }
}
