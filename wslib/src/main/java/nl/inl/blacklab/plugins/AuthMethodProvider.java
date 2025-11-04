package nl.inl.blacklab.plugins;

import java.util.Map;

import nl.inl.blacklab.server.auth.AuthMethod;

/** Provides a way of determining the logged-in user */
public abstract class AuthMethodProvider extends Plugin {

    public abstract AuthMethod get(Map<String, Object> param);
}
