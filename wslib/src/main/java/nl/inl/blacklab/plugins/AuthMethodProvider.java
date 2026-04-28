package nl.inl.blacklab.plugins;

import nl.inl.blacklab.plugins.param.PluginParams;
import nl.inl.blacklab.server.auth.AuthMethod;

/** Provides a way of determining the logged-in user */
public abstract class AuthMethodProvider extends Plugin {

    public abstract AuthMethod get(PluginParams params);

}
