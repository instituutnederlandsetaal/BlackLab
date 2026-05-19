package nl.inl.blacklab.server.lib.requests;

import nl.inl.blacklab.server.index.IndexManager;
import nl.inl.blacklab.server.lib.User;

public record RequestServerInfo(IndexManager indexManager, User user, boolean includeCustomInfo, boolean debugMode) {
    public static RequestServerInfo fromParams(IndexManager indexManager, User user, boolean includeCustomInfo, boolean debugMode) {
        return new RequestServerInfo(indexManager, user, includeCustomInfo, debugMode);
    }
}
