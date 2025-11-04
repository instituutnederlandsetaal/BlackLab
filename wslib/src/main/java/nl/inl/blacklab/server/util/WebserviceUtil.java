package nl.inl.blacklab.server.util;

import org.apache.commons.lang3.StringUtils;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import nl.inl.blacklab.instrumentation.MetricsProvider;
import nl.inl.blacklab.instrumentation.RequestInstrumentationProvider;
import nl.inl.blacklab.server.exceptions.ConfigurationException;
import nl.inl.blacklab.server.exceptions.InternalServerError;

/** Reusable utilities for implementing BlackLab webservice. */
public class WebserviceUtil {

    private WebserviceUtil() {
    }

    public static String internalErrorMessage(String code) {
        return "An internal error occurred. Please contact the administrator. Error code: " + code + ".";
    }

    public static String internalErrorMessage(Exception e, boolean debugMode, String code) {
        if (debugMode) {
            if (e instanceof InternalServerError)
                return internalErrorMessage(e.getMessage(), true, code);
            return internalErrorMessage(e.getClass().getName() + ": " + e.getMessage(), true, code);
        }
        return internalErrorMessage(code);
    }

    public static String internalErrorMessage(String message, boolean debugMode, String code) {
        if (debugMode) {
            return message + " (Internal error code " + code + ")";
        }
        return internalErrorMessage(code);
    }

    public static String shortenIpv6(String longAddress) {
        return longAddress.replaceFirst("(^|:)(0+(:|$)){2,8}", "::").replaceAll("(:|^)0+([0-9A-Fa-f])", "$1$2");
    }

    public static RequestInstrumentationProvider createInstrumentationProvider(String registryProviderName,
            String providerName) {
        createInstrumentationRegistry(registryProviderName);

        if (StringUtils.isBlank(providerName)) {
            return RequestInstrumentationProvider.noOpProvider();
        }

        String fqClassName = providerName.startsWith("nl.inl.blacklab.instrumentation")
            ? providerName
            : String.format("nl.inl.blacklab.instrumentation.impl.%s", providerName);

        try {
            return (RequestInstrumentationProvider)
                    Class.forName(fqClassName).getDeclaredConstructor().newInstance();

        } catch (Exception ex) {
            throw new ConfigurationException("Can not create request instrumentation provider with class" + fqClassName);
        }
    }

    public static void createInstrumentationRegistry(String registryProviderClassName) {
        String fqClassName = registryProviderClassName.startsWith("nl.inl.blacklab.instrumentation")
            ? registryProviderClassName
            : String.format("nl.inl.blacklab.instrumentation.impl.%s", registryProviderClassName);

        try {
            MetricsProvider meterRegistryProvider = (MetricsProvider)
                Class.forName(fqClassName).getDeclaredConstructor().newInstance();
            MeterRegistry registry = meterRegistryProvider.getRegistry();
            Metrics.addRegistry(registry);
        } catch (Exception ex) {
            throw new ConfigurationException("Can not create metrics provider with class" + fqClassName);
        }
    }
}
