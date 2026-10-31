package com.tzfun.tzfun.service;

import com.tzfun.tzfun.common.CustomException;
import com.tzfun.tzfun.common.UserContext;
import com.tzfun.tzfun.dto.OrderDTO;
import com.tzfun.tzfun.entity.Car;
import com.tzfun.tzfun.entity.CarType;
import com.tzfun.tzfun.entity.Order;
import com.tzfun.tzfun.entity.OrderStatus;
import com.tzfun.tzfun.mapper.CarMapper;
import com.tzfun.tzfun.mapper.CarTypeMapper;
import com.tzfun.tzfun.mapper.OrderMapper;
import com.tzfun.tzfun.mapper.StoreMapper;
import com.tzfun.tzfun.common.mq.MqProducer;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 订单：下单链路 + 状态机 + 取还车。
 */
@Service
public class OrderService {

    private static final int FREE_MILEAGE = 200;
    private static final BigDecimal OVER_MILEAGE_FEE = new BigDecimal("2");
    private static final BigDecimal DAMAGE_FEE = new BigDecimal("200");

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
    private RedisInventoryService inventoryService;
    @Resource
    private CouponService couponService;
    @Resource
    private MqProducer mqProducer;

    @Value("${rocketmq.order.timeout-topic}")
    private String timeoutTopic;

    /** 下单：校验 -> 占库存 -> 算钱 -> 落库；占库存后落库失败须 release 补偿。 */
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

        LocalDate start = dto.getStartTime().toLocalDate();
        LocalDate end = dto.getEndTime().toLocalDate();
        int n = carTypeMapper.countAvailable(dto.getStoreId(), dto.getCarTypeId());
        if (n <= 0) {
            throw new CustomException("400", "该门店该车型无可用车辆");
        }
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            inventoryService.initSlot(dto.getStoreId(), dto.getCarTypeId(), d, n);
        }
        if (!inventoryService.occupy(dto.getStoreId(), dto.getCarTypeId(), start, end)) {
            throw new CustomException("400", "该时段无车");
        }

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

        try {
            orderMapper.insert(order);
        } catch (Exception e) {
            inventoryService.release(dto.getStoreId(), dto.getCarTypeId(), start, end);
            throw e;
        }
        mqProducer.send(timeoutTopic, String.valueOf(order.getId()), TIMEOUT_DELAY_LEVEL);
        return order;
    }

    /** 确认（mock 支付确认）：PENDING -> CONFIRMED。 */
    public void confirm(Long orderId) {
        getOwnedOrder(orderId);
        changeStatus(orderId, OrderStatus.PENDING, OrderStatus.CONFIRMED);
    }

    /** 取车：分配在库车辆 + 回写取车信息，CONFIRMED -> IN_USE。 */
    public void pickup(Long orderId) {
        Order o = getOwnedOrder(orderId);
        if (!OrderStatus.CONFIRMED.name().equals(o.getStatus())) {
            throw new CustomException("400", "订单未确认，无法取车");
        }
        Car car = allocateCar(o.getStoreId(), o.getCarTypeId());
        try {
            changeStatus(orderId, OrderStatus.CONFIRMED, OrderStatus.IN_USE);
        } catch (CustomException e) {
            carMapper.markAvailable(car.getId());
            throw e;
        }
        orderMapper.fillPickup(orderId, car.getId(),
                "车牌:" + car.getPlateNo() + " 里程:0 油表:满 外观:完好");
    }

    /** 还车：算超里程/车损 mock 费 + 回写还车信息，IN_USE -> RETURNED。 */
    public void returnCar(Long orderId, Integer mileage, Boolean damaged) {
        Order o = getOwnedOrder(orderId);
        if (!OrderStatus.IN_USE.name().equals(o.getStatus())) {
            throw new CustomException("400", "订单非使用中，无法还车");
        }
        BigDecimal extra = calcExtra(mileage, damaged);
        changeStatus(orderId, OrderStatus.IN_USE, OrderStatus.RETURNED);
        inventoryService.release(o.getStoreId(), o.getCarTypeId(),
                o.getStartTime().toLocalDate(), o.getEndTime().toLocalDate());
        if (o.getCarId() != null) {
            carMapper.markAvailable(o.getCarId());
        }
        orderMapper.fillReturn(orderId,
                "里程:" + (mileage == null ? "-" : mileage) + " 车损:" + (Boolean.TRUE.equals(damaged) ? "是" : "否")
                        + " 额外费:" + extra,
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
        inventoryService.release(o.getStoreId(), o.getCarTypeId(),
                o.getStartTime().toLocalDate(), o.getEndTime().toLocalDate());
        if (o.getCouponId() != null) {
            couponService.returnCoupon(o.getCouponId());
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

    private String genOrderNo() {
        return "TZ" + System.currentTimeMillis() + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
