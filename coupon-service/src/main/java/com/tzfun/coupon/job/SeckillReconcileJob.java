package com.tzfun.coupon.job;

import com.tzfun.coupon.entity.CouponTemplate;
import com.tzfun.coupon.mapper.CouponTemplateMapper;
import com.tzfun.coupon.service.SeckillService;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 秒杀落库对账：比对 Redis 已扣 vs DB 已发，兜底 MQ 丢失导致的库存被吞。
 * 只对「已结束 5 分钟以上」的券执行，避免把 MQ 在途消息误判为丢失。
 */
@Component
public class SeckillReconcileJob {

    @Resource
    private CouponTemplateMapper couponTemplateMapper;
    @Resource
    private SeckillService seckillService;

    /** 每分钟对账一次。 */
    @Scheduled(fixedRate = 60000)
    public void reconcile() {
        List<CouponTemplate> list = couponTemplateMapper.listSeckillEnded();
        for (CouponTemplate t : list) {
            seckillService.reconcileStock(t);
        }
    }
}
