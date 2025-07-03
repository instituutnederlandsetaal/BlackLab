package nl.inl.blacklab.server.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BLSConfigProtocol {

    private static final Logger logger = LogManager.getLogger(BLSConfigProtocol.class);

    private boolean omitEmptyProperties = false;

    public boolean isOmitEmptyProperties() {
        return omitEmptyProperties;
    }

    @SuppressWarnings("unused")
    public void setOmitEmptyProperties(boolean omitEmptyProperties) {
        this.omitEmptyProperties = omitEmptyProperties;
    }

    private String accessControlAllowOrigin = "*";

    private String defaultOutputType = "XML";

    public String getDefaultOutputType() {
        return defaultOutputType;
    }

    @SuppressWarnings("unused")
    public void setDefaultOutputType(String defaultOutputType) {
        this.defaultOutputType = defaultOutputType;
    }

    public String getAccessControlAllowOrigin() {
        return accessControlAllowOrigin;
    }

    @SuppressWarnings("unused")
    public void setAccessControlAllowOrigin(String accessControlAllowOrigin) {
        this.accessControlAllowOrigin = accessControlAllowOrigin;
    }

    @Deprecated
    public void setUseOldElementNames(boolean useOldElementNames) {
        logger.warn("IMPORTANT: Found deprecated setting useOldElementNames. This setting doesn't do anything anymore and will eventually be removed.");
    }

}
