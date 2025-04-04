package com.shortthirdman.springboot.ratelimiter.monitoring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

@Slf4j
@Component
public class SystemMetricsCollector {

    private final OperatingSystemMXBean osBean;

    public SystemMetricsCollector() {
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
    }

    public SystemMetrics collectMetrics() {
        double cpuLoad = getProcessCpuLoad();
        long freeMemory = Runtime.getRuntime().freeMemory();
        long totalMemory = Runtime.getRuntime().totalMemory();
        double memoryUsage = 1.0 - (double) freeMemory / totalMemory;

        return new SystemMetrics(cpuLoad, memoryUsage);
    }

    private double getProcessCpuLoad() {
        if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
            return ((com.sun.management.OperatingSystemMXBean) osBean)
                    .getProcessCpuLoad();
        }
        return osBean.getSystemLoadAverage();
    }
}
