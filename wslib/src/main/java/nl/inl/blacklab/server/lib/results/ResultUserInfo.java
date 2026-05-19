package nl.inl.blacklab.server.lib.results;

import nl.inl.blacklab.server.index.IndexManager;
import nl.inl.blacklab.server.lib.User;

public class ResultUserInfo {
    private final boolean loggedIn;
    private final String userId;
    private final boolean canCreateIndex;

    ResultUserInfo(User user, IndexManager indexManager) {
        this.loggedIn = user.isLoggedIn();
        this.userId = user.getId();
        this.canCreateIndex = indexManager.canCreateIndex(user);
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public String getUserId() {
        return userId;
    }

    public boolean canCreateIndex() {
        return canCreateIndex;
    }
}
