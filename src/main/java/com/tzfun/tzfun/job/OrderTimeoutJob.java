package com.tzfun.tzfun.job;

import com.tzfun.tzfun.entity.Order;
import com.tzfun.tzfun.mapper.OrderMapper;
import com.tzfun.tzfun.service.OrderService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 订单超时兜底任务：每 5 分钟扫超时 PENDING 订单走 cancelTimeout，
 * 防 MQ 延迟消息丢失导致库存永不释放（双保险）。
 */
@Component
public class OrderTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutJob.class);

    @Resource
    private OrderMapper orderMapper;
    @Resource
    private OrderService orderService;

    @Value("${rocketmq.order.timeout-minutes}")
    private int timeoutMinutes;

    @Scheduled(fixedRate = 300000)//时间单位ms 5分钟
    public void scanTimeoutOrders() {
        // 查询出所有PENDING 且超时 N 分钟的订单
        List<Order> orders = orderMapper.selectTimeoutPending(timeoutMinutes);
        //  循环每一个超时的订单
        for (Order o : orders) {
            try {
                //  超时的定时取消
                //  调用取消订单方法：改状态、归还库存、发通知等
                orderService.cancelTimeout(o.getId());
            } catch (Exception e) {
                //  单个订单取消报错，只打日志，不抛异常、不中断循环
                log.error("超时取消订单失败 orderId={}", o.getId(), e);
            }
        }
    }
}
