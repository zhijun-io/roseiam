# 邀请与员工生命周期实施计划

**状态：** Approved<br>
**审批记录：** 用户于 2026-10-20 批准本计划<br>
**Spec：** [`spec.md`](spec.md)

## 风险与依赖

- 邀请可选角色需复用 RBAC 校验；停用与审计、会话撤销需同事务/即时生效。
- IAM-OPS-01 Slice 1、IAM-AUTH-01 Slice 1–2、IAM-RBAC-01 Slice 1–2、IAM-SETTING-01 Slice 1（运行期邮件配置读取）；审计写入依赖 IAM-AUDIT-01 Slice 1。

## Slice 1：邀请或重发员工邀请

- **目标：** 邀请或重发员工邀请。

- **Acceptance：** AC-1–AC-5。

- **依赖：** IAM-OPS-01 Slice 1；IAM-AUTH-01 Slice 1；IAM-RBAC-01 Slice 1–2；IAM-SETTING-01 Slice 1；IAM-AUDIT-01 Slice 1。

- **测试或证明：** 先写规范化邮箱唯一性、重复状态、权限拒绝、可选角色校验、邮件失败和邀请重发测试；实现邀请事务、令牌摘要和 SMTP 发送适配。

- **实现概要：** 新增 user 单数包中的 UserInvitation 流程与 API；事务内保存 Invited User、邀请、角色关联和审计，事务提交后发送邮件；失败可重试同邮箱。

- **验证命令：** `./mvnw verify`。

- **完成：** true

## Slice 2：员工接受邀请并激活账号

- **目标：** 员工接受邀请并激活账号。

- **Acceptance：** AC-6–AC-8。

- **依赖：** Slice 1。

- **测试或证明：** 为有效/过期/已用令牌和并发重复提交编写 PostgreSQL 集成测试；实现原子消费邀请、邮箱验证、密码摘要和 Active 状态转换。

- **实现概要：** 实现邀请接受 API；使用并发安全的一次性令牌消费，成功不自动创建会话，失败不改变账户或凭据。

- **验证命令：** `./mvnw verify`。

- **完成：** true

## Slice 3：管理员查询、修改、停用和启用用户

- **目标：** 管理员查询、修改、停用和启用用户。

- **Acceptance：** AC-9–AC-14。

- **依赖：** Slice 1、Slice 2；IAM-AUTH-01 Slice 2；IAM-RBAC-01 Slice 2；IAM-AUDIT-01 Slice 1。

- **测试或证明：** 为分页和敏感字段剔除、姓名更新、权限拒绝、资源不存在、停用撤销全部会话、最后管理员保护及重新启用旧会话不恢复编写集成测试。

- **实现概要：** 实现用户查询/姓名维护/状态命令端点；停用事务内写审计并撤销会话，启用只恢复账号状态；保持稳定 ID 与邮箱不可由姓名更新改变。

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
| 2026-10-20 | Slice 1–3 | `IdentityAndJsonbIntegrationTest` 覆盖 bootstrap 邀请重试/唯一角色关系；容器 Mailpit 冒烟覆盖接受邀请、激活和登录；`mise exec -- ./mvnw verify` | 通过；管理员邀请/停用分页等场景未逐项自动化覆盖 |


### 修订历史


| 日期 | 原因和涉及切片 | 审批 |
| --- | --- | --- |
| 2026-10-20 | 初始实施计划；覆盖本 Spec 全部 AC | 用户批准；可进入 `sdd-build` |
