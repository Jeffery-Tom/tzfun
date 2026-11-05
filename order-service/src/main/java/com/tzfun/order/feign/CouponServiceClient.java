package com.tzfun.order.feign;

import com.tzfun.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

/**
 * 调 coupon-service：用券抵扣（返回折扣额）/ 退券。
 */
@FeignClient(name = "coupon-service")
public interface CouponServiceClient {

    @PostMapping("/internal/coupon/applyCoupon")
    Result<BigDecimal> applyCoupon(@RequestParam("userCouponId") Long userCouponId,
                                   @RequestParam("orderId") Long orderId,
                                   @RequestParam("orderAmount") BigDecimal orderAmount);

    @PostMapping("/internal/coupon/returnCoupon")
    Result<Void> returnCoupon(@RequestParam("userCouponId") Long userCouponId);
}
