package com.tzfun.order.service;

import com.tzfun.common.CustomException;
import com.tzfun.common.Result;
import com.tzfun.common.UserContext;
import com.tzfun.common.outbox.OutboxEvent;
import com.tzfun.common.outbox.OutboxMapper;
import com.tzfun.order.dto.OrderDTO;
import com.tzfun.order.entity.CarType;
import com.tzfun.order.entity.Order;
import com.tzfun.order.entity.OrderStatus;
import com.tzfun.order.entity.Store;
import com.tzfun.order.feign.CarServiceClient;
import com.tzfun.order.feign.CouponServiceClient;
import com.tzfun.order.mapper.CarMapper;
import com.tzfun.order.mapper.CarTypeMapper;
import com.tzfun.order.mapper.OrderMapper;
import com.tzfun.order.mapper.StoreMapper;
import com.tzfun.common.mq.MqProducer;
import com.tzfun.common.util.SnowflakeIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OrderService 单元测试：用 Mockito 注入依赖，不启动 Spring 上下文。
 * 覆盖：
 *  - 模块C 核心：confirmPaid 事务内写状态 + 本地消息表（Outbox）
 *  - 模块A：Redisson 分布式锁防重复下单（正常下单 / 同用户重叠订单被拒 / 锁必释放）
 *  - 模块B：超时计费/违约金（宽限内 0 / 超 24h 叠加违约金）
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
    @Mock RedissonClient redissonClient;
    @Mock RLock rLock;
    @Mock SnowflakeIdGenerator snowflakeIdGenerator;
    @InjectMocks OrderService orderService;

    private final ObjectMapper realMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws InterruptedException {
        MockitoAnnotations.openMocks(this);
        // toPayloadJson 需要真实的 ObjectMapper（Jackson 3）
        ReflectionTestUtils.setField(orderService, "objectMapper", realMapper);
        // 模块A：默认锁可获取、且由当前线程持有（便于 finally 释放校验）
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        // tryLock 声明了受检异常 InterruptedException，这里用 doReturn 形式并交由方法 throws 处理
        doReturn(true).when(rLock).tryLock(anyLong(), any(TimeUnit.class));
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        // 单元测试无 Spring，@Value 不会注入，手动给超时释放主题赋值，并 stub 三参 send 重载
        ReflectionTestUtils.setField(orderService, "timeoutTopic", "order-pay-timeout");
        when(mqProducer.send(any(), any(), anyInt())).thenReturn(true);
        when(snowflakeIdGenerator.nextId()).thenReturn(1000L);
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

    private OrderDTO orderDto(Long storeId, Long carTypeId, LocalDateTime start, LocalDateTime end) {
        OrderDTO dto = new OrderDTO();
        dto.setStoreId(storeId);
        dto.setCarTypeId(carTypeId);
        dto.setStartTime(start);
        dto.setEndTime(end);
        return dto;
    }

    // ---------------- 模块C：confirmPaid ----------------
    @Test
    void confirmPaid_success_insertsOutboxAndChangesStatus() {
        Order o = pendingOrder(1L);
        when(orderMapper.selectById(1L)).thenReturn(o);
        when(orderMapper.updateStatus(eq(1L), eq("PENDING"), eq("CONFIRMED"))).thenReturn(1);

        orderService.confirmPaid(1L, new BigDecimal("100"), null);

        verify(orderMapper).updateStatus(1L, "PENDING", "CONFIRMED");
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
        verify(outboxMapper, never()).insert(any());
    }

    @Test
    void confirmPaid_orderNotFound_throws() {
        when(orderMapper.selectById(99L)).thenReturn(null);
        assertThrows(CustomException.class, () -> orderService.confirmPaid(99L, BigDecimal.TEN, null));
    }

    @Test
    void changeStatus_conflict_throwsAndCallsUpdate() {
        when(orderMapper.updateStatus(eq(1L), eq("PENDING"), eq("CONFIRMED"))).thenReturn(0);
        assertThrows(CustomException.class,
                () -> orderService.changeStatus(1L, OrderStatus.PENDING, OrderStatus.CONFIRMED));
        verify(orderMapper).updateStatus(1L, "PENDING", "CONFIRMED");
    }

    // ---------------- 模块A：Redisson 防重复下单 ----------------
    @Test
    void createOrder_happyPath_insertsAndSendsMq() {
        UserContext.set(10L, "RENTER");
        OrderDTO dto = orderDto(1L, 1L,
                LocalDateTime.of(2026, 11, 20, 10, 0),
                LocalDateTime.of(2026, 11, 21, 10, 0)); // 1 晚：DAYS.between+1 = 2 天
        Store store = new Store(); store.setId(1L);
        CarType type = new CarType(); type.setId(1L); type.setDailyPrice(new BigDecimal("100"));

        when(storeMapper.selectById(1L)).thenReturn(store);
        when(carTypeMapper.selectById(1L)).thenReturn(type);
        when(orderMapper.selectOverlap(eq(10L), eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(carServiceClient.occupy(eq(1L), eq(1L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Result.success(true));
        when(mqProducer.send(any(), any())).thenReturn(true);

        Order o = orderService.createOrder(dto);

        assertNotNull(o);
        assertEquals(OrderStatus.PENDING.name(), o.getStatus());
        assertEquals(new BigDecimal("200"), o.getTotalAmount()); // 2 天 * 100
        // 锁内查重真的被调用
        verify(orderMapper).selectOverlap(eq(10L), eq(1L), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(carServiceClient).occupy(eq(1L), eq(1L), any(LocalDate.class), any(LocalDate.class));
        verify(orderMapper).insert(any(Order.class));
        verify(mqProducer).send(any(), any(), anyInt());
        // 无论成败 finally 都释放锁
        verify(rLock).unlock();
        UserContext.clear();
    }

    @Test
    void createOrder_duplicateOverlap_rejectedWithoutOccupyOrInsert() {
        UserContext.set(10L, "RENTER");
        OrderDTO dto = orderDto(1L, 1L,
                LocalDateTime.of(2026, 11, 20, 10, 0),
                LocalDateTime.of(2026, 11, 21, 10, 0));
        Store store = new Store(); store.setId(1L);
        CarType type = new CarType(); type.setId(1L); type.setDailyPrice(new BigDecimal("100"));
        when(storeMapper.selectById(1L)).thenReturn(store);
        when(carTypeMapper.selectById(1L)).thenReturn(type);
        // 锁内查重命中：同用户同车型时间窗重叠的进行中订单
        when(orderMapper.selectOverlap(eq(10L), eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(new Order()));

        CustomException ex = assertThrows(CustomException.class, () -> orderService.createOrder(dto));
        assertEquals("该时段已有相同车型的订单", ex.getMessage());
        // 被拒后绝不占库存、绝不落库
        verify(carServiceClient, never()).occupy(anyLong(), anyLong(), any(), any());
        verify(orderMapper, never()).insert(any());
        verify(rLock).unlock();
        UserContext.clear();
    }

    // ---------------- 模块B：超时计费/违约金 ----------------
    private BigDecimal invokeCalcOvertime(Order o) throws Exception {
        Method m = OrderService.class.getDeclaredMethod("calcOvertime", Order.class);
        m.setAccessible(true);
        return (BigDecimal) m.invoke(orderService, o);
    }

    @Test
    void calcOvertime_notYetDue_returnsZero() throws Exception {
        Order o = pendingOrder(1L);
        o.setEndTime(LocalDateTime.now().plusMinutes(10)); // 还没到还车时间
        o.setCarTypeId(1L);
        assertEquals(0, invokeCalcOvertime(o).compareTo(BigDecimal.ZERO));
    }

    @Test
    void calcOvertime_withinGrace_returnsZero() throws Exception {
        Order o = pendingOrder(1L);
        o.setEndTime(LocalDateTime.now().minusMinutes(30)); // 晚还 30 分钟，宽限 60 分钟内
        o.setCarTypeId(1L);
        assertEquals(0, invokeCalcOvertime(o).compareTo(BigDecimal.ZERO));
    }

    @Test
    void calcOvertime_beyond24h_appliesPenalty() throws Exception {
        Order o = pendingOrder(1L);
        o.setEndTime(LocalDateTime.now().minusHours(48)); // 晚还 48 小时
        o.setCarTypeId(1L);
        CarType type = new CarType(); type.setId(1L); type.setDailyPrice(new BigDecimal("100"));
        when(carTypeMapper.selectById(1L)).thenReturn(type);

        // 48h 超时：扣宽限 1h -> 47h；100*1.5*47/24=293.75；>24h 叠加 3 天=300 -> 593.75
        BigDecimal fee = invokeCalcOvertime(o);
        assertEquals(0, fee.compareTo(new BigDecimal("593.75")));
    }
}
