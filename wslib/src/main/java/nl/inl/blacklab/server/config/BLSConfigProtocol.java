package nl.inl.blacklab.server.config;

public class BLSConfigProtocol {

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

}
