package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.OutboxEvent;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventMapper extends BaseMapper<OutboxEvent> {

    @Select("SELECT * FROM tb_outbox_event WHERE " +
            "((status IN (0,2) AND next_retry_time <= NOW()) OR " +
            "(status = 3 AND locked_until <= NOW())) " +
            "ORDER BY create_time ASC, id ASC LIMIT #{limit} FOR UPDATE SKIP LOCKED")
    List<OutboxEvent> selectClaimable(@Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM tb_outbox_event WHERE status IN (0,2,3)")
    long countPending();

    @Select("SELECT MIN(create_time) FROM tb_outbox_event WHERE status IN (0,2,3)")
    LocalDateTime oldestPendingTime();
}
