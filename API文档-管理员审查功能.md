# 管理员审查功能 API 文档

> 本文档面向前端开发，涵盖所有需要管理员权限的功能，包括评论审核、用户管理、备份管理、搜索索引管理、计数修复、数据权限体系，以及安全审计。

---

## 通用说明

### 基础路径
```
所有 API 以 /api 开头
```

### 认证方式
- 所有管理员接口均需携带 JWT Token，且登录用户必须拥有 `ROLE_ADMIN` 角色
- Header: `Authorization: Bearer <token>`

### 统一响应格式
```json
{
  "code": 200,
  "message": "成功",
  "data": {}
}
```

---

## 目录

1. [管理员角色体系](#1-管理员角色体系)
2. [评论审核](#2-评论审核)
3. [用户管理](#3-用户管理)
4. [备份管理](#4-备份管理)
5. [搜索索引管理](#5-搜索索引管理)
6. [计数修复](#6-计数修复)
7. [数据权限隔离机制](#7-数据权限隔离机制)
8. [安全审计](#8-安全审计)

---

## 1. 管理员角色体系

### 1.1 角色定义

系统定义两种角色，存储在 JWT Token 的 `authorities` 字段中：

| 角色 | Spring Security 标识 | 说明 |
|------|---------------------|------|
| 普通用户 | `ROLE_USER` | 所有注册用户的默认角色 |
| 管理员 | `ROLE_ADMIN` | 管理员角色，需在 JWT 签发时注入 |

> JWT Token 中 `authorities` 字段示例：`["ROLE_USER"]` 或 `["ROLE_ADMIN", "ROLE_USER"]`

### 1.2 管理员的两种权限机制

| 机制 | 实现方式 | 影响范围 |
|------|---------|---------|
| **声明式** | `@PreAuthorize("hasRole('ADMIN')")` 注解 | 拦截无权限请求，返回 403 |
| **数据层** | MyBatis-Plus `DataPermissionInterceptor` | 管理员自动绕过 `user_id` 数据隔离，可查看全量数据 |
| **编程式** | `UserContextHolder.isAdmin()` 代码判断 | 分类权限等业务逻辑判断 |

### 1.3 管理员可查看的数据范围

| 数据表 | 普通用户 | 管理员 |
|--------|---------|--------|
| `article` | 仅自己的文章 | **所有用户的文章** |
| `category` | 自己的分类 + 系统公共分类（user_id=0） | **所有分类** |
| `article_comment` | 无限制（公开评论） | 无限制 + 审核权限 |
| `user` | 仅自己的信息 | **所有用户信息** |

---

## 2. 评论审核

当配置 `app.comment.auto-approve=false` 时，用户发表的评论进入待审核状态，需要管理员审核通过后才能公开展示。

### 2.1 审核配置项

```properties
# 评论自动审核开关（默认 true，设为 false 启用审核流程）
app.comment.auto-approve=${COMMENT_AUTO_APPROVE:true}

# 敏感词处理策略
# REJECT - 直接拒绝评论
# FLAG   - 替换敏感词为 *** 并标记为待审核
app.comment.sensitive-word-action=${COMMENT_SENSITIVE_WORD_ACTION:REJECT}
```

### 2.2 评论状态说明

| status | 含义 | 说明 |
|--------|------|------|
| 0 | 待审核 | 新评论等待管理员审核 |
| 1 | 已通过 | 审核通过，公开展示 |
| 2 | 已拒绝 | 审核拒绝，不展示 |

### 2.3 获取待审核评论列表

```
GET /api/comments/pending
```

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": [
    {
      "id": 1,
      "articleId": 100,
      "userId": 5,
      "userNickname": "张三",
      "userAvatar": null,
      "parentId": null,
      "replyToUserId": null,
      "replyToUsername": null,
      "content": "这是一条待审核的评论",
      "status": 0,
      "replyCount": 0,
      "replies": [],
      "createTime": "2026-05-14T10:00:00",
      "updateTime": "2026-05-14T10:00:00"
    }
  ]
}
```

### 2.4 审核评论（通过/拒绝）

```
PUT /api/comments/{commentId}/review?status={status}
```

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| commentId | Long | 评论 ID |

**请求参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| status | Integer | 是 | 1-审核通过，2-审核拒绝 |

**响应示例（通过）**:
```json
{
  "code": 200,
  "message": "审核通过",
  "data": null
}
```

**响应示例（拒绝）**:
```json
{
  "code": 200,
  "message": "审核拒绝",
  "data": null
}
```

**行为说明**:
- 审核通过（status=1）：评论状态变为已通过，对所有人可见，返回 `"审核通过"`
- 审核拒绝（status=2）：评论被标记为拒绝，文章的 `comment_count` 会自动重新计算（仅统计已通过的评论），返回 `"审核拒绝"`

### 2.5 敏感词过滤

系统内置敏感词库：`["诈骗", "赌博", "色情", "暴力", "毒品", "枪支"]`

根据 `app.comment.sensitive-word-action` 配置：
- **REJECT（拒绝）**：包含敏感词的评论直接拒绝，返回错误提示
- **FLAG（标记）**：敏感词被替换为 `***`，评论内容保留但标记为待审核状态

---

## 3. 用户管理

管理员可以查看所有用户信息、搜索用户、重置任意用户密码。

### 3.1 获取所有用户列表

```
GET /api/users
```

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": [
    {
      "id": 1,
      "username": "admin",
      "nickname": "管理员",
      "email": "admin@example.com",
      "avatar": null,
      "articleCount": 15,
      "role": "ROLE_ADMIN",
      "status": 1,
      "createTime": "2025-01-01T00:00:00"
    },
    {
      "id": 2,
      "username": "user1",
      "nickname": "张三",
      "email": "zhangsan@example.com",
      "avatar": null,
      "articleCount": 8,
      "role": "ROLE_USER",
      "status": 1,
      "createTime": "2025-03-15T10:00:00"
    }
  ]
}
```

### 3.2 搜索用户

按用户名、昵称或邮箱模糊搜索用户。

```
GET /api/users/search?keyword=张三
```

**请求参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| keyword | String | 是 | 搜索关键词（匹配用户名、昵称、邮箱） |

### 3.3 获取用户总数

```
GET /api/users/stats
```

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": 128
}
```

### 3.4 重置用户密码

管理员可直接重置任意用户的密码，无需验证旧密码。

```
POST /api/users/{id}/reset-password
```

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 用户 ID |

**请求参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| newPassword | String | 是 | 新密码（6-100 位） |

**响应示例**:
```json
{
  "code": 200,
  "message": "密码重置成功",
  "data": null
}
```

> 与用户自主修改密码（需提供旧密码）不同，此接口为管理员强制重置，无需旧密码验证。

---

## 4. 备份管理

管理员可以手动触发全站备份、查看备份历史、清理过期备份。

### 4.1 手动触发备份

```
POST /api/backup/trigger
```

立即执行一次全站备份（备份所有公开文章的 Markdown 内容为 ZIP 压缩包）。

**响应示例**:
```json
{
  "code": 200,
  "message": "备份任务已触发，备份文件: backup_20260514_143000.zip",
  "data": "backup_20260514_143000.zip"
}
```

### 4.2 查看备份记录

```
GET /api/backup/records
```

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": [
    {
      "id": 1,
      "fileName": "backup_20260514_020000.zip",
      "fileSize": 2048000,
      "articleCount": 256,
      "status": "SUCCESS",
      "createTime": "2026-05-14T02:00:00"
    },
    {
      "id": 2,
      "fileName": "backup_20260513_020000.zip",
      "fileSize": 1980000,
      "articleCount": 250,
      "status": "SUCCESS",
      "createTime": "2026-05-13T02:00:00"
    }
  ]
}
```

### 4.3 清理过期备份

```
DELETE /api/backup/clean?retentionDays=30
```

**请求参数**:
| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| retentionDays | Integer | 否 | 30 | 保留天数，早于此天数的备份文件将被删除 |

**响应示例**:
```json
{
  "code": 200,
  "message": "已清理 5 条过期备份记录",
  "data": "已清理 5 条过期备份记录"
}
```

### 4.4 自动备份配置

```properties
# 备份调度 cron 表达式（默认每天凌晨 2 点）
app.backup.cron=${APP_BACKUP_CRON:0 0 2 * * ?}

# 备份文件保留天数
app.backup.retention-days=${APP_BACKUP_RETENTION_DAYS:30}

# 备份文件存储目录
app.export.dir=${APP_EXPORT_DIR:exports}
```

---

## 5. 搜索索引管理

管理员可以重建 Elasticsearch 全文搜索索引，以及对单篇文章进行索引操作。

### 5.1 重建所有搜索索引

重新从 MySQL 读取所有文章数据，全量重建 Elasticsearch 索引。

```
POST /api/search/rebuild-indexes
```

**响应示例**:
```json
{
  "code": 200,
  "message": "索引重建完成",
  "data": 256
}
```

> `data` 为已索引的文章数量。此操作为全量重建，数据量大时可能耗时较长。

### 5.2 索引单篇文章

将指定文章同步到 Elasticsearch 索引。

```
POST /api/search/index/{articleId}
```

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| articleId | Long | 文章 ID |

**响应示例**:
```json
{
  "code": 200,
  "message": "索引成功",
  "data": true
}
```

### 5.3 删除单篇文章索引

从 Elasticsearch 中移除指定文章的索引。

```
DELETE /api/search/index/{articleId}
```

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| articleId | Long | 文章 ID |

---

## 6. 计数修复

修复文章的 `like_count`、`comment_count`、`favorite_count` 计数字段，从源表重新计算以确保数据一致性。这两个接口已通过 `@PreAuthorize("hasRole('ADMIN')")` 进行了权限保护。

### 6.1 修复所有文章的计数

```
POST /api/admin/articles/reconcile-counts
```

**响应示例**:
```json
{
  "code": 200,
  "message": "计数修复完成",
  "data": {
    "elapsedMs": 1250
  }
}
```

**实现说明**:
- 从 `article_like`、`article_comment`（仅 status=1 已通过评论）、`user_favorite` 三张表重新计算
- 使用一条 SQL 批量更新所有文章
- 绕过数据权限拦截器（`@InterceptorIgnore(dataPermission = "true")`），确保覆盖所有用户的文章
- 返回执行耗时（毫秒）

### 6.2 修复单篇文章的计数

```
POST /api/admin/articles/{id}/reconcile-counts
```

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 文章 ID |

**响应示例**:
```json
{
  "code": 200,
  "message": "文章(id=100)计数修复完成",
  "data": "success"
}
```

---

## 7. 数据权限隔离机制

### 7.1 机制概述

系统通过 MyBatis-Plus 的 `DataPermissionInterceptor` 在 SQL 层面自动注入数据过滤条件，实现多租户式的数据隔离。

### 7.2 数据过滤规则

| 角色 | article 表 | category 表 |
|------|-----------|-------------|
| 未登录 | 不过滤（由业务层控制） | 不过滤 |
| 普通用户（ROLE_USER） | `WHERE user_id = <当前用户ID>` | `WHERE (user_id = 0 OR user_id = <当前用户ID>)` |
| 管理员（ROLE_ADMIN） | **不过滤，可查看全部** | **不过滤，可查看全部** |

### 7.3 绕过数据权限

对于确实需要跨用户查询的管理功能（如计数修复），使用 `@InterceptorIgnore(dataPermission = "true")` 注解绕过数据权限拦截器。当前使用此机制的方法：

| Mapper | 方法 | 用途 |
|--------|------|------|
| `ArticleMapper` | `selectByIdIgnorePermission()` | 评论审核等内部逻辑需要查找任意文章 |
| `ArticleMapper` | `reconcileAllCounts()` | 修复所有文章的计数字段 |
| `ArticleMapper` | `reconcileCountsByArticleId()` | 修复单篇文章的计数字段 |
| `AdminMapper` | `selectAllArticlesIgnorePermission()` | 管理员查看所有文章 |
| `AdminMapper` | `selectAllCategoriesIgnorePermission()` | 管理员查看所有分类 |
| `AdminMapper` | `getUserStats()` | 获取用户统计数据 |

---

## 8. 安全审计

### 8.1 权限保护现状

所有 14 个管理员端点均已添加 `@PreAuthorize("hasRole('ADMIN')")` 保护，未授权访问返回 403。

| 控制器 | 受保护端点 |
|--------|-----------|
| `AdminController` | `/api/admin/articles/reconcile-counts`, `/api/admin/articles/{id}/reconcile-counts` |
| `UserController` | `/api/users`, `/api/users/search`, `/api/users/stats`, `/api/users/{id}/reset-password` |
| `InteractionController` | `/api/comments/{commentId}/review`, `/api/comments/pending` |
| `BackupController` | `/api/backup/trigger`, `/api/backup/records`, `/api/backup/clean` |
| `SearchController` | `/api/search/rebuild-indexes`, `/api/search/index/{articleId}` (POST + DELETE) |

### 8.2 实现方式

每个管理员端点方法上添加 `@PreAuthorize("hasRole('ADMIN')")` 注解，配合 `SecurityConfig` 中的 `@EnableMethodSecurity` 生效。Spring Security 在方法调用前拦截，验证当前用户的 JWT Token 中是否包含 `ROLE_ADMIN` 权限，不满足则返回 403。

### 8.3 管理员功能入口设计建议（前端）

建议在前端为管理员用户单独设计管理面板入口：

1. **顶部导航栏**：检测到 `ROLE_ADMIN` 角色后显示"管理"菜单项
2. **管理后台布局**：左侧边栏导航，包含以下模块：
   - 仪表盘（用户数、文章数等统计概览）
   - 用户管理（用户列表、搜索、密码重置）
   - 评论审核（待审核列表、审核操作、审核历史）
   - 备份管理（手动备份、备份记录、清理设置）
   - 搜索管理（索引重建、索引状态）
   - 数据维护（计数修复）
3. **角色判断**：从 JWT Token 的 `authorities` 字段判断是否包含 `ROLE_ADMIN`

### 8.4 未来可扩展的管理功能

以下功能建议在后续版本中加入管理后台：

| 功能 | 说明 | 优先级 |
|------|------|--------|
| 平台统计看板 | 总用户数、总文章数、日活、阅读趋势等 | 中 |
| 操作审计日志 | 记录管理员的关键操作，满足合规需求 | 中 |
| 内容举报处理 | 用户举报不当内容，管理员审核处理 | 中 |
| 用户禁用/启用 | 管理员可以封禁违规用户 | 中 |
| 系统配置管理 | 通过管理后台修改系统配置项 | 低 |
| 公告管理 | 管理员发布系统公告，推送给所有用户 | 低 |

---

## 附录：管理员功能清单速查表

| 序号 | 功能 | 端点 | 方法 | @PreAuthorize |
|------|------|------|------|--------------|
| 1 | 修复所有计数 | `/api/admin/articles/reconcile-counts` | POST | 已保护 |
| 2 | 修复单篇计数 | `/api/admin/articles/{id}/reconcile-counts` | POST | 已保护 |
| 3 | 审核评论 | `/api/comments/{commentId}/review` | PUT | 已保护 |
| 4 | 待审核评论列表 | `/api/comments/pending` | GET | 已保护 |
| 5 | 用户列表 | `/api/users` | GET | 已保护 |
| 6 | 搜索用户 | `/api/users/search` | GET | 已保护 |
| 7 | 用户总数 | `/api/users/stats` | GET | 已保护 |
| 8 | 重置密码 | `/api/users/{id}/reset-password` | POST | 已保护 |
| 9 | 触发备份 | `/api/backup/trigger` | POST | 已保护 |
| 10 | 备份记录 | `/api/backup/records` | GET | 已保护 |
| 11 | 清理备份 | `/api/backup/clean` | DELETE | 已保护 |
| 12 | 重建索引 | `/api/search/rebuild-indexes` | POST | 已保护 |
| 13 | 索引单篇文章 | `/api/search/index/{articleId}` | POST | 已保护 |
| 14 | 删除文章索引 | `/api/search/index/{articleId}` | DELETE | 已保护 |
