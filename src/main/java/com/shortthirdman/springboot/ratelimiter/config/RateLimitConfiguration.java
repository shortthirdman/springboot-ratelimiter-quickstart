package com.shortthirdman.springboot.ratelimiter.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RateLimitConfiguration {

    @Value("${ratelimiter.overdraft-capacity:50}")
    private int overdraftCapacity;

    @Value("${ratelimiter.refill-capacity:40}")
    private int refillCapacity;

    @Bean
    public Bucket createNewBucket() {
        long overdraft = 50;
        Refill refill = Refill.intervally(40, Duration.ofMinutes(1));
        Bandwidth limit = Bandwidth.classic(overdraft, refill);
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
