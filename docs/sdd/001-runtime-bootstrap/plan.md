# 运行环境与管理员初始化实施计划

**状态：** Approved<br>
**审批记录：** 用户于 2026-10-20 批准本计划<br>
**Spec：** [`spec.md`](spec.md)

## 风险与依赖

- 计划采用设计建议的 Spring Boot 4.1.1、Java 25、Maven、PostgreSQL 18 与 Docker Compose。仓库目前没有应用源码或构建配置；首个切片需建立项目骨架，后续命令基于生成的 Maven Wrapper。
- 无；该计划是其他切片的运行前置。

## Slice 1：可重复启动 IAM 本地服务

- **目标：** 可重复启动 IAM 本地服务。

- **Acceptance：** AC-1–AC-4。

- **依赖：** 无。

- **测试或证明：** 先验证新项目的启动和服务依赖测试失败；新增 Maven Wrapper、Spring Boot MVC/JPA/Validation/Actuator、PostgreSQL 迁移、Compose、Mailpit 和本地环境配置。就绪探针检查数据库及必要初始化；SMTP 本地配置固定为 Mailpit 无认证/无 TLS。

- **实现概要：** 创建应用骨架与特性单数包（auth、user、role、audit、platformsetting、usersetting），配置 Long 主键、无外键约束、版本化迁移；提供健康端点、Compose 和运行说明。

- **验证命令：** `./mvnw test && docker compose config && docker compose up --wait`。

- **完成：** false

## Slice 2：一次性初始化首位平台管理员

- **目标：** 一次性初始化首位平台管理员。

- **Acceptance：** AC-5–AC-8。

- **依赖：** Slice 1。

- **测试或证明：** 为初始化命令和重复执行场景先写应用集成测试；实现无管理员时创建 Invited User/UserInvitation、发验证邮件，激活后授予 PlatformAdmin；有管理员时拒绝覆盖并返回可识别结果。种入本地 general/mail 默认设置。

- **实现概要：** 添加一次性命令/runner、初始化配置校验、事务服务、邮件适配器和 Mailpit 集成验证；不得创建默认密码或将生产秘密写入仓库。

- **验证命令：** `./mvnw verify`。

- **完成：** false

## Slice 3：部署来源、安全密钥与备份恢复说明

- **目标：** 部署来源、安全密钥与备份恢复说明。

- **Acceptance：** AC-9–AC-11。

- **依赖：** Slice 1。

- **测试或证明：** 为 CORS 精确来源和部署配置绑定编写配置测试；通过文档演练验证备份、恢复后 API 可用。

- **实现概要：** 配置精确 CORS allowlist（空值拒绝跨域、拒绝通配符）、非 Cookie API；添加环境配置说明及数据库备份/恢复步骤。SMTP 加密密钥仅由部署 Secret 注入。

- **验证命令：** `./mvnw test && docker compose config`。

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
