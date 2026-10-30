# TzFun 阶段1 · 模块6 任务卡：支付（mock）+ 异步回调幂等

> 交付顺序：第 6 张（依赖模块4 订单状态机、模块5 用券）
> 配套：总任务卡 `TzFun阶段1任务卡.md` §0 硬约束 + §6；规格书 v1.0 §7

## 目标
mock 支付网关，重点演示真实支付最难的"异步回调幂等"，并在支付成功时确认订单 + 用券。

## 表 `payment`
`id BIGINT PK, order_id BIGINT, out_trade_no VARCHAR(64) NOT NULL UNIQUE COMMENT '幂等键=订单号', amount DECIMAL(10,2), channel VARCHAR(20) DEFAULT 'mock', status VARCHAR(20) COMMENT '未支付/已支付/失败/退款', pay_time DATETIME`

## 接口
- `POST /pay/create?orderId=` → 生成支付单（status=未支付），返回 mock 支付页/二维码占位
- `POST /pay/notify` → 模拟网关异步回调（body 带 `out_trade_no` / `orderId` / `amount`）

## `PaymentService.handleNotify(dto)`
```java
Payment p = paymentMapper.selectByOutTradeNo(outTradeNo);
if (p.getStatus() == 已支付) return "SUCCESS";          // 幂等去重
int n = orderMapper.updateStatus(orderId, PENDING, CONFIRMED);
if (n == 1) {
    if (couponId != null) couponService.useCoupon(couponId, orderId); // WHERE status=0
    // 阶段1 同步发站内通知占位；阶段2 改 MQ 事件
}
paymentMapper.markPaid(outTradeNo);
return "SUCCESS";
```

## 硬约束（违反打回）
- `out_trade_no` 唯一 + "已支付直接 SUCCESS" = **回调幂等**（必做）
- 订单确认 `WHERE status=PENDING` 防重复确认
- 金额 DECIMAL，禁 double/float

## 验收点
- [ ] 重复回调不重复确认/记账
- [ ] 支付成功订单转 CONFIRMED 且券被用
- [ ] 未支付订单保持 PENDING（待超时释放，阶段2 做）
