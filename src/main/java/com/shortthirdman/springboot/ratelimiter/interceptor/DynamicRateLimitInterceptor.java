package com.shortthirdman.springboot.ratelimiter.interceptor;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.shortthirdman.springboot.ratelimiter.monitoring.*;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class DynamicRateLimitInterceptor implements HandlerInterceptor, RateLimitConfigProvider {

    private final Cache<String, Bucket> bucketCache;
    private final SystemMetricsCollector metricsCollector;
    private final DynamicRateLimitCalculator calculator;
    private final AtomicReference<RateLimitConfigValues> currentConfig;
    private final ScheduledExecutorService scheduler;
    private final RateLimitMetrics metrics;

    public DynamicRateLimitInterceptor(SystemMetricsCollector metricsCollector,
                                       DynamicRateLimitCalculator calculator, MeterRegistry meterRegistry) {
        this.metricsCollector = metricsCollector;
        this.calculator = calculator;
        this.currentConfig = new AtomicReference<>(
                new RateLimitConfigValues(100, Duration.ofMinutes(1))
        );
        this.bucketCache = Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.metrics = new RateLimitMetrics(meterRegistry, this);
        startMetricsUpdateTask();
    }

    private void startMetricsUpdateTask() {
        scheduler.scheduleAtFixedRate(
                this::updateRateLimitConfig,
                0,
                10,
                TimeUnit.SECONDS
        );
    }

    private void updateRateLimitConfig() {
        try {
            SystemMetrics metrics = metricsCollector.collectMetrics();
            RateLimitConfigValues newConfig = calculator.calculateLimit(metrics);

            RateLimitConfigValues oldConfig = currentConfig.get();
            if (hasSignificantChange(oldConfig, newConfig)) {
                currentConfig.set(newConfig);
                log.info("Rate limit updated: {}/{}s",
                        newConfig.getLimit(),
                        newConfig.getRefillDuration().getSeconds());

                // Clear cache to force bucket recreation with new limits
                bucketCache.invalidateAll();
            }
        } catch (Exception e) {
            log.error("Error updating rate limit config", e);
        }
    }

    private boolean hasSignificantChange(RateLimitConfigValues oldConfig,
                                         RateLimitConfigValues newConfig) {
        double limitChange = Math.abs(1.0 -
                (double) newConfig.getLimit() / oldConfig.getLimit());
        return limitChange > 0.2; // 20% change threshold
    }

    public RateLimitConfigValues getRateLimitConfig() {
        return this.currentConfig.get();
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        String path = request.getRequestURI();
        String method = request.getMethod();

        Timer.Sample timerSample = metrics.startTimer();
        boolean rateLimited = false;
        try {
            metrics.recordRequest();

            String clientId = getClientIdentifier(request);
            Bucket bucket = bucketCache.get(clientId, this::createBucket);

            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

            if (probe.isConsumed()) {
                addRateLimitHeaders(response, probe);
                return true;
            }

            metrics.incrementRateLimitExceeded();
            handleRateLimitExceeded(response, probe);
            return false;
        } finally {
            metrics.stopTimer(timerSample, path, method, rateLimited);
        }
    }

    private Bucket createBucket(String clientId) {
        RateLimitConfigValues config = currentConfig.get();
        return Bucket.builder()
                .addLimit(Bandwidth.classic(
                        config.getLimit(),
                        Refill.intervally(config.getLimit(),
                                config.getRefillDuration())
                ))
                .build();
    }

    private String getClientIdentifier(HttpServletRequest request) {
        // Could combine multiple factors: IP, user ID, API key, etc.
        return request.getRemoteAddr();
    }

    private void addRateLimitHeaders(HttpServletResponse response,
                                     ConsumptionProbe probe) {
        RateLimitConfigValues config = currentConfig.get();
        response.addHeader("X-Rate-Limit-Limit",
                String.valueOf(config.getLimit()));
        response.addHeader("X-Rate-Limit-Remaining",
                String.valueOf(probe.getRemainingTokens()));
        response.addHeader("X-Rate-Limit-Reset",
                String.valueOf(probe.getNanosToWaitForRefill() /
                        1_000_000_000));
    }

    private void handleRateLimitExceeded(HttpServletResponse response,
                                         ConsumptionProbe probe)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        String errorMessage = String.format(
                "Rate limit exceeded. Try again in %d seconds",
                probe.getNanosToWaitForRefill() / 1_000_000_000
        );

        response.getWriter().write(
                String.format(
                        "{\"error\": \"%s\", \"retryAfter\": %d}",
                        errorMessage,
                        probe.getNanosToWaitForRefill() / 1_000_000_000
                )
        );
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }
}
