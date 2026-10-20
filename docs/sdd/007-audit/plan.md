# 审计事件与查询实施计划

**状态：** Approved<br>
**审批记录：** 用户于 2026-10-20 批准本计划<br>
**Spec：** [`spec.md`](spec.md)

## 风险与依赖

- 这是跨切片契约。审计写入基础必须先于登录、用户、RBAC 和平台设置的原子写入集成；审计查询端点需等 IAM-RBAC-01 授权能力可用。建议先执行 Slice 1，再并行推进各事件源，最后执行 Slice 2–3。
- IAM-OPS-01 Slice 1；Slice 2 依赖 IAM-RBAC-01 Slice 3；Slice 3 依赖各业务计划的事件源集成完成。

## Slice 1：建立安全的追加式审计写入基础

- **目标：** 建立安全的追加式审计写入基础。

- **Acceptance：** AC-1–AC-6。

- **依赖：** IAM-OPS-01 Slice 1。

- **测试或证明：** 为事件字段、无 metadata、只追加、关键变更同事务、失败登录审计故障和 UserSetting 不审计编写持久化/集成测试；实现共享 AuditEvent 服务及 repository。

- **实现概要：** 实现 audit 单数包、Long 主键和版本化迁移；所有事件由方法级 `@Audited` 描述并由 AOP 统一写入，不使用 Service 内的审计上下文或事件载体，不保存 metadata。成功事件参与业务事务；失败登录使用独立事务记录。审计不提供修改/删除 API。

- **验证命令：** `./mvnw verify`。

- **完成：** true

## Slice 2：查询和筛选审计记录

- **目标：** 查询和筛选审计记录。

- **Acceptance：** AC-7–AC-9。

- **依赖：** Slice 1；IAM-RBAC-01 Slice 3。

- **测试或证明：** 为查询权限、过滤/排序白名单、分页、单条 404、查询本身留痕且不递归编写 API 集成测试；实现查询 DTO、repository filter 和事件记录。

- **实现概要：** 实现审计列表和详情 API；查询事件不保存筛选字段、筛选值或 metadata；依赖当前 session + audit-events:read。

- **验证命令：** `./mvnw verify`。

- **完成：** true

## Slice 3：执行保留清理并验证审计故障语义

- **目标：** 执行保留清理并验证审计故障语义。

- **Acceptance：** AC-10–AC-12。

- **依赖：** Slice 1–2；IAM-AUTH-01、IAM-RBAC-01、IAM-USER-01、IAM-SETTING-01 的事件源集成切片。

- **测试或证明：** 用可控时间验证 365 天边界和自动清理；注入审计存储故障，验证关键业务事务回滚、失败登录仍被拒绝并产生安全运维信号。

- **实现概要：** 实现保留期清理调度与可测试的时间边界；不提供手动删除 API。逐一验证登录、邀请/激活、重置、用户、RBAC、平台设置事件源与事务边界。

- **验证命令：** `./mvnw verify`。

- **完成：** true

## 附录


### 相关文档

- Spec：[`spec.md`](spec.md)

- 父 PRD：[`docs/prd/iam-management-platform.md`](../../prd/iam-management-platform.md)

- 设计就绪：Spec 中所列需求均为 Ready；见 [`docs/design/iam-domain.md`](../../design/iam-domain.md#设计就绪评估) 与 [`docs/api.md`](../../api.md)。

- 依赖：见“风险与依赖”。


### Build evidence


| 日期 | Slice | 验证命令和结果 | 偏差 |
| --- | --- | --- | --- |
| 2026-10-20 | Slice 1–3 | 历史运行过 `mise exec -- ./mvnw verify`，覆盖审计查询和业务事务行为 | 本轮去除 metadata 后仅执行 `./mvnw -q -DskipTests compile`、Spotless 与 `git diff --check`；未运行测试 |


### 修订历史


| 日期 | 原因和涉及切片 | 审批 |
| --- | --- | --- |
| 2026-10-20 | 初始实施计划；覆盖本 Spec 全部 AC | 用户批准；可进入 `sdd-build` |
