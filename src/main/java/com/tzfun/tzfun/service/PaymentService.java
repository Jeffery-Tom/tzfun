package com.tzfun.tzfun.service;

import com.tzfun.tzfun.common.CustomException;
import com.tzfun.tzfun.common.UserContext;
import com.tzfun.tzfun.dto.PayNotifyDTO;
import com.tzfun.tzfun.entity.Order;
import com.tzfun.tzfun.entity.Payment;
import com.tzfun.tzfun.mapper.OrderMapper;
import com.tzfun.tzfun.mapper.PaymentMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

/**
 * 支付（mock）：创建支付单 + 异步回调幂等。
 */
@Service
public class PaymentService {

    @Resource
    private PaymentMapper paymentMapper;
    @Resource
    private OrderMapper orderMapper;
    @Resource
    private CouponService couponService;

    /** 创建 mock 支付单：校验归属 + 状态 PENDING + 幂等复用已有未支付单。 */
    public String createPay(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new CustomException("404", "订单不存在");
        }
        if (!order.getUserId().equals(UserContext.getUserId())) {
            throw new CustomException("403", "无权操作他人订单");
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new CustomException("400", "订单状态不可支付");
        }
        String outTradeNo = order.getOrderNo();
        Payment existing = paymentMapper.selectByOutTradeNo(outTradeNo);
        if (existing != null) {
            if ("已支付".equals(existing.getStatus())) {
                throw new CustomException("400", "订单已支付");
            }
            return mockPage(existing);
        }

        Payment p = new Payment();
        p.setOrderId(orderId);
        p.setOutTradeNo(outTradeNo);
        p.setAmount(order.getPayAmount());
        p.setChannel("mock");
        p.setStatus("未支付");
        paymentMapper.insert(p);
        return mockPage(p);
    }

    /** 异步回调：幂等处理，返回 SUCCESS/FAIL 供网关判断是否重试。 */
    public String handleNotify(PayNotifyDTO dto) {
        Payment p = paymentMapper.selectByOutTradeNo(dto.getOutTradeNo());
        if (p != null && "已支付".equals(p.getStatus())) {
            return "SUCCESS"; // 幂等去重：已处理过直接成功
        }

        Order order = orderMapper.selectById(dto.getOrderId());
        if (order == null) {
            return "FAIL";
        }
        // 金额防篡改：对比订单应付额（权威值），而非 dto 自证
        if (dto.getAmount() == null || dto.getAmount().compareTo(order.getPayAmount()) != 0) {
            return "FAIL";
        }

        if (p == null) {
            p = new Payment();
            p.setOrderId(dto.getOrderId());
            p.setOutTradeNo(dto.getOutTradeNo());
            p.setAmount(order.getPayAmount());
            p.setChannel("mock");
            p.setStatus("未支付");
            paymentMapper.insert(p);
        }

        // 防重复确认：条件更新，行数=0 说明已手动 confirm，属正常不报错
        orderMapper.updateStatus(dto.getOrderId(), "PENDING", "CONFIRMED");

        if (dto.getCouponId() != null) {
            try {
                BigDecimal discount = couponService.applyCoupon(dto.getCouponId(), dto.getOrderId(), order.getPayAmount());
                BigDecimal newPay = order.getPayAmount().subtract(discount);
                if (newPay.compareTo(BigDecimal.ZERO) < 0) {
                    newPay = BigDecimal.ZERO;
                }
                orderMapper.updateDiscount(dto.getOrderId(), discount, newPay, dto.getCouponId());
            } catch (CustomException e) {
                // 券已用（status!=0）属期望终态，幂等忽略
            }
        }

        paymentMapper.markPaid(dto.getOutTradeNo());
        return "SUCCESS";
    }

    private String mockPage(Payment p) {
        return "mock支付页(模拟二维码) out_trade_no=" + p.getOutTradeNo() + " 应付=" + p.getAmount();
    }
}
