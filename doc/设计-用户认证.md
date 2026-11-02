# TzFun 阶段1 · 模块1 任务卡：用户与认证

> 交付顺序：第 1 张（无依赖，最先做）  
> 配套：总任务卡 `TzFun阶段1任务卡.md` §0 硬约束 + §1；规格书 v1.0 §3.1  
> 规则：CC 严格按本卡实现，不得自行加需求/加表/加中间件。

## 目标

实现租客/管理员注册登录 + JWT 鉴权 + 角色校验骨架，是整个项目的登录态基础。

## 表 `user`

`id BIGINT PK AUTO_INCREMENT, username VARCHAR(50) NOT NULL UNIQUE, password VARCHAR(100) NOT NULL, role VARCHAR(20) NOT NULL COMMENT 'RENTER/ADMIN', name VARCHAR(50), phone VARCHAR(20), license_no VARCHAR(50) COMMENT '驾照,mock', idcard_no VARCHAR(50) COMMENT '身份证,mock', avatar VARCHAR(255), create_time DATETIME`

## 接口

- `POST /user/register` {username, password, name?, phone?} → 默认 `role=RENTER`，密码 **BCrypt** 加密；用户名重复报"已注册"
- `POST /user/login` {username, password} → 校验通过后返回 `{token, role, name}`
- `GET /user/info` → 返回当前登录用户信息（从 Token 取）

## 关键类/方法

- `JwtUtil.generateToken(userId, role)` / `parseToken(token)` → 返回 Claims(userId, role)
- `LoginInterceptor.preHandle`：放行 `/user/login`、`/user/register`；其余从 Header `Authorization` 取 token，校验后写入 `UserContext`
- `WebConfig` 注册拦截器，排除登录/注册/静态资源
- `UserContext`：ThreadLocal 存/取 userId、role
- 后续 admin 接口用 `UserContext.getRole() == "ADMIN"` 校验，非管理员抛 403

## 硬约束（违反打回）

- 密码必须 BCrypt，禁止明文
- 角色枚举只有 `RENTER` / `ADMIN`（无 SERVICE）
- 统一 `Result{code,msg,data}` 返回；自定义 `CustomException` + 全局异常处理器

## 验收点

- [ ] 注册默认 RENTER，密码密文存储
- [ ] 登录返回 JWT，能凭 token 访问 /user/info
- [ ] 无 token / 假 token 访问受保护接口被拦截
- [ ] 角色字段只有 RENTER/ADMIN
