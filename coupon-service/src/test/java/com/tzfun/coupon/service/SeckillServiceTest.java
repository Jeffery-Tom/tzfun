package com.tzfun.coupon.service;

import com.tzfun.common.CustomException;
import com.tzfun.common.mq.MqProducer;
import com.tzfun.coupon.entity.CouponTemplate;
import com.tzfun.coupon.mapper.CouponTemplateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SeckillService 单元测试：Mockito 注入 Redis/MQ/Mapper，不启动 Spring 上下文。
 * 覆盖模块D 的核心：库存分段售罄、秒杀成功发 MQ、MQ 失败回滚预扣、不在秒杀时间抛异常。
 */
class SeckillServiceTest {

    @Mock StringRedisTemplate redis;
    @Mock CouponTemplateMapper couponTemplateMapper;
    @Mock MqProducer mqProducer;
    @Mock ValueOperations<String, String> valueOps;
    @InjectMocks SeckillService seckillService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private CouponTemplate validTemplate(int total) {
        CouponTemplate t = new CouponTemplate();
        t.setId(1L);
        t.setSeckill(1);
        t.setStatus(1);
        t.setTotalCount(total);
        t.setClaimStart(LocalDateTime.now().minusHours(1));
        t.setClaimEnd(LocalDateTime.now().plusHours(1));
        return t;
    }

    @Test
    void seckill_soldOut_throws() {
        when(couponTemplateMapper.selectById(1L)).thenReturn(validTemplate(100));
        // 所有分段预扣均返回 0（已空）
        when(redis.execute(any(RedisScript.class), anyList())).thenReturn(0L);

        CustomException ex = assertThrows(CustomException.class, () -> seckillService.seckill(1L, 100L));
        assertEquals("该券已被抢完", ex.getMessage());
        verify(mqProducer, never()).send(any(), any());
    }

    @Test
    void seckill_success_sendsMq() {
        when(couponTemplateMapper.selectById(1L)).thenReturn(validTemplate(100));
        // 第一段预扣成功返回 1
        when(redis.execute(any(RedisScript.class), anyList())).thenReturn(1L);
        when(mqProducer.send(any(), any())).thenReturn(true);

        seckillService.seckill(1L, 100L);

        // 选段 = userId.hashCode % 10 = 100 % 10 = 0；MQ body 格式 userId:templateId:seg
        String expectedPayload = "100:1:" + Math.floorMod(Long.hashCode(100L), 10);
        verify(mqProducer).send(any(), eq(expectedPayload));
    }

    @Test
    void seckill_mqFail_rollsBackStock() {
        when(couponTemplateMapper.selectById(1L)).thenReturn(validTemplate(100));
        when(redis.execute(any(RedisScript.class), anyList())).thenReturn(1L);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(mqProducer.send(any(), any())).thenReturn(false);

        CustomException ex = assertThrows(CustomException.class, () -> seckillService.seckill(1L, 100L));
        assertEquals("系统繁忙，请重试", ex.getMessage());
        // 预扣成功后 MQ 发送失败，必须按段回滚，避免库存被吞
        verify(redis).opsForValue();
        verify(valueOps).increment(contains("seckill:stock:1:"), eq(1L));
    }

    @Test
    void seckill_notInTime_throws() {
        CouponTemplate t = validTemplate(100);
        t.setClaimStart(LocalDateTime.now().plusHours(1));
        t.setClaimEnd(LocalDateTime.now().plusHours(2));
        when(couponTemplateMapper.selectById(1L)).thenReturn(t);
        CustomException ex = assertThrows(CustomException.class, () -> seckillService.seckill(1L, 100L));
        assertEquals("不在秒杀时间内", ex.getMessage());
    }
}
