-- 本地消息表（Outbox）：订单支付成功后，事件与业务数据同库同事务落表，再由 OutboxRelay 异步中继到 MQ
CREATE TABLE IF NOT EXISTS outbox_event (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_type VARCHAR(64)  NOT NULL COMMENT '聚合类型，如 order',
    aggregate_id   VARCHAR(64)  NOT NULL COMMENT '聚合ID，如订单ID',
    event_type     VARCHAR(64)  NOT NULL COMMENT '事件类型，如 order-paid',
    payload        JSON         NOT NULL COMMENT '事件内容（JSON）',
    status         TINYINT      NOT NULL DEFAULT 0 COMMENT '0未发送 1已发送',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_time      DATETIME     NULL,
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '本地消息表';
