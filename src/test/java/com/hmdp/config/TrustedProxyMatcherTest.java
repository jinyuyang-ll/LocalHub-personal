package com.hmdp.config;

import com.hmdp.exception.RateLimitExceededException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TrustedProxyMatcherTest {

    @AfterEach void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test void matchesExactAddressAndCidr() {
        TrustedProxyMatcher matcher = new TrustedProxyMatcher("10.0.0.8,192.168.0.0/16");
        assertTrue(matcher.matches("10.0.0.8"));
        assertTrue(matcher.matches("192.168.9.10"));
        assertFalse(matcher.matches("10.0.0.9"));
    }

    @Test void spoofedForwardedHeaderIsIgnoredForDirectClient() throws Throwable {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), anyList(), anyString(), anyString(), anyString(), anyString())).thenReturn(0L);
        RateLimitAspect aspect = new RateLimitAspect();
        ReflectionTestUtils.setField(aspect, "stringRedisTemplate", redis);
        ReflectionTestUtils.setField(aspect, "trustedProxyMatcher", new TrustedProxyMatcher("10.0.0.0/8"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.20");
        request.addHeader("X-Forwarded-For", "1.1.1.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        com.hmdp.annotation.RateLimit limit = mock(com.hmdp.annotation.RateLimit.class);
        when(limit.key()).thenReturn("ip-test");
        when(limit.limit()).thenReturn(1);
        when(limit.windowSeconds()).thenReturn(60);
        when(limit.type()).thenReturn(com.hmdp.annotation.RateLimitType.IP);

        assertThrows(RateLimitExceededException.class,
                () -> aspect.around(mock(ProceedingJoinPoint.class), limit));
        verify(redis).execute(any(), eq(java.util.Collections.singletonList("rate:ip-test:203.0.113.20")),
                anyString(), anyString(), anyString(), anyString());
    }
}
