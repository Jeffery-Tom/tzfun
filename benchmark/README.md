# 压测（秒杀 + 下单防重）

针对 `coupon-service`（9084）秒杀、`order-service`（9083）下单防重的并发压测脚本，用于产出简历可引用的量化数据。

## 文件

| 文件 | 说明 |
|---|---|
| `seckill-bench.jmx` | 秒杀脚本：200 线程并发、读 200 个不同用户 token |
| `order-bench.jmx` | 下单防重脚本：同一用户 N 并发重复下单，验证 Redisson 锁 + 锁内查重 |
| `tokens.csv` | 200 个已登录用户 JWT（bench_1..bench_200，密码 bench123，有效期 1 天，过期需重新生成） |
| `README.md` | 本说明 |

## 前置条件

1. coupon-service 已启动（9084），Nacos/Redis/RocketMQ/MySQL 就绪。
2. 秒杀券 `id=5`：`seckill=1`、`status=1`、`total_count=100`、时间窗覆盖当前时间。

## 每次压测前重置库存

```bash
# 1) 重置 DB 计数与已领记录
mysql -uroot -proot -h127.0.0.1 -e "UPDATE tzfun.coupon_template SET claimed_count=0 WHERE id=5; DELETE FROM tzfun.user_coupon WHERE template_id=5;"

# 2) 清 Redis 分段库存 + 模板缓存（10 段 + init + template 缓存）
docker exec tzfun-redis sh -c 'redis-cli --scan --pattern "seckill:stock:5:*" | xargs -r redis-cli del; redis-cli del coupon:template:5'
```

> 券 id 不是 5 时，把上面 `5` 与脚本里变量 `TEMPLATE_ID` 一起改。

## 运行

### 方式一：命令行（推荐，产出 HTML 报告）

```bash
cd E:\ITstudy\TzFun\benchmark
rm -rf report result.jtl
D:\Jmeter\apache-jmeter-5.6.3\bin\jmeter.bat -n -t seckill-bench.jmx -l result.jtl -e -o report
```

跑完打开 `benchmark\report\index.html` 看报告。

### 方式二：GUI（调试用）

```bash
D:\Jmeter\apache-jmeter-5.6.3\bin\jmeter.bat -t E:\ITstudy\TzFun\benchmark\seckill-bench.jmx
```

## 结果判读（关键口径）

脚本内置响应断言：`"code":"200"`（抢到）或 `"code":"400"`（售罄）判**成功**；`500`/`429` 判**失败**。因此聚合报告里的 `Error%` 就是「异常率」。

| 指标 | 含义 | 期望值 |
|---|---|---|
| Samples | 总请求数 | 200 |
| Throughput | 吞吐量（QPS） | 记录实测值 |
| Average / 99% Line | 平均 / P99 响应 | 记录实测值（本机预期 10~20ms 级） |
| Error% | 异常率 | **0%** |
| DB `claimed_count` | 实发券数 | **= 100**（= total_count，零超发） |
| DB `user_coupon` 条数 | 落库数 | **= 100**（与 claimed 一致） |
| Redis 10 段库存 | 剩余 | 全为 0（卖光） |

## 简历可写的一句话

> 秒杀链路：Redis 库存分段(10段) + Lua 原子预扣 + MQ 异步落库 + 唯一索引兜底，三层防超卖；200 并发压测零超发、错误率 0%，P99 响应 X ms、吞吐 Y QPS。

---

# 下单防重压测（order-service）

针对 `order-service`（9083）`POST /order`：**同一用户 N 并发重复下单同一车型时段**，验证 Redisson 分布式锁 + 锁内 selectOverlap 查重，只允许 1 单成功，其余拦截。

## 前置条件

1. order-service（9083）、car-service（9082）已启动，Nacos/Redis/MySQL 就绪。
2. 门店 `id=1`、车型 `id=1` 存在且有在库车辆（决定库存槽位初始值）。
3. 测试用户无该时段的进行中订单。

## 每次压测前重置

```bash
# 清理测试用户的该时段订单 + 时间窗库存槽位
mysql -uroot -proot -h127.0.0.1 -e "DELETE FROM tzfun.orders WHERE start_time='2026-11-22 10:00:00';"
docker exec tzfun-redis sh -c 'redis-cli --scan --pattern "inv:1:1:*" | xargs -r redis-cli del'
```

## 运行（token 用命令行传入，同一用户）

```bash
cd E:\ITstudy\TzFun\benchmark
TOKEN=$(head -1 tokens.csv)   # 取任意一个用户 token
rm -rf order-report order-result.jtl
D:\Jmeter\apache-jmeter-5.6.3\bin\jmeter.bat -n -t order-bench.jmx -l order-result.jtl -e -o order-report -Jtoken=%TOKEN%
```

> 脚本内 `num_threads` 默认为 100；机器内存紧张时降到 50 再跑（Jmeter 与多个服务共存易触发 native OOM）。

## 结果判读

| 指标 | 含义 | 期望值 |
|---|---|---|
| Samples | 总请求数 | = N（线程数） |
| Error% | 异常率（500/429 判失败） | **0%** |
| DB 该时段订单数 | 实下单数 | **= 1**（零重复） |
| 业务返回分布 | 成功 / 重复拦截 | **1 个 200 + (N-1) 个 400** |

## 简历可写的一句话

> 下单接口用 Redisson 分布式锁串行化同一用户 + 锁内查重叠订单，防并发重复下单；同一用户 100 并发重复下单，仅 1 单落库、错误率 0%。

