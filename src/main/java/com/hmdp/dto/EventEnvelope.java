package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Stable wire format for events emitted by the Outbox relay.
 * Business payloads live in {@code data}; transport metadata is never mixed
 * into a domain-specific DTO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventEnvelope {
    private String eventId;
    private String eventType;
    private Long aggregateId;
    private String occurredAt;
    private Object data;
}
