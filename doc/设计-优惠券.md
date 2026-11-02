# TzFun 阶段1 · 模块5 任务卡：优惠券（领 + 抵）

> 交付顺序：第 5 张（依赖模块4 下单抵扣、模块1 用户）
> 配套：总任务卡 `TzFun阶段1任务卡.md` §0 硬约束 + §5；规格书 v1.0 §5.1/§5.2

## 目标
普通优惠券系统：领券（防超领）+ 下单抵扣（防复用），为订单提供真实促销场景（区别于 JobHub 的空）。

## 表
- `coupon_template(id BIGINT PK, name VARCHAR(100), type VARCHAR(20) COMMENT '满减/折扣/立减', value DECIMAL(10,2), min_spend DECIMAL(10,2), max_discount DECIMAL(10,2), scope_city VARCHAR(50), scope_store BIGINT, scope_type BIGINT, total_count INT, claimed_count INT DEFAULT 0, per_user_limit INT, claim_start DATETIME, claim_end DATETIME, use_start DATETIME, use_end DATETIME, status TINYINT)`
- `user_coupon(id BIGINT PK, user_id BIGINT, template_id BIGINT, status TINYINT COMMENT '0未用/1已用/2过期', use_order_id BIGINT, expire_time DATETIME, create_time DATETIME)` + **`UNIQUE(user_id, template_id)`**（per_user_limit=1 时天然限领）

## 接口
- `GET /coupon/listTemplates` → 可领列表
- `POST /coupon/claim/{templateId}` → 登录用户领
- `GET /coupon/myCoupons` → 我的卡包（status=0）
- （抵扣在支付成功时由模块6 调 `useCoupon`）

## 关键方法 `CouponService`
- `claim(templateId, userId)`：事务内 `UPDATE coupon_template SET claimed_count=claimed_count+1 WHERE id=? AND claimed_count < total_count`（行数=0 抛"已领完"）；`INSERT user_coupon`（唯一索引拦重复领，捕获 `DuplicateKeyException` → 回退 claimed_count + 提示"已领取"）
- `calcDiscount(template, orderAmount) → BigDecimal`：满减=value；折扣=orderAmount×(1−rate) 封顶 max_discount；立减=value；不超 orderAmount
- `useCoupon(userCouponId, orderId)`：`UPDATE user_coupon SET status=1, use_order_id=? WHERE id=? AND status=0`（行数=0 抛"券不可用"）；**仅支付成功时调**
- `returnCoupon(userCouponId)`：取消/超时 `UPDATE user_coupon SET status=0, use_order_id=NULL WHERE id=? AND status=1`
- `CouponExpireJob`：Spring `@Scheduled` 扫 `expire_time < now AND status=0` → status=2

## 硬约束（违反打回）
- 领券必须有 `claimed_count < total_count` + 行数判断（漏 = 超发，打回）
- 限领靠唯一索引，禁"先查再插"
- 抵扣 BigDecimal，禁 double/float
- 用券 `WHERE status=0`

## 验收点
- [ ] 领券不超发（总量 + 每人限领）
- [ ] 抵扣计算 BigDecimal 正确
- [ ] 用券防复用（条件更新）
- [ ] 取消/超时退券
