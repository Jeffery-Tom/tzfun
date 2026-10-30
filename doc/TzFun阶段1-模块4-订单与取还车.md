# TzFun 阶段1 · 模块4 任务卡：订单状态机 + 下单链路 + 取还车

> 交付顺序：第 4 张（依赖模块1 用户、模块3 占/释库存）
> 配套：总任务卡 `TzFun阶段1任务卡.md` §0 硬约束 + §4 + §7；规格书 v1.0 §5.3/§6

## 目标
把下单、状态机、取还车串起来；下单时调模块3 的 `occupy`，失败/取消做 `release` 补偿。

## 表 `order`
`id BIGINT PK, order_no VARCHAR(50) NOT NULL UNIQUE, user_id BIGINT, store_id BIGINT, car_type_id BIGINT, start_time DATETIME, end_time DATETIME, status VARCHAR(20), total_amount DECIMAL(10,2), discount_amount DECIMAL(10,2), pay_amount DECIMAL(10,2), coupon_id BIGINT, create_time DATETIME, update_time DATETIME, pickup_info VARCHAR(500), return_info VARCHAR(500)`

## 状态枚举 `OrderStatus`
`PENDING / CONFIRMED / IN_USE / RETURNED / SETTLED / CANCELLED`
合法流转 ALLOWED：
```
PENDING  → {CONFIRMED, CANCELLED}
CONFIRMED→ {IN_USE,    CANCELLED}
IN_USE   → {RETURNED}
RETURNED → {SETTLED}
```

## 下单 `OrderService.createOrder(dto)` 链路
1. 校验时间窗/车型存在
2. `occupy()` 占库存 → 失败抛"该时段无车"
3. `total_amount = daily_price × 天数`
4. `INSERT order(status=PENDING)` + 生成 `order_no`
5. 占库存/落库失败 → **`release()` 补偿**

## 状态变更 `changeStatus(orderId, old, new)`
- 先查 ALLOWED 合法 → `UPDATE order SET status=#{new} WHERE id=#{id} AND status=#{old}` → 行数=0 抛并发冲突

## 取还车（mock，与状态机联动）
- `POST /order/pickup`：分配具体 car（store+type 下 status='在库' 取一辆），填 `pickup_info`（里程/油表/外观 mock），status → IN_USE
- `POST /order/return`：填 `return_info`，算额外费（超里程/车损 mock），status → RETURNED
- `POST /order/settle`：结清，status → SETTLED
- `POST /order/cancel`：PENDING → CANCELLED + `release()`
- 提前还车：`release()` 剩余日期；续租：再 `occupy()` 新增日期（阶段1 可简化并标 TODO）

## 硬约束（违反打回）
- 状态流转必须 ALLOWED 校验 + `WHERE status=old`（禁止直接 `SET status=?` 不带旧值）
- 金额全 DECIMAL，禁 double/float
- 占库存与释放必须成对（下单占，取消/超时/提前还释放）

## 验收点
- [ ] 下单成功占库存，失败不占
- [ ] 状态机 ALLOWED + 条件更新，非法跳步被拒
- [ ] 取消/提前还车触发 release
- [ ] 金额全 DECIMAL
