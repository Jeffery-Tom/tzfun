package com.tzfun.order.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 分布式锁：单机模式，地址走 @Value（Docker 环境变量可覆盖）。
 * 用 redisson 核心 + 手动 @Bean，不用 redisson-spring-boot-starter（规避 SB4 自动配置兼容风险）。
 */
@Configuration
public class RedissonConfig {

    @Value("${redisson.address:redis://localhost:6379}")
    private String address;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer().setAddress(address);
        return Redisson.create(config);
    }
}
