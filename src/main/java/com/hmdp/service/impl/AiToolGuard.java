package com.hmdp.service.impl;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class AiToolGuard {

    private final Set<String> allowedTools = new HashSet<>(Arrays.asList(
            "queryShop",
            "queryVoucher",
            "queryOrderStatus",
            "createReservation"
    ));

    public boolean isAllowed(String toolName) {
        return allowedTools.contains(toolName);
    }
}
