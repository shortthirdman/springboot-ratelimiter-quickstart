package com.shortthirdman.springboot.ratelimiter.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.Map;

public class RateLimitMetrics {

    private final MeterRegistry meterRegistry;
    private final Counter rateLimitExceeded;
    private final Counter requestsTotal;
    private final Gauge currentLimit;

    public RateLimitMetrics(MeterRegistry registry,
                            RateLimitConfigProvider configProvider) {
        this.meterRegistry = registry;

        this.rateLimitExceeded = Counter.builder("rate_limit.exceeded")
                .description("Number of rate limit exceeded events")
                .tag("type", "exceeded")
                .register(registry);

        this.requestsTotal = Counter.builder("rate_limit.requests")
                .description("Total number of requests processed")
                .tag("type", "total")
                .register(registry);

        this.currentLimit = Gauge.builder("rate_limit.current",
                        configProvider,
                        this::getCurrentLimit)
                .description("Current rate limit value")
                .tag("type", "limit")
                .register(registry);
    }

    public Timer.Sample startTimer() {
        return Timer.start();
    }

    public void stopTimer(Timer.Sample sample, String path, String method, boolean rateLimited) {
        Timer timer = Timer.builder("rate_limit.request.duration")
                .description("Request duration through rate limiter")
                .tags(
                        "path", path,
                        "method", method,
                        "rate_limited", String.valueOf(rateLimited),
                        "component", "rate_limiter"
                )
                .register(meterRegistry);
        sample.stop(timer);
    }

    public void incrementRateLimitExceeded() {
        rateLimitExceeded.increment();
    }

    public void recordRequest() {
        requestsTotal.increment();
    }

    private double getCurrentLimit(RateLimitConfigProvider provider) {
        return provider.getRateLimitConfig().getLimit();
    }

    public Map<String, Number> getCurrentMetrics() {
        return Map.of(
                "rateLimitExceeded", rateLimitExceeded.count(),
                "totalRequests", requestsTotal.count(),
                "currentLimit", currentLimit.value()
        );
    }
}
