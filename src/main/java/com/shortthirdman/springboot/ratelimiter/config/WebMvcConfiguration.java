package com.shortthirdman.springboot.ratelimiter.config;

import com.shortthirdman.springboot.ratelimiter.interceptor.DynamicRateLimitInterceptor;
import com.shortthirdman.springboot.ratelimiter.interceptor.RateLimitInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

    private final RateLimitInterceptor interceptor;

    @Autowired
    private DynamicRateLimitInterceptor rateLimiter;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimiter)
                .addPathPatterns("/api/**");
        registry.addInterceptor(interceptor)
                .addPathPatterns("/api/**");
    }

    public WebMvcConfiguration(RateLimitInterceptor interceptor) {
        this.interceptor = interceptor;
    }
}
