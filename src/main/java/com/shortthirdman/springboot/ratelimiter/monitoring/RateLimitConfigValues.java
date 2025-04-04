package com.shortthirdman.springboot.ratelimiter.monitoring;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Duration;

@Data
@AllArgsConstructor
public class RateLimitConfigValues {

    private int limit;
    private Duration refillDuration;
}
