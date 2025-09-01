package nl.inl.blacklab.server.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BLSConfigAuth {
    
    Map<String, String> system = new HashMap<>();
    
    List<String> overrideUserIdIps = Collections.emptyList();

    /**
     * An insecure way of authenticating a user via HTTP headers
     * X-BlackLabAccessToken / X-BlackLabUserId, only to be used for testing.
     */
    final String debugHttpHeaderAuthToken = "";
    
    public Map<String, String> getSystem() {
        return system;
    }

    @SuppressWarnings("unused")
    public void setSystem(Map<String, String> system) {
        this.system = system;
    }

    @SuppressWarnings("unused")
    public void setOverrideUserIdIps(List<String> overrideUserIdIps) {
        this.overrideUserIdIps = overrideUserIdIps;
    }

    public boolean isOverrideIp(String ip) {
        return overrideUserIdIps.contains(ip);
    }

    public String getDebugHttpHeaderAuthToken() {
        return debugHttpHeaderAuthToken;
    }
}
