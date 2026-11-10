# TzFun 租车平台

> 自营车队租车业务后端，采用「车辆 × 时间段」二维库存模型，围绕高并发下单、支付幂等、超时释放与优惠券秒杀构建的完整业务闭环。

## 项目介绍

TzFun 是一个面向自营车队模式的租车平台后端系统。与电商的「商品库存」不同，租车业务的核心资源——车辆——在时间维度上不可叠加：同一辆车在不同日期属于完全不同的库存单元。因此系统最大的挑战是**在并发场景下准确管理「车 × 时间」二维库存，避免重复占车与超租**。

围绕这一核心难点，项目实现了从选车、下单、支付到取还车的完整业务闭环，并在此基础上落地了三组高并发设计：

- **时间窗库存**：将每日可用车数按「门店 + 车型 + 日期」拆分为 Redis 槽位，以 Lua 脚本原子地完成「校验—扣减」，从根本上杜绝并发超租；MySQL 作为真相源，提供缓存重建兜底。
- **支付与订单一致性**：支付采用异步回调模型，以商户订单号做幂等去重、以订单库应付额校验防金额篡改；订单状态流转统一以「条件更新 + 受影响行数」实现乐观锁，避免并发错乱。
- **异步化与高并发秒杀**：引入 RocketMQ 承担延迟消息与异步落库，下单未支付由「延迟消息 + 定时任务」双保险自动释放库存；优惠券秒杀以 Redis+Lua 预扣 + MQ 异步落库 + 唯一索引/条件更新构建三层防超卖。

贯穿整套设计的一条主线是：**原子扣减始终在 Redis+Lua，数据库以条件更新兜底，任何库存预扣都配以补偿回退。**

## 技术栈

- 后端：Spring Boot 4.1.2 · Java 21 · MyBatis（注解版）
- 存储：MySQL 8（业务数据）· Redis（时间窗库存 / 秒杀预扣）
- 消息：RocketMQ 5.x（异步落库 · 延迟消息 · 超时释放）
- 安全：JWT + BCrypt

## 功能特性

- 用户认证与鉴权（JWT 签发校验，ThreadLocal 传递身份，水平越权防护）
- 门店 / 车型 / 车辆基础数据管理
- 时间窗库存（按「门店+车型+日期」分槽，Redis+Lua 原子占释，防重复占车）
- 订单状态机（PENDING→CONFIRMED→IN_USE→RETURNED→SETTLED，条件更新乐观锁）
- 优惠券（领券防超发、用券防复用、BigDecimal 精确计费）
- 支付（mock 网关 + 异步回调幂等 + 金额防篡改）
- 超时未支付自动释放（延迟消息 + 定时任务双保险）
- 优惠券秒杀（Redis+Lua 预扣 + MQ 异步落库，三层防超卖）

## 核心设计亮点

| 难点 | 方案 | 说明 |
|------|------|------|
| 并发重复占车 | Redis+Lua 原子占释 | 把「查剩余—判断—扣减」打包为单线程原子操作，杜绝超租 |
| 订单状态并发错乱 | 条件更新 + 行数判断（乐观锁） | `UPDATE ... WHERE status=old`，行数=0 视为被其它路径抢先 |
| 支付重复通知 | 商户订单号幂等 + 金额以我方为准 | 无 token 回调下防伪造、防重复处理 |
| 下单后不付钱库存占死 | 延迟消息 + 定时任务双保险 | 延迟消息负责及时提醒，定时任务负责精确兜底 |
| 优惠券超发 | Redis 预扣 + 唯一索引 + 条件更新 | 三层防超卖，任何环节失败回滚预扣 |

## 快速开始

### 环境要求

JDK 21 · Maven 3.9+ · MySQL 8 · Redis 7+ · RocketMQ 5.x（需启动 NameServer 与 Broker）

### 1. 初始化数据库

执行汇总建表脚本（开箱即用，含全部表结构与示例优惠券数据）：

```bash
mysql -u <user> -p <dbname> < doc/schema.sql
```

### 2. 配置

修改 `src/main/resources/application.yml`：

- `spring.datasource`：MySQL 连接
- `spring.data.redis`：Redis 连接
- `rocketmq.name-server`：NameServer 地址（默认 `localhost:9876`）

### 3. 启动中间件

- MySQL、Redis 正常启动
- RocketMQ（消息默认落在 `user.home` 下的 `store` 目录；C 盘空间紧张时建议在 `conf/broker.conf` 配置 `storePathRootDir` 指向其它盘）：

```bash
mqnamesrv.cmd
mqbroker.cmd -n localhost:9876 autoCreateTopicEnable=true
```

### 4. 启动应用

```bash
mvn spring-boot:run
```

默认端口 8080。

### 核心接口示例

- `POST /carType/available` 选车（按天过滤可用车型）
- `POST /order` 下单（占库存 → 落库，失败补偿）
- `POST /pay/create` → `POST /pay/notify` 支付（mock 网关异步回调）
- `POST /coupon/claim` 领券
- `POST /coupon/seckill/{templateId}` 秒杀
- `POST /order/pickup` · `/order/return` 取还车

## 项目结构

```
src/main/java/com/tzfun/tzfun/
├── controller/   接口层
├── service/      业务逻辑（库存 / 订单 / 支付 / 优惠券 / 秒杀）
├── mapper/       MyBatis 注解 SQL
├── consumer/     RocketMQ 消费者（超时释放 / 秒杀落库）
├── job/          定时任务（过期券 / 超时订单兜底）
├── common/mq/    MQ 生产 / 消费基类
├── config/       配置（Redis / MQ / 拦截器）
├── interceptor/  登录拦截
├── entity/dto/vo 实体 / 入参 / 出参
└── util/         工具（JWT 等）
resources/lua/    occupy.lua · release.lua · seckill_stock.lua
doc/schema.sql    数据库初始化脚本
doc/设计-*.md      各模块设计文档
```
这是用来测试PR和CI的演示分支

