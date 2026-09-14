package com.hmdp.service.impl;

import com.hmdp.entity.OutboxEvent;
import com.hmdp.mapper.OutboxEventMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

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

    @Test void eventMustBeClaimedBeforePublishAndCarriesLeaseOwner() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), OutboxEvent.class);
        OutboxEventMapper mapper = mock(OutboxEventMapper.class);
        OutboxEvent candidate = new OutboxEvent();
        candidate.setId(9L);
        candidate.setEventId("event-9");
        candidate.setStatus(0);
        candidate.setVersion(0L);
        when(mapper.selectClaimable(10)).thenReturn(Collections.singletonList(candidate));
        when(mapper.update(isNull(), any())).thenReturn(1);
        OutboxEventServiceImpl service = new OutboxEventServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);

        LocalDateTime lease = LocalDateTime.now().plusSeconds(30);
        List<OutboxEvent> claimed = service.claimPendingEvents(10, "worker-a", lease);

        assertEquals(1, claimed.size());
        assertEquals(3, claimed.get(0).getStatus());
        assertEquals("worker-a", claimed.get(0).getLockedBy());
        assertEquals(1L, claimed.get(0).getVersion());
        verify(mapper).update(isNull(), any());
    }

    @Test void expiredProcessingLeaseCanBeClaimedByAnotherWorker() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), OutboxEvent.class);
        OutboxEvent abandoned = new OutboxEvent();
        abandoned.setId(10L);
        abandoned.setEventId("event-10");
        abandoned.setStatus(3);
        abandoned.setVersion(4L);
        abandoned.setLockedBy("dead-worker");
        abandoned.setLockedUntil(LocalDateTime.now().minusSeconds(1));
        OutboxEventMapper mapper = mock(OutboxEventMapper.class);
        when(mapper.selectClaimable(1)).thenReturn(Collections.singletonList(abandoned));
        when(mapper.update(isNull(), any())).thenReturn(1);
        OutboxEventServiceImpl service = new OutboxEventServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);

        List<OutboxEvent> reclaimed = service.claimPendingEvents(1, "worker-b", LocalDateTime.now().plusSeconds(30));

        assertEquals(1, reclaimed.size());
        assertEquals("worker-b", reclaimed.get(0).getLockedBy());
        assertEquals(5L, reclaimed.get(0).getVersion());
    }
}
