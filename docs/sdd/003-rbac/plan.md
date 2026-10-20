# 角色与权限管理实施计划

**状态：** Approved<br>
**审批记录：** 用户于 2026-10-20 批准本计划<br>
**Spec：** [`spec.md`](spec.md)

## 风险与依赖

- RBAC 是用户与平台设置管理的授权前置。审计写入基础需先具备，角色变更与 AuditEvent 才能同事务提交。
- IAM-OPS-01 Slice 1、IAM-AUTH-01 Slice 2；审计变更写入依赖 IAM-AUDIT-01 Slice 1。

## Slice 1：维护权限目录与角色定义

- **目标：** 维护权限目录与角色定义。

- **Acceptance：** AC-1–AC-5。

- **依赖：** IAM-OPS-01 Slice 1；IAM-AUTH-01 Slice 2；IAM-AUDIT-01 Slice 1。

- **测试或证明：** 先为固定权限目录、未授权访问、角色增改删、重复名称/代码、未知权限及内置/已分配角色保护编写 API 集成测试；实现角色和 RolePermission 持久化及管理 API。

- **实现概要：** 按领域设计种入权限码和内置角色；提供具名 DTO、白名单校验、应用层关系校验、审计同事务写入；不添加数据库外键。

- **验证命令：** `./mvnw verify`。

- **完成：** false

## Slice 2：分配多角色并保护平台管理员

- **目标：** 分配多角色并保护平台管理员。

- **Acceptance：** AC-6–AC-10。

- **依赖：** Slice 1。

- **测试或证明：** 为多对多唯一关系、重复分配/撤销、PlatformAdmin 限制、最后管理员保护和权限立即生效编写 PostgreSQL 集成测试；实现 UserRole 命令和并发安全的最后管理员检查。

- **实现概要：** 实现 userrole 单数包关联、角色授予/撤销授权与审计事务；每次请求按当前数据计算权限，不缓存旧授权快照。

- **验证命令：** `./mvnw verify`。

- **完成：** false

## Slice 3：按当前角色权限执行服务端访问控制

- **目标：** 按当前角色权限执行服务端访问控制。

- **Acceptance：** AC-11–AC-13。

- **依赖：** Slice 1、Slice 2。

- **测试或证明：** 构造权限并集、默认拒绝、操作者 ID 篡改、401/403 与信息泄露测试；接入受保护 API 授权拦截并对关键端点作代表性请求验证。

- **实现概要：** 建立统一授权服务，从已认证 UserSession 获取操作者并查询当前有效权限；拒绝规则保持无显式 deny 的既定语义。

- **验证命令：** `./mvnw test && ./mvnw verify`。

- **完成：** false

## 附录


### 相关文档

- Spec：[`spec.md`](spec.md)

- 父 PRD：[`docs/prd/iam-management-platform.md`](../../prd/iam-management-platform.md)

- 设计就绪：Spec 中所列需求均为 Ready；见 [`docs/design/iam-domain.md`](../../design/iam-domain.md#设计就绪评估) 与 [`docs/api.md`](../../api.md)。

- 依赖：见“风险与依赖”。


### Build evidence


| 日期 | Slice | 验证命令和结果 | 偏差 |
| --- | --- | --- | --- |

| — | — | 尚未开始实现 | — |


### 修订历史


| 日期 | 原因和涉及切片 | 审批 |
| --- | --- | --- |

| 2026-10-20 | 初始实施计划；覆盖本 Spec 全部 AC | 用户批准；可进入 `sdd-build` |
