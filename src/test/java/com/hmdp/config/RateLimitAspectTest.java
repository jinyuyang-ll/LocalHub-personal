package com.hmdp.config;

import com.hmdp.annotation.RateLimit;
import com.hmdp.annotation.RateLimitType;
import com.hmdp.exception.RateLimitExceededException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RateLimitAspectTest {
    @Test void rejectsWhenLuaDeniesRequest() throws Throwable {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), anyList(), anyString(), anyString(), anyString(), anyString())).thenReturn(0L);
        RateLimitAspect aspect = new RateLimitAspect();
        ReflectionTestUtils.setField(aspect, "stringRedisTemplate", redis);
        RateLimit limit = mock(RateLimit.class);
        when(limit.key()).thenReturn("test"); when(limit.limit()).thenReturn(1);
        when(limit.windowSeconds()).thenReturn(60); when(limit.type()).thenReturn(RateLimitType.GLOBAL);
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        assertThrows(RateLimitExceededException.class, () -> aspect.around(point, limit));
        verifyNoInteractions(point);
    }
}
