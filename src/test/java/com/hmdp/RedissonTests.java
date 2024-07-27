package com.hmdp;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Slf4j
@SpringBootTest(classes = HmDianPingApplication.class)
public class RedissonTests {
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private RedissonClient redissonClient2;
    @Resource
    private RedissonClient redissonClient3;

    private RLock rLock;
    @BeforeEach
    public void getRlock(){
//        rLock=redissonClient.getLock("lock");
        RLock lock1 = redissonClient.getLock("order");
        RLock lock2 = redissonClient2.getLock("order");
        RLock lock3 = redissonClient3.getLock("order");
        rLock = redissonClient.getMultiLock(lock1, lock2, lock3);
    }

    @Test
    public void method1() throws InterruptedException {
        boolean isLock = rLock.tryLock(1L, TimeUnit.SECONDS);
        if (!isLock){
            log.info("获取锁失败--1");
        }
        try {
            log.info("获取锁成功--1");
            method2();
            log.info("执行业务--1");
        }
        finally {
            log.info("准备释放锁--1");
            rLock.unlock();
        }
    }

    public void method2(){
        boolean isLock = rLock.tryLock();
        if (!isLock){
            log.info("获取锁失败--2");
        }
        try {
            log.info("获取锁成功--2");
            log.info("执行业务--2");
        }
        finally {
            log.info("准备释放锁--2");
            rLock.unlock();
        }
    }
}
