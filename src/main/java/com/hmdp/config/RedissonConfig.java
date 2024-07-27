package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class RedissonConfig {
    @Value("${spring.redis.host}")
    private String host;
    @Value("${spring.redis.port}")
    private String port;
    @Value("${spring.redis.password}")
    private String password;
    @Bean
    public RedissonClient redissonClient(){
        //配置
        Config config=new Config();
        config.useSingleServer().setAddress("redis://"+host+":"+port).setPassword(password);
        //创建对并且返回
        return Redisson.create(config);
    }
    @Bean
    public RedissonClient redissonClient2(){
        //配置
        Config config=new Config();
        config.useSingleServer().setAddress("redis://"+host+":"+"6380").setPassword(password);
        //创建对并且返回
        return Redisson.create(config);
    }
    @Bean
    public RedissonClient redissonClient3(){
        //配置
        Config config=new Config();
        config.useSingleServer().setAddress("redis://"+host+":"+"6381").setPassword(password);
        //创建对并且返回
        return Redisson.create(config);
    }

}