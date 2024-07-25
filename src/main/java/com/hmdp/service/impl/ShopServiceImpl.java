package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.hmdp.utils.RedisData;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Override
    public Result queryById(Long id) {
        // 空值缓存解决缓存穿透
        // Shop shop = queryWithPassThrough(id);

        // setnx分布式锁解决缓存击穿 + 空值缓存解决缓存穿透
        // Shop shop = queryWithMutex(id);

        // 逻辑过期解决缓存击穿
        // Shop shop = queryWithLogicalExpire(id);

        // redis封装类 + 逻辑过期解决缓存击穿
        Shop shop = cacheClient.queryWithLogicalExpire(
                CACHE_SHOP_KEY,
                id,
                Shop.class,
                this::getById, // id -> getById(id)
                20L,
                TimeUnit.SECONDS
        );

        if(shop == null) {
            return Result.fail("商铺不存在");
        }
        return Result.ok(shop);

    }
    private Shop queryWithLogicalExpire(Long id) { // 逻辑过期解决缓存击穿问题
        String shopKey = CACHE_SHOP_KEY + id;
        //从redis中查询
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        //判断是否存在
        if (StringUtils.isEmpty(shopJson)) { // 假设数据已经提前预热，不会为空
            //不存在返回空
            return null;
        }
        //命中 反序列化
        RedisData redisDate = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject jsonObject = (JSONObject) redisDate.getData();
        Shop shop = BeanUtil.toBean(jsonObject, Shop.class);
        LocalDateTime expireTime = redisDate.getExpireTime();
        //判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期 直接返回
            return shop;
        }
        //已过期
        //获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean flag = tryLock(lockKey);
        //是否获取锁成功
        if (flag) {
            //成功 异步重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    this.saveShopToRedis(id, 20L);
                } finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }
        //返回过期商铺信息
        return shop;
    }
    private Shop queryWithMutex(Long id) { // 利用setnx分布式锁解决缓存击穿问题
        String shopKey = CACHE_SHOP_KEY + id;
        //从redis中查询
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        //判断是否存在
        if (StringUtils.isNotEmpty(shopJson)) {
            //存在直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //判断空值
        if ("".equals(shopJson)) {
            return null;
        }
        //实现缓存重建
        //获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            //是否获取成功
            if (!isLock) {
                //获取失败 休眠并且重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //成功 通过id查询数据库
            shop = getById(id);
            //模拟重建延时
            Thread.sleep(200);
            if (shop == null) {
                //redis写入空值
                stringRedisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                //数据库不存在 返回错误
                return null;
            }
            //数据库存在 写入redis
            stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放互斥锁
            unLock(lockKey);
        }
        //返回
        return shop;
    }
    private Shop queryWithPassThrough(Long id) { // 空值缓存解决缓存穿透
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id); // 从redis中获取数据
        if(StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class); // 反序列化
        }

        if(shopJson != null) {
            return null; // 空值缓存(用于解决缓存穿透问题)
        }

        Shop shop = getById(id); // 从数据库中获取数据
        if(shop == null) {
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES); // 空值写入redis
            return null;
        }
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES); // 写入redis
        return shop;
    }

    @Override
    @Transactional(rollbackFor = Exception.class) // 更新数据库和删除缓存保证原子性
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id == null) {
            return Result.fail("商铺id不能为空");
        }
        updateById(shop); // 更新数据库
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id); // 删除redis缓存
        return Result.ok();
    }

    private boolean tryLock(String key) { // 用于解决缓存击穿问题
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key) { // 用于解决缓存击穿问题
        stringRedisTemplate.delete(key);
    }
    public void saveShopToRedis(Long id, Long expireSeconds){
        //查询店铺数据
        Shop shop = getById(id);
        //封装逻辑过期
        RedisData redisDate = new RedisData();
        redisDate.setData(shop);
        redisDate.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //写了redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisDate));
    }
}
