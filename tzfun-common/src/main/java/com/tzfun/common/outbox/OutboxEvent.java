package com.tzfun.common.outbox;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 本地消息表实体：业务事务内与业务数据同库插入，事务提交后由 OutboxRelay 异步中继到 MQ。
 */
@Data
public class OutboxEvent {

    private Long id;
    /** 聚合类型，如 order */
    private String aggregateType;
    /** 聚合 ID，如订单 ID */
    private String aggregateId;
    /** 事件类型，如 order-paid */
    private String eventType;
    /** 事件内容（JSON 字符串） */
    private String payload;
    /** 0 未发送 / 1 已发送 */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime sentTime;
}
