package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.hmdp.utils.CacheClient;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
public class HmdpTests {
    @Resource
    private ShopServiceImpl shopService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private CacheClient cacheClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void saveShop() { // 店铺信息预热
        List<Shop> shopList = shopService.list();
        for (Shop shop : shopList) {
            cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + shop.getId(), shop, 30L, TimeUnit.MINUTES);
        }
    }

    @Test
    public void testIdWorker() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(500);
        CountDownLatch countDownLatch = new CountDownLatch(300); // 用于300个线程全部执行完毕后再执行后续代码
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                Long id = redisIdWorker.nextId("order");
                //println有同步锁 会增加耗时
                System.out.println("id:" + id);
            }
            countDownLatch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            executorService.submit(task);
        }
        countDownLatch.await();
        long end = System.currentTimeMillis();
        System.out.println("耗时：" + (end - begin));
    }
    @Test
    public void testLoadShopData() {
        // 查询店铺信息
        List<Shop> list = shopService.list();
        // 分组
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // 分批写入
        for (Map.Entry<Long, List<Shop>> longListEntry : map.entrySet()) {
            // 获取类型id
            Long typeId = longListEntry.getKey();
            // 获取店铺集合
            List<Shop> value = longListEntry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            // 写入redis
            String key = SHOP_GEO_KEY + typeId;
            /*
             * for (Shop shop : value) {
             * Double x = shop.getX();
             * Double y = shop.getY();
             * stringRedisTemplate.opsForGeo()
             * .add(key
             * , new Point(x, y)
             * , shop.getId().toString());
             * }
             */
            for (Shop shop : value) {
                locations.add(new RedisGeoCommands.GeoLocation<>(shop.getId().toString(),
                        new Point(shop.getX(), shop.getY()))); // 经纬度形成一个点，放到location对象中
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }

}
