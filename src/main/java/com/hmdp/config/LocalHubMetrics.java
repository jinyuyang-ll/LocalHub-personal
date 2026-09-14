package com.hmdp.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class LocalHubMetrics {

    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, AtomicLong> gauges = new ConcurrentHashMap<>();

    public LocalHubMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void increment(String name, String result) {
        Counter.builder("localhub." + name).tag("result", result).register(registry).increment();
    }

    public void record(String name, long startedNanos) {
        Timer.builder("localhub." + name + ".duration")
                .publishPercentileHistogram()
                .register(registry)
                .record(System.nanoTime() - startedNanos, TimeUnit.NANOSECONDS);
    }

    public void setGauge(String name, long value) {
        gauges.computeIfAbsent(name, key -> {
            AtomicLong gauge = new AtomicLong();
            registry.gauge("localhub." + key, gauge);
            return gauge;
        }).set(value);
    }

    public void incrementGauge(String name) {
        gauges.computeIfAbsent(name, key -> {
            AtomicLong gauge = new AtomicLong();
            registry.gauge("localhub." + key, gauge);
            return gauge;
        }).incrementAndGet();
    }
}
