package com.hmdp.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class BlogCommentRequest {
    @NotBlank(message = "评论内容不能为空")
    @Size(max = 255, message = "评论不能超过255字")
    private String content;
    private Long parentId;
    private Long answerId;
}
