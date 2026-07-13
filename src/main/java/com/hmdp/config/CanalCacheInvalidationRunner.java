package com.hmdp.config;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import com.github.benmanes.caffeine.cache.Cache;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "localhub.canal", name = "enabled", havingValue = "true")
public class CanalCacheInvalidationRunner implements ApplicationRunner {

    @Value("${localhub.canal.host:127.0.0.1}")
    private String host;

    @Value("${localhub.canal.port:11111}")
    private Integer port;

    @Value("${localhub.canal.destination:example}")
    private String destination;

    @Value("${localhub.canal.username:}")
    private String username;

    @Value("${localhub.canal.password:}")
    private String password;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private Cache<Long, Shop> shopLocalCache;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void run(ApplicationArguments args) {
        executor.submit(this::listen);
    }

    private void listen() {
        CanalConnector connector = CanalConnectors.newSingleConnector(
                new InetSocketAddress(host, port),
                destination,
                username,
                password
        );
        try {
            connector.connect();
            connector.subscribe(".*\\..*");
            connector.rollback();
            log.info("Canal cache invalidation listener started. destination={}", destination);
            while (true) {
                Message message = connector.getWithoutAck(100);
                long batchId = message.getId();
                try {
                    if (batchId != -1 && !message.getEntries().isEmpty()) {
                        handleEntries(message.getEntries());
                    }
                    connector.ack(batchId);
                } catch (Exception e) {
                    connector.rollback(batchId);
                    log.error("Canal message handling failed. batchId={}", batchId, e);
                }
            }
        } catch (Exception e) {
            log.error("Canal listener stopped unexpectedly", e);
        } finally {
            connector.disconnect();
        }
    }

    private void handleEntries(List<CanalEntry.Entry> entries) throws Exception {
        for (CanalEntry.Entry entry : entries) {
            if (entry.getEntryType() != CanalEntry.EntryType.ROWDATA) {
                continue;
            }
            CanalEntry.RowChange rowChange = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
            if (!"tb_shop".equalsIgnoreCase(entry.getHeader().getTableName())) {
                continue;
            }
            for (CanalEntry.RowData rowData : rowChange.getRowDatasList()) {
                Long shopId = findShopId(rowData);
                if (shopId != null) {
                    stringRedisTemplate.delete(CACHE_SHOP_KEY + shopId);
                    shopLocalCache.invalidate(shopId);
                    log.info("Invalidated shop cache by Canal. shopId={}", shopId);
                }
            }
        }
    }

    private Long findShopId(CanalEntry.RowData rowData) {
        Long id = findId(rowData.getAfterColumnsList());
        return id == null ? findId(rowData.getBeforeColumnsList()) : id;
    }

    private Long findId(List<CanalEntry.Column> columns) {
        for (CanalEntry.Column column : columns) {
            if ("id".equalsIgnoreCase(column.getName())) {
                return Long.valueOf(column.getValue());
            }
        }
        return null;
    }
}
