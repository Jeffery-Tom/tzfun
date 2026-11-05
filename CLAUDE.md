# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# TzFun 项目约定与关键决策

> 业务方向（已冻结）：**自营车队租车平台**（平台自有车 + 门店），非 P2P。MVP = 选车 / 订单 / 优惠券 / 支付(mock) / 取还车；AI 客服后置（复用 JobHub 的 RAG）。作者大三学生，目标「中小厂 Java 后端实习简历项目」，要求本地能跑通、可演示、不过度设计。完整架构见 `doc/TzFun架构规格v1.0.md`。

## 技术栈（已定，与 scaffold 一致）

- Spring Boot 4.1.2 + Java 21 + MyBatis（注解版，已用 @Select/@Insert）+ MySQL 8 + JWT(jjwt 0.12.6) + BCrypt(spring-security-crypto)。
- 阶段1 已引入 **Redis**（模块3 时间窗库存，StringRedisTemplate + Lua 原子占释）。
- 阶段2 才引入 RocketMQ（异步化 / 超时释放 / 用车券秒杀）；阶段3 SpringCloud（Gateway/Nacos/Seata）；ES 可选。
- **阶段1 严禁擅自引入** RocketMQ / SpringCloud / ES / WebSocket 等；**阶段2 引入 RocketMQ 已在范围**（见 doc/阶段2开场指令.md）；**阶段3 才引入 SpringCloud**；ES / WebSocket / 其他新中间件仍严禁擅自引入，不在范围即过度设计。
- 分阶段任务卡在 `doc/TzFun阶段1任务卡.md` 及 `doc/TzFun阶段1-模块N-*.md`，按模块逐个 `Read` 实现。

## 阶段1 模块进度（CC 自检用）

- ✅ 模块1 用户认证：已完成（UserController/UserService/JwtUtil/LoginInterceptor/UserContext 等）。
- ✅ 模块2 门店/车型/车辆：已完成（Store/CarType/Car 三表 CRUD + availableCount + UserContext.requireAdmin 鉴权）。✅ 模块3 时间窗库存(Redis+Lua)：已完成（RedisInventoryService + occupy/release.lua 原子占释 + /carType/available 选车 + rebuildFromDb 兜底 + 占位 orders 表）。✅ 模块4 订单状态机/下单/取还车（Order/OrderMapper/OrderService/OrderController/OrderStatus 状态机 + 下单占库存补偿 + 取还车 + 水平越权）→ ✅ 模块5 优惠券：已完成（CouponTemplate/UserCoupon 双表 + 领券先插唯一索引拦重复再条件更新 claimed_count 防超发 + useCoupon WHERE status=0 防复用 + calcDiscount DECIMAL 封顶不超订单额 + 过期 Job）→ ✅ 模块6 支付(mock)：已完成（PaymentController/PaymentService/PaymentMapper/Payment/PayNotifyDTO + handleNotify 幂等 out_trade_no 唯一+已支付直接 SUCCESS + 订单确认 WHERE status=PENDING + useCoupon 重复回调 try/catch 幂等忽略 + 金额 DECIMAL 比对订单权威值防篡改 + WebConfig 放行 /pay/notify)。
- ✅ 阶段2（已完成）：RocketMQ 接入（原生 rocketmq-client 4.9.8 手动封装，不用 starter）+ 超时未支付自动释放库存（延迟消息 + 定时任务双保险）+ 优惠券秒杀（Redis Lua 预扣 + MQ 异步落库 + 唯一索引兜底）。Sentinel 限流后置未做。
- 🔧 阶段3 微服务化 Spring Cloud：模块A 微服务骨架（已验收）、模块B 业务迁移+网关鉴权前移（已验收）均完成；**模块C 订单 Outbox 最终一致（代码完成，待用户运行期验收）**。CC 按 A→B→C→D 顺序实现（只写代码）；CI + SkyWalking 归用户（见模块E）。架构硬约束/代码约定同阶段1/2。
- 每模块开始前先 `Read doc/TzFun阶段1-模块N-*.md`（阶段2 改读 doc/阶段2开场指令.md）。

## 项目结构速查（/clear 后靠此重建认知，禁止全项目扫描）

包根：`src/main/java/com/tzfun/tzfun/`，启动类 `TzFunApplication`。

- `common/`：`Result<T>`(code:String/msg/data，成功 code="200")、`CustomException`(code,msg)、`GlobalExceptionHandler`(统一异常处理，含 `@Valid` 校验异常)、`config/`(`WebConfig` 注册拦截器、`PasswordConfig` 提供 BCryptPasswordEncoder)。
- `controller/UserController`：`/user/register`、`/user/login`、`/user/info`，返回 `Result<UserVO>`。
- `service/UserService`：注册(BCrypt 加密 + selectByUsername 查重)、登录(校验密码 + JwtUtil 签发)。
- `mapper/UserMapper`：MyBatis 注解版 `@Select/@Insert`，按 username 查、插用户。
- `entity/`：`User`(id,username,password,role,name...)、`Role`(枚举 RENTER/ADMIN)。
- `dto/RegisterDTO`(`@Valid` 校验)、`vo/UserVO`(无 password)、`vo/LoginVO`(token/role/name)。
- `interceptor/LoginInterceptor`：校验 `Authorization: Bearer <token>`，放行 login/register；`context/UserContext`(ThreadLocal 存当前用户，afterCompletion 调 remove 防泄漏)。
- `util/JwtUtil`：claim + expireMs 签发/解析。
- `resources/application.yml`（注意：不是 .properties）：DB=tzfun(root/root)，**服务端口 9070**（不是 8080），MyBatis 不自动建表（需手动建库建表）；Redis host=localhost:6379。
- 模块2 基础数据：`controller/StoreController`(`/store/list`、`POST/PUT /store`(admin))、`CarTypeController`(`/carType/list`、`POST/PUT`(admin)、`/carType/availableCount`)、`CarController`(`/car/list`(admin,可按 storeId/carTypeId 过滤))；`service/StoreService`/`CarTypeService`/`CarService` 标准 CRUD，admin 写接口与 `CarService.list` 调 `UserContext.requireAdmin()` 拦非管理员(403)；`mapper/StoreMapper`/`CarTypeMapper`/`CarMapper` 注解版，`CarTypeMapper.countAvailable` 数 `status='在库'`；`entity/` 新增 `Store`、`CarType`(dailyPrice BigDecimal)、`Car`(storeId,carTypeId,plateNo,status)；`dto/StoreDTO`/`CarTypeDTO`(`@Valid` 校验)。
- 模块3 时间窗库存：`service/RedisInventoryService`(initSlot=SET NX、getAvailable、occupy/release 调 Lua、rebuildFromDb 从 orders 聚合重算剩余)；`resources/lua/occupy.lua`(先判 cur>=need 再 DECRBY，检查+扣减原子)、`release.lua`(INCRBY)；`controller/CarTypeController` 新增 `POST /carType/available`(RENTER 可查，按门店/城市+日期段过滤已满日期，返回 AvailableVO 列表)；`entity/Order`+`mapper/OrderMapper`(selectActive 仅 PENDING/CONFIRMED/IN_USE，占位表 `orders`)；Redis 用 `StringRedisTemplate`。
- 模块4 订单：`controller/OrderController`(`POST /order` 下单、`/confirm` mock支付确认、`/pickup` 取车、`/return` 还车、`/settle` 结算、`/cancel` 取消)；`service/OrderService`(createOrder 先 occupy 占库存→落库失败 release 补偿、状态机 changeStatus 用 `UPDATE...WHERE status=old` 行数=1、getOwnedOrder 水平越权 403、allocateCar markRented 条件更新防重分、returnCar 释放时间窗库存)；`entity/OrderStatus`(PENDING/CONFIRMED/IN_USE/RETURNED/SETTLED/CANCELLED + ALLOWED 流转表)；`entity/Order`(order_no/user_id/起止时间/status/totalAmount|discountAmount|payAmount DECIMAL/coupon_id)；`mapper/OrderMapper`(insert/fillPickup/fillReturn/updateStatus 条件更新/selectActive 仅 PENDING|CONFIRMED|IN_USE)；`dto/OrderDTO`(@Valid：storeId/carTypeId/startTime/endTime LocalDateTime)。
- 模块5 优惠券：`controller/CouponController`(`/coupon/listTemplates` 可领列表、`/coupon/claim/{templateId}`(登录用户领)、`/coupon/myCoupons` 我的卡包)；`service/CouponService`(claim 先插 user_coupon→捕获 DuplicateKeyException 拦重复领→再 incrementClaimed WHERE claimed_count<total_count 防超发、calcDiscount 满减/折扣 BigDecimal 封顶 max_discount 且不超订单额、useCoupon 调 UserCouponMapper.useCoupon WHERE status=0 防复用、returnCoupon 幂等退券)；`entity/CouponTemplate`(type/value/minSpend/maxDiscount DECIMAL,totalCount/claimedCount,claimStart/claimEnd,status 1上架)、`entity/UserCoupon`(status 0未用/1已用/2过期,useOrderId)；`mapper/CouponTemplateMapper`(listAvailable 上架且在领取窗内、incrementClaimed 条件更新)、`mapper/UserCouponMapper`(insert useGeneratedKeys、useCoupon WHERE status=0、returnCoupon、expireCoupons status=2)；`job/CouponExpireJob`(@Scheduled hourly 调 expireCoupons，@EnableScheduling 已在启动类加)。
- 模块6 支付(mock)：`controller/PaymentController`(`/pay/create` 创建支付单校验归属+状态PENDING+幂等复用已有未支付单、`/pay/notify` 网关异步回调放行无token 返回纯文本 SUCCESS/FAIL)；`service/PaymentService`(createPay 校验 userId 归属 403、handleNotify 先查已支付直接 SUCCESS 幂等去重、金额比对 order.payAmount 防篡改、updateStatus PENDING→CONFIRMED 条件更新、useCoupon 重复回调 try/catch 忽略、markPaid 置已支付)；`entity/Payment`(outTradeNo 幂等键/amount DECIMAL/status)、`dto/PayNotifyDTO`(@Valid outTradeNo/orderId/amount/couponId)；`mapper/PaymentMapper`(selectByOutTradeNo/markPaid)；建表 payment 含 `UNIQUE KEY uk_out_trade_no`；`config/WebConfig` 放行 `/pay/notify`。
- 阶段2 RocketMQ：`common/mq/MqProducer`(原生 rocketmq-client 手动封装，send 普通/延迟消息，失败记日志返回 false 由调用方补偿)、`common/mq/MqConsumerSupport`(抽象基类，管 DefaultMQPushConsumer start/shutdown + body UTF-8 解码 + 异常走重试)；`consumer/OrderTimeoutConsumer`(收 `order-pay-timeout` → `cancelTimeout`)、`consumer/SeckillCouponConsumer`(秒杀落库：插 user_coupon 唯一索引兜底 + incrementClaimed 条件更新，失败回滚预扣)；`job/OrderTimeoutJob`(@Scheduled 5 分钟扫超时 PENDING 兜底，防 MQ 丢失)；`service/OrderService` 新增 `cancelTimeout`(无登录态仅 PENDING，吞 409 幂等) + `doCancel`(状态机+release 库存+退券) + createOrder 发延迟消息(level 15=20 分钟)；`service/SeckillService`(校验 seckill=1 → Lua 预扣 → 发 MQ → 失败回滚 INCRBY)；`resources/lua/seckill_stock.lua`(cur>=1 才 DECRBY)；`controller/CouponController` 新增 `POST /coupon/seckill/{templateId}`；`coupon_template` 加 `seckill` 字段(ALTER，SQL 已追加 doc/模块5-SQL)；`application.yml` 加 rocketmq 配置(name-server/producer-group/timeout-topic/seckill-topic)。

**规则**：新建模块一律复用上述资产（Result/CustomException/GlobalExceptionHandler/拦截器/JwtUtil/UserContext/PasswordConfig），禁止为同功能重写；表结构变更须同时给出建表/改表 SQL。**`/clear` 后只需 Read 本文件 + 本模块子卡即可，禁止 `ls`/扫描整个 `src`、禁止读 `target/`、`*.jar`、本地 Maven 仓库。**

## 代码约定（通用，务必遵守）

- 统一返回 `Result{code:String, msg, data:Object}`，成功 `code="200"`；失败抛 `CustomException(code,msg)`，`GlobalExceptionHandler` 统一处理（含 `@Valid` 参数校验异常）。
- Service 不写接口 + Impl，直接 `@Service` 注解 + `@Resource` 注入依赖。
- 实体 ≠ DTO ≠ VO：Entity 对应表；DTO 接收入参 + `@Valid` 校验；VO 出参去敏感字段（不含 password）。
- 鉴权：自建拦截器（不用 Spring Security），身份一律取自 token，不信任前端传参；改/删/查他人数据前校验归属（水平越权防护）。
- 命名简洁明了、见名知意。

## 架构硬约束（阶段1 必守，违反打回）

- 金额一律 DECIMAL(10,2) 或 BIGINT 分，**禁 float/double**。
- 并发修改统一「条件更新 + 受影响行数=1」（占车/用券/改单状态/领券计数），**禁「先查再改」**。
- 角色只有 RENTER / ADMIN，无 SERVICE。
- 优惠券：领券 `UPDATE ... WHERE claimed_count<total_count` + 行数判断；用券 `WHERE status=0` 条件更新；抵扣 BigDecimal（详见 doc/模块5）。
- 订单状态机：合法流转表 + `UPDATE ... WHERE status=old` 条件更新（详见 doc/模块4）。
- 支付(mock)：异步回调 `out_trade_no` 唯一 + 已支付直接返回 SUCCESS = 幂等（详见 doc/模块6）。

## 明确不做（避免过度设计）

- 单元测试、Docker、Swagger、SSE 流式（如需启用，先与用户确认）。

## 开发流程（与用户的工作约定）

- 按阶段推进，每完成一阶段停下与用户核对，不一口气做完。
- 每完成一阶段做一次 git commit（中文 conventional commit，只提交本阶段文件；工作区里非本阶段的既有改动单独指出、不混提交）。
- 用户在学习阶段：完成后从学习者角度讲解「这是干嘛的、为什么这么干」。
- 方案有多个可行选择时先问（AskUserQuestion），不自作主张。
- 省 token：短会话、只读必要文件、写完不回显大段代码；适时提醒 `/compact` `/clear` `/model sonnet`。

## 省 Token 硬约定（模块级，必守，违反即浪费）

实现每个模块时严格遵守，避免像模块1 那样把 token 烧在排错循环与重复读取上：

1. **回复极简**：只输出「改了哪个文件 / 哪几行 / 自测结论（通过 or 不通过 + 证据）」，禁止复述整段源码或重复解释已写过的逻辑。
2. **报错传文本，不传截图**：用户给的报错请贴**纯文本日志**；自己构建统一用 `mvn -q compile`（静默），不要刷全量 Maven 日志进上下文。
3. **不碰无关大目录**：禁止读取 `target/`、`*.jar`、本地 Maven 仓库、`.git`、`node_modules` 等；只读本次模块相关源码与对应子卡。
4. **复用已有资产**：User / JwtUtil / LoginInterceptor / UserContext / Result / CustomException / GlobalExceptionHandler 等模块1 已建能力直接调用，禁止为同功能重写一遍。
5. **自测克制**：实现完只做子卡规定的必要自测，不自创额外验证脚本或多轮全量构建。
6. **长会话及时瘦身**：排错超过 3 轮或上下文明显变长时，主动建议用户 `/compact` 或 `/clear` 后重开，不要带着累积噪音硬撑。
