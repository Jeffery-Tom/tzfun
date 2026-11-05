package com.tzfun.common.outbox;

import com.tzfun.common.mq.MqProducer;
import jakarta.annotation.Resource;

import java.util.List;

/**
 * Outbox 中继抽象基类：扫描未发送事件 → 逐条发 MQ → 成功标记已发。
 * 子类指定 topic；定时调度在模块C 的 order-service 里用 @Scheduled 驱动 relay()。
 */
public abstract class OutboxRelay {

    @Resource
    protected OutboxMapper outboxMapper;
    @Resource
    protected MqProducer mqProducer;

    /** 事件发送到的 MQ Topic。 */
    protected abstract String topic();

    /**
     * 中继一批未发送事件，返回成功发送条数。
     * 发送失败保持 status=0，留待下一轮重试；markSent 条件更新保证不重复标记。
     */
    public int relay() {
        List<OutboxEvent> events = outboxMapper.selectUnsent();
        int sent = 0;
        for (OutboxEvent e : events) {
            if (mqProducer.send(topic(), e.getPayload())) {
                if (outboxMapper.markSent(e.getId()) == 1) {
                    sent++;
                }
            }
        }
        return sent;
    }
}
