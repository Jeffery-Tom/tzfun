package com.tzfun.coupon.job;

import com.tzfun.coupon.entity.CouponTemplate;
import com.tzfun.coupon.mapper.CouponTemplateMapper;
import com.tzfun.coupon.service.SeckillService;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 秒杀库存预热：提前初始化「即将开始」的秒杀券分段库存，消灭首请求冷启动。
 */
@Component
public class SeckillWarmupJob {

    @Resource
    private CouponTemplateMapper couponTemplateMapper;
    @Resource
    private SeckillService seckillService;

    /** 每 30s 扫描即将开始的秒杀券并预热（幂等，已初始化跳过）。 */
    @Scheduled(fixedRate = 30000)
    public void warmup() {
        List<CouponTemplate> list = couponTemplateMapper.listSeckillStartingSoon();
        for (CouponTemplate t : list) {
            if (t.getTotalCount() != null) {
                seckillService.warmupStock(t.getId(), t.getTotalCount());
            }
        }
    }
}
