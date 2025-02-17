package nl.inl.blacklab.server.auth;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.server.search.UserRequest;

/**
 * Used for CLARIN login (Shibboleth), which passes userid in an attribute
 * called "eppn". Special class because for unknown reasons, we (sometimes?) get
 * the same userid twice in the attribute (i.e.
 * user@domain.com;user@domain.com). We detect and correct this anomaly here.
 */
public class AuthClarinEppn extends AuthRequestValue {

    private static final Logger logger = LogManager.getLogger(AuthClarinEppn.class);

    public AuthClarinEppn(Map<String, Object> param) {
        super(AttributeType.ATTRIBUTE, "eppn");
        if (param.size() > 0)
            logger.warn("Parameters were passed to " + this.getClass().getName() + ", but it takes no parameters.");
    }

    @Override
    protected String getUserId(UserRequest request) {
        String userId = super.getUserId(request);
        if (userId != null) {
            String[] parts = userId.split(";", 2);
            if (parts.length == 2 && parts[0].equals(parts[1])) {
                // The user id string is of the form "USERID;USERID".
                // Only return it once.
                return parts[0];
            }
        }
        return userId;
    }
}
