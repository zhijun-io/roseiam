# 个人资料与用户偏好

- **状态：** Approved
- **审批记录：** 用户于 2026-10-20 确认本规格及全部 AC；批准进入 `sdd-plan`
- **版本：** 1.0
- **Spec ID：** IAM-ME-01
- **父 PRD：** [IAM 管理平台 PRD](../../prd/iam-management-platform.md)，FR-5

## 结果与范围

已登录员工可查看当前身份、更新自己的 `name`，并读取或替换自己的语言、时区和主题偏好。

**范围内**
- 当前用户资料读取和本人姓名修改。
- `preferences` 类型 UserSetting 的读取、完整替换和缺省值。
- 以当前会话身份实施自有数据访问边界。

**范围外**
- 修改邮箱、密码、账号状态、角色或权限。
- 用户之间读取或编辑他人资料/偏好。

## 上游契约

- **设计就绪：** Ready — 设计评估覆盖 FR-5；见 [B1 设计就绪评估](../../design/iam-domain.md#设计就绪评估)。
- **设计/API：** [User 与 UserSetting 字段规则](../../design/iam-domain.md#字段清单建议基线)、[用户设置规则](../../design/iam-domain.md#审计与平台设置)、[个人资料与用户设置 API](../../api.md#用户与个人资料)。
- **依赖：** IAM-OPS-01、IAM-AUTH-01、IAM-USER-01。

## 用户场景与验收条件

### UC-1 — 查看并修改个人资料

作为已登录员工，我希望查看当前用户信息并维护自己的姓名。

- **AC-1：** `GET /api/me` 从当前有效 `UserSession` 确定用户并返回其个人资料；请求不能通过参数选择其他 User。
- **AC-2：** `PATCH /api/me` 只接受必填 `name` 更新；空值或超出领域设计约束的姓名被拒绝，邮箱、状态、角色、权限和 ID 不可由请求体改变。
- **AC-3：** 姓名实际变更写入对应审计事件；无效或未认证请求不改变资料。

### UC-2 — 管理自己的语言、时区与主题

作为已登录员工，我希望设置适合自己的显示偏好。

- **AC-4：** `GET /api/me/settings` 返回当前用户 `preferences`；没有存储记录时仍返回 `locale=zh-CN`、`timeZone=Asia/Shanghai`、`theme=system`。
- **AC-5：** `PUT /api/me/settings` 必须完整提供 `locale`、`timeZone`、`theme`，整体替换 `preferences`；未知键、缺少必填键、无效时区或不支持的主题被拒绝且不产生部分更新。
- **AC-6：** `theme` 只接受 `system`、`light` 或 `dark`；`locale` 必须是格式有效的 BCP 47 语言标签，不额外使用首版 locale 白名单；`timeZone` 必须是有效 IANA 时区标识。
- **AC-7：** 设置记录使用 `userId + type` 唯一对应当前用户的 `preferences`；用户不能通过 path、body 或 query 的其他用户 ID 访问他人的设置。
- **AC-8：** 首批用户偏好变更不产生审计事件；偏好 API 不能更改身份、安全或授权字段。

## 约束或验证说明

- API 使用具名 DTO；存储层 `type=preferences`、JSON `value` 不直接暴露给客户端。
- 所有请求均须通过有效会话检查；个人资源归属检查以服务端认证身份为准。

## 附录

### 相关文档

- [规格拆分](../breakdown.md)
- [PRD](../../prd/iam-management-platform.md)
- [领域设计](../../design/iam-domain.md)
- [API 契约](../../api.md)

### 修订历史

| 版本 | 日期 | 变更 | 审批 / 计划影响 |
| --- | --- | --- | --- |
| 0.1 | 2026-10-20 | 初始探索性规格 | Pending；尚无计划 |
| 0.1 | 2026-10-20 | PRD 1.1 获批且 B1 设计评估 Ready；AC 未变 | Draft；尚无计划 |
| 1.0 | 2026-10-20 | 用户确认并批准全部 AC；无行为变更 | Approved；可进入 `sdd-plan` |
