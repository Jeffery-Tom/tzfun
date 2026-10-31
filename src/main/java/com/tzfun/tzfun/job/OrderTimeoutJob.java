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

    @Scheduled(fixedRate = 300000)
    public void scanTimeoutOrders() {
        List<Order> orders = orderMapper.selectTimeoutPending(timeoutMinutes);
        for (Order o : orders) {
            try {
                orderService.cancelTimeout(o.getId());
            } catch (Exception e) {
                log.error("超时取消订单失败 orderId={}", o.getId(), e);
            }
        }
    }
}
