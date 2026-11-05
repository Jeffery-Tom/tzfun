package com.tzfun.payment.feign;

import com.tzfun.common.Result;
import com.tzfun.payment.entity.Order;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

/**
 * 调 order-service：查订单（建支付单）/ 支付确认（金额比对 + 状态流转 + 用券）。
 */
@FeignClient(name = "order-service")
public interface OrderServiceClient {

    @GetMapping("/internal/order/{orderId}")
    Result<Order> getOrder(@PathVariable("orderId") Long orderId);

    @PostMapping("/internal/order/confirmPaid")
    Result<Void> confirmPaid(@RequestParam("orderId") Long orderId,
                             @RequestParam("amount") BigDecimal amount,
                             @RequestParam(value = "couponId", required = false) Long couponId);
}
