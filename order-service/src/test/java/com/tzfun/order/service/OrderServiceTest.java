package com.tzfun.order.service;

import com.tzfun.common.CustomException;
import com.tzfun.common.outbox.OutboxEvent;
import com.tzfun.common.outbox.OutboxMapper;
import com.tzfun.order.entity.Order;
import com.tzfun.order.entity.OrderStatus;
import com.tzfun.order.feign.CarServiceClient;
import com.tzfun.order.feign.CouponServiceClient;
import com.tzfun.order.mapper.CarMapper;
import com.tzfun.order.mapper.CarTypeMapper;
import com.tzfun.order.mapper.OrderMapper;
import com.tzfun.order.mapper.StoreMapper;
import com.tzfun.common.mq.MqProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OrderService 单元测试：用 Mockito 注入依赖，不启动 Spring 上下文。
 * 重点覆盖模块C 的核心：confirmPaid 在事务内写订单状态 + 插本地消息表（Outbox）。
 */
class OrderServiceTest {

    @Mock OrderMapper orderMapper;
    @Mock CarTypeMapper carTypeMapper;
    @Mock StoreMapper storeMapper;
    @Mock CarMapper carMapper;
    @Mock CarServiceClient carServiceClient;
    @Mock CouponServiceClient couponServiceClient;
    @Mock MqProducer mqProducer;
    @Mock OutboxMapper outboxMapper;
    @InjectMocks OrderService orderService;

    private final ObjectMapper realMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // toPayloadJson 需要真实的 ObjectMapper（Jackson 3）
        ReflectionTestUtils.setField(orderService, "objectMapper", realMapper);
    }

    private Order pendingOrder(Long id) {
        Order o = new Order();
        o.setId(id);
        o.setOrderNo("TZ" + id);
        o.setUserId(10L);
        o.setStoreId(1L);
        o.setCarTypeId(1L);
        o.setStatus(OrderStatus.PENDING.name());
        o.setPayAmount(new BigDecimal("100"));
        return o;
    }

    @Test
    void confirmPaid_success_insertsOutboxAndChangesStatus() {
        Order o = pendingOrder(1L);
        when(orderMapper.selectById(1L)).thenReturn(o);
        when(orderMapper.updateStatus(eq(1L), eq("PENDING"), eq("CONFIRMED"))).thenReturn(1);

        orderService.confirmPaid(1L, new BigDecimal("100"), null);

        // 状态机：PENDING -> CONFIRMED
        verify(orderMapper).updateStatus(1L, "PENDING", "CONFIRMED");
        // 本地消息表：同事务插入 ORDER_PAID 事件，payload 含 orderId
        verify(outboxMapper).insert(argThat((OutboxEvent e) ->
                "ORDER_PAID".equals(e.getEventType())
                && "order".equals(e.getAggregateType())
                && "1".equals(e.getAggregateId())
                && e.getPayload() != null
                && e.getPayload().contains("\"orderId\":1")));
    }

    @Test
    void confirmPaid_amountMismatch_throws() {
        Order o = pendingOrder(1L);
        when(orderMapper.selectById(1L)).thenReturn(o);
        CustomException ex = assertThrows(CustomException.class,
                () -> orderService.confirmPaid(1L, new BigDecimal("50"), null));
        assertEquals("400", ex.getCode());
        // 金额不符时不应落本地消息表
        verify(outboxMapper, never()).insert(any());
    }

    @Test
    void confirmPaid_orderNotFound_throws() {
        when(orderMapper.selectById(99L)).thenReturn(null);
        assertThrows(CustomException.class, () -> orderService.confirmPaid(99L, BigDecimal.TEN, null));
    }

    @Test
    void changeStatus_conflict_throwsAndCallsUpdate() {
        // 状态合法流转被允许（updateStatus 被调用），但受影响行数=0（并发已被改）-> 抛冲突
        when(orderMapper.updateStatus(eq(1L), eq("PENDING"), eq("CONFIRMED"))).thenReturn(0);
        assertThrows(CustomException.class,
                () -> orderService.changeStatus(1L, OrderStatus.PENDING, OrderStatus.CONFIRMED));
        verify(orderMapper).updateStatus(1L, "PENDING", "CONFIRMED");
    }
}
