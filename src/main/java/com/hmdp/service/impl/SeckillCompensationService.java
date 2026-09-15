package com.hmdp.service.impl;

import com.hmdp.dto.SeckillOrderMessage;
import com.hmdp.enums.SeckillOrderState;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class SeckillCompensationService {

    @Resource private SeckillRollbackService rollbackService;

    public boolean compensateFailure(SeckillOrderMessage message, String reason) {
        return rollbackService.rollback(message, reason);
    }

    public boolean compensateFailureClaim(SeckillOrderMessage message, String reason, String claimToken) {
        return rollbackService.rollbackClaim(message, reason, claimToken);
    }

    public boolean compensateDuplicate(SeckillOrderMessage message, String reason) {
        return rollbackService.rollback(message, reason, SeckillOrderState.DUPLICATE, true);
    }
}
