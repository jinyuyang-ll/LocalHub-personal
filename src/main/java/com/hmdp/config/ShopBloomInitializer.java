package com.hmdp.config;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisBloomFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.SHOP_BLOOM_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_BLOOM_SIZE;

@Slf4j
@Component
public class ShopBloomInitializer implements ApplicationRunner {

    @Resource
    private IShopService shopService;

    @Resource
    private RedisBloomFilter redisBloomFilter;

    @Override
    public void run(ApplicationArguments args) {
        List<Shop> shops = shopService.list();
        for (Shop shop : shops) {
            redisBloomFilter.put(SHOP_BLOOM_KEY, shop.getId(), SHOP_BLOOM_SIZE);
        }
        log.info("Initialized shop bloom filter, size={}", shops.size());
    }
}
