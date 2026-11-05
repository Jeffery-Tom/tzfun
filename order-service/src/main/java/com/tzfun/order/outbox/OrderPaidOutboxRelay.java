package com.tzfun.order.outbox;

import com.tzfun.common.outbox.OutboxRelay;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 订单支付成功事件中继：每 5 秒扫描未发送的 outbox_event 并投递到 order-paid。
 * 发送失败保留 status=0 下轮重试（至少一次投递）；markSent 条件更新保证不重复标记。
 */
@Component
public class OrderPaidOutboxRelay extends OutboxRelay {

    @Override
    protected String topic() {
        return "order-paid";
    }

    @Scheduled(fixedDelay = 5000)
    public void schedule() {
        relay();
    }
}
