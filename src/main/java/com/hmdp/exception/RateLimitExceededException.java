package com.hmdp.exception;

public class RateLimitExceededException extends BusinessException {
    public RateLimitExceededException() {
        super(ErrorCode.RATE_LIMITED);
    }
}
