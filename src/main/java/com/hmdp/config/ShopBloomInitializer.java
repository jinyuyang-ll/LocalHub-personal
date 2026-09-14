package com.hmdp.config;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisBloomFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.SHOP_BLOOM_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_BLOOM_SIZE;
import static com.hmdp.utils.RedisConstants.SHOP_BLOOM_REBUILD_LOCK_KEY;

@Slf4j
@Component
public class ShopBloomInitializer implements ApplicationRunner {

    @Resource
    private IShopService shopService;

    @Resource
    private RedisBloomFilter redisBloomFilter;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private LocalHubMetrics metrics;

    @Override
    public void run(ApplicationArguments args) {
        rebuild();
    }

    @Scheduled(cron = "${localhub.cache.shop-bloom-rebuild-cron:0 0 4 * * *}")
    public void scheduledRebuild() {
        rebuild();
    }

    public void rebuild() {
        RLock lock = redissonClient.getLock(SHOP_BLOOM_REBUILD_LOCK_KEY);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(0, 5, TimeUnit.MINUTES);
            if (!acquired) return;
            List<Shop> shops = shopService.list();
            redisBloomFilter.rebuild(SHOP_BLOOM_KEY,
                    shops.stream().map(Shop::getId).collect(Collectors.toList()), SHOP_BLOOM_SIZE);
            metrics.setGauge("bloom.shop.entries", shops.size());
            log.info("Rebuilt shop bloom filter atomically, size={}", shops.size());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) lock.unlock();
        }
    }
}
