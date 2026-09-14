package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;
import javax.validation.constraints.Future;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_reservation")
public class Reservation implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    @NotNull(message = "店铺编号不能为空")
    @Positive(message = "店铺编号必须为正整数")
    private Long shopId;

    @NotNull(message = "预约时间不能为空")
    @Future(message = "预约时间必须晚于当前时间")
    private LocalDateTime reserveTime;

    /**
     * 1 RESERVED, 2 CANCELLED, 3 FINISHED
     */
    private Integer status;

    @Size(max = 200, message = "备注不能超过200字")
    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
