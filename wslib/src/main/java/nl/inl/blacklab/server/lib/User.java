package nl.inl.blacklab.server.lib;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Represents either a unique (logged-in) user, or a unique session (when not
 * logged in).
 */
public class User {
    /** Will be used in character class in regexes below */
    private static final String USER_ID_CHARS_FOR_REGEXES = "a-zA-Z0-9\\-._!$&'()*+,;=@";

    /** Matches a valid user id */
    private static final String REGEX_VALID_USER_ID = "^[" + USER_ID_CHARS_FOR_REGEXES + "]+$";

    /** Matches an invalid character in a user id */
    private static final String REGEX_NON_USERID_CHAR = "[^" + USER_ID_CHARS_FOR_REGEXES + "]";

    /** The user id if logged in; null otherwise */
    private String userId;

    /** The session id */
    private final String sessionId;
    
    /** Is this the superuser? */
    private final boolean superuser;

    /**
     * Create a new user object with user id (i.e. not anonymous).
     *
     * @param userId unique id identifying this user
     * @return the new user
     */
    public static User fromId(String userId) {
        return fromIdAndSessionId(userId, null);
    }


    /**
     * Create a new user object.
     *
     * @param userId unique id identifying this user, or null if not logged in
     * @param sessionId the session id
     * @return the new user
     */
    public static User fromIdAndSessionId(String userId, String sessionId) {
        return new User(userId, sessionId, false);
    }

    /**
     * Create a new anonymous user.
     *
     * @param sessionId the session id
     * @return the new user
     */
    public static User anonymous(String sessionId) {
        return new User(null, sessionId, false);
    }

    private User(String userId, String sessionId, boolean superuser) {
        this.userId = null;
        if (userId != null) {
            // Replace any non-URL-safe characters from userid with _.
            // Also leave out colon because we use colon as a separator
            // between userid and index name.
            this.userId = sanitize(userId);
        }
        this.sessionId = sessionId;
        this.superuser = superuser;
    }

    @Override
    public String toString() {
        return userId != null ? userId : "SESSION:" + sessionId;
    }

    public String uniqueId() {
        return userId != null ? userId : "S:" + sessionId;
    }

    public String uniqueIdShort() {
        String str = uniqueId();
        return str.length() > 6 ? str.substring(0, 6) : str;
    }

    public boolean isLoggedIn() {
        return userId != null;
    }

    public String getId() {
        return userId;
    }

    public String getUserDirName() {
        // NOTE: we want a safe directory name, so instead of trying to
        // get rid of non-safe characters, we just strip everything that
        // isn't a regular letter and append an MD5 of the original id
        // for uniqueness.
        String id = getId();
        String stripped = id.replaceAll("[^a-zA-Z]", "_");
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.reset();
            md5.update(id.getBytes());
            byte[] hashBytes = md5.digest();
            BigInteger hashInt = new BigInteger(1, hashBytes);
            String hashHex = hashInt.toString(16);
            String zeroes = "0".repeat(Math.max(0, 32 - hashHex.length()));
            return stripped + "_" + zeroes + hashHex;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean isValidUserId(String userId) {
        return userId.matches(REGEX_VALID_USER_ID);
    }

    public static String sanitize(String originalUserId) {
        if (originalUserId == null || originalUserId.isEmpty())
            return null;

        return originalUserId.replaceAll(REGEX_NON_USERID_CHAR, "_");
    }

    public boolean isSuperuser() {
        return superuser;
    }

    public boolean canManageFormatsFor(String userIdFromFormatIdentifier) {
        return userIdFromFormatIdentifier.equals(getId()) || isSuperuser();
    }
}
