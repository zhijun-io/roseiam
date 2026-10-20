# IAM 管理平台 API 契约

- **状态：** Accepted API contract（对应 PRD 1.1 Approved B1）
- **版本：** 0.8
- **日期：** 2026-10-20
- **范围：** PRD B1；员工身份管理 API。
- **领域规则：** [IAM 领域设计](design/iam-domain.md)

本文定义 PRD 1.1 Approved B1 的资源边界和外部行为，是供客户端与 `sdd-spec` 使用的接受契约；切片级验收细节仍由各自 Draft/Approved spec 管理。

## 通用约定

- Base path 建议为 `/api`。首版不额外加入 URL 版本段；未来需要不兼容演进时再定义版本策略。
- JSON 请求/响应；所有凭据与管理操作必须经 TLS。
- 前后端分离首版使用 `Authorization: Bearer <opaque-token>`；token 是随机不透明值，只保存摘要并关联到服务端 `UserSession`，不使用自包含 JWT。此方式可立即撤销，并避免 Cookie/CSRF 机制。
- CORS 使用精确的前端来源白名单，不允许通配来源；token 不通过 URL 或 Cookie 传递。当前用户由服务端 UserSession 确定；禁止请求体或查询参数指定操作者 ID。
- 错误响应采用 RFC 9457 Problem Details；已知业务、校验和认证错误会提供稳定 `title`、`instance` 请求路径，部分响应也提供 `urn:roseiam:problem:<code>` 类型。当前重复键和未处理异常沿用 ProblemDetail 默认 `about:blank` 类型。请求 ID 通过 `X-Request-Id` 响应头返回，不放在错误响应体。不得返回堆栈、秘密或可区分账号存在性的密码重置信息。
- 未认证返回 `401`；已认证但无权返回 `403`；不存在返回 `404`；语义冲突返回 `409`；格式、校验及当前实现中的领域规则拒绝返回 `400`。当前实现未使用 `422`。
- 用户和审计事件列表使用 `page`（从 0 开始）、`size` 分页，服务端将页长限制为 1–100；角色与权限目录当前返回完整列表，不分页。
- 权限在服务端对每个操作判定。可复用 `GET`、幂等 `PUT`/`DELETE` 语义；非幂等 `POST` 的重试行为在有重试需求时另行定义。

## 身份与会话

| 方法与资源 | 需求 | 用途 / 输入 | 响应 | 授权与语义 |
| --- | --- | --- | --- | --- |
| `POST /api/auth/login` | FR-1 | 登录；邮箱、密码 | `200` 会话已建立；返回一次性 opaque bearer token、token 类型、`idleExpiresAt` 与 `absoluteExpiresAt` | 匿名；邮箱未验证或账号非活动状态拒绝；不实现登录限流 |
| `POST /api/auth/logout` | FR-1 | 登出当前会话 | `204` | 必须提供当前 Bearer token；立即撤销对应 UserSession；再次提交已撤销 token 仍不改变状态 |
| `POST /api/auth/password-resets` | FR-1 | 请求密码重置；邮箱 | `202` 通用受理响应 | 匿名；无论账号是否存在均返回相同外部结果；仅对存在且已验证邮箱发送邮件 |
| `POST /api/auth/password-resets/completion` | FR-1 | 重置密码；一次性令牌、新密码 | `204` | 匿名但须持有效令牌；30 分钟有效且只能使用一次；完成后撤销该用户全部 UserSession；无复杂度策略 |

## 用户与个人资料

| 方法与资源 | 需求 | 用途 / 输入 | 响应 | 授权与语义 |
| --- | --- | --- | --- | --- |
| `POST /api/user-invitations` | FR-2 | 邀请员工；目标邮箱、可选角色 ID | `201` UserInvitation 已创建；返回邀请 ID 与到期时间，不返回令牌 | `users:invite`；创建 `Invited` 状态 User；对已有 `Invited` 邮箱重发并撤销旧令牌；已有 `Active` 或 `Disabled` 用户返回 `409`（停用用户应走重新启用流程）；邮件在事务提交后异步投递，SMTP 失败不改变 `201` 响应，管理员可重新邀请 |
| `POST /api/user-invitations/acceptance` | FR-2 | 接受用户邀请；邀请令牌、密码、必填 `name` | `200` 已有 User 激活并验证邮箱 | 匿名；邀请有效 72 小时且只能使用一次；成功后令牌失效 |
| `GET /api/users` | FR-2 | 查询用户；分页和白名单过滤 | `200` 用户概要页 | `users:read` |
| `GET /api/users/{userId}` | FR-2 | 查询单个员工 | `200` 用户概要 | `users:read` |
| `PATCH /api/users/{userId}` | FR-2 | 修改管理员可维护的 `name`（不含已验证邮箱） | `200` 更新后的概要 | `users:write`；不可更改稳定 ID 或直接替换邮箱；每次实质变更产生审计事件 |
| `POST /api/users/{userId}/disable` | FR-2、FR-8 | 停用员工；无请求体 | `200` 当前账号状态 | `users:disable`；幂等；成功后立即拒绝后续请求并使会话失效 |
| `POST /api/users/{userId}/enable` | FR-2 | 重新启用员工 | `200` 当前账号状态 | `users:enable`；旧会话不恢复，用户需重新登录 |
| `GET /api/me` | FR-5 | 读取当前用户资料 | `200` 当前用户资料 | 已认证 UserSession；身份来自当前 token |
| `PATCH /api/me` | FR-5 | 修改本人 `name` | `200` 更新后的资料 | 已认证 UserSession；首版不提供自助邮箱变更；实质变更写审计，仅记录字段名 |
| `GET /api/me/settings` | FR-5 | 读取当前用户设置（`locale`、`timeZone`、`theme`） | `200` 用户设置对象；无记录时返回默认值 `zh-CN`、`Asia/Shanghai`、`system` | 已认证 UserSession；只能读取自己的设置 |
| `PUT /api/me/settings` | FR-5 | 完整替换当前用户设置对象，必须提供 `locale`、`timeZone`、`theme` | `200` 更新后设置对象 | 已认证 UserSession；未知键拒绝；省略必填键返回 `400`；成功后保存完整 `preferences` 值 |

## 角色与权限

| 方法与资源 | 需求 | 用途 / 输入 | 响应 | 授权与语义 |
| --- | --- | --- | --- | --- |
| `GET /api/permissions` | FR-3 | 查询平台权限目录 | `200` 权限列表 | `roles:read`；首版目录由平台定义 |
| `GET /api/roles` | FR-3 | 查询角色 | `200` 角色列表 | `roles:read` |
| `POST /api/roles` | FR-3 | 创建角色；名称、描述、权限 ID 集合 | `201` 新角色 | `roles:write`；名称冲突返回 `409`；记录审计；当前响应不设置 `Location` |
| `GET /api/roles/{roleId}` | FR-3 | 查询角色及权限 | `200` 角色 | `roles:read` |
| `PATCH /api/roles/{roleId}` | FR-3 | 修改角色名称、描述或权限集合 | `200` 更新后角色 | `roles:write`；未知权限拒绝；记录审计 |
| `DELETE /api/roles/{roleId}` | FR-3 | 删除自定义角色 | `204` | `roles:write`；仍有 UserRole 关联时 `409`；内置角色不可删除 |
| `PUT /api/users/{userId}/roles/{roleId}` | FR-3 | 创建 UserRole 关联 | `204` | `user-roles:write`；重复分配幂等；记录审计 |
| `DELETE /api/users/{userId}/roles/{roleId}` | FR-3 | 删除 UserRole 关联 | `204` | `user-roles:write`；重复撤销幂等；记录审计 |

## 平台设置

平台设置存储层采用 `type` + JSON `value`，首批类型为 `general` 与 `mail`；用户设置存储层采用 `userId` + `type` + JSON `value`，首批类型为 `preferences`。API 维持具名 DTO，不暴露通用存储记录；读取和更新仍按下面列出的业务字段操作，服务端将字段映射到相应类型的 JSON 对象并验证类型白名单与字段结构。

| 方法与资源 | 需求 | 用途 / 输入 | 响应 | 授权与语义 |
| --- | --- | --- | --- | --- |
| `GET /api/platform-settings` | FR-6 | 读取平台名称、默认语言、前端基础 URL、SMTP 主机/端口/用户名/发件人/认证与 TLS 状态 | `200` 设置集合，含 `smtpPasswordConfigured` 布尔值 | `platform-settings:read`；只返回白名单字段，不返回 SMTP 密码 |
| `PATCH /api/platform-settings` | FR-6 | 更新 `platformName`、`defaultLocale`、`frontendBaseUrl`、SMTP 主机/端口/用户名/密码/发件人/认证/TLS 字段 | `200` 更新结果，含 `smtpPasswordConfigured` | `platform-settings:write`；未知键拒绝；省略字段保持原值；SMTP 密码只写入不读回；成功变更写审计且不记录设置值 |

## 审计

| 方法与资源 | 需求 | 用途 / 输入 | 响应 | 授权与语义 |
| --- | --- | --- | --- | --- |
| `GET /api/audit-events` | FR-7 | 按已批准字段分页查询事件 | `200` 只读事件页 | `audit-events:read`；按时间、操作者、动作、目标、结果筛选；保留 365 天 |
| `GET /api/audit-events/{eventId}` | FR-7 | 读取单条事件 | `200` 审计事件 | `audit-events:read`；无更新/删除操作 |

## 集成契约

邀请和密码重置通过邮件发送一次性、限时凭据入口；SMTP 与 `frontendBaseUrl` 由平台管理员在平台设置中配置。当前实现通过事务提交事件和异步监听器发送 SMTP 邮件，不使用消息队列或自动重试，属于 best-effort 投递；进程在事件处理前崩溃可能导致邮件未发送。邀请和邀请记录先提交，接口返回 `201`；投递失败时记录含请求 ID 和异常的运维日志，管理员可重新邀请同一邮箱（旧令牌撤销）。密码重置请求始终返回通用 `202`；投递失败不改变响应，重置记录仍有效，用户可再次请求（旧令牌撤销并生成新令牌）。请求和审计载荷不得包含明文 token。

## 安全与跨域约定

- 本仓库只实现后端 API，不实现前端。CORS 来源由环境配置为精确白名单，默认拒绝其他来源。
- 不实现登录限流、失败次数锁定或密码复杂度/轮换规则；密码仍必须非空且经 Spring Security 密码编码器安全存储。
- UserSession 使用 15 分钟空闲期限和 8 小时绝对期限，由服务端强制执行；登出、密码重置、账号停用均立即撤销。
- 登录使用 Spring Security `AuthenticationFilter` + `DaoAuthenticationProvider`；受保护请求通过 resource-server opaque-token introspector 验证 UserSession；登出使用 Spring Security LogoutFilter。每个受保护请求检查 UserSession 有效性、User 状态及当前 RBAC 权限；角色/权限变更下一个请求即生效。
- 成功登录创建 UserSession，以及关键账号、资料、角色、权限、设置变更，必须与对应 AuditEvent 在同一数据库事务中提交；审计写入失败时业务变更和会话创建均回滚。SMTP 发送不属于该事务。失败登录仍写入审计事件；审计写入故障不改变拒绝认证的结果，并记录运维错误。
- 审计保留 365 天，到期自动清理；API 不提供更新或删除。

## 部署配置

- SMTP 主机、端口、用户名、密码、发件人、认证和 STARTTLS 由 `PlatformSetting` 管理；本地 Docker Compose 提供 Mailpit（SMTP `mailpit:1025`，无认证、无 TLS）。邮件由事务提交后的异步监听器投递；首位管理员初始化也发布异步邀请事件，SMTP 故障不会阻止应用启动。
- `frontendBaseUrl` 由 `PlatformSetting` 管理；精确 CORS origin 仍由部署环境白名单提供，未配置的 origin 默认拒绝。
- SMTP 密码仅通过 TLS 提交，服务端使用部署环境提供的加密密钥加密保存；读取接口只返回 `smtpPasswordConfigured`，不回显密码；审计和日志仅记设置字段发生变化，不记录值。
- SMTP 密码更新时省略该字段表示不变；提供新非空值表示替换。清除密码使用 `clearSmtpPassword: true`，仅在禁用 SMTP 认证时允许。
- 首版不提供邮箱变更端点。
- 审计查询产生审计事件；该查询事件本身不再次触发审计写入。

## 修订历史

| 版本 | 日期 | 变更 | 状态 |
| --- | --- | --- | --- |
| 0.1 | 2026-10-20 | 初始 REST API 契约提案 | 待确认 |
| 0.2 | 2026-10-20 | 对齐前后端分离、不透明 UserSession、邮件验证/邀请/重置和 UserSetting | PRD 1.1 待审批 |
| 0.3 | 2026-10-20 | 登录/登出路由改为指定路径；SMTP 与前端 URL 改为平台设置，并补充字段与 Mailpit 本地约定 | PRD 1.1 待审批 |
| 0.4 | 2026-10-20 | 说明设置使用 type + JSON value 存储，公开 API 继续提供具名 DTO | PRD 1.1 待审批 |
| 0.5 | 2026-10-20 | 明确邀请与激活语义、设置默认值和完整替换、登录期限及审计失败行为；移除支持联系邮箱 | PRD 1.1 待审批 |
| 0.6 | 2026-10-20 | 用户批准 PRD 1.1 B1 默认方案；确认 API 契约并通过设计就绪评估 | Ready |
| 0.7 | 2026-10-20 | 对齐当前实现：修正错误响应、角色列表/创建响应、邮件投递与失败行为，并记录认证过滤器实现 | Implementation-aligned |
| 0.8 | 2026-10-20 | 补充异常响应 `type` 的实际差异和禁用请求体；与控制器实现复核 | Implementation-aligned |
| 0.9 | 2026-10-20 | 邮件改为事务提交后的 best-effort 异步投递；邀请 SMTP 故障不再改写已创建资源的 `201` 响应 | Implementation-aligned |
