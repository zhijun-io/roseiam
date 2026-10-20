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

- **测试或证明：** 通过 Maven Wrapper、Spring Boot MVC/MyBatis-Plus/Validation/Actuator、PostgreSQL 迁移、Compose、Mailpit 和本地环境配置启动服务。就绪探针检查数据库；SMTP 本地配置使用 Mailpit 无认证/无 TLS。

- **实现概要：** 创建应用骨架与特性单数包（identity、user、role、audit、platformsetting），配置 Long 主键、无外键约束、版本化迁移；提供健康端点、Compose 和运行说明。

- **验证命令：** `./mvnw test && docker compose config && docker compose up --wait`。

- **完成：** true

## Slice 2：一次性初始化首位平台管理员

- **目标：** 一次性初始化首位平台管理员。

- **Acceptance：** AC-5–AC-8。

- **依赖：** Slice 1。

- **测试或证明：** `IdentityAndJsonbIntegrationTest` 验证首位管理员邀请重试不会重复创建用户和角色；容器冒烟验证启动时通过 Mailpit 投递邀请，另以 API 接受邀请并登录。无已激活管理员时创建 Invited User/UserInvitation，接受邀请后激活并保留 PlatformAdmin 角色；已有管理员时不覆盖。种入本地 general/mail 默认设置。

- **实现概要：** 添加一次性命令/runner、初始化配置校验、事务服务、邮件适配器和 Mailpit 集成验证；不得创建默认密码或将生产秘密写入仓库。

- **验证命令：** `./mvnw verify`。

- **完成：** true

## Slice 3：部署来源、安全密钥与备份恢复说明

- **目标：** 部署来源、安全密钥与备份恢复说明。

- **Acceptance：** AC-9–AC-11。

- **依赖：** Slice 1。

- **测试或证明：** Compose 配置验证通过；README 提供本地备份/恢复步骤。CORS allowlist 与实际生产备份恢复演练未包含在本轮自动化验证中。

- **实现概要：** 配置精确 CORS allowlist（空值拒绝跨域、拒绝通配符）、非 Cookie API；添加环境配置说明及数据库备份/恢复步骤。SMTP 加密密钥仅由部署 Secret 注入。

- **验证命令：** `./mvnw test && docker compose config`。

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
| 2026-10-20 | Slice 1–3 | `mise exec -- ./mvnw verify`、`docker compose config -q`、`docker compose -f compose.full.yaml config -q`、容器镜像构建及 `/actuator/health/readiness` 冒烟 | 通过；备份恢复演练与 CORS 配置测试未执行 |


### 修订历史


| 日期 | 原因和涉及切片 | 审批 |
| --- | --- | --- |
| 2026-10-20 | 初始实施计划；覆盖本 Spec 全部 AC | 用户批准；可进入 `sdd-build` |
