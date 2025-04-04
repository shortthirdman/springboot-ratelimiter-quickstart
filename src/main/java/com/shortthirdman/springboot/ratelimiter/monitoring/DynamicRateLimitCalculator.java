package com.shortthirdman.springboot.ratelimiter.monitoring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
public class DynamicRateLimitCalculator {

    private static final int BASE_LIMIT = 100;
    private static final double CPU_THRESHOLD_HIGH = 0.8;
    private static final double CPU_THRESHOLD_MEDIUM = 0.5;
    private static final double MEMORY_THRESHOLD_HIGH = 0.8;
    private static final double MEMORY_THRESHOLD_MEDIUM = 0.5;

    public RateLimitConfigValues calculateLimit(SystemMetrics metrics) {
        int limit = BASE_LIMIT;

        // Apply CPU load factor
        limit = adjustLimitBasedOnCpu(limit, metrics.getCpuLoad());

        // Apply memory usage factor
        limit = adjustLimitBasedOnMemory(limit, metrics.getMemoryUsage());

        Duration refillDuration = calculateRefillDuration(metrics);

        log.debug("Calculated rate limit: {}/{}s", limit,
                refillDuration.getSeconds());

        return new RateLimitConfigValues(limit, refillDuration);
    }

    private int adjustLimitBasedOnCpu(int currentLimit, double cpuLoad) {
        if (cpuLoad > CPU_THRESHOLD_HIGH) {
            return (int) (currentLimit * 0.3); // Severe reduction
        } else if (cpuLoad > CPU_THRESHOLD_MEDIUM) {
            return (int) (currentLimit * 0.6); // Moderate reduction
        }
        return currentLimit;
    }

    private int adjustLimitBasedOnMemory(int currentLimit,
                                         double memoryUsage) {
        if (memoryUsage > MEMORY_THRESHOLD_HIGH) {
            return (int) (currentLimit * 0.4);
        } else if (memoryUsage > MEMORY_THRESHOLD_MEDIUM) {
            return (int) (currentLimit * 0.7);
        }
        return currentLimit;
    }

    private Duration calculateRefillDuration(SystemMetrics metrics) {
        double maxLoad = Math.max(metrics.getCpuLoad(),
                metrics.getMemoryUsage());
        if (maxLoad > 0.8) {
            return Duration.ofMinutes(2);
        } else if (maxLoad > 0.5) {
            return Duration.ofMinutes(1);
        }
        return Duration.ofSeconds(30);
    }
}
