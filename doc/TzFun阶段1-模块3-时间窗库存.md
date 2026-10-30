# TzFun 阶段1 · 模块3 任务卡：选车 + 时间窗库存（Redis + Lua）

> 交付顺序：第 3 张（依赖模块2 的 availableCount 提供 N）
> 配套：总任务卡 `TzFun阶段1任务卡.md` §0 硬约束 + §3；规格书 v1.0 §4
> ⚠️ 本模块是**全项目含金量最高**的一块，CC 必须严格按"检查+扣减合一"实现。

## 目标
实现租车最核心的"二维库存"：按天分槽存剩余可用车数，用 Lua 原子占/释，防并发重复占车（double booking）。

## 库存模型
- Key：`inv:{storeId}:{carTypeId}:{date}` → 当天剩余可用辆数（初始 = N，来自模块2 availableCount）
- 一次预约 `[start, end]` 展开为覆盖的日期列表（跨天 = 多个 key）

## Lua 脚本（放 `resources/lua/`，务必"检查 + 扣减"在一个脚本内）
- `occupy.lua`：先遍历 KEYS 判 `cur >= need`，再统一 `DECRBY`；全过返回 1，否则 0
- `release.lua`：遍历 KEYS `INCRBY`

## 关键类 `RedisInventoryService`
- `void initSlot(storeId, carTypeId, date, int n)` → `SET key n NX`（不存在才设）
- `int getAvailable(storeId, carTypeId, LocalDate date)` → GET 单日剩余
- `boolean occupy(storeId, carTypeId, start, end)` → 展开日期 → 调 occupy.lua → 返回成功否
- `void release(storeId, carTypeId, start, end)` → 调 release.lua
- `void rebuildFromDb(storeId, carTypeId)` → 从 `order` 聚合真实占用重算剩余（Redis 丢/初始化兜底）

## 选车接口
- `POST /carType/available` {city, storeId, carTypeId?, start, end, priceMin?, priceMax?}
  → 返回该时段每天仍可用的车型列表（用 `getAvailable` 过滤已满日期段）

## 硬约束（违反打回）
- Lua 脚本"检查 + 扣减"必须在一个脚本内（两步分开 = 竞态，打回）
- Redis 是加速层，DB(order) 是真相源，必须能从 DB 重建（rebuildFromDb）
- 跨天预约必须展开为多个 date 槽

## 验收点
- [ ] occupy.lua 检查与扣减合一；并发双占被挡
- [ ] 选车接口正确过滤已满日期段
- [ ] rebuildFromDb 能从 DB 重算剩余
- [ ] 释放（取消 / 提前还车）有对应方法可调用
