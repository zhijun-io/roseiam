# 个人资料与用户偏好实施计划

**状态：** Approved<br>
**审批记录：** 用户于 2026-10-20 批准本计划<br>
**Spec：** [`spec.md`](spec.md)

## 风险与依赖

- 个人资料和偏好以当前服务端会话身份做所有权校验；不得通过请求参数选择其他用户。
- IAM-OPS-01 Slice 1、IAM-AUTH-01 Slice 2、IAM-USER-01 Slice 2；审计写入依赖 IAM-AUDIT-01 Slice 1。

## Slice 1：查看并更新当前用户资料

- **目标：** 查看并更新当前用户资料。

- **Acceptance：** AC-1–AC-3。

- **依赖：** IAM-OPS-01 Slice 1；IAM-AUTH-01 Slice 2；IAM-USER-01 Slice 2；IAM-AUDIT-01 Slice 1。

- **测试或证明：** 先写当前身份绑定、不能读取他人、姓名校验、禁止改邮箱/角色/ID、审计和未认证测试；实现 GET/PATCH /api/me 与审计事务。

- **实现概要：** 实现 me DTO 与应用服务；主体始终来自当前有效 UserSession，姓名真实变更记录审计。

- **验证命令：** `./mvnw test && ./mvnw verify`。

- **完成：** false

## Slice 2：读取和完整替换个人偏好

- **目标：** 读取和完整替换个人偏好。

- **Acceptance：** AC-4–AC-8。

- **依赖：** Slice 1。

- **测试或证明：** 先写缺省值、完整字段校验、BCP 47、IANA 时区、主题枚举、未知键、所有权隔离及无审计行为测试；实现 UserSetting `preferences` JSON value 的读写。

- **实现概要：** 实现 GET/PUT /api/me/settings；以 userId + type 保持唯一性，存储未初始化时返回约定默认值，使用具名 DTO 且不暴露通用记录。

- **验证命令：** `./mvnw verify`。

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
