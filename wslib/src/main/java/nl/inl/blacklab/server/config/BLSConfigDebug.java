package nl.inl.blacklab.server.config;

import java.util.List;

import nl.inl.blacklab.server.util.BlsUtils;

public class BLSConfigDebug {

    /** Default value for debug.addresses */
    public static final List<String> DEBUG_ADDRESSES_LOCALHOST = List.of("127.0.0.1",
            "0:0:0:0:0:0:0:1", "::1");

    /** Explicit list of debug addresses */
    List<String> addresses = DEBUG_ADDRESSES_LOCALHOST;

    /** Run all local requests in debug mode */
    boolean alwaysAllowDebugInfo = false;

    /** For metrics gathering purpouses */
    String metricsProvider = "";

    /** For instrumentation purpouses */
    String requestInstrumentationProvider = "";

    public List<String> getAddresses() {
        return addresses;
    }

    @SuppressWarnings("unused")
    public void setAddresses(List<String> addresses) {
        this.addresses = addresses;
    }

    public boolean isDebugMode(String ip) {
        if (alwaysAllowDebugInfo) {
            return true;
        }
        return BlsUtils.wildcardIpsContain(addresses, ip);
    }

    public String getMetricsProvider() {
        return metricsProvider;
    }

    @SuppressWarnings("unused")
    public void setMetricsProviderName(String metricsProviderName) {
        this.metricsProvider = metricsProviderName;
    }

    public String getRequestInstrumentationProvider() {
        return requestInstrumentationProvider;
    }

    @SuppressWarnings("unused")
    public void setRequestInstrumentationProvider(String requestInstrumentationProvider) {
        this.requestInstrumentationProvider = requestInstrumentationProvider;
    }
}
