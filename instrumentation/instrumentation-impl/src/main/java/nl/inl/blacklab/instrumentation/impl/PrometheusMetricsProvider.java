package nl.inl.blacklab.instrumentation.impl;

import java.io.IOException;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmHeapPressureMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import nl.inl.blacklab.instrumentation.MetricsProvider;

/**
 * Create a registry backed by prometheus
 * Adds system metrics to the registry
 */
public class PrometheusMetricsProvider implements MetricsProvider {
    private static final Logger logger = LogManager.getLogger(PrometheusMetricsProvider.class);
    private static final String DEFAULT_PROM_ENDPOINT = "/metrics";

    private final MeterRegistry theRegistry;

    public PrometheusMetricsProvider() {
        theRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        addSystemMetrics(theRegistry);
    }

    @Override
    public MeterRegistry getRegistry() {
        return theRegistry;
    }

    /**
     * Adds metrics to measure the behaviour of the underlying JVM.
     * @param registry the registry
     */
    private void addSystemMetrics(MeterRegistry registry) {
        new JvmMemoryMetrics().bindTo(registry);
        new JvmGcMetrics().bindTo(registry);
        new JvmHeapPressureMetrics().bindTo(registry);
        new JvmThreadMetrics().bindTo(registry);
        new ProcessorMetrics().bindTo(registry);
    }

    /**
     * A simple request handler that responds to prometheus  metrics scrapping requests
     * @param registry must be of type {@link PrometheusMeterRegistry}
     * @return true if the request was handled, false otherwise
     */
    public static boolean handlePrometheus(MeterRegistry registry, HttpServletRequest request, HttpServletResponse responseObject, String charEncoding) {
        if (!request.getRequestURI().contains(DEFAULT_PROM_ENDPOINT)) {
            return false;
        }
        Optional<MeterRegistry> aRegistry = Optional.of(registry);
        if (registry instanceof CompositeMeterRegistry composite) {
            aRegistry = composite.getRegistries().stream().filter(r -> r instanceof PrometheusMeterRegistry).findFirst();
        }
        if (!aRegistry.isPresent() || !(aRegistry.get() instanceof PrometheusMeterRegistry prometheusMeterRegistry)) {
            logger.warn("Can not respond to /metrics without a PrometheusRegistry");
            responseObject.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return true;
        }

        try {
            prometheusMeterRegistry.scrape(responseObject.getWriter());
            responseObject.setStatus(HttpServletResponse.SC_OK);
            responseObject.setCharacterEncoding(charEncoding);
            responseObject.setContentType(TextFormat.CONTENT_TYPE_004);
        } catch (IOException exception) {
            logger.error("Can't scrape prometheus metrics", exception);
        }
        return true;
    }
}
