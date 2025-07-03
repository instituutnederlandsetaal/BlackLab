package nl.inl.blacklab.server.lib.results;

public class ResultUserInfo {
    private final boolean loggedIn;
    private final String userId;
    private final boolean canCreateIndex;

    ResultUserInfo(boolean loggedIn, String userId, boolean canCreateIndex) {
        this.loggedIn = loggedIn;
        this.userId = userId;
        this.canCreateIndex = canCreateIndex;
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
