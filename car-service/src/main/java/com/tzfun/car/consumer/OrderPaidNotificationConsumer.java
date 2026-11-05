package com.tzfun.car.consumer;

import com.tzfun.common.mq.MqConsumerSupport;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 订单支付成功通知（占位消费者）：仅打印记录，演示多下游最终一致。
 * 副作用仅为日志，天然幂等。
 */
@Component
public class OrderPaidNotificationConsumer extends MqConsumerSupport {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidNotificationConsumer.class);

    @Override
    protected String group() {
        return "tzfun-order-paid-notification";
    }

    @Override
    protected String topic() {
        return "order-paid";
    }

    @Override
    protected void onMessage(String body) {
        log.info("[订单支付通知] 收到 order-paid 事件: {}", body);
    }

    @PostConstruct
    public void init() throws Exception {
        super.start();
    }
}
