package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisBloomFilter;
import com.hmdp.utils.SystemConstants;
import com.hmdp.config.LocalHubMetrics;
import cn.hutool.json.JSONUtil;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private Cache<Long, Shop> shopLocalCache;

    @Resource(name = "shopSearchLocalCache")
    private Cache<String, List<Shop>> shopSearchLocalCache;

    @Resource
    private LocalHubMetrics metrics;

    @Resource
    private RedisBloomFilter redisBloomFilter;

    @Override
    public Result queryById(Long id) {
        if (!redisBloomFilter.mightContain(SHOP_BLOOM_KEY, id, SHOP_BLOOM_SIZE)) {
            return Result.fail("店铺不存在！");
        }

        Shop localShop = shopLocalCache.getIfPresent(id);
        if (localShop != null) {
            return Result.ok(localShop);
        }

        // 解决缓存穿透
        Shop shop = cacheClient
                .queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        shopLocalCache.put(id, shop);
        // 7.返回
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 1.更新数据库
        updateById(shop);
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        shopLocalCache.invalidate(id);
        shopSearchLocalCache.invalidateAll();
        stringRedisTemplate.opsForValue().increment(CACHE_SHOP_SEARCH_VERSION_KEY);
        redisBloomFilter.put(SHOP_BLOOM_KEY, id, SHOP_BLOOM_SIZE);
        return Result.ok();
    }

    @Override
    public Result searchShops(String name, Integer current) {
        int pageNumber = current == null || current < 1 ? 1 : current;
        String normalizedName = StrUtil.blankToDefault(name, "").trim().toLowerCase(Locale.ROOT);
        String version = StrUtil.blankToDefault(stringRedisTemplate.opsForValue().get(CACHE_SHOP_SEARCH_VERSION_KEY), "0");
        String queryToken = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(normalizedName.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String cacheKey = version + ":" + pageNumber + ":" + queryToken;

        List<Shop> local = shopSearchLocalCache.getIfPresent(cacheKey);
        if (local != null) {
            metrics.increment("cache.shop.search", "caffeine_hit");
            return Result.ok(local);
        }

        String redisKey = CACHE_SHOP_SEARCH_KEY + cacheKey;
        String cached = stringRedisTemplate.opsForValue().get(redisKey);
        if (StrUtil.isNotBlank(cached)) {
            List<Shop> shops = JSONUtil.toList(JSONUtil.parseArray(cached), Shop.class);
            shopSearchLocalCache.put(cacheKey, shops);
            metrics.increment("cache.shop.search", "redis_hit");
            return Result.ok(shops);
        }

        List<Shop> shops = query()
                .like(StrUtil.isNotBlank(normalizedName), "name", normalizedName)
                .page(new Page<>(pageNumber, SystemConstants.MAX_PAGE_SIZE))
                .getRecords();
        stringRedisTemplate.opsForValue().set(redisKey, JSONUtil.toJsonStr(shops), 10, TimeUnit.MINUTES);
        shopSearchLocalCache.put(cacheKey, shops);
        metrics.increment("cache.shop.search", "db_fallback");
        return Result.ok(shops);
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 1.判断是否需要根据坐标查询
        if (x == null || y == null) {
            // 不需要坐标查询，按数据库查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }

        // 2.计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        // 3.查询redis、按照距离排序、分页。结果：shopId、distance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo() // GEOSEARCH key BYLONLAT x y BYRADIUS 10 WITHDISTANCE
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        // 4.解析出id
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            // 没有下一页了，结束
            return Result.ok(Collections.emptyList());
        }
        // 4.1.截取 from ~ end的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            // 4.2.获取店铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            // 4.3.获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        // 5.根据id查询Shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        // 6.返回
        return Result.ok(shops);
    }
}
