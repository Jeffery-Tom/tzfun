# TzFun 阶段1 任务卡（CC 实现指引 · 配套架构规格 v1.0）

> 用途：本卡是 `TzFun架构规格v1.0.md` 的"CC 友好版"。把本卡 + 规格书一起交给 Claude Code（CC）。  
> 规则：CC **严格按本卡实现，不得自行加需求 / 加表 / 加中间件**。实现完逐条对照规格书 §9 验收清单自检。  
> 范围：仅阶段1（单体 MVP）。RocketMQ / SpringCloud / ES / AI 客服 均不在本阶段。



---

## 0. 全局硬约束（CC 必守，违反即打回）

1. **金额字段**一律 `DECIMAL(10,2)` 或 `BIGINT` 分，**禁止 float/double**（看到直接打回）。
2. **并发修改统一套路**：条件更新 + 受影响行数=1（占车 / 用券 / 改单状态 / 领券计数），**禁止"先查再改"**。
3. **角色只有 `RENTER` / `ADMIN` 两个枚举**，无 SERVICE。
4. **仅 mock 外部依赖**（支付网关、实名、地图、短信）；内部流程与一致性必须真实。
5. 包结构：`com.tzfun.tzfun.{controller,service,mapper,entity,config,interceptor,util}`。
6. 统一返回 `Result{code,msg,data}`；异常走全局异常处理器 + 自定义异常 `CustomException`。
7. 不引入 SpringCloud / RocketMQ / ES（阶段2/3 才上）。

---

## 1. 用户与认证

### 表 `user`

`id, username(UNIQUE), password(BCrypt 加密), role(RENTER/ADMIN), name, phone, license_no(驾照,mock), idcard_no(身份证,mock), avatar, create_time`

### 接口

- `POST /user/register` —— 注册（默认 RENTER 角色，BCrypt 加密密码）
- `POST /user/login` —— 登录，返回 JWT
- `GET  /user/info`  —— 当前登录用户（从 Token 解析）

### 关键类/方法

- `JwtUtil.generateToken(userId, role)` / `parseToken(token)`
- `LoginInterceptor` + `WebConfig`：放行 `/user/login`、`/user/register`，其余校验 Token，解析后注入 `UserContext`（ThreadLocal 存 userId、role）
- 管理员接口（门店/车型/车辆/订单管理）内部校验 `role == ADMIN`，非管理员抛 403

---

## 2. 门店 / 车型 / 车辆

### 表

- `store(id, name, city, address, lng, lat)`
- `car_type(id, name, brand, seats, transmission(自动/手动), daily_price DECIMAL, image)`
- `car(id, store_id, car_type_id, plate_no, status(在库/出租中/维护))`  —— **MVP 仅取车时分配具体 car**

### 接口（RENTER 查，ADMIN 管）

- `GET  /store/list` · `POST /store`（admin）
- `GET  /carType/list` · `POST /carType`（admin）
- `GET  /car/list`（admin 查看车辆）

### 库存基数来源

- 某门店某车型可用车辆数 `N = SELECT COUNT(*) FROM car WHERE store_id=? AND car_type_id=? AND status='在库'`
- 供时间窗库存初始化（见 §3）

---

## 3. 选车 + 时间窗库存（核心，Redis + Lua）

### 库存模型

- Key：`inv:{storeId}:{carTypeId}:{date}` → 该门店该车型**该天**剩余可用辆数（初始 = N）
- 一次预约 `[start, end]` 展开为覆盖的日期列表（跨天 = 多个 key）

### Lua 脚本（务必"检查 + 扣减合一"，放 `resources/lua/`）

- `occupy.lua`（见规格 §4.3）：先遍历 KEYS 判 `cur >= need`，再统一 `DECRBY`；全过返回 1，否则 0
- `release.lua`（见规格 §4.4）：遍历 KEYS `INCRBY`

### 关键类 `RedisInventoryService`

- `boolean occupy(storeId, carTypeId, LocalDate start, LocalDate end)` —— 展开日期列表 → 调 occupy.lua → 返回是否成功
- `void release(storeId, carTypeId, start, end)` —— 调 release.lua
- `int getAvailable(storeId, carTypeId, LocalDate date)` —— GET 单日剩余（给选车列表用）
- `void initSlot(storeId, carTypeId, date, int n)` —— `SET key n NX`（不存在才设）
- `void rebuildFromDb(storeId, carTypeId)` —— 从 `order` 表聚合真实占用重算剩余（Redis 丢失 / 初始化兜底）

### 选车接口

- `POST /carType/available` 入参 `{city, storeId, carTypeId?, start, end, priceMin?, priceMax?}`  
  → 返回"该条件下所选时间段每天仍可用"的车型列表（用 `getAvailable` 过滤掉已满日期段）
- 注意：**选车只是查可用，不下单**；真正占库存在"下单"步骤（§4）。

---

## 4. 订单状态机 + 下单链路

### 表 `order`

`id, order_no(UNIQUE), user_id, store_id, car_type_id, start_time, end_time, status, total_amount DECIMAL, discount_amount DECIMAL, pay_amount DECIMAL, coupon_id, create_time, update_time, pickup_info, return_info`

### 状态枚举 `OrderStatus`

`PENDING / CONFIRMED / IN_USE / RETURNED / SETTLED / CANCELLED`

### 合法流转（ALLOWED）

```
PENDING  → {CONFIRMED, CANCELLED}
CONFIRMED→ {IN_USE,    CANCELLED}
IN_USE   → {RETURNED}
RETURNED → {SETTLED}
```

### 下单链路 `OrderService.createOrder(dto)`

1. 参数校验（时间窗合法、车型/门店存在）
2. `occupy()` 占时间窗库存 → 失败抛"该时段无车"
3. 算金额：`total_amount = daily_price × 天数`（券抵扣在支付成功时算，下单时只预估展示）
4. `INSERT order(status=PENDING)` + 生成 `order_no`（唯一）
5. 占库存失败或落库失败 → **补偿 `release()`**

### 状态变更 `OrderService.changeStatus(orderId, old, new)`

- 先查 `ALLOWED` 合法 → `UPDATE order SET status=#{new} WHERE id=#{id} AND status=#{old}` → 行数=0 抛并发冲突

### 取还车（mock，与状态机联动）

- `POST /order/pickup`：管理员/系统分配具体 `car`（从 store+type 下 status=在库 取一辆），填 `pickup_info`（里程/油表/外观 mock），status → IN_USE
- `POST /order/return`：填 `return_info`，算额外费（超里程/车损 mock），status → RETURNED
- `POST /order/settle`：结清尾款，status → SETTLED
- 提前还车：释放剩余日期槽 `release()`；续租：再 `occupy()` 新增日期（阶段1 可简化并标 TODO）

---

## 5. 优惠券（领 + 抵）

### 表

- `coupon_template(id, name, type(满减/折扣/立减), value DECIMAL, min_spend DECIMAL, max_discount DECIMAL, scope_city, scope_store, scope_type, total_count, claimed_count, per_user_limit, claim_start, claim_end, use_start, use_end, status)`
- `user_coupon(id, user_id, template_id, status(0未用/1已用/2过期), use_order_id, expire_time, create_time)` —— **`UNIQUE(user_id, template_id)`**（per_user_limit=1 时天然限领）

### 领券 `CouponService.claim(templateId, userId)`

- 事务内：`UPDATE coupon_template SET claimed_count=claimed_count+1 WHERE id=? AND claimed_count < total_count` → 行数=0 抛"已领完"
- `INSERT user_coupon`（唯一索引拦截重复领；捕获 `DuplicateKeyException` → 回退 `claimed_count` + 提示"已领取"）

### 抵扣计算 `CouponService.calcDiscount(template, orderAmount) → BigDecimal`

- 满减 = `value`；折扣 = `orderAmount × (1 − rate)` 封顶 `max_discount`；立减 = `value`；且不超过 `orderAmount`

### 用券 `CouponService.useCoupon(userCouponId, orderId)`

- `UPDATE user_coupon SET status=1, use_order_id=? WHERE id=? AND status=0` → 行数=0 抛"券不可用 / 已被使用"
- **仅在支付成功（CONFIRMED）时调用**

### 退券 `CouponService.returnCoupon(userCouponId)`

- 订单取消 / 超时：`UPDATE user_coupon SET status=0, use_order_id=NULL WHERE id=? AND status=1`

### 过期 `CouponExpireJob`（Spring `@Scheduled` 或 XXL-JOB）

- 扫 `expire_time < now AND status=0` → `status=2`

---

## 6. 支付 mock + 异步回调幂等

### 表 `payment`

`id, order_id, out_trade_no(UNIQUE, 幂等键=订单号), amount DECIMAL, channel='mock', status(未支付/已支付/失败/退款), pay_time`

### 接口

- `POST /pay/create(orderId)` —— 生成支付单（status=未支付），返回 mock 支付页/二维码占位
- `POST /pay/notify` —— 模拟网关异步回调（body 带 `out_trade_no` / `orderId` / `amount`）

### `PaymentService.handleNotify(dto)`

```java
Payment p = paymentMapper.selectByOutTradeNo(outTradeNo);
if (p.getStatus() == 已支付) return "SUCCESS";          // 幂等去重
int n = orderMapper.updateStatus(orderId, PENDING, CONFIRMED);
if (n == 1) {
    if (couponId != null) couponService.useCoupon(couponId, orderId); // WHERE status=0
    // 阶段2 才发 MQ 事件；阶段1 可同步发站内通知占位
}
paymentMapper.markPaid(outTradeNo);
return "SUCCESS";
```

- `out_trade_no` 唯一 + "已支付直接 SUCCESS" = **回调幂等**
- 订单 `WHERE status=PENDING` = **防重复确认**

---

## 7. CC 自检清单（实现完对照规格 §9）

- [ ] Lua 脚本"检查 + 扣减"写在一起（两步分开 = 竞态，打回）
- [ ] 金额字段全 `DECIMAL`，无 `float/double`
- [ ] 所有并发修改走"条件更新 + 受影响行数=1"
- [ ] 领券有 `claimed_count < total_count` + 行数判断
- [ ] 用券有 `WHERE status=0` 条件更新
- [ ] 支付回调幂等（`out_trade_no` 唯一 + 重复回调直接 SUCCESS）
- [ ] 角色仅 `RENTER` / `ADMIN`
- [ ] 订单状态机有 `ALLOWED` 表 + `WHERE status=old`
- [ ] 取消 / 超时 / 提前还车都有库存释放补偿

---

## 8. 模块拆分与交付顺序（子任务卡，逐个交给 CC）

总卡是"契约总纲"，实际派活按下面 6 张子卡**逐个**交给 CC，每完成一张验收过再下一张：

| 顺序 | 子卡文件                     | 模块                        | 依赖    |
| -- | ------------------------ | ------------------------- | ----- |
| 1  | `TzFun阶段1-模块1-用户认证.md`   | 用户认证 + JWT                | 无     |
| 2  | `TzFun阶段1-模块2-基础数据.md`   | 门店/车型/车辆 + availableCount | 模块1   |
| 3  | `TzFun阶段1-模块3-时间窗库存.md`  | 选车 + Redis+Lua 库存（核心）     | 模块2   |
| 4  | `TzFun阶段1-模块4-订单与取还车.md` | 订单状态机 + 下单 + 取还车          | 模块1,3 |
| 5  | `TzFun阶段1-模块5-优惠券.md`    | 领券 + 抵扣 + 防复用             | 模块1,4 |
| 6  | `TzFun阶段1-模块6-支付.md`     | 支付 mock + 回调幂等            | 模块4,5 |

> 每子卡自带：目标 / 表 / 接口 / 关键方法 / 硬约束 / 验收点，且都引用本总卡 §0 硬约束与规格书对应章节。  
> 验收方式：每子卡末尾"验收点"对照打钩；重点看 CC 是否违反 §0 硬约束（金额 DECIMAL、条件更新+行数、角色仅 RENTER/ADMIN）。

---

*本卡冻结后随规格 v1.0 一同交付 CC。任何变更先改规格书并升版本，再同步本卡与子卡。*
