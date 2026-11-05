package com.tzfun.car.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzfun.car.service.RedisInventoryService;
import com.tzfun.common.mq.MqConsumerSupport;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 订单支付成功事件消费（库存最终核对）：
 * 以 orderId（=aggregateId）做 Redis SET NX 幂等键，重复投递只处理一次；
 * 首次触发 RedisInventoryService.rebuildFromDb，以 DB 订单表为真相重算该门店车型的库存槽位。
 */
@Component
public class OrderPaidConsumer extends MqConsumerSupport {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidConsumer.class);

    @Resource
    private StringRedisTemplate redis;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private RedisInventoryService redisInventoryService;

    @Override
    protected String group() {
        return "tzfun-car-order-paid";
    }

    @Override
    protected String topic() {
        return "order-paid";
    }

    @Override
    protected void onMessage(String body) throws Exception {
        JsonNode node = objectMapper.readTree(body);
        String orderId = node.path("orderId").asText();
        Long storeId = node.path("storeId").asLong();
        Long carTypeId = node.path("carTypeId").asLong();
        // 幂等去重：同一条事件重复投递，第二次 SET NX 失败直接忽略
        Boolean first = redis.opsForValue().setIfAbsent("order-paid:" + orderId, "1");
        if (!Boolean.TRUE.equals(first)) {
            log.info("订单支付事件重复投递，忽略: orderId={}", orderId);
            return;
        }
        redisInventoryService.rebuildFromDb(storeId, carTypeId);
        log.info("库存最终核对完成: orderId={} storeId={} carTypeId={}", orderId, storeId, carTypeId);
    }

    @PostConstruct
    public void init() throws Exception {
        super.start();
    }
}
