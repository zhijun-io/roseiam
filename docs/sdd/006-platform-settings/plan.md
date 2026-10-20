# 平台设置与邮件配置实施计划

**状态：** Approved<br>
**审批记录：** 用户于 2026-10-20 批准本计划<br>
**Spec：** [`spec.md`](spec.md)

## 风险与依赖

- 邮件投递使用最新运行期设置。SMTP 密码加密密钥必须从部署环境注入；密钥缺失时启用 SMTP 认证的配置更新应失败关闭。
- IAM-OPS-01 Slice 1–2、IAM-AUTH-01 Slice 2、IAM-RBAC-01 Slice 1–3、IAM-AUDIT-01 Slice 1。

## Slice 1：读取并安全更新 general/mail 设置

- **目标：** 读取并安全更新 general/mail 设置。

- **Acceptance：** AC-1–AC-8、AC-11。

- **依赖：** IAM-OPS-01 Slice 1–2；IAM-AUTH-01 Slice 2；IAM-RBAC-01 Slice 1–3；IAM-AUDIT-01 Slice 1。

- **测试或证明：** 先写设置读取脱敏、读写权限、字段白名单、URL/SMTP 校验、PATCH 保留省略字段、加密密码轮换/清除、审计原子性和 schema 类型测试；实现设置存储及管理 API。

- **实现概要：** 实现 platformsetting 单数包，按 type + JSON value 保存 general/mail，DTO 屏蔽密文，仅返回 smtpPasswordConfigured；审计只记录变化字段。

- **验证命令：** `./mvnw verify`。

- **完成：** false

## Slice 2：使用最新设置发送邀请与密码重置邮件

- **目标：** 使用最新设置发送邀请与密码重置邮件。

- **Acceptance：** AC-9–AC-10。

- **依赖：** Slice 1；IAM-USER-01 Slice 1；IAM-AUTH-01 Slice 3。

- **测试或证明：** 编写设置变更后新邮件采用新 SMTP/frontendBaseUrl 的集成测试，覆盖投递失败不回滚已提交设置/审计；通过 Mailpit 验证实际邮件内容与链接。

- **实现概要：** 把 SMTP 和前端基础 URL 读取封装为邮件发送端口；邀请和密码恢复复用当前已提交设置，数据库事务外发送，结果遵照对应 Spec。

- **验证命令：** `./mvnw verify；docker compose up --wait`。

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
