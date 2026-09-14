package com.hmdp.dto;

import lombok.Data;

import java.time.LocalDateTime;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

@Data
public class AiReservationRequest {
    @Positive(message = "店铺编号必须为正整数")
    private Long shopId;
    private LocalDateTime reserveTime;
    @Size(max = 200, message = "备注不能超过200字")
    private String remark;
    private String confirmationToken;
}
