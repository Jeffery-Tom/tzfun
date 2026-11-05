package com.tzfun.order.controller;

import com.tzfun.common.Result;
import com.tzfun.order.dto.OrderDTO;
import com.tzfun.order.entity.Order;
import com.tzfun.order.service.OrderService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口。
 */
@RestController
@RequestMapping("/order")
public class OrderController {

    @Resource
    private OrderService orderService;

    /** 下单。 */
    @PostMapping
    public Result<Order> create(@Valid @RequestBody OrderDTO dto) {
        return Result.success(orderService.createOrder(dto));
    }

    /** 确认（mock 支付确认）。 */
    @PostMapping("/confirm")
    public Result<Void> confirm(@RequestParam Long orderId) {
        orderService.confirm(orderId);
        return Result.success();
    }

    /** 取车。 */
    @PostMapping("/pickup")
    public Result<Void> pickup(@RequestParam Long orderId) {
        orderService.pickup(orderId);
        return Result.success();
    }

    /** 还车。 */
    @PostMapping("/return")
    public Result<Void> returnCar(@RequestParam Long orderId,
                                  @RequestParam(required = false) Integer mileage,
                                  @RequestParam(required = false) Boolean damaged) {
        orderService.returnCar(orderId, mileage, damaged);
        return Result.success();
    }

    /** 结算。 */
    @PostMapping("/settle")
    public Result<Void> settle(@RequestParam Long orderId) {
        orderService.settle(orderId);
        return Result.success();
    }

    /** 取消。 */
    @PostMapping("/cancel")
    public Result<Void> cancel(@RequestParam Long orderId) {
        orderService.cancel(orderId);
        return Result.success();
    }
}
