package com.hmdp.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiReservationRequest {
    private Long shopId;
    private LocalDateTime reserveTime;
    private String remark;
    private String confirmationToken;
}
