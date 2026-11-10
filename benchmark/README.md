# 秒杀压测（优惠券）

针对 `coupon-service`（端口 9084）的 `/coupon/seckill/{templateId}` 接口的并发压测脚本，用于产出简历可引用的量化数据。

## 文件

| 文件 | 说明 |
|---|---|
| `seckill-bench.jmx` | Jmeter 脚本：200 线程并发、循环 1 次、读 200 个不同用户 token |
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
