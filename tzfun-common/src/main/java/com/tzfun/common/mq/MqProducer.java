package com.tzfun.common.mq;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * MQ 发送封装：普通消息 + 延迟消息，复用同一个 Producer。
 * 发送失败只记日志、返回 false（不抛异常），由调用方决定是否补偿：
 * 超时消息失败有定时任务兜底；秒杀落库失败需调用方回滚 Redis 预扣。
 */
@Component
public class MqProducer {

    private static final Logger log = LoggerFactory.getLogger(MqProducer.class);

    @Value("${rocketmq.name-server}")
    private String nameServer;
    @Value("${rocketmq.producer-group}")
    private String producerGroup;

    private DefaultMQProducer producer;

    @PostConstruct
    public void init() throws MQClientException {
        producer = new DefaultMQProducer(producerGroup);
        producer.setNamesrvAddr(nameServer);
        producer.setVipChannelEnabled(false);
        producer.start();
        log.info("RocketMQ producer 启动: group={}, namesrv={}", producerGroup, nameServer);
    }

    @PreDestroy
    public void destroy() {
        if (producer != null) {
            producer.shutdown();
        }
    }

    /** 发普通消息。 */
    public boolean send(String topic, String body) {
        return send(topic, body, 0);
    }

    /** 发延迟消息，delayLevel 为 RocketMQ 固定等级（如 15 = 20 分钟）。 */
    public boolean send(String topic, String body, int delayLevel) {
        try {
            Message msg = new Message(topic, body.getBytes(StandardCharsets.UTF_8));
            if (delayLevel > 0) {
                msg.setDelayTimeLevel(delayLevel);
            }
            producer.send(msg);
            return true;
        } catch (Exception e) {
            log.error("MQ 发送失败 topic={} body={}", topic, body, e);
            return false;
        }
    }
}
