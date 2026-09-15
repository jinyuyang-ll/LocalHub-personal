package com.hmdp.service.impl;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * Common event-id idempotency gate. Call this inside the same database
 * transaction as the consumer's business mutation so a failure rolls both back.
 */
@Service
public class ConsumerMessageService {

    @Resource private JdbcTemplate jdbc;

    public boolean tryRecord(String consumerName, String eventId, String eventType) {
        int inserted = jdbc.update(
                "INSERT IGNORE INTO tb_consumer_message(consumer_name,event_id,event_type) VALUES (?,?,?)",
                consumerName, eventId, eventType);
        return inserted == 1;
    }
}
