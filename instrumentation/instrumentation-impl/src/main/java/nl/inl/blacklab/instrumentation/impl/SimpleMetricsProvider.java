package nl.inl.blacklab.instrumentation.impl;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import nl.inl.blacklab.instrumentation.MetricsProvider;

/**
 * Creates a registry that tracks via
 * micrometer's SimpleRegistry
 */
public class SimpleMetricsProvider implements MetricsProvider {
    private final MeterRegistry registry;

    public SimpleMetricsProvider() {
        registry = new SimpleMeterRegistry();
    }

    @Override
    public MeterRegistry getRegistry() {
        return registry;
    }
}
