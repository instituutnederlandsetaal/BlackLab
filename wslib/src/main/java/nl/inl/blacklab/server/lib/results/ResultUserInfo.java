package nl.inl.blacklab.server.lib.results;

import nl.inl.blacklab.server.index.IndexManager;
import nl.inl.blacklab.server.lib.User;

public class ResultUserInfo {
    private final boolean loggedIn;
    private final String userId;
    private final boolean canCreateIndex;
    private final String clientIp;

    ResultUserInfo(User user, IndexManager indexManager, String clientIp) {
        this.loggedIn = user.isLoggedIn();
        this.userId = user.getId();
        this.canCreateIndex = indexManager.canCreateIndex(user);
        this.clientIp = clientIp;
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

    public String getClientIp() {
        return clientIp;
    }
}
