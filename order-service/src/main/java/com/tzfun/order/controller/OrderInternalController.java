package com.tzfun.order.controller;

import com.tzfun.common.Result;
import com.tzfun.order.entity.Order;
import com.tzfun.order.service.OrderService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 服务间内部接口（供 payment-service Feign 调用，鉴权在网关已做、此处信任服务间头）。
 */
@RestController
@RequestMapping("/internal/order")
public class OrderInternalController {

    @Resource
    private OrderService orderService;

    @GetMapping("/{orderId}")
    public Result<Order> getOrder(@PathVariable Long orderId) {
        return Result.success(orderService.getOrderById(orderId));
    }

    @PostMapping("/confirmPaid")
    public Result<Void> confirmPaid(@RequestParam Long orderId,
                                    @RequestParam BigDecimal amount,
                                    @RequestParam(required = false) Long couponId) {
        orderService.confirmPaid(orderId, amount, couponId);
        return Result.success();
    }
}
