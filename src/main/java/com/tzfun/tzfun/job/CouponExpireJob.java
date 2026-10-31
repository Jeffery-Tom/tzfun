package com.tzfun.tzfun.job;

import com.tzfun.tzfun.mapper.UserCouponMapper;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 优惠券过期任务：定期把未用且已过期的券置为过期。
 */
@Component
public class CouponExpireJob {

    @Resource
    private UserCouponMapper userCouponMapper;

    /** 每小时扫一次。 */
    @Scheduled(fixedRate = 3600000)
    public void expire() {
        userCouponMapper.expireCoupons();
    }
}
