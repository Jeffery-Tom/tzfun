# TzFun 阶段1 · 模块2 任务卡：门店 / 车型 / 车辆

> 交付顺序：第 2 张（依赖模块1 的 ADMIN 角色校验）
> 配套：总任务卡 `TzFun阶段1任务卡.md` §0 硬约束 + §2；规格书 v1.0 §3.2/3.3/3.4

## 目标
建好三张基础表 + CRUD，并暴露"某门店某车型可用车辆数 N"查询（供模块3 库存初始化）。

## 表
- `store(id BIGINT PK, name VARCHAR(100), city VARCHAR(50), address VARCHAR(255), lng DECIMAL(10,7), lat DECIMAL(10,7), create_time DATETIME)`
- `car_type(id BIGINT PK, name VARCHAR(100), brand VARCHAR(50), seats INT, transmission VARCHAR(20) COMMENT '自动/手动', daily_price DECIMAL(10,2), image VARCHAR(255), create_time DATETIME)`
- `car(id BIGINT PK, store_id BIGINT, car_type_id BIGINT, plate_no VARCHAR(20), status VARCHAR(20) COMMENT '在库/出租中/维护', create_time DATETIME)`

## 接口（RENTER 可查，ADMIN 可增改）
- `GET /store/list` · `POST /store`(admin) · `PUT /store`(admin)
- `GET /carType/list` · `POST /carType`(admin) · `PUT /carType`(admin)
- `GET /car/list`(admin，可按 storeId / carTypeId 过滤)
- `GET /carType/availableCount?storeId=&carTypeId=` → 返回该门店该车型 `status='在库'` 的车辆数 N（模块3 用）

## 关键类
- `StoreService` / `CarTypeService` / `CarService` 标准 CRUD（MyBatis mapper）
- 所有 admin 写接口内部校验 `UserContext.getRole() == "ADMIN"`

## 硬约束
- 金额 `daily_price` 用 DECIMAL，禁 double/float
- 角色校验复用模块1 的 UserContext，不重新解析 token

## 验收点
- [ ] 三张表建好，store/carType 有 list + admin 增改
- [ ] availableCount 返回正确在库车辆数
- [ ] admin 接口非管理员访问被拒
