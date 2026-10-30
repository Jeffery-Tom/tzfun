# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# TzFun 项目约定与关键决策

> 业务方向（已冻结）：**自营车队租车平台**（平台自有车 + 门店），非 P2P。MVP = 选车 / 订单 / 优惠券 / 支付(mock) / 取还车；AI 客服后置（复用 JobHub 的 RAG）。作者大三学生，目标「中小厂 Java 后端实习简历项目」，要求本地能跑通、可演示、不过度设计。完整架构见 `doc/TzFun架构规格v1.0.md`。

## 技术栈（已定，与 scaffold 一致）

- Spring Boot 4.1.2 + Java 21 + MyBatis（注解版，已用 @Select/@Insert）+ MySQL 8 + JWT(jjwt 0.12.6) + BCrypt(spring-security-crypto)。
- 阶段1 还需引入 **Redis**（时间窗库存，见模块3）。
- 阶段2 才引入 RocketMQ（异步化 / 超时释放 / 用车券秒杀）；阶段3 SpringCloud（Gateway/Nacos/Seata）；ES 可选。
- **阶段1 严禁擅自引入** RocketMQ / SpringCloud / ES / WebSocket / 任何新中间件——不在范围，加了即过度设计。
- 分阶段任务卡在 `doc/TzFun阶段1任务卡.md` 及 `doc/TzFun阶段1-模块N-*.md`，按模块逐个 `Read` 实现。

## 阶段1 模块进度（CC 自检用）

- ✅ 模块1 用户认证：已完成（UserController/UserService/JwtUtil/LoginInterceptor/UserContext 等）。
- ⬜ 模块2 门店车型车辆 → 模块3 时间窗库存(Redis+Lua) → 模块4 订单状态机/下单/取还车 → 模块5 优惠券 → 模块6 支付(mock)。
- 每模块开始前先 `Read doc/TzFun阶段1-模块N-*.md`。

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
