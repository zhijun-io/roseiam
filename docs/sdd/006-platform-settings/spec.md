# 平台设置与邮件配置

- **状态：** Approved
- **审批记录：** 用户于 2026-10-20 确认本规格及全部 AC；批准进入 `sdd-plan`
- **版本：** 1.0
- **Spec ID：** IAM-SETTING-01
- **父 PRD：** [IAM 管理平台 PRD](../../prd/iam-management-platform.md)，FR-6

## 结果与范围

获授权平台管理员可读取和维护平台展示、前端链接及 SMTP 配置，平台能使用当前配置发送邀请和密码重置邮件。

**范围内**
- `general` 与 `mail` 两种 PlatformSetting 类型及具名 API DTO。
- SMTP 凭据的加密保存、只写不读回和秘密脱敏。
- 初始设置与运行期配置的衔接。

**范围外**
- 前端页面、多个 SMTP 供应商管理、邮件模板编辑器和 SMTP 测试发送 API。
- 动态修改 CORS allowlist；来源仍由部署配置控制。

## 上游契约

- **设计就绪：** Ready — 设计评估覆盖 FR-6；见 [B1 设计就绪评估](../../design/iam-domain.md#设计就绪评估)。
- **设计/API：** [PlatformSetting 字段和规则](../../design/iam-domain.md#字段清单建议基线)、[设置权限与安全](../../design/iam-domain.md#审计与平台设置)、[平台设置 API](../../api.md#平台设置)。
- **依赖：** IAM-OPS-01、IAM-AUTH-01、IAM-RBAC-01。

## 用户场景与验收条件

### UC-1 — 查看平台设置

作为有 `platform-settings:read` 权限的管理员，我希望读取运行中的平台配置。

- **AC-1：** GET 返回 `general` 的 `platformName`、`defaultLocale`、`frontendBaseUrl` 和白名单允许返回的 SMTP 主机、端口、用户名、发件人、认证与 STARTTLS 状态。
- **AC-2：** SMTP 密码不出现在任何读取响应中；只返回 `smtpPasswordConfigured` 布尔值。
- **AC-3：** 无认证请求返回 `401`；无 `platform-settings:read` 权限返回 `403`。

### UC-2 — 更新平台设置

作为有 `platform-settings:write` 权限的 `PlatformAdmin`，我希望更新展示与邮件连接设置。

- **AC-4：** PATCH 只接受领域/API 契约中 `general` 与 `mail` 的白名单字段；`frontendBaseUrl` 必须是带主机名的绝对 HTTP(S) URL，不得包含 userinfo、query 或 fragment；SMTP host 非空、port 在 1–65535 范围内；无效字段或不一致的 SMTP 状态被拒绝，原值保持不变。
- **AC-5：** 更新请求省略的字段保持原值；仅提供的字段被更新。设置更新与对应审计事件在同一数据库事务原子提交。
- **AC-6：** `smtpPassword` 只写入；服务端使用部署提供的密钥加密存储，日志、审计载荷、错误响应及读取响应均不得包含明文或密文。
- **AC-7：** SMTP 密码字段省略表示不变；提供新非空值表示替换；只有当 SMTP 认证关闭时，`clearSmtpPassword: true` 才允许清除密码。
- **AC-8：** `smtpAuthEnabled=true` 时必须配置 SMTP username、非空密码和密码加密密钥；认证关闭时不要求用户名/密码；本地 Mailpit 默认无认证、无 TLS，不能把本地默认当作生产安全配置。

### UC-3 — 使用配置发送业务邮件

作为员工或管理员，我希望邀请和密码重置邮件使用最新有效的 SMTP 与前端基础 URL 设置。

- **AC-9：** 邀请和密码重置邮件使用当前 `mail` 设置投递，链接由 `frontendBaseUrl` 生成；成功提交设置后，后续新邮件使用新配置。
- **AC-10：** SMTP 投递是数据库事务外部副作用；当前实现由 after-commit 异步监听器 best-effort 发送，无自动重试或持久化 outbox。投递失败按对应邮件功能规格记录，不回滚已提交的设置或已经原子提交的审计事件。
- **AC-11：** 当前 API 对外为具名 DTO；服务端按 `type=general` 或 `type=mail` 映射至 JSON `value` 并验证类型及字段白名单，不向客户端暴露通用存储记录。

## 约束或验证说明

- 平台设置数据模型及字段列表以领域设计/API 契约为准；SMTP 密钥来自部署环境，不属于 PlatformSetting。
- CORS origin 由部署白名单决定，平台设置 API 不得更改 CORS 信任边界。

## 附录

### 相关文档

- [规格拆分](../breakdown.md)
- [PRD](../../prd/iam-management-platform.md)
- [领域设计](../../design/iam-domain.md)
- [API 契约](../../api.md)

### 修订历史

| 版本 | 日期 | 变更 | 审批 / 计划影响 |
| --- | --- | --- | --- |
| 1.1 | 2026-10-20 | 明确 SMTP 邮件采用 after-commit best-effort 异步投递，无 outbox 和自动重试 | 与用户批准的事件异步方向及平台设置切片实现对齐 |

| 版本 | 日期 | 变更 | 审批 / 计划影响 |
| --- | --- | --- | --- |
| 0.1 | 2026-10-20 | 初始探索性规格 | Pending；尚无计划 |
| 0.1 | 2026-10-20 | PRD 1.1 获批且 B1 设计评估 Ready；AC 未变 | Draft；尚无计划 |
| 1.0 | 2026-10-20 | 用户确认并批准全部 AC；无行为变更 | Approved；可进入 `sdd-plan` |
