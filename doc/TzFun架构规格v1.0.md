# TzFun 租车平台 · 架构规格 v1.0（已冻结）

> 状态：✅ v1.0 已冻结（2026-10-29），用户评审通过，即日起交 Claude Code（CC）按本文档实现。
> 角色分工：**CC 负责写代码**；**用户 + 本 AI 负责定架构、出规格、验收与面试话术**。
> 本文档是“实现契约 + 验收清单”二合一，CC 实现时逐条对照，用户验收时逐条打钩。
> ⚠️ 冻结后任何业务/技术取舍变更，**必须先改本文档再改代码**，并升版本号（v1.0 → v1.1 等）。

---

## 0. 项目定位与目标

- **模式**：自营车队（平台自有车 + 门店），MVP 不做 P2P。
- **目标**：做一个**业务闭环完整 + 有 3~5 个技术深度点**的简历项目，避免“CRUD 玩具”或“为用中间件而用中间件”。
- **非目标（mock 掉，不接真实服务）**：真实支付网关（支付宝/微信）、实名认证的政府接口、真实地图/定位、真实短信。这些用 mock 或留接口即可，重点是**流程与一致性**。
- **核心叙事**：租车库存是“车 × 时间段”的二维库存（区别于电商一维超卖），用 Redis+Lua 防重复占车；优惠/订单/支付侧用统一的一致性套路（条件更新 + 受影响行数 + 补偿）。

---

## 1. 业务域与功能清单

### 1.1 MVP（必做，优先做完）
| 模块 | 功能 | 说明 |
|---|---|---|
| 用户/认证 | 注册登录、角色（租客 RENTER / 管理员 ADMIN）、JWT、基础鉴权 | 驾照/身份证可 mock 字段；客服功能后置为 AI 客服（见 §1.3） |
| 车辆 + 门店 | 门店（城市/地址）、车型（品牌/座位/变速箱/日租价/图片）、车辆（车牌/所属门店/车型） | 车辆可 MVP 仅在取车时分配 |
| 选车 | 按 城市 + 时间窗 + 门店 + 车型 + 价格 筛选**可用车辆** | 核心：时间窗可用性 |
| 预订 / 订单 | 下单 → 占时间窗库存 → 支付(mock) → 状态机 | 见 §5.3 / §5.4 |
| 优惠券系统 | 领券 + 卡包 + 下单抵扣 + 防超领 + 防复用 | 见 §5.2 |
| 取还车 | 取车/还车流程（验车单 mock）、费用结算 | mock 为主 |
| 限流/基础防护 | 网关/接口层基础限流 | 为阶段2 Sentinel 铺垫 |

### 1.2 阶段2（高并发亮点，引入 RocketMQ）
- 订单异步化 / 削峰（通知、积分、账单解耦）
- **超时未支付释放库存**（MQ 延迟消息 / XXL-JOB）——见 §5.5
- **优惠券秒杀**（节假日用车券）：Redis 预扣 + Lua 防超卖 + Sentinel 限流 + MQ 异步落库

### 1.3 阶段末 / 补
- **AI 客服（独立模块，后续做）**：不依赖 WebSocket 实时聊天，复用 JobHub 的 RAG 思路（Python + 向量检索 + 生成）做“问答式智能客服”，沉淀租车 FAQ / 规则知识库。与现有 AI 服务（用车券检索）共用同一套技术底座。

### 1.4 可选 / 阶段3
- ES 车辆检索（MVP 用 MySQL 过滤即可；ES 仅作“检索深度”亮点或写进演进方向）
- SpringCloud 拆分（Gateway / Nacos / OpenFeign / Seata 分布式事务）
- P2P 车主端（对标 Turo/Getaround，差异化加分项，难度高）

---

## 2. 技术栈总览（贴在业务上，不硬塞）

| 技术 | 用在哪 | 为什么（业务驱动） | 阶段 |
|---|---|---|---|
| SpringBoot + MyBatis + MySQL | 订单/车辆/用户 CRUD + 事务 | 业务底座 | MVP |
| **Redis** | ① 车辆时间窗库存（Lua 原子占/释）② 优惠券库存计数 ③ Redisson 分布式锁（防重复提交）④ 会话/热门车缓存 | 租车二维库存一致性的核心 | MVP |
| **RocketMQ** | 订单异步化、超时释放库存（延迟消息）、秒杀落库 | 异步/削峰/最终一致性补偿 | 阶段2 |
| **SpringCloud** | Gateway 鉴权入口、Nacos 注册/配置、OpenFeign 调用、Sentinel 限流、Seata 分布式事务 | 服务化与高可用，复杂度高，**别一上来就上** | 阶段3 |
| **AI 客服（Python RAG）** | 问答式智能客服（租车 FAQ / 规则知识库） | 复用 JobHub 的 RAG 底座，业务刚需（客服），非硬凑；不依赖 WebSocket 实时聊天 | 补（阶段末） |
| XXL-JOB | 订单超时扫描兜底、优惠券过期 | 定时补偿，比纯 MQ 延迟更稳的兜底 | 阶段2 |
| Sentinel | 秒杀/接口限流降级 | 高并发保护 | 阶段2 |
| ES（可选） | 车辆复杂检索 | 仅检索深度亮点；MVP 不需 | 可选 |

> 原则：**每一项技术必须能回答“用在哪个真实业务场景、解决什么问题”**。禁止出现“为了写简历而堆”的中间件。

---

## 3. 领域模型与核心表结构初稿

> 字段为初稿，CC 实现时可微调，但核心约束（唯一索引、状态字段、金额用 DECIMAL/BIGINT 分）不得省。

### 3.1 `user`（用户）
`id, username, password, role(RENTER/ADMIN), name, phone, license_no(驾照,mock), idcard_no(身份证,mock), avatar, create_time`

### 3.2 `store`（门店）
`id, name, city, address, lng, lat(可选)`

### 3.3 `car_type`（车型）
`id, name, brand, seats, transmission(自动/手动), daily_price, image`

### 3.4 `car`（具体车辆，MVP 可仅取车时分配）
`id, store_id, car_type_id, plate_no, status(在库/出租中/维护)`

### 3.5 `order`（订单）——核心表
`id, order_no(唯一), user_id, store_id, car_type_id, start_time, end_time, status, total_amount, discount_amount, pay_amount, coupon_id, create_time, update_time, pickup_info, return_info`
- `status` 取值见 §5.3（PENDING/CONFIRMED/IN_USE/RETURNED/SETTLED/CANCELLED）
- `total/discount/pay_amount` 一律 **DECIMAL**（不要用 float/double）

### 3.6 `payment`（支付）
`id, order_id, out_trade_no(唯一, 幂等键=订单号), amount, channel(mock), status(未支付/已支付/失败/退款), pay_time`
- `out_trade_no` **唯一索引**，防重复记账

### 3.7 `coupon_template`（券模板）
`id, name, type(满减/折扣/立减), value, min_spend, max_discount, scope_city, scope_store, scope_type, total_count, claimed_count, per_user_limit, claim_start, claim_end, use_start, use_end, status`

### 3.8 `user_coupon`（用户券）
`id, user_id, template_id, status(未使用/已使用/已过期), use_order_id, expire_time, create_time`
- 当 `per_user_limit=1` 时加 **唯一索引 `(user_id, template_id)`**，天然限领

---

## 4. 核心难点一：时间窗库存（Redis + Lua）

### 4.1 问题本质
租车库存是 **(门店, 车型, 时间段)** 的二维可用性：某门店某车型有 N 辆车，某时间段被订 k 辆则剩 N−k。难点是并发下“检查该时间段各点都有空位 + 占掉一个”必须**原子**，否则重复占车（double booking）。

### 4.2 数据模型（按天分槽）
- Key：`inv:{storeId}:{carTypeId}:{date}` → 该门店该车型**这一天**剩余可用辆数（初始 = 该门店该车型车辆数 N）
- 一次预约 `[start, end]` 展开为覆盖的多个 `date` 槽位（跨天即多 key）

### 4.3 占用脚本（原子：检查+扣减合一）
```lua
-- KEYS: 预订覆盖的所有日期槽位 key
-- ARGV[1]: 需要占用数量（一般=1）
-- 返回 1=成功, 0=某槽位不足（冲突/超卖）
local need = tonumber(ARGV[1])
for i = 1, #KEYS do
    local cur = tonumber(redis.call('GET', KEYS[i]) or '0')
    if cur < need then return 0 end
end
for i = 1, #KEYS do
    redis.call('DECRBY', KEYS[i], need)
end
return 1
```

### 4.4 释放脚本（取消 / 超时未支付 / 提前还车）
```lua
-- KEYS: 槽位; ARGV[1]: 释放数量
local need = tonumber(ARGV[1])
for i = 1, #KEYS do
    redis.call('INCRBY', KEYS[i], need)
end
return 1
```

### 4.5 关键约束（CC 必须遵守）
- **Redis 是加速层，DB 订单表是真相源**：订单存真实占用（start/end/status），Redis 丢了我从 DB 重新聚合剩余数重建槽位。
- 槽位初始化：车辆/车型创建时 `SET key N NX`（不存在才设），或由定时任务从 DB 重建。
- 提前还车：`INCRBY` 释放剩余日期槽；续租：再跑一次 Lua 占新增日期。
- **防超卖/防重复占车靠 Redis+Lua，不是 MQ**。MQ 只做异步/削峰/超时释放（见 §5.5）。

---

## 5. 核心难点二：优惠券（防超领 + 防复用）

### 5.1 领取 + 防超领（MVP 用 DB 足够，不需 Redis）
```sql
UPDATE coupon_template
SET claimed_count = claimed_count + 1
WHERE id = ? AND claimed_count < total_count;   -- 条件卡住总量
```
- 执行后**受影响行数 = 1** 才说明领到名额；= 0 说明已领完。
- 每人限领：靠 `user_coupon` 唯一索引 `(user_id, template_id)`；插入重复 → 捕获 `DuplicateKeyException` → 提示已领取，并把 `claimed_count` 回退（或事务内先插后改保证一致）。
- 整段包 `@Transactional` 或用 `SELECT ... FOR UPDATE` 锁模板行串行化。
- **对比秒杀**：普通领券并发低 → DB 行锁/乐观锁够用；秒杀抢券才上 Redis+Lua+MQ。这是同一思路的低并发热身版。

### 5.2 下单抵扣 + 防重复使用
校验：券归属本人、`status=0`、未过期、订单可参与金额 ≥ `min_spend`、scope 匹配本单。
计算（**BigDecimal**，禁 float/double）：满减=`value`；折扣=`原价×(1−rate)` 封顶 `max_discount`；立减=`value`；抵扣不超过应付。
```sql
UPDATE user_coupon
SET status = 1, use_order_id = #{orderId}
WHERE id = #{couponId} AND status = 0;   -- 只有未使用才能改
```
- 受影响行数 = 1 才用券成功；= 0 说明并发下已被占用 → 失败重试。这是防“一张券被两单同时抵扣”的原子锁。
- 订单取消/超时 → 把券 `status` 退回 0、清 `use_order_id`（券回退）。
- **设计决策**：券在**支付成功(CONFIRMED)时**才标记已用，PENDING 取消时券原封不动，逻辑最干净。

---

## 6. 核心难点三：订单状态机

### 6.1 状态与流转
```
[*] → PENDING(待支付,已占库存)
PENDING → CONFIRMED(已确认/已支付) : 支付成功
PENDING → CANCELLED(已取消)        : 用户取消/超时未支付
CONFIRMED → IN_USE(进行中,已取车)   : 取车验车
CONFIRMED → CANCELLED              : 支付后取消(退款)
IN_USE → RETURNED(已还车)           : 还车验车
RETURNED → SETTLED(已结算)          : 结清尾款
CANCELLED / SETTLED → [*]
```

### 6.2 实现（与 JobHub 同款：合法表 + 条件更新）
```java
Map<Status, Set<Status>> ALLOWED = Map.of(
    PENDING,  Set.of(CONFIRMED, CANCELLED),
    CONFIRMED,Set.of(IN_USE, CANCELLED),
    IN_USE,   Set.of(RETURNED),
    RETURNED, Set.of(SETTLED)
);
// 变更：先校验在 ALLOWED 内，再
int n = orderMapper.updateStatus(id, oldStatus, newStatus);
// SQL: UPDATE order SET status=#{new} WHERE id=#{id} AND status=#{old}
if (n == 0) throw new CustomException("状态冲突，已被其他人修改");
```
`WHERE status=old` + 行数=1 = 防并发改单的原子锁（与占车/用券同一套路）。

---

## 7. 核心难点四：支付（mock）+ 异步回调幂等

- 不接真实网关；mock 重点演示**异步回调 + 幂等**（真实网关都是异步通知到账）。
- `payment.out_trade_no` 唯一；回调处理：
```java
Payment p = paymentMapper.selectByOutTradeNo(outTradeNo);
if (p.getStatus() == 已支付) return "SUCCESS";        // 已处理，直接去重
int n = orderMapper.updateStatus(orderId, PENDING, CONFIRMED);
if (n == 1) {
    couponMapper.useCoupon(couponId);                 // WHERE status=0 条件更新
    mq.sendOrderConfirmedEvent(orderId);              // 异步：通知/积分/账单
}
paymentMapper.markPaid(outTradeNo);
return "SUCCESS";
```
- `out_trade_no` 唯一 + “已支付直接返回 SUCCESS” = **回调幂等**；订单 `WHERE status=PENDING` = **防重复确认**。
- 失败/放弃：订单保持 PENDING，由超时任务处理。

---

## 8. 核心难点五：MQ 超时释放补偿

- `PENDING` 订单 15 分钟内未收到成功回调 → 自动 `CANCELLED` 并**释放 Redis 时间窗库存 + 退回券**。
- 实现：下单发 **MQ 延迟消息**（或 XXL-JOB 扫 `status=PENDING AND create_time < now-15min`）。
- **职责边界（面试必讲）**：MQ 不做扣减（那是 Lua 的活），MQ 做**异步通知、削峰、超时释放这种最终一致性补偿**。

---

## 9. 验收清单（用户审 CC 代码时逐条打钩）

### 9.1 时间窗库存
- [ ] Lua 脚本是否把“检查所有槽位 + 一次性扣减”写在一起（两步分开=竞态，打回）
- [ ] 失败/超时/取消/提前还车是否都有释放补偿
- [ ] Redis 与 DB 是否分离、能否从 DB 重建槽位
- [ ] 跨天预约是否展开为多个 date 槽位

### 9.2 优惠券
- [ ] 领券是否 `WHERE claimed_count < total_count` + 行数判断（漏=超发，打回）
- [ ] 每人限领是否靠唯一索引/计数，而非“先查再插”
- [ ] 抵扣计算是否 BigDecimal（看到 double/float 打回）
- [ ] 用券是否 `WHERE status=0` 条件更新（防复用）
- [ ] 取消/超时是否退回券

### 9.3 订单状态机
- [ ] 流转是否先校验 ALLOWED 再 `WHERE status=old` 条件更新 + 行数判断
- [ ] 是否禁止非法跳步/回头（如 SETTLED→PENDING）

### 9.4 支付
- [ ] 回调是否幂等（out_trade_no 唯一 + 重复回调直接 SUCCESS）
- [ ] 订单确认是否 `WHERE status=PENDING` 条件更新（防重复确认）
- [ ] 超时未支付是否释放库存 + 退券（补偿完整）

### 9.5 通用
- [ ] 金额字段是否 DECIMAL/BIGINT 分，无 float/double
- [ ] 所有“并发修改”是否走“条件更新 + 受影响行数=1”套路（统一一致性叙事）
- [ ] 是否有 mock 但流程完整的支付/实名/取还车，而非假装实现

---

## 10. 分阶段实施路线

1. **阶段1 单体跑通**：SpringBoot+MyBatis+MySQL+Redis。选车/预订/订单状态机/优惠券/取还车/基础鉴权。难点：时间窗库存 Lua、防重复下单。
2. **阶段2 引入 MQ + 营销**：RocketMQ 异步化、超时释放库存、用车券秒杀（Sentinel 限流）。
3. **阶段3 拆分 + 进阶**：Gateway/Nacos/OpenFeign/Seata/Sentinel；按需 ES/Canal/XXL-JOB。
4. **阶段4 差异化（可选）**：P2P 车主端 / 动态定价。

---

## 11. 面试话术要点（速记）

- **库存**：租车是“车×时间段”二维库存，用 Redis 按天分槽 + Lua 原子占/释，防并发重复占车；Redis 是加速层，DB 是真相源可重建。
- **优惠券**：发放用 `UPDATE ... WHERE claimed_count<total_count` + 行数卡总量（低并发 DB 够）；用券靠 `WHERE status=0` 条件更新防重复使用；金额 BigDecimal。
- **状态机**：合法流转表 + `UPDATE ... WHERE status=old` 条件更新防乱跳/并发改单。
- **支付**：mock 网关重点演示异步回调幂等（out_trade_no 唯一 + 已支付直接 SUCCESS）。
- **MQ**：只做异步/削峰/超时释放补偿，扣减原子性交给 Redis+Lua。
- **技术选型**：每一项都能讲清“用在哪个业务场景、解决什么问题”，不为堆而堆。

---

## 12. 风险与原则

1. **CC 易出玩具**：必须按本文档逐条实现与验收，禁止“能跑的 CRUD”。
2. **不为中间件而中间件**：ES / SpringCloud / 秒杀 都是“有真实场景才上”，MVP 用 MySQL + 单体即可。
3. **一致性叙事统一**：全项目用“条件更新 + 受影响行数 + 补偿”一套话术，面试好讲。
4. **mock 不等于偷工**：支付/实名/取还车要“流程完整、一致性真实”，仅外部依赖 mock。

---
*✅ v1.0 已冻结。即日起交 CC 开工，验收对照本文档逐条打钩。任何变更先改文档再改代码，并升版本号。*
