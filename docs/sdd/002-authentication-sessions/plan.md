# 认证、密码恢复与会话实施计划

**状态：** Approved<br>
**审批记录：** 用户于 2026-10-20 批准本计划<br>
**Spec：** [`spec.md`](spec.md)

## 风险与依赖

- 登录成功、会话和成功审计要求同事务提交。审计写入基础需在本计划 Slice 1 集成前就绪；若按 breakdown 总序推进，应先提前完成 IAM-AUDIT-01 Slice 1。
- IAM-OPS-01 Slice 1（运行环境）；审计原子写入依赖 IAM-AUDIT-01 Slice 1（审计写入基础）。

## Slice 1：员工使用已验证邮箱登录

- **目标：** 员工使用已验证邮箱登录。

- **Acceptance：** AC-1–AC-3。

- **依赖：** IAM-OPS-01 Slice 1；IAM-SETTING-01 Slice 1；IAM-AUDIT-01 Slice 1。

- **测试或证明：** 先写登录成功/失败、未验证和非活动账户、审计持久化故障测试；实现密码摘要校验、不透明随机 Bearer token、UserSession 持久化及原子成功审计。失败登录拒绝认证并记录失败事件。

- **实现概要：** 建立 auth 单数包、DTO、认证应用服务、密码编码器、token 摘要、session/audit repository 与事务边界；错误响应不暴露凭据。

- **验证命令：** `./mvnw test && ./mvnw verify`。

- **完成：** true

## Slice 2：受保护请求校验会话并登出

- **目标：** 受保护请求校验会话并登出。

- **Acceptance：** AC-4–AC-8。

- **依赖：** Slice 1。

- **测试或证明：** 为无 token、撤销/过期 token、停用账户、当前权限更新、空闲与绝对超时及重复登出编写 Web/集成测试；接入 Bearer 认证过滤器和当前 UserSession 校验。

- **实现概要：** 实现 15 分钟空闲和 8 小时绝对期限、每请求活动更新时间、即时撤销、401/403 映射及 POST /api/auth/logout 幂等语义。

- **验证命令：** `./mvnw test`。

- **完成：** true

## Slice 3：安全请求并完成密码恢复

- **目标：** 安全请求并完成密码恢复。

- **Acceptance：** AC-9–AC-12。

- **依赖：** Slice 1；平台邮件配置由 IAM-SETTING-01 Slice 1 提供运行时邮件配置读取。

- **测试或证明：** 测试未知/未验证/有效邮箱统一 202、邮件发送、令牌一次性/过期/并发使用、重置后撤销会话及停用用户保持停用；实现恢复令牌摘要、30 分钟有效期和密码更新事务。

- **实现概要：** 实现 password-recoveries API 与邮件端口适配，邮件链接由 frontendBaseUrl 生成；日志、审计、错误和存储不得泄漏令牌明文。

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
| 2026-10-20 | Slice 1–3 | `IdentityAndJsonbIntegrationTest` 覆盖成功/错误密码、session 持久化与登出；`ApiAuthorizationIntegrationTest` 覆盖受保护 API；Mailpit 容器冒烟覆盖邀请接受/登录/登出链路；`mise exec -- ./mvnw verify` | 通过；密码恢复并发、会话过期边界与审计存储故障未逐项自动化覆盖 |


### 修订历史


| 日期 | 原因和涉及切片 | 审批 |
| --- | --- | --- |
| 2026-10-20 | 初始实施计划；覆盖本 Spec 全部 AC | 用户批准；可进入 `sdd-build` |
