package com.tzfun.order.consumer;

import com.tzfun.common.mq.MqConsumerSupport;
import com.tzfun.order.service.OrderService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 订单超时未支付：延迟消息到达后，若仍 PENDING 则取消并释放库存（复用 OrderService.cancelTimeout）。
 */
@Component
public class OrderTimeoutConsumer extends MqConsumerSupport {

    @Resource
    private OrderService orderService;

    @Value("${rocketmq.order.timeout-topic}")
    private String timeoutTopic;

    @Override
    protected String group() {
        return "tzfun-order-timeout-consumer";
    }

    @Override
    protected String topic() {
        return timeoutTopic;
    }

    @Override
    protected void onMessage(String body) {
        orderService.cancelTimeout(Long.valueOf(body));
    }

    @PostConstruct
    public void init() throws Exception {
        super.start();
    }
}
