# IAM 首批实现规格拆分

**状态：** Approved breakdown（父 PRD 1.1 已批准，领域/API 设计关卡 Ready；七份规格均已批准为 1.0）
**日期：** 2026-10-20

本拆分将 PRD 1.1 Approved 的 B1 范围拆为可独立验收的功能切片。序号便于浏览；实际执行顺序以各实施计划的切片依赖为准。审计写入基础需先于依赖它的关键业务事务，平台邮件配置读取需先于邀请和密码重置邮件的动态配置集成。

| 顺序 / Spec ID | 规格 | 目标 | 覆盖需求 | 依赖 / 顺序说明 |
| --- | --- | --- | --- | --- |
| 001 / IAM-OPS-01 | [运行环境与管理员初始化](001-runtime-bootstrap/spec.md) / [Plan](<001-runtime-bootstrap/plan.md>) | 在干净环境运行后端、数据库和邮件测试服务，并安全建立首位平台管理员 | NFR-4、NFR-5 | 基础设施前置；其他切片在此环境中集成 |
| 002 / IAM-AUTH-01 | [认证、密码恢复与会话](002-authentication-sessions/spec.md) / [Plan](<002-authentication-sessions/plan.md>) | 员工登录、恢复密码并管理当前会话 | FR-1、NFR-1、NFR-2 | 依赖运行环境和首位管理员初始化 |
| 003 / IAM-RBAC-01 | [角色与权限管理](003-rbac/spec.md) / [Plan](<003-rbac/plan.md>) | 管理角色权限并按多角色权限并集授权 | FR-3、FR-4 | 依赖认证与内置管理员身份 |
| 004 / IAM-USER-01 | [邀请与员工生命周期](004-user-lifecycle/spec.md) / [Plan](<004-user-lifecycle/plan.md>) | 邀请、激活、维护、停用和重新启用员工 | FR-2、FR-8 | 依赖认证；角色分配与 IAM-RBAC-01 集成 |
| 005 / IAM-ME-01 | [个人资料与用户偏好](005-self-service/spec.md) / [Plan](<005-self-service/plan.md>) | 员工维护自己的姓名与个人偏好 | FR-5 | 依赖认证与 User 生命周期 |
| 006 / IAM-SETTING-01 | [平台设置与邮件配置](006-platform-settings/spec.md) / [Plan](<006-platform-settings/plan.md>) | 管理平台展示、链接和 SMTP 设置 | FR-6 | 依赖管理员身份、权限控制和初始化设置 |
| 007 / IAM-AUDIT-01 | [审计事件与查询](007-audit/spec.md) / [Plan](<007-audit/plan.md>) | 记录并查询关键认证与管理事件 | FR-7、NFR-3 | 审计写入需与各切片的关键状态变更/成功登录同事务实现；查询依赖认证与权限 |

## 覆盖检查

- 功能需求 FR-1–FR-8 均已映射到至少一个规格。
- 非功能需求 NFR-1–NFR-3 已映射到认证、用户生命周期和审计规格；NFR-4–NFR-5 已映射到运行环境规格。
- 所有跨切片共享规则仍以 [领域设计](../design/iam-domain.md) 和 [API 契约](../api.md) 为准；规格只定义切片验收行为。
- 七个规格均为 1.0 Approved。父 PRD 已获批且设计关卡 Ready；七份实施计划均已获批，可按各计划中的切片依赖进入 `sdd-build`。

## 修订历史

| 版本 | 日期 | 变更 | 状态 |
| --- | --- | --- | --- |
| 0.1 | 2026-10-20 | 初始规格拆分并映射 FR-1–FR-8、NFR-1–NFR-5 | Draft |
| 0.1 | 2026-10-20 | PRD 1.1 获批，B1 设计就绪评估 Ready | 规格待逐项批准 |
| 1.0 | 2026-10-20 | 用户确认并批准七份 feature spec 及全部 AC | Approved；可进入 `sdd-plan` |
| 1.1 | 2026-10-20 | 为七份已批准 Spec 生成独立实施计划并映射 AC | Plans Draft |
| 1.2 | 2026-10-20 | 用户批准七份计划 | Approved；可进入 `sdd-build` |
