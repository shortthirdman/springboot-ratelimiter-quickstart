package com.shortthirdman.springboot.ratelimiter.monitoring;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SystemMetrics {
    private double cpuLoad;
    private double memoryUsage;
}
