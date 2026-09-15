package com.hmdp.config;

import com.hmdp.dto.Result;
import com.hmdp.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebExceptionAdviceTest {
    @Test void rateLimitUsesHttp429() {
        ResponseEntity<Result> response = new WebExceptionAdvice()
                .handleRateLimitExceeded(new RateLimitExceededException());
        assertEquals(429, response.getStatusCodeValue());
        assertEquals("RATE_LIMITED", response.getBody().getCode());
    }
}
