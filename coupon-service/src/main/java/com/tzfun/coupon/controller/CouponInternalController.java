package com.tzfun.coupon.controller;

import com.tzfun.common.Result;
import com.tzfun.coupon.service.CouponService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 优惠券服务间内部接口（供 order-service Feign 调用）。
 */
@RestController
@RequestMapping("/internal/coupon")
public class CouponInternalController {

    @Resource
    private CouponService couponService;

    @PostMapping("/applyCoupon")
    public Result<BigDecimal> applyCoupon(@RequestParam Long userCouponId,
                                          @RequestParam Long orderId,
                                          @RequestParam BigDecimal orderAmount) {
        return Result.success(couponService.applyCoupon(userCouponId, orderId, orderAmount));
    }

    @PostMapping("/returnCoupon")
    public Result<Void> returnCoupon(@RequestParam Long userCouponId) {
        couponService.returnCoupon(userCouponId);
        return Result.success();
    }
}
