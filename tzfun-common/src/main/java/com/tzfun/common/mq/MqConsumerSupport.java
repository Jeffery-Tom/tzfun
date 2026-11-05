package com.tzfun.common.mq;

import jakarta.annotation.PreDestroy;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;

/**
 * MQ 消费者基类：子类声明 group/topic + 实现 onMessage，这里统一管 start/shutdown 与 body 解码。
 * onMessage 抛异常视为消费失败，走 RocketMQ 重试，因此业务逻辑须自身幂等。
 */
public abstract class MqConsumerSupport {

    private static final Logger log = LoggerFactory.getLogger(MqConsumerSupport.class);

    @Value("${rocketmq.name-server}")
    private String nameServer;

    private DefaultMQPushConsumer consumer;

    /** 消费组名。 */
    protected abstract String group();

    /** 订阅的 Topic。 */
    protected abstract String topic();

    /** 消费一条消息，body 已按 UTF-8 解码。 */
    protected abstract void onMessage(String body) throws Exception;

    /** 子类在 @PostConstruct 里调 start() 启动。 */
    protected void start() throws MQClientException {
        consumer = new DefaultMQPushConsumer(group());
        consumer.setNamesrvAddr(nameServer);
        consumer.setVipChannelEnabled(false);
        consumer.subscribe(topic(), "*");
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt msg : msgs) {
                String body = new String(msg.getBody(), StandardCharsets.UTF_8);
                try {
                    onMessage(body);
                } catch (Exception e) {
                    log.error("MQ 消费失败 topic={} msgId={} body={}", msg.getTopic(), msg.getMsgId(), body, e);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();
        log.info("RocketMQ consumer 启动: group={} topic={}", group(), topic());
    }

    @PreDestroy
    public void shutdown() {
        if (consumer != null) {
            consumer.shutdown();
        }
    }
}
