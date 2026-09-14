package com.hmdp.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class AiChatRequest {

    @NotBlank(message = "问题不能为空")
    @Size(max = 2000, message = "问题不能超过2000字")
    private String message;
    private String conversationId;
}
