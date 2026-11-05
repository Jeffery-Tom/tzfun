package com.tzfun.payment.service;

import com.tzfun.common.CustomException;
import com.tzfun.common.Result;
import com.tzfun.common.UserContext;
import com.tzfun.payment.dto.PayNotifyDTO;
import com.tzfun.payment.entity.Order;
import com.tzfun.payment.entity.Payment;
import com.tzfun.payment.feign.OrderServiceClient;
import com.tzfun.payment.mapper.PaymentMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 支付（mock）：创建支付单 + 异步回调幂等。
 */
@Service
public class PaymentService {

    @Resource
    private PaymentMapper paymentMapper;
    @Resource
    private OrderServiceClient orderServiceClient;

    /** 创建 mock 支付单：校验归属 + 状态 PENDING + 幂等复用已有未支付单。 */
    public String createPay(Long orderId) {
        Result<Order> r = orderServiceClient.getOrder(orderId);
        Order order = (r != null && "200".equals(r.getCode())) ? r.getData() : null;
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
        // 已支付直接 SUCCESS（幂等去重）
        if (p != null && "已支付".equals(p.getStatus())) {
            return "SUCCESS";
        }
        // 金额比对 + 订单确认 + 用券，统一由 order-service 完成（Feign）
        Result<Void> r = orderServiceClient.confirmPaid(dto.getOrderId(), dto.getAmount(), dto.getCouponId());
        if (r == null || !"200".equals(r.getCode())) {
            return "FAIL";
        }
        // 极端情况补建支付单后置为已支付
        if (p == null) {
            p = new Payment();
            p.setOrderId(dto.getOrderId());
            p.setOutTradeNo(dto.getOutTradeNo());
            p.setAmount(dto.getAmount());
            p.setChannel("mock");
            p.setStatus("未支付");
            paymentMapper.insert(p);
        }
        paymentMapper.markPaid(dto.getOutTradeNo());
        return "SUCCESS";
    }

    private String mockPage(Payment p) {
        return "mock支付页(模拟二维码) out_trade_no=" + p.getOutTradeNo() + " 应付=" + p.getAmount();
    }
}
