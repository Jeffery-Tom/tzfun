package com.tzfun.tzfun.service;

import com.tzfun.tzfun.common.CustomException;
import com.tzfun.tzfun.common.mq.MqProducer;
import com.tzfun.tzfun.entity.CouponTemplate;
import com.tzfun.tzfun.mapper.CouponTemplateMapper;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 优惠券秒杀：Redis Lua 预扣（防超卖）+ MQ 异步落库（唯一索引兜底）。
 * 库存原子性在 Redis+Lua，MQ 只做异步/削峰；落库失败回滚预扣。
 */
@Service
public class SeckillService {

    @Resource
    private StringRedisTemplate redis;
    @Resource
    private CouponTemplateMapper couponTemplateMapper;
    @Resource
    private MqProducer mqProducer;

    @Value("${rocketmq.coupon.seckill-topic}")
    private String seckillTopic;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT = load();

    private static DefaultRedisScript<Long> load() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/seckill_stock.lua"));
        script.setResultType(Long.class);
        return script;
    }

    private String stockKey(Long templateId) {
        return "seckill:stock:" + templateId;
    }

    /** 秒杀入口：校验 -> Redis 预扣 -> 发 MQ 异步落库。返回成功仅代表已受理，最终结果以卡包为准。 */
    public void seckill(Long templateId, Long userId) {
        CouponTemplate t = couponTemplateMapper.selectById(templateId);
        if (t == null || t.getSeckill() == null || t.getSeckill() != 1
                || t.getStatus() == null || t.getStatus() != 1) {
            throw new CustomException("404", "秒杀券不存在或已下架");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(t.getClaimStart()) || now.isAfter(t.getClaimEnd())) {//活动时间开关
            throw new CustomException("400", "不在秒杀时间内");
        }
        // 初始化库存槽（不存在才设，值 = total_count）
        redis.opsForValue().setIfAbsent(stockKey(templateId), String.valueOf(t.getTotalCount()));
        // Lua 原子预扣：cur >= 1 才 DECRBY
        Long r = redis.execute(SECKILL_SCRIPT, List.of(stockKey(templateId)));
        if (r == null || r != 1L) {
            throw new CustomException("400", "该券已被抢完");
        }
        //  预扣成功，发 MQ 异步落库；发送失败回滚预扣，避免库存被吞
        //  生产者send:组装 Message：topic + body（`userId:templateId` 字符串）
        if (!mqProducer.send(seckillTopic, userId + ":" + templateId)) {
            rollbackStock(templateId);
            throw new CustomException("500", "系统繁忙，请重试");
        }
    }

    /** 回滚预扣：INCRBY +1（供发送失败/落库失败时退回）。 */
    public void rollbackStock(Long templateId) {
        redis.opsForValue().increment(stockKey(templateId), 1);
    }
}
