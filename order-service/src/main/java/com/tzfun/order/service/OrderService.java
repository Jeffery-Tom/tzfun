package com.tzfun.order.service;

import com.tzfun.common.CustomException;
import com.tzfun.common.Result;
import com.tzfun.common.UserContext;
import com.tzfun.common.mq.MqProducer;
import com.tzfun.common.outbox.OutboxEvent;
import com.tzfun.common.outbox.OutboxMapper;
import com.tzfun.order.dto.OrderDTO;
import com.tzfun.order.entity.Car;
import com.tzfun.order.entity.CarType;
import com.tzfun.order.entity.Order;
import com.tzfun.order.entity.OrderStatus;
import com.tzfun.order.feign.CarServiceClient;
import com.tzfun.order.feign.CouponServiceClient;
import com.tzfun.order.mapper.CarMapper;
import com.tzfun.order.mapper.CarTypeMapper;
import com.tzfun.order.mapper.OrderMapper;
import com.tzfun.order.mapper.StoreMapper;
import tools.jackson.databind.ObjectMapper;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 订单：下单链路 + 状态机 + 取还车。
 */
@Service
public class OrderService {

    private static final int FREE_MILEAGE = 200;
    private static final BigDecimal OVER_MILEAGE_FEE = new BigDecimal("2");
    private static final BigDecimal DAMAGE_FEE = new BigDecimal("200");

    /** 超时宽限分钟数。 */
    private static final int OVERTIME_GRACE_MINUTES = 60;
    /** 超时费率：按日租金的 1.5 倍折算。 */
    private static final BigDecimal OVERTIME_RATE = new BigDecimal("1.5");
    /** 超时超过该小时数加收违约金。 */
    private static final int OVERTIME_PENALTY_HOURS = 24;
    /** 违约金 = 3 天日租金。 */
    private static final BigDecimal PENALTY_DAYS = new BigDecimal("3");

    /** RocketMQ 延迟等级 15 = 20 分钟（4.x 固定 18 级，无精确 15 分钟档，精确释放靠定时任务兜底）。 */
    private static final int TIMEOUT_DELAY_LEVEL = 15;

    @Resource
    private OrderMapper orderMapper;
    @Resource
    private CarTypeMapper carTypeMapper;
    @Resource
    private StoreMapper storeMapper;
    @Resource
    private CarMapper carMapper;
    @Resource
    private CarServiceClient carServiceClient;
    @Resource
    private CouponServiceClient couponServiceClient;
    @Resource
    private MqProducer mqProducer;
    @Resource
    private OutboxMapper outboxMapper;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private RedissonClient redissonClient;

    @Value("${rocketmq.order.timeout-topic}")
    private String timeoutTopic;

    /** 下单：校验 -> 分布式锁防重复 -> 占库存(Feign car) -> 算钱 -> 落库；占库存后落库失败须 release 补偿。 */
    public Order createOrder(OrderDTO dto) {
        Long userId = UserContext.getUserId();
        if (!dto.getStartTime().isBefore(dto.getEndTime())) {
            throw new CustomException("400", "取车时间必须早于还车时间");
        }
        CarType type = carTypeMapper.selectById(dto.getCarTypeId());
        if (type == null) {
            throw new CustomException("404", "车型不存在");
        }
        if (storeMapper.selectById(dto.getStoreId()) == null) {
            throw new CustomException("404", "门店不存在");
        }

        // Redisson 锁：串行化同一用户下单，锁内查重，防并发重复下单
        // 不传 leaseTime，看门狗自动续期，避免锁内业务超时导致锁提前释放
        RLock lock = redissonClient.getLock("order:create:" + userId);
        boolean locked;
        try {
            locked = lock.tryLock(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CustomException("500", "系统繁忙，请重试");
        }
        if (!locked) {
            throw new CustomException("429", "操作过于频繁，请稍后重试");
        }
        try {
            // 锁内查重：同用户同车型、时间窗重叠的进行中订单
            if (!orderMapper.selectOverlap(userId, dto.getCarTypeId(), dto.getStartTime(), dto.getEndTime()).isEmpty()) {
                throw new CustomException("400", "该时段已有相同车型的订单");
            }

            LocalDate start = dto.getStartTime().toLocalDate();
            LocalDate end = dto.getEndTime().toLocalDate();
            // 原子占库存：Feign 调 car-service，car 内部 countAvailable + initSlot + Lua
            Result<Boolean> occupyR = carServiceClient.occupy(dto.getStoreId(), dto.getCarTypeId(), start, end);
            // 降级/熔断：下游不可用返回 500，与「无车」区分开
            if (occupyR == null || "500".equals(occupyR.getCode())) {
                throw new CustomException("500", "库存服务不可用，请稍后再试");
            }
            if (!"200".equals(occupyR.getCode()) || !Boolean.TRUE.equals(occupyR.getData())) {
                throw new CustomException("400", "该时段无车");
            }

            // 库存扣成功
            long days = ChronoUnit.DAYS.between(start, end) + 1;
            BigDecimal total = type.getDailyPrice().multiply(BigDecimal.valueOf(days));

            Order order = new Order();
            order.setOrderNo(genOrderNo());
            order.setUserId(userId);
            order.setStoreId(dto.getStoreId());
            order.setCarTypeId(dto.getCarTypeId());
            order.setStartTime(dto.getStartTime());
            order.setEndTime(dto.getEndTime());
            order.setStatus(OrderStatus.PENDING.name());
            order.setTotalAmount(total);
            order.setDiscountAmount(BigDecimal.ZERO);
            order.setPayAmount(total);

            // 失败补偿：先扣库存再落库，落库失败须 release 回滚
            try {
                orderMapper.insert(order);
            } catch (DuplicateKeyException e) {
                // 唯一索引兜底：锁失效时 DB 拦「同用户同车型同取车时间」重复
                carServiceClient.release(dto.getStoreId(), dto.getCarTypeId(), start, end);
                throw new CustomException("400", "请勿重复下单");
            } catch (Exception e) {
                carServiceClient.release(dto.getStoreId(), dto.getCarTypeId(), start, end);
                throw e;
            }
            // 超时未支付延迟释放
            mqProducer.send(timeoutTopic, String.valueOf(order.getId()), TIMEOUT_DELAY_LEVEL);
            return order;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 确认（mock 支付确认）：PENDING -> CONFIRMED。 */
    public void confirm(Long orderId) {
        getOwnedOrder(orderId);
        changeStatus(orderId, OrderStatus.PENDING, OrderStatus.CONFIRMED);
    }

    /** 查询订单（供 payment-service Feign 建支付单用）。 */
    public Order getOrderById(Long orderId) {
        return orderMapper.selectById(orderId);
    }

    /** 支付回调确认（供 payment-service Feign 用，无登录态）：金额比对 + 状态流转 + 用券回写折扣。
     *  状态流转与本地消息表同库同事务：要么都成要么都失败，事件由 OutboxRelay 异步中继到 MQ。 */
    @Transactional
    public void confirmPaid(Long orderId, BigDecimal amount, Long couponId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new CustomException("404", "订单不存在");
        }
        // 金额防篡改：对比订单应付额（权威值）
        if (amount == null || amount.compareTo(order.getPayAmount()) != 0) {
            throw new CustomException("400", "金额不符");
        }
        changeStatus(orderId, OrderStatus.PENDING, OrderStatus.CONFIRMED);
        insertOrderPaidEvent(order);
        if (couponId != null) {
            try {
                Result<BigDecimal> r = couponServiceClient.applyCoupon(couponId, orderId, order.getPayAmount());
                if (r != null && "200".equals(r.getCode()) && r.getData() != null) {
                    BigDecimal discount = r.getData();
                    BigDecimal newPay = order.getPayAmount().subtract(discount);
                    if (newPay.compareTo(BigDecimal.ZERO) < 0) {
                        newPay = BigDecimal.ZERO;
                    }
                    orderMapper.updateDiscount(orderId, discount, newPay, couponId);
                }
            } catch (Exception e) {
                // 券已用/不可用等，幂等忽略
            }
        }
    }

    /** 取车：分配在库车辆 + 回写取车信息，CONFIRMED -> IN_USE。 */
    public void pickup(Long orderId) {
        Order o = getOwnedOrder(orderId);
        if (!OrderStatus.CONFIRMED.name().equals(o.getStatus())) {
            throw new CustomException("400", "订单未确认，无法取车");
        }
        Car car = allocateCar(o.getStoreId(), o.getCarTypeId());
        // 推进状态  CONFIRMED→IN_USE
        try {
            changeStatus(orderId, OrderStatus.CONFIRMED, OrderStatus.IN_USE);
        } catch (CustomException e) {
            // 状态推进失败补偿，把刚 markRented 的车退回在库
            carMapper.markAvailable(car.getId());
            throw e;
        }
        orderMapper.fillPickup(orderId, car.getId(),
                "车牌:" + car.getPlateNo() + " 里程:0 油表:满 外观:完好");
    }

    /** 还车：算超里程/车损/超时 mock 费 + 回写还车信息，IN_USE -> RETURNED。 */
    public void returnCar(Long orderId, Integer mileage, Boolean damaged) {
        Order o = getOwnedOrder(orderId);
        if (!OrderStatus.IN_USE.name().equals(o.getStatus())) {
            throw new CustomException("400", "订单非使用中，无法还车");
        }
        // 算附加费（超里程 + 车损 + 超时/违约金）
        BigDecimal overtime = calcOvertime(o);
        BigDecimal extra = calcExtra(mileage, damaged).add(overtime);
        //  IN_USE→RETURNED
        changeStatus(orderId, OrderStatus.IN_USE, OrderStatus.RETURNED);
        //  还车 释放时间窗库存（Feign car）
        carServiceClient.release(o.getStoreId(), o.getCarTypeId(),
                o.getStartTime().toLocalDate(), o.getEndTime().toLocalDate());
        //  车从"出租中"变回"在库"
        if (o.getCarId() != null) {
            carMapper.markAvailable(o.getCarId());
        }
        // 写还车信息 + 追加费
        orderMapper.fillReturn(orderId,
                "里程:" + (mileage == null ? "-" : mileage) + " 车损:" + (Boolean.TRUE.equals(damaged) ? "是" : "否")
                        + " 超时费:" + overtime + " 额外费:" + extra,
                extra);
    }

    /** 结算：RETURNED -> SETTLED。 */
    public void settle(Long orderId) {
        getOwnedOrder(orderId);
        changeStatus(orderId, OrderStatus.RETURNED, OrderStatus.SETTLED);
    }

    /** 取消（登录用户手动）：PENDING/CONFIRMED -> CANCELLED，释放库存并退券。 */
    public void cancel(Long orderId) {
        doCancel(getOwnedOrder(orderId));
    }

    /** 超时未支付自动取消（MQ/定时任务，无登录态）：仅 PENDING 才处理，幂等。 */
    public void cancelTimeout(Long orderId) {
        Order o = orderMapper.selectById(orderId);
        if (o == null || !OrderStatus.PENDING.name().equals(o.getStatus())) {
            return;
        }
        try {
            doCancel(o);
        } catch (CustomException e) {
            // 并发下状态已被改（支付成功/已取消），条件更新冲突属正常，吞掉保证幂等
        }
    }

    /** 取消核心：状态流转 + 释放时间窗库存 + 退券（若已用券）。 */
    private void doCancel(Order o) {
        OrderStatus cur = OrderStatus.valueOf(o.getStatus());
        if (cur != OrderStatus.PENDING && cur != OrderStatus.CONFIRMED) {
            throw new CustomException("400", "当前状态不可取消");
        }
        changeStatus(o.getId(), cur, OrderStatus.CANCELLED);
        carServiceClient.release(o.getStoreId(), o.getCarTypeId(),
                o.getStartTime().toLocalDate(), o.getEndTime().toLocalDate());
        if (o.getCouponId() != null) {
            couponServiceClient.returnCoupon(o.getCouponId());
        }
    }

    /** 状态流转：先校验 ALLOWED，再条件更新；受影响行数=0 视为并发冲突。 */
    public void changeStatus(Long orderId, OrderStatus old, OrderStatus newStatus) {
        if (!old.canTransitTo(newStatus)) {
            throw new CustomException("400", "非法状态流转: " + old + " -> " + newStatus);
        }
        if (orderMapper.updateStatus(orderId, old.name(), newStatus.name()) != 1) {
            throw new CustomException("409", "订单状态已变更，请刷新重试");
        }
    }

    /** 归属校验：仅本人可操作自己的订单。 */
    private Order getOwnedOrder(Long orderId) {
        Order o = orderMapper.selectById(orderId);
        if (o == null) {
            throw new CustomException("404", "订单不存在");
        }
        if (!o.getUserId().equals(UserContext.getUserId())) {
            throw new CustomException("403", "无权操作他人订单");
        }
        return o;
    }

    /** 分配一辆在库车辆（条件更新防重复分配）。 */
    private Car allocateCar(Long storeId, Long carTypeId) {
        Car car = carMapper.selectAvailable(storeId, carTypeId);
        if (car == null) {
            throw new CustomException("409", "该门店该车型暂无在库车辆");
        }
        if (carMapper.markRented(car.getId()) != 1) {
            throw new CustomException("409", "车辆已被占用，请重试");
        }
        return car;
    }

    /** 超里程/车损 mock 费。 */
    private BigDecimal calcExtra(Integer mileage, Boolean damaged) {
        BigDecimal extra = BigDecimal.ZERO;
        if (mileage != null && mileage > FREE_MILEAGE) {
            extra = extra.add(OVER_MILEAGE_FEE.multiply(BigDecimal.valueOf(mileage - FREE_MILEAGE)));
        }
        if (Boolean.TRUE.equals(damaged)) {
            extra = extra.add(DAMAGE_FEE);
        }
        return extra;
    }

    /** 超时费/违约金：晚于约定还车时间，宽限 60 分钟；超时按小时计（日租金 1.5 倍折算），超 24h 加违约金。 */
    private BigDecimal calcOvertime(Order o) {
        LocalDateTime now = LocalDateTime.now();
        if (!now.isAfter(o.getEndTime())) {
            return BigDecimal.ZERO;
        }
        long minutes = ChronoUnit.MINUTES.between(o.getEndTime(), now);
        if (minutes <= OVERTIME_GRACE_MINUTES) {
            return BigDecimal.ZERO;
        }
        long overtimeHours = (long) Math.ceil((minutes - OVERTIME_GRACE_MINUTES) / 60.0);
        CarType type = carTypeMapper.selectById(o.getCarTypeId());
        BigDecimal dailyPrice = type == null ? BigDecimal.ZERO : type.getDailyPrice();
        // 超时费 = 日租金 * 1.5 * 小时数 / 24（保留 2 位，四舍五入）
        BigDecimal fee = dailyPrice.multiply(OVERTIME_RATE)
                .multiply(BigDecimal.valueOf(overtimeHours))
                .divide(BigDecimal.valueOf(24), 2, RoundingMode.HALF_UP);
        if (overtimeHours > OVERTIME_PENALTY_HOURS) {
            fee = fee.add(dailyPrice.multiply(PENALTY_DAYS));
        }
        return fee;
    }

    /** 插入订单支付成功事件（本地消息表，与订单状态同事务）。 */
    private void insertOrderPaidEvent(Order order) {
        OutboxEvent evt = new OutboxEvent();
        evt.setAggregateType("order");
        evt.setAggregateId(String.valueOf(order.getId()));
        evt.setEventType("ORDER_PAID");
        evt.setPayload(toPayloadJson(order));
        outboxMapper.insert(evt);
    }

    /** 事件载荷：消费端只依赖这些字段，orderId 即幂等键（aggregateId）。 */
    private String toPayloadJson(Order order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId());
        payload.put("orderNo", order.getOrderNo());
        payload.put("storeId", order.getStoreId());
        payload.put("carTypeId", order.getCarTypeId());
        payload.put("amount", order.getPayAmount());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new CustomException("500", "订单事件序列化失败");
        }
    }

    private String genOrderNo() {
        return "TZ" + System.currentTimeMillis() + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
