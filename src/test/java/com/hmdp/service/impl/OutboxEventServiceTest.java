package com.hmdp.service.impl;

import com.hmdp.entity.OutboxEvent;
import com.hmdp.mapper.OutboxEventMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OutboxEventServiceTest {
    @Test void newEventHasStableIdAndVersion() {
        OutboxEventMapper mapper = mock(OutboxEventMapper.class);
        when(mapper.insert(any())).thenReturn(1);
        OutboxEventServiceImpl service = new OutboxEventServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        service.createEvent("VoucherOrder", 1L, "CREATED", "orders", "{}");
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(mapper).insert(captor.capture());
        assertNotNull(captor.getValue().getEventId());
        assertEquals(0L, captor.getValue().getVersion());
        assertEquals(0, captor.getValue().getStatus());
    }
}
