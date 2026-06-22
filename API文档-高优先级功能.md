# Markdown 知识库系统 - 完整 API 文档

> 本文档面向前端开发，涵盖本项目全部 20 个控制器、120 个 API 接口，包括认证、用户管理、文章 CRUD、标签、分类、搜索、导出、备份、AI 功能、关注系统、协作、版本控制、收藏/点赞/评论、图片上传、通知系统、阅读历史、阅读进度、文章系列等。

---

## 📦 通用说明

### 基础路径
```
所有 API 以 /api 开头
```

### 认证方式
- 所有接口（除图片文件访问外）均需携带 JWT Token
- Header: `Authorization: Bearer <token>`

### 统一响应格式
```json
{
  "code": 200,       // 状态码
  "message": "成功",  // 提示信息
  "data": {}         // 响应数据
}
```

### 分页响应格式
```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "pageNum": 1,
    "pageSize": 10,
    "total": 100,
    "list": []
  }
}
```

---

## 目录

### 核心功能 API
1. [认证 API](#1-认证-api)
2. [用户管理 API](#2-用户管理-api)
3. [文章管理 API](#3-文章管理-api)
4. [标签管理 API](#4-标签管理-api)
5. [分类管理 API](#5-分类管理-api)
6. [文章版本控制 API](#6-文章版本控制-api)
7. [点赞 API](#7-点赞-api)
8. [收藏 API](#8-收藏-api)
9. [评论 API](#9-评论-api)
10. [热门文章 API](#10-热门文章-api)

### 扩展功能 API
11. [图片上传管理 API](#11-图片上传管理-api)
12. [通知系统 API](#12-通知系统-api)
13. [全文搜索 API](#13-全文搜索-api)
14. [文件导出 API](#14-文件导出-api)
15. [数据备份 API（管理员）](#15-数据备份-api管理员)
16. [DeepSeek AI 功能 API](#16-deepseek-ai-功能-api)
17. [阅读历史 API](#17-阅读历史-api)
18. [文章系列/合集 API](#18-文章系列合集-api)
19. [文章导入 API](#19-文章导入-api)
20. [计数修复 API（管理员）](#20-计数修复-api管理员)

### 社交功能 API
21. [用户关注 API](#21-用户关注-api)
22. [文章协作 API](#22-文章协作-api)

### 附录
- [数据模型](#-数据模型)
- [数据库表结构](#-数据库表结构)
- [前端集成建议](#-前端集成建议)
- [现有功能改进建议](#-现有功能改进建议)
- [新增功能建议](#-新增功能建议)
- [优先级排序总览](#-优先级排序总览)

---

## 1. 认证 API

**基础路径**: `/api/auth`

### 1.1 用户登录

```
POST /api/auth/login
```

**请求体**:
```json
{
  "username": "admin",       // 用户名（必填）
  "password": "123456"       // 密码（必填）
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "登录成功",
  "data": "eyJhbGciOiJIUzI1NiJ9..."   // JWT Token
}
```

> **限流机制**: 同一 IP 5 分钟内最多 5 次登录尝试，超出后锁定 15 分钟。

### 1.2 用户注册

```
POST /api/auth/register
```

**请求体**:
```json
{
  "username": "newuser",           // 用户名（必填）
  "password": "123456",            // 密码（必填）
  "confirmPassword": "123456",     // 确认密码（必填）
  "nickname": "新用户",            // 昵称（可选，默认为用户名）
  "email": "user@example.com"     // 邮箱（可选）
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "注册成功，欢迎邮件已发送",
  "data": 1    // 新用户 ID
}
```

> 注册后如提供了邮箱，会自动发送欢迎邮件和验证邮件。

### 1.3 邮箱验证

```
GET /api/auth/verify-email?userId=1&token=xxx
```

**请求参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | 是 | 用户 ID |
| token | String | 是 | 验证令牌 |

**响应示例**:
```json
{
  "code": 200,
  "message": "邮箱验证成功",
  "data": null
}
```

### 1.4 获取当前用户信息（POST）

```
POST /api/auth/me
```

> 需携带 JWT Token。返回当前登录用户的完整信息（密码字段置空）。

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "id": 1,
    "username": "admin",
    "nickname": "管理员",
    "email": "admin@example.com",
    "role": "ROLE_ADMIN",
    "status": 1,
    "avatar": null,
    "createTime": "2026-01-15T10:00:00",
    "updateTime": "2026-04-24T10:00:00"
  }
}
```

### 1.5 发送密码重置邮件

```
POST /api/auth/forgot-password
```

**请求体**:
```json
{
  "email": "user@example.com"
}
```

**响应**:
```json
{
  "code": 200,
  "message": "如果该邮箱已注册，重置密码邮件已发送",
  "data": null
}
```

> 无论邮箱是否注册，统一返回相同提示，防止用户枚举。重置链接 15 分钟有效。

### 1.6 验证重置令牌

```
GET /api/auth/reset-password/validate?token=xxx&userId=123
```

**请求参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| token | String | 是 | 邮件链接中的 token |
| userId | Long | 是 | 邮件链接中的 userId |

**响应（有效）**:
```json
{
  "code": 200,
  "data": { "valid": true }
}
```

**响应（无效/过期）**:
```json
{
  "code": 200,
  "data": { "valid": false, "message": "令牌已过期" }
}
```

### 1.7 重置密码

```
POST /api/auth/reset-password
```

**请求体**:
```json
{
  "token": "xxx",
  "userId": "123",
  "newPassword": "newPass123"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| token | String | 是 | 邮件中的令牌 |
| userId | String | 是 | 用户 ID（字符串类型） |
| newPassword | String | 是 | 新密码，6-100 位 |

**响应**:
```json
{
  "code": 200,
  "message": "密码重置成功，请使用新密码登录",
  "data": null
}
```

### 认证集成流程

1. 用户登录 → `POST /api/auth/login` → 获取 JWT Token
2. 后续请求 Header 携带 `Authorization: Bearer <token>`
3. 忘记密码 → 输入邮箱 → `POST /api/auth/forgot-password`
4. 邮件中的链接携带 `token` 和 `userId` → 重置密码页面
5. 校验 token → `GET /api/auth/reset-password/validate`
6. 重置密码 → `POST /api/auth/reset-password`

---

## 2. 用户管理 API

**基础路径**: `/api/users`

### 2.1 获取当前用户信息

```
GET /api/users/me
```

**响应**: 返回当前登录用户完整信息（密码置空）。

### 2.2 获取用户公开资料

```
GET /api/users/{id}/profile
```

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 用户 ID |

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "id": 1,
    "username": "zhangsan",
    "nickname": "张三",
    "avatar": null,
    "articleCount": 42,
    "followerCount": 56,
    "followingCount": 23,
    "createTime": "2025-01-15T10:00:00"
  }
}
```

### 2.3 获取用户详情

```
GET /api/users/{id}
```

**响应**: UserVO 对象（含 id、username、nickname、email、role、status、avatar、createTime、updateTime）。

### 2.4 更新当前用户信息

```
PUT /api/users/me
```

**请求体**:
```json
{
  "nickname": "新昵称",
  "avatar": "https://example.com/avatar.jpg",
  "email": "new@example.com"
}
```

> 不能通过此接口更新用户名和密码。

### 2.5 修改密码

```
PUT /api/users/me/password?oldPassword=xxx&newPassword=yyy
```

**请求参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| oldPassword | String | 是 | 原密码 |
| newPassword | String | 是 | 新密码 |

### 2.6 获取所有用户列表（管理员）

```
GET /api/users
```

> 需要 `ADMIN` 角色。

**响应**: UserVO 列表。

### 2.7 搜索用户（管理员）

```
GET /api/users/search?keyword=xxx
```

> 需要 `ADMIN` 角色。

### 2.8 获取用户统计（管理员）

```
GET /api/users/stats
```

> 需要 `ADMIN` 角色。返回用户总数。

### 2.9 重置用户密码（管理员）

```
POST /api/users/{id}/reset-password?newPassword=xxx
```

> 需要 `ADMIN` 角色。

---

## 3. 文章管理 API

**基础路径**: `/api/articles`

### 3.1 创建文章

```
POST /api/articles
```

**请求体**:
```json
{
  "title": "文章标题",                    // 必填
  "content": "# Markdown 内容...",        // 必填
  "categoryId": 1,                        // 可选，分类 ID
  "videoUrl": "https://...",              // 可选，视频 URL
  "aiStatus": 0,                          // 可选，AI 摘要状态（默认 0）
  "status": "DRAFT",                      // 可选，DRAFT/PRIVATE/PUBLIC（默认 DRAFT）
  "allowExport": 1,                       // 可选，是否允许他人导出（默认 1-允许）
  "tagNames": ["java", "spring"]          // 可选，标签名称列表
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "文章创建成功",
  "data": 100    // 文章 ID
}
```

### 3.2 获取文章详情

```
GET /api/articles/{id}
```

**响应**: ArticleVO 对象。访问时自动记录阅读历史。

### 3.3 获取文章详情（含视频和时间戳目录）

```
GET /api/articles/{id}/detail
```

**响应**: ArticleDetailVO 对象，包含视频信息和时间戳目录。

### 3.4 更新文章

```
PUT /api/articles/{id}
```

**请求体**: 同创建文章（3.1）。

### 3.5 删除文章

```
DELETE /api/articles/{id}
```

### 3.6 获取文章列表（分页）

```
GET /api/articles?pageNum=1&pageSize=10
```

**请求参数**:
| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| pageNum | Integer | 否 | 1 | 页码 |
| pageSize | Integer | 否 | 10 | 每页条数 |
| categoryId | Long | 否 | - | 分类 ID |
| tagId | Long | 否 | - | 标签 ID |
| keyword | String | 否 | - | 搜索关键词 |
| status | Integer | 否 | - | 文章状态（0-草稿, 1-私密, 2-公开） |
| isPublic | Integer | 否 | - | 是否公开 |

**响应**: 分页的 ArticleVO 列表。

### 3.7 获取我的文章列表

```
GET /api/articles/my?pageNum=1&pageSize=10
```

**请求参数**: 同文章列表（3.6）。包含草稿和私密文章。

### 3.8 获取指定用户的公开文章

```
GET /api/articles/user/{userId}?pageNum=1&pageSize=10
```

**请求参数**: 同文章列表（3.6），不含 status 和 isPublic。

### 3.9 获取我的文章统计

```
GET /api/articles/my/stats
```

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "totalArticles": 42,
    "publicArticles": 30,
    "draftArticles": 8,
    "privateArticles": 4,
    "totalViews": 15000,
    "totalLikes": 320
  }
}
```

### 3.10 增加文章阅读量

```
POST /api/articles/{id}/view
```

### 3.11 更新 AI 摘要状态

```
POST /api/articles/{id}/ai-status?aiStatus=2&summary=摘要内容
```

**请求参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| aiStatus | Integer | 是 | AI 状态（0-未生成, 1-生成中, 2-已生成） |
| summary | String | 否 | AI 生成的摘要内容 |

### 3.12 批量更新文章状态

```
PUT /api/articles/batch-status?status=2&isPublic=1
```

**请求体**: 文章 ID 数组
```json
[1, 2, 3]
```

### 3.13 保存/更新文章（支持视频绑定）

```
POST /api/articles/save
```

**请求体**:
```json
{
  "id": null,                    // null 表示新建，有值表示更新
  "title": "文章标题",
  "content": "# Markdown 内容...",
  "categoryId": 1,
  "videoUrl": "https://...",
  "allowExport": 1,
  "tagIds": [1, 2, 3]
}
```

### 3.14 获取文章时间戳目录

```
GET /api/articles/{id}/timestamps
```

**响应**: ArticleTimestamp 列表（用于视频文章的时间戳导航）。

### 3.15 更新文章导出权限

```
PUT /api/articles/{id}/allow-export?allowExport=1
```

**请求参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| allowExport | Integer | 是 | 1-允许导出, 0-禁止导出 |

### 3.16 置顶文章

```
PUT /api/articles/{id}/pin
```

> 每个用户最多置顶 3 篇文章。

### 3.17 取消置顶

```
PUT /api/articles/{id}/unpin
```

---

## 4. 标签管理 API

**基础路径**: `/api/tags`

### 4.1 创建标签

```
POST /api/tags
```

**请求体**:
```json
{
  "name": "java"
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "标签创建成功",
  "data": 1    // 标签 ID
}
```

### 4.2 获取标签详情

```
GET /api/tags/{id}
```

### 4.3 更新标签

```
PUT /api/tags/{id}
```

**请求体**:
```json
{
  "name": "新标签名"
}
```

### 4.4 删除标签

```
DELETE /api/tags/{id}
```

### 4.5 获取所有标签列表

```
GET /api/tags
```

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": [
    { "id": 1, "name": "java", "createTime": "2026-01-15T10:00:00" },
    { "id": 2, "name": "spring", "createTime": "2026-01-15T10:00:00" }
  ]
}
```

### 4.6 搜索标签

```
GET /api/tags/search?keyword=java
```

### 4.7 获取所有标签名称列表

```
GET /api/tags/names
```

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": ["java", "spring", "vue"]
}
```

### 4.8 获取热门标签

```
GET /api/tags/popular?limit=10
```

---

## 5. 分类管理 API

**基础路径**: `/api/categories`

### 5.1 创建分类

```
POST /api/categories
```

**请求体**:
```json
{
  "name": "技术文章",
  "description": "技术相关文章分类",
  "sortOrder": 1
}
```

### 5.2 获取分类详情

```
GET /api/categories/{id}
```

### 5.3 更新分类

```
PUT /api/categories/{id}
```

**请求体**: 同创建分类。

### 5.4 删除分类

```
DELETE /api/categories/{id}
```

### 5.5 获取所有分类列表

```
GET /api/categories
```

**响应**: 按 sortOrder 排序的 Category 列表。

### 5.6 调整分类排序

```
POST /api/categories/{id}/sort?sortOrder=3
```

---

## 6. 文章版本控制 API

**基础路径**: `/api/articles/{articleId}/versions`

### 6.1 获取版本列表

获取文章的所有历史版本。

```
GET /api/articles/{articleId}/versions
```

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| articleId | Long | 文章 ID |

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": [
    {
      "id": 1,
      "articleId": 100,
      "version": 1,
      "title": "文章标题",
      "content": "Markdown 内容...",
      "summary": "摘要...",
      "changeNote": "更新文章",
      "operatorId": 1,
      "operatorName": "用户1",
      "createTime": "2026-04-24T10:00:00"
    }
  ]
}
```

### 6.2 获取版本详情

获取指定版本的完整内容。

```
GET /api/articles/{articleId}/versions/{versionId}
```

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| articleId | Long | 文章 ID |
| versionId | Long | 版本 ID |

### 6.3 回滚到指定版本

将文章回滚到指定历史版本。

```
POST /api/articles/{articleId}/versions/{versionId}/rollback?changeNote=xxx
```

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| articleId | Long | 文章 ID |
| versionId | Long | 版本 ID |

**请求参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| changeNote | String | 否 | 回滚备注 |

### 6.4 比较版本差异

比较两个版本的差异（返回 unified diff 格式文本）。

```
GET /api/articles/{articleId}/versions/diff?versionId1=1&versionId2=2
```

**请求参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| versionId1 | Long | 是 | 旧版本 ID |
| versionId2 | Long | 是 | 新版本 ID |

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": "@@ -1,5 +1,5 @@\n-旧内容\n+新内容"
}
```

---

## 7. 点赞 API

### 7.1 点赞/取消点赞

切换文章的点赞状态（已赞则取消，未赞则点赞）。

```
POST /api/articles/{articleId}/like
```

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| articleId | Long | 文章 ID |

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "liked": true,       // true-已点赞, false-已取消
    "likeCount": 42      // 当前点赞总数
  }
}
```

### 7.2 查询点赞状态

查询当前用户是否已点赞某篇文章。

```
GET /api/articles/{articleId}/like/status
```

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "liked": true,
    "likeCount": 42
  }
}
```

### 7.3 获取点赞数

获取文章的点赞总数（无需登录）。

```
GET /api/articles/{articleId}/like/count
```

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "likeCount": 42
  }
}
```

---

## 8. 收藏 API

### 8.1 收藏夹分类管理

**基础路径**: `/api/favorites/folders`

#### 3.1.1 创建收藏夹

```
POST /api/favorites/folders
```

**请求体**:
```json
{
  "name": "技术文章",           // 收藏夹名称（必填，最长50字）
  "description": "收藏的技术相关文章"  // 收藏夹描述（可选，最长200字）
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "收藏夹创建成功",
  "data": {
    "id": 1,
    "name": "技术文章",
    "description": "收藏的技术相关文章",
    "sortOrder": 0,
    "articleCount": 0,
    "createTime": "2026-04-27T12:00:00",
    "updateTime": "2026-04-27T12:00:00"
  }
}
```

#### 3.1.2 获取收藏夹列表

获取当前用户的所有收藏夹，包含每个收藏夹下的文章数量。

```
GET /api/favorites/folders
```

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": [
    {
      "id": 1,
      "name": "技术文章",
      "description": "收藏的技术相关文章",
      "sortOrder": 0,
      "articleCount": 5,
      "createTime": "2026-04-27T12:00:00",
      "updateTime": "2026-04-27T12:00:00"
    },
    {
      "id": 2,
      "name": "学习笔记",
      "description": null,
      "sortOrder": 1,
      "articleCount": 3,
      "createTime": "2026-04-27T13:00:00",
      "updateTime": "2026-04-27T13:00:00"
    }
  ]
}
```

#### 3.1.3 重命名收藏夹

```
PUT /api/favorites/folders/{folderId}
```

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| folderId | Long | 收藏夹 ID |

**请求体**:
```json
{
  "name": "新名称"
}
```

**响应**: 更新后的 FavoriteFolderVO

#### 3.1.4 删除收藏夹

```
DELETE /api/favorites/folders/{folderId}
```

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| folderId | Long | 收藏夹 ID |

> ⚠️ 删除收藏夹不会删除已收藏的文章记录，只是移除该收藏夹分类。

#### 3.1.5 更新收藏夹排序

```
PUT /api/favorites/folders/sort
```

**请求体**:
```json
{
  "folderIds": [3, 1, 2]
}
```

> 按期望的顺序传入收藏夹ID列表，数字越小越靠前。

---

### 8.2 收藏/取消收藏

切换文章的收藏状态。

```
POST /api/articles/{articleId}/favorite?folderName=默认收藏夹
```

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| articleId | Long | 文章 ID |

**请求参数**:
| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| folderName | String | 否 | "默认收藏夹" | 收藏夹名称 |

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "favorited": true    // true-已收藏, false-已取消
  }
}
```

### 8.3 查询收藏状态

```
GET /api/articles/{articleId}/favorite/status
```

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "favorited": true
  }
}
```

### 8.4 获取我的收藏列表

分页获取当前用户的收藏文章列表。

```
GET /api/favorites?pageNum=1&pageSize=10&folderName=默认收藏夹
```

**请求参数**:
| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| pageNum | Integer | 否 | 1 | 页码 |
| pageSize | Integer | 否 | 10 | 每页条数 |
| folderName | String | 否 | - | 收藏夹名称（不传则查全部） |

**响应**: 分页的 ArticleVO 列表

### 8.5 获取收藏夹名称列表（旧接口）

获取当前用户的所有收藏夹名称（仅返回名称列表，兼容旧版前端）。

```
GET /api/favorites/folder-names
```

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": ["默认收藏夹", "技术文章", "学习笔记"]
}
```

---

## 9. 评论 API

### 9.1 添加评论

对文章发表评论或回复他人的评论。

```
POST /api/articles/{articleId}/comments
```

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| articleId | Long | 文章 ID |

**请求体**:
```json
{
  "content": "这是一条评论",           // 评论内容（必填，最长2000字）
  "parentId": "123"                   // 父评论ID（回复时传，一级评论不传）
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "评论成功",
  "data": {
    "commentId": 456
  }
}
```

> ⚠️ **敏感词过滤**：评论内容会自动进行敏感词过滤，严重敏感词会被拒绝。

### 9.2 获取评论列表

获取文章的评论列表（树形结构，一级评论带子回复）。

```
GET /api/articles/{articleId}/comments?pageNum=1&pageSize=10
```

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| articleId | Long | 文章 ID |

**请求参数**:
| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| pageNum | Integer | 否 | 1 | 页码 |
| pageSize | Integer | 否 | 10 | 每页条数 |

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "pageNum": 1,
    "pageSize": 10,
    "total": 5,
    "list": [
      {
        "id": 1,
        "articleId": 100,
        "userId": 1,
        "userNickname": "张三",
        "userAvatar": null,
        "parentId": null,
        "replyToUserId": null,
        "replyToUsername": null,
        "content": "好文章！",
        "status": 1,
        "replyCount": 2,
        "replies": [
          {
            "id": 2,
            "articleId": 100,
            "userId": 2,
            "userNickname": "李四",
            "userAvatar": null,
            "parentId": 1,
            "replyToUserId": 1,
            "replyToUsername": "张三",
            "content": "谢谢！",
            "status": 1,
            "replyCount": 0,
            "replies": [],
            "createTime": "2026-04-24T11:00:00",
            "updateTime": "2026-04-24T11:00:00"
          }
        ],
        "createTime": "2026-04-24T10:30:00",
        "updateTime": "2026-04-24T10:30:00"
      }
    ]
  }
}
```

### 9.3 删除评论

```
DELETE /api/comments/{commentId}
```

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| commentId | Long | 评论 ID |

### 9.4 审核评论（管理员）

```
PUT /api/comments/{commentId}/review?status=1
```

**请求参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| status | Integer | 是 | 0-待审核, 1-已通过, 2-已拒绝 |

### 9.5 获取待审核评论列表（管理员）

```
GET /api/comments/pending
```

### 9.6 获取我的评论历史

获取当前用户的评论列表（分页）。

```
GET /api/comments/my?pageNum=1&pageSize=10
```

**请求参数**:
| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| pageNum | Integer | 否 | 1 | 页码 |
| pageSize | Integer | 否 | 10 | 每页条数 |

**响应**: 分页的 CommentVO 列表。

---

## 10. 热门文章 API

### 10.1 获取热门文章排行榜

```
GET /api/articles/hot?type=views&limit=10
```

**请求参数**:
| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| type | String | 否 | "views" | 排序方式：views-按阅读量, likes-按点赞数 |
| limit | Integer | 否 | 10 | 返回数量 |

**响应**: ArticleVO 列表

---

## 11. 图片上传管理 API

**基础路径**: `/api/images`

### 11.1 上传图片

上传图片文件到服务器，自动生成缩略图。

```
POST /api/images/upload
```

**请求格式**: `multipart/form-data`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | File | 是 | 图片文件（支持 jpg/png/gif/webp/bmp，最大10MB） |
| articleId | Long | 否 | 关联的文章 ID |

**响应示例**:
```json
{
  "code": 200,
  "message": "图片上传成功",
  "data": {
    "id": 1,
    "originalName": "photo.jpg",
    "url": "http://localhost:8080/api/images/1/file",
    "thumbnailUrl": "http://localhost:8080/api/images/1/thumbnail",
    "fileSize": 1024000,
    "width": 1920,
    "height": 1080,
    "mimeType": "image/jpeg",
    "createTime": "2026-04-24T12:00:00"
  }
}
```

> **ImageVO 字段说明**:
> - `url`: 原图访问地址
> - `thumbnailUrl`: 缩略图访问地址（300x300 等比例缩放）

### 11.2 获取我的图片列表

```
GET /api/images/my
```

**响应**: ImageVO 列表

### 11.3 获取文章的图片列表

```
GET /api/images/article/{articleId}
```

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| articleId | Long | 文章 ID |

### 11.4 删除图片

```
DELETE /api/images/{imageId}
```

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| imageId | Long | 图片 ID |

### 11.5 获取图片文件（公开访问）

直接访问图片文件，可用于 `<img>` 标签的 `src`。

```
GET /api/images/{imageId}/file
```

> 返回图片二进制流，Content-Type 为图片的 MIME 类型。

---

## 12. 通知系统 API

**基础路径**: `/api/notifications`

### 12.1 订阅实时通知（SSE）

```
GET /api/notifications/subscribe
```

> 返回 SSE（Server-Sent Events）流，用于实时接收新通知。此接口已白名单化，无需 JWT Token（SSE 握手后无法携带 Header）。

### 12.2 获取未读通知列表

```
GET /api/notifications/unread
```

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": [
    {
      "id": 1,
      "userId": 1,
      "type": "COMMENT",
      "title": "你的文章有新评论",
      "content": "张三 评论了你的文章《Spring Boot入门》：好文章！",
      "relatedArticleId": 100,
      "relatedUserId": 2,
      "relatedUserName": "张三",
      "isRead": 0,
      "createTime": "2026-04-24T12:00:00"
    }
  ]
}
```

> **通知类型(type)**:
> - `COMMENT` - 新评论/回复
> - `LIKE` - 新点赞
> - `FAVORITE` - 新收藏
> - `SYSTEM` - 系统通知

### 12.3 获取所有通知列表

```
GET /api/notifications
```

### 12.4 获取未读通知数量

```
GET /api/notifications/unread/count
```

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "count": 5
  }
}
```

### 12.5 标记通知为已读

```
PUT /api/notifications/{notificationId}/read
```

### 12.6 全部标记为已读

```
PUT /api/notifications/read-all
```

### 12.7 删除通知

```
DELETE /api/notifications/{notificationId}
```

---

## 13. 全文搜索 API

**基础路径**: `/api/search`

基于 Elasticsearch 的全文搜索服务。

### 13.1 全文搜索文章

```
GET /api/search/articles?keyword=Spring&pageNum=1&pageSize=10
```

**请求参数**:
| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| keyword | String | 是 | - | 搜索关键词 |
| pageNum | Integer | 否 | 1 | 页码 |
| pageSize | Integer | 否 | 10 | 每页条数 |

**响应示例**:
```json
{
  "code": 200,
  "message": "搜索完成",
  "data": [
    {
      "articleId": 100,
      "title": "Spring Boot 入门",
      "content": "...匹配的高亮内容...",
      "summary": "摘要...",
      "authorName": "张三",
      "categoryName": "技术",
      "highlight": "<em>Spring</em> Boot 入门"
    }
  ]
}
```

### 13.2 根据作者搜索

```
GET /api/search/author?authorName=张三&pageNum=1&pageSize=10
```

### 13.3 根据分类搜索

```
GET /api/search/category?categoryName=技术&pageNum=1&pageSize=10
```

### 13.4 根据标签搜索

```
GET /api/search/tag?tag=java&pageNum=1&pageSize=10
```

### 13.5 获取搜索建议（自动补全）

```
GET /api/search/suggestions?prefix=Spr&limit=10
```

**响应示例**:
```json
{
  "code": 200,
  "message": "获取搜索建议成功",
  "data": ["Spring Boot", "Spring Cloud", "Spring Security"]
}
```

### 13.6 重建所有文章索引（管理员）

```
POST /api/search/rebuild-indexes
```

> 需要 `ADMIN` 角色。返回重建的文档数量。

### 13.7 获取索引统计信息

```
GET /api/search/stats
```

**响应**: 返回索引中的文档总数。

### 13.8 索引单篇文章（管理员）

```
POST /api/search/index/{articleId}
```

> 需要 `ADMIN` 角色。

### 13.9 删除文章索引（管理员）

```
DELETE /api/search/index/{articleId}
```

> 需要 `ADMIN` 角色。

---

## 14. 文件导出 API

**基础路径**: `/api/export`

### 14.1 导出文章为 PDF

```
POST /api/export/{articleId}/pdf
```

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| articleId | Long | 文章 ID |

**响应示例**:
```json
{
  "code": 200,
  "message": "PDF导出成功",
  "data": "exports/article_100_20260518.pdf"
}
```

> 返回文件路径，需调用下载接口获取文件。

### 14.2 导出文章为 Word

```
POST /api/export/{articleId}/word
```

**响应**: 同 PDF 导出，返回 .docx 文件路径。

### 14.3 导出所有文章为 Markdown ZIP

```
POST /api/export/all-markdown
```

**响应**: 返回 .zip 文件路径。

### 14.4 下载导出文件

```
GET /api/export/download?filePath=exports/article_100_20260518.pdf
```

> 返回文件二进制流，支持 PDF/DOCX/ZIP 格式。有路径穿越防护。

### 14.5 获取导出记录

```
GET /api/export/records
```

**响应**: 当前用户的导出记录列表。

### 14.6 删除导出记录

```
DELETE /api/export/records/{recordId}
```

---

## 15. 数据备份 API（管理员）

**基础路径**: `/api/backup`

所有接口需要 `ADMIN` 角色。

### 15.1 手动触发备份

```
POST /api/backup/trigger
```

**响应示例**:
```json
{
  "code": 200,
  "message": "备份任务已触发",
  "data": null
}
```

### 15.2 获取备份记录

```
GET /api/backup/records
```

**响应**: BackupRecord 列表（含备份时间、文件大小、状态等）。

### 15.3 清理过期备份

```
DELETE /api/backup/clean?retentionDays=30
```

**请求参数**:
| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| retentionDays | int | 否 | 30 | 保留天数 |

> 自动备份定时任务默认每天凌晨 2 点执行（可通过 `app.backup.cron` 配置）。

---

## 16. DeepSeek AI 功能 API

**基础路径**: `/api/deepseek`

### 16.1 获取 AI 服务状态

```
GET /api/deepseek/status
```

**响应示例**:
```json
{
  "code": 200,
  "message": "DeepSeek API 状态查询成功",
  "data": {
    "serviceName": "DeepSeek AI 服务",
    "apiConfigured": true,
    "apiUrl": "https://api.deepseek.com/v1",
    "model": "deepseek-chat",
    "timeoutSeconds": 30,
    "maxTokens": 500,
    "connected": true,
    "status": "连接正常"
  }
}
```

### 16.2 获取 AI 配置信息

```
GET /api/deepseek/config
```

> 返回当前 AI 配置（不包含 API Key 明文）。

### 16.3 测试 API 连接

```
POST /api/deepseek/test-connection
```

**响应示例**:
```json
{
  "code": 200,
  "message": "DeepSeek API 连接正常",
  "data": {
    "connected": true,
    "serviceName": "DeepSeek AI 服务",
    "testSummary": "这是一段测试内容的摘要..."
  }
}
```

### 16.4 生成文章摘要

```
POST /api/deepseek/generate-summary
```

**请求体**:
```json
{
  "content": "# 文章标题\n\n这里是文章的 Markdown 内容..."
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "AI 摘要生成成功",
  "data": {
    "originalLength": 5000,
    "summaryLength": 200,
    "summary": "本文介绍了...",
    "serviceUsed": "DeepSeek AI 服务",
    "success": true
  }
}
```

### 16.5 生成文章标题

```
POST /api/deepseek/generate-title
```

**请求体**:
```json
{
  "content": "文章内容..."
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "AI 标题生成成功",
  "data": {
    "title": "AI 生成的标题",
    "success": true
  }
}
```

### 16.6 AI 聊天

```
POST /api/deepseek/chat
```

**请求体**:
```json
{
  "message": "你好，请介绍一下 Spring Boot"
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "聊天响应生成成功",
  "data": {
    "request": "你好，请介绍一下 Spring Boot",
    "response": "Spring Boot 是...",
    "serviceUsed": "DeepSeek AI 服务"
  }
}
```

### 16.7 文本润色

```
POST /api/deepseek/polish
```

**请求体**:
```json
{
  "content": "需要润色的文本内容...",
  "style": "academic",     // 可选：学术风格
  "tone": "formal"         // 可选：正式语气
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "文本润色成功",
  "data": {
    "original": "原始文本...",
    "polished": "润色后的文本...",
    "success": true
  }
}
```

---

## 17. 阅读历史 API

### 17.1 获取阅读历史（分页）

```
GET /api/reading-history?pageNum=1&pageSize=20
```

**请求参数**:
| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| pageNum | Integer | 否 | 1 | 页码 |
| pageSize | Integer | 否 | 20 | 每页条数 |

**响应示例**:
```json
{
  "code": 200,
  "data": {
    "pageNum": 1,
    "pageSize": 20,
    "total": 50,
    "list": [
      {
        "articleId": 100,
        "title": "Spring Boot 入门",
        "authorName": "张三",
        "progress": 65,
        "lastPosition": "## 配置数据源",
        "lastReadTime": "2026-05-14T10:30:00"
      }
    ]
  }
}
```

**ReadingHistoryVO 字段**:
| 字段 | 类型 | 说明 |
|------|------|------|
| articleId | Long | 文章 ID |
| title | String | 文章标题（已删除的文章显示"已删除的文章"） |
| authorName | String | 作者昵称 |
| progress | Integer | 阅读进度百分比 0-100 |
| lastPosition | String | 最后阅读位置（由前端保存的自由文本） |
| lastReadTime | DateTime | 最后阅读时间 |

### 17.2 删除单条阅读历史

```
DELETE /api/reading-history/{id}
```

`{id}` 是 `reading_progress` 表主键 ID（非 articleId）。

### 17.3 清空所有阅读历史

```
DELETE /api/reading-history
```

### 17.4 保存/更新阅读进度

保存或更新当前用户对某篇文章的阅读进度。同一用户同一篇文章只会保留一条记录，重复调用会更新已有记录。

```
POST /api/reading-progress
```

**请求体**:
```json
{
  "articleId": "100",
  "progress": "65",
  "lastPosition": "## 配置数据源"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| articleId | String | 是 | 文章 ID（字符串类型） |
| progress | String | 否 | 阅读进度百分比 0-100（字符串类型，不传则不更新进度值） |
| lastPosition | String | 否 | 最后阅读位置的自由文本（如章节标题、段落标识等，由前端自行定义） |

**响应示例**:
```json
{
  "code": 200,
  "message": "进度已保存",
  "data": null
}
```

> **前端集成建议**: 建议在用户滚动阅读时防抖调用（间隔 10-30 秒），`progress` 可根据滚动位置百分比计算，`lastPosition` 可传当前可见的章节标题或锚点 ID，用于下次打开时定位。

### 17.5 获取单篇文章的阅读进度

获取当前用户对指定文章的阅读进度。

```
GET /api/reading-progress/{articleId}
```

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| articleId | Long | 文章 ID |

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "id": 42,
    "userId": 1,
    "articleId": 100,
    "progress": 65,
    "lastPosition": "## 配置数据源",
    "lastReadTime": "2026-05-18T14:30:00",
    "createTime": "2026-05-10T09:00:00",
    "updateTime": "2026-05-18T14:30:00"
  }
}
```

**ReadingProgress 字段**:
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 记录主键 ID |
| userId | Long | 用户 ID |
| articleId | Long | 文章 ID |
| progress | Integer | 阅读进度百分比 0-100 |
| lastPosition | String | 最后阅读位置（前端自定义文本） |
| lastReadTime | DateTime | 最后阅读时间 |
| createTime | DateTime | 首次创建时间 |
| updateTime | DateTime | 最近更新时间 |

> 如果用户从未阅读过该文章，返回 `data: null`。

### 17.6 获取所有阅读进度列表

获取当前用户所有文章的阅读进度记录（不分页，返回全部）。

```
GET /api/reading-progress
```

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": [
    {
      "id": 42,
      "userId": 1,
      "articleId": 100,
      "progress": 65,
      "lastPosition": "## 配置数据源",
      "lastReadTime": "2026-05-18T14:30:00",
      "createTime": "2026-05-10T09:00:00",
      "updateTime": "2026-05-18T14:30:00"
    },
    {
      "id": 43,
      "userId": 1,
      "articleId": 200,
      "progress": 100,
      "lastPosition": "## 总结",
      "lastReadTime": "2026-05-17T10:00:00",
      "createTime": "2026-05-15T08:00:00",
      "updateTime": "2026-05-17T10:00:00"
    }
  ]
}
```

> **与阅读历史的区别**: 此接口返回原始进度记录（不含文章标题、作者等信息），适合用于恢复阅读状态。如需带文章信息的分页列表，请使用 `GET /api/reading-history`。

---

## 18. 文章系列/合集 API

**基础路径**: `/api/series`

### 18.1 创建系列

```
POST /api/series
```

**请求体**:
```json
{
  "title": "Spring Boot 入门系列",
  "description": "从零开始学习 Spring Boot",
  "isPublic": true,
  "articleIds": [10, 15, 22]
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| title | String | 是 | 系列标题 |
| description | String | 否 | 系列描述 |
| isPublic | Boolean | 否 | 是否公开，默认 true |
| articleIds | List\<Long\> | 否 | 初始化包含的文章 ID 列表 |

**响应示例**:
```json
{
  "code": 200,
  "message": "系列创建成功",
  "data": {
    "id": 1,
    "title": "Spring Boot 入门系列",
    "description": "从零开始学习 Spring Boot",
    "coverImageUrl": null,
    "authorName": "张三",
    "articleCount": 3,
    "isPublic": 1,
    "articles": [
      { "id": 10, "title": "第一章：环境搭建", "sortOrder": 1 },
      { "id": 15, "title": "第二章：第一个应用", "sortOrder": 2 },
      { "id": 22, "title": "第三章：数据库操作", "sortOrder": 3 }
    ],
    "createTime": "2026-05-14T10:00:00",
    "updateTime": "2026-05-14T10:00:00"
  }
}
```

### 18.2 更新系列信息

```
PUT /api/series/{id}
```

**请求体**（所有字段可选）:
```json
{
  "title": "新标题",
  "description": "新描述",
  "isPublic": false
}
```

### 18.3 删除系列

```
DELETE /api/series/{id}
```

### 18.4 获取系列详情

```
GET /api/series/{id}
```

响应格式同 10.1 创建系列返回值。

### 18.5 获取我的系列列表

```
GET /api/series?pageNum=1&pageSize=10
```

### 18.6 获取某用户的公开系列

```
GET /api/series/user/{userId}?pageNum=1&pageSize=10
```

### 18.7 向系列添加文章

```
POST /api/series/{id}/articles
```

**请求体**:
```json
{
  "articleId": 30,
  "sortOrder": 3
}
```

### 18.8 从系列移除文章

```
DELETE /api/series/{id}/articles/{articleId}
```

### 18.9 调整系列内文章排序

```
PUT /api/series/{id}/articles/sort
```

**请求体**（按期望顺序传入文章 ID 列表）:
```json
{
  "articleIds": [22, 10, 15]
}
```

---

## 19. 文章导入 API

**基础路径**: `/api/articles/import`

支持两种导入方式：上传 .md/.txt 文件导入，以及从外部 URL 抓取内容导入。所有导入的文章默认状态为草稿（DRAFT）。

### 19.1 从文件导入

上传一个或多个 Markdown 文件（`.md`、`.markdown`、`.txt`），自动以文件名作为文章标题创建文章。

```
POST /api/articles/import/file
```

**请求格式**: `multipart/form-data`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| files | File[] | 是 | 上传的 Markdown 文件（支持 .md / .markdown / .txt） |
| categoryId | Long | 否 | 指定文章分类 ID，不传则为未分类 |

**响应示例**:
```json
{
  "code": 200,
  "message": "导入成功",
  "data": [101, 102, 103]
}
```

> `data` 为导入成功的文章 ID 列表。文件名去掉扩展名后作为文章标题。文件内容使用 UTF-8 编码读取。

### 19.2 从 URL 导入

抓取指定 URL 的网页内容，自动提取页面标题，将 HTML 内容转换为文章正文。

```
POST /api/articles/import/url
```

**请求体**:
```json
{
  "url": "https://example.com/article",
  "categoryId": 1
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| url | String | 是 | 目标网页 URL |
| categoryId | Long | 否 | 指定文章分类 ID |

**响应示例**:
```json
{
  "code": 200,
  "message": "导入成功",
  "data": 104
}
```

> 使用 Jsoup 抓取网页，超时 15 秒。文章开头会自动添加原文链接的引用。

### 前端集成建议

- 文件导入支持批量选择多个 `.md` 文件，一次请求全部上传
- URL 导入适合从其他知识库/博客迁移内容
- 导入后的文章为**草稿**状态，需手动编辑后发布
- 支持同时指定 `categoryId` 将导入文章归入特定分类

---

## 20. 计数修复 API（管理员）

需要 `ADMIN` 角色。

### 20.1 修复所有文章的计数

从源表（article_like、article_comment、user_favorite）重新计算所有文章的 like_count、comment_count、favorite_count。

```
POST /api/admin/articles/reconcile-counts
```

**响应**:
```json
{
  "code": 200,
  "message": "计数修复完成",
  "data": { "elapsedMs": 1250 }
}
```

### 20.2 修复单篇文章的计数

```
POST /api/admin/articles/{id}/reconcile-counts
```

---

## 21. 用户关注 API

**基础路径**: `/api/users`

### 21.1 关注用户

```
POST /api/users/{id}/follow
```

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 要关注的用户 ID |

**响应示例**:
```json
{
  "code": 200,
  "message": "关注成功",
  "data": true
}
```

> 已关注时返回 `false` 和消息"已关注"。

### 21.2 取消关注

```
POST /api/users/{id}/unfollow
```

### 21.3 查询关注状态

```
GET /api/users/{id}/follow/status
```

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": true    // true-已关注, false-未关注
}
```

### 21.4 获取粉丝列表

```
GET /api/users/{id}/followers
```

**响应**: UserVO 列表。

### 21.5 获取关注列表

```
GET /api/users/{id}/following
```

**响应**: UserVO 列表。

### 21.6 获取关注者的文章动态

```
GET /api/users/following/articles?page=1&size=10
```

**请求参数**:
| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| page | Integer | 否 | 1 | 页码 |
| size | Integer | 否 | 10 | 每页条数 |

**响应**: 分页的 ArticleVO 列表（按发布时间倒序，仅包含关注用户的公开文章）。

---

## 22. 文章协作 API

**基础路径**: `/api/articles`

### 22.1 获取文章协作者列表

```
GET /api/articles/{id}/collaborators
```

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 文章 ID |

**响应**: ArticleCollaborator 列表。

### 22.2 添加协作者

```
POST /api/articles/{id}/collaborators
```

**请求体**:
```json
{
  "userId": "2",           // 协作者用户 ID
  "permission": "EDIT"     // 权限：VIEW-只读, EDIT-可编辑
}
```

### 22.3 移除协作者

```
DELETE /api/articles/{id}/collaborators/{userId}
```

### 22.4 获取共享给我的文章

```
GET /api/articles/shared?page=1&size=10
```

**请求参数**:
| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| page | Integer | 否 | 1 | 页码 |
| size | Integer | 否 | 10 | 每页条数 |

**响应**: 分页的 ArticleVO 列表（其他用户共享给当前用户的文章）。

> **文章置顶 API**: 置顶/取消置顶端点（`PUT /api/articles/{id}/pin` 和 `PUT /api/articles/{id}/unpin`）已包含在 [文章管理 API](#3-文章管理-api) 章节 3.16 和 3.17 中。

---

## 📋 数据模型

### ArticleVO（文章展示对象）
```json
{
  "id": 100,
  "userId": 1,
  "authorName": "张三",
  "categoryId": 1,
  "categoryName": "技术",
  "title": "文章标题",
  "content": "Markdown 内容",
  "videoUrl": null,
  "summary": "摘要...",
  "aiStatus": 2,
  "status": "PUBLIC",
  "viewCount": 100,
  "likeCount": 42,
  "commentCount": 5,
  "favoriteCount": 10,
  "tags": [
    { "id": 1, "name": "java", "createTime": "..." }
  ],
  "createTime": "2026-04-24T10:00:00",
  "updateTime": "2026-04-24T11:00:00"
}
```

### ImageVO（图片展示对象）
```json
{
  "id": 1,
  "originalName": "photo.jpg",
  "url": "http://localhost:8080/api/images/1/file",
  "thumbnailUrl": "http://localhost:8080/api/images/1/thumbnail",
  "fileSize": 1024000,
  "width": 1920,
  "height": 1080,
  "mimeType": "image/jpeg",
  "createTime": "2026-04-24T12:00:00"
}
```

### CommentVO（评论展示对象-树形）
```json
{
  "id": 1,
  "articleId": 100,
  "userId": 1,
  "userNickname": "张三",
  "userAvatar": null,
  "parentId": null,
  "replyToUserId": null,
  "replyToUsername": null,
  "content": "评论内容",
  "status": 1,
  "replyCount": 2,
  "replies": [ /* 子评论列表，结构与父评论相同 */ ],
  "createTime": "2026-04-24T10:00:00",
  "updateTime": "2026-04-24T10:00:00"
}
```

### ArticleVersion（版本对象）
```json
{
  "id": 1,
  "articleId": 100,
  "version": 1,
  "title": "文章标题",
  "content": "Markdown 内容",
  "summary": "摘要",
  "changeNote": "更新文章",
  "operatorId": 1,
  "operatorName": "用户1",
  "createTime": "2026-04-24T10:00:00"
}
```

### Notification（通知对象）
```json
{
  "id": 1,
  "userId": 1,
  "type": "COMMENT",
  "title": "你的文章有新评论",
  "content": "张三 评论了你的文章...",
  "relatedArticleId": 100,
  "relatedUserId": 2,
  "relatedUserName": "张三",
  "isRead": 0,
  "createTime": "2026-04-24T10:00:00"
}
```

### ReadingHistoryVO（阅读历史对象）
```json
{
  "articleId": 100,
  "title": "Spring Boot 入门",
  "authorName": "张三",
  "progress": 65,
  "lastPosition": "## 配置数据源",
  "lastReadTime": "2026-05-14T10:30:00"
}
```

### ArticleSeriesVO（系列对象）
```json
{
  "id": 1,
  "title": "Spring Boot 入门系列",
  "description": "从零开始学习 Spring Boot",
  "coverImageUrl": null,
  "authorName": "张三",
  "articleCount": 3,
  "isPublic": 1,
  "articles": [
    { "id": 10, "title": "第一章：环境搭建", "sortOrder": 1 },
    { "id": 15, "title": "第二章：第一个应用", "sortOrder": 2 },
    { "id": 22, "title": "第三章：数据库操作", "sortOrder": 3 }
  ],
  "createTime": "2026-05-14T10:00:00",
  "updateTime": "2026-05-14T10:00:00"
}
```

---

## 🔗 数据库表结构

| 表名 | 说明 | 关键字段 |
|------|------|----------|
| `article_version` | 文章版本历史 | article_id, version, title, content, change_note, operator_id |
| `article_like` | 文章点赞 | article_id, user_id (联合唯一) |
| `user_favorite` | 用户收藏 | user_id, article_id, folder_name |
| `article_comment` | 评论 | article_id, user_id, parent_id, content, status |
| `image` | 图片资源 | user_id, article_id, storage_path, thumbnail_path, file_size |
| `notification` | 通知 | user_id, type, title, content, is_read |
| `reading_progress` | 阅读进度 | user_id, article_id, progress, last_position, last_read_time |
| `article_series` | 文章系列 | user_id, title, description, cover_image_url, article_count, is_public |
| `article_series_item` | 系列-文章关联 | series_id, article_id, sort_order |

---

## 📝 前端集成建议

### 评论组件
- 使用 `GET /api/articles/{id}/comments` 获取树形评论列表
- 一级评论的 `replies` 字段包含子回复
- 发表评论时传 `parentId` 实现回复功能
- 评论内容会自动过滤敏感词

### 图片上传
- 使用 `multipart/form-data` 格式上传
- 上传成功后返回 `url` 和 `thumbnailUrl`
- 将 `url` 插入 Markdown 编辑器：`![图片描述](url)`
- 列表展示时使用 `thumbnailUrl` 提高加载速度

### 通知系统
- 轮询 `GET /api/notifications/unread/count` 获取未读数量
- 点击通知列表时调用 `PUT /api/notifications/{id}/read` 标记已读
- 通知类型 `type` 可用于显示不同图标

### 版本控制
- 文章编辑页可添加"历史版本"入口
- 调用 `GET /api/articles/{id}/versions` 获取版本列表
- 选择两个版本调用 diff 接口查看差异
- 点击"回滚"调用 rollback 接口

### 忘记密码
- 用户点击"忘记密码" → `POST /api/auth/forgot-password` 发送邮件
- 邮件中的链接携带 `token` 和 `userId` 参数
- 重置页加载时调用 `GET /api/auth/reset-password/validate` 校验 token
- 用户输入新密码 → `POST /api/auth/reset-password`

### 阅读历史
- 文章阅读页可调用 `POST /api/reading-progress` 保存阅读进度
- 用户中心"阅读历史"页调用 `GET /api/reading-history` 获取分页列表
- 支持单条删除 `DELETE /api/reading-history/{id}` 和全部清空

### 文章系列
- 用户可以将多篇文章组织为有序系列（如教程连载）
- 创建系列时可传入初始文章列表
- 系列详情中的 `articles` 数组按 `sortOrder` 排序
- 前端展示时可用左右箭头或目录导航切换上一章/下一章

### 评论审核（配置项）
- `app.comment.auto-approve=false` 时评论需审核，前端发评论后提示"评论已提交，等待审核"
- 管理员可通过 `GET /api/comments/pending` 查看待审核评论

### 全文搜索
- 使用 `GET /api/search/articles?keyword=xxx` 进行全文搜索，返回高亮结果
- 搜索建议 `GET /api/search/suggestions?prefix=xxx` 可用于搜索框自动补全
- 支持按作者、分类、标签维度搜索

### 文件导出
- 调用导出接口后返回文件路径，再通过 `GET /api/export/download?filePath=xxx` 下载
- 支持 PDF、Word、Markdown ZIP 三种格式
- 导出记录可通过 `GET /api/export/records` 查看

### AI 功能
- `POST /api/deepseek/generate-summary` 根据文章内容生成摘要
- `POST /api/deepseek/generate-title` 根据内容生成标题
- `POST /api/deepseek/polish` 对文本进行润色，支持指定风格和语气
- `GET /api/deepseek/status` 检查 AI 服务连接状态

### 用户关注
- 关注/取关使用 `POST /api/users/{id}/follow` 和 `POST /api/users/{id}/unfollow`
- 关注者文章动态 `GET /api/users/following/articles` 可用于信息流页面
- 粉丝/关注列表返回 UserVO 列表

### 文章协作
- 文章所有者可通过 `POST /api/articles/{id}/collaborators` 添加协作者
- 权限分为 `VIEW`（只读）和 `EDIT`（可编辑）
- 被共享的文章通过 `GET /api/articles/shared` 获取

### SSE 实时通知
- `GET /api/notifications/subscribe` 返回 SSE 流，可实时接收新通知
- 前端使用 `EventSource` 连接，比轮询更高效
- 此接口无需 JWT（已白名单化）

### Redis 缓存（行为变更）
- 热门文章 `GET /api/articles/hot` 有 5 分钟缓存，数据最多延迟 5 分钟
- 标签/分类列表有 30 分钟缓存，增删改操作自动清缓存

---

## 🔴 现有功能改进建议

### I-1. 评论审核机制修复 ✅ 已完成

**已实现**:
- 新增配置项 `app.comment.auto-approve`（默认 `true` 兼容旧行为），设为 `false` 时评论创建后为待审核状态
- 新增配置项 `app.comment.sensitive-word-action`（`REJECT` 拒绝 / `FLAG` 标记替换）
- 敏感词库更新为实际词汇（诈骗、赌博、色情、暴力、毒品、枪支），支持扩展
- 新增 `GET /api/comments/my` 获取当前用户评论历史（分页）

**新增 API**（已纳入 [评论 API](#9-评论-api) 章节 9.6）:

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/comments/my` | 获取当前用户的评论历史（分页） |

---

### I-2. 忘记密码流程 ✅ 已完成

详见 [认证 API](#1-认证-api) 章节 1.5-1.7。

**已实现**:

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/forgot-password` | 发送密码重置邮件（输入邮箱） |
| POST | `/api/auth/reset-password` | 通过 token 重置密码 |
| GET | `/api/auth/reset-password/validate` | 验证重置 token 是否有效 |

> 密码重置 token 存储在用户表的 `verification_token` 字段（前缀 `pwreset:`），15 分钟有效，使用后立即清除。

---

### I-3. Redis 缓存层启用 ✅ 已完成

**已实现**:
- 新增 `RedisConfig` 配置类，启用 Spring Cache，配置多级 TTL
- 热门文章列表缓存到 Redis，TTL 5 分钟（`@Cacheable("hotArticles")`）
- 标签列表缓存 TTL 30 分钟（`@Cacheable("tags")`），增删改自动清除缓存
- 分类列表缓存 TTL 30 分钟（`@Cacheable("categories")`），增删改自动清除缓存
- 新增 `ViewCountService`，阅读量先写入 Redis 计数器，每 5 分钟批量同步到 MySQL
- 定时同步使用 Redis 分布式锁避免多实例重复执行

---

### I-4. API 限流保护

**现状问题**: 所有端点无限流机制，公开端点（如文章列表、视图计数）可被恶意刷量。登录接口无失败次数限制，存在暴力破解风险。

**建议添加**:
- 引入 Spring Cloud Sentinel 或 Bucket4j 实现限流
- 登录接口：同一 IP 5 分钟内最多 10 次尝试，超出后锁定 15 分钟
- 公开文章接口：每 IP 每秒最多 30 次请求
- 验证码发送接口：同一邮箱 1 分钟内只能发送 1 次
- 限流配置可通过 Nacos 动态调整

---

### I-5. 计数字段一致性修复 ✅ 已完成

详见 [计数修复 API](#20-计数修复-api管理员) 章节。

**已实现**:

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/admin/articles/reconcile-counts` | 从源表重新计算所有文章的计数字段 |
| POST | `/api/admin/articles/{id}/reconcile-counts` | 重新计算单篇文章的计数字段 |

> 需要 `ADMIN` 角色。`ArticleMapper` 新增 `reconcileAllCounts()` 和 `reconcileCountsByArticleId()` 方法，使用一条 SQL 从 article_like、article_comment、user_favorite 三张源表重新计算计数。

---

### I-6. Feign 客户端微服务拆分

**现状问题**: `AiServiceClient`、`ExportServiceClient`、`UserServiceClient` 三个 Feign 接口已定义但未被调用。AI 摘要生成、导出、用户管理等逻辑都在本服务内实现，未真正走微服务调用。

**建议修改**:
- 在 `application.properties` 增加开关 `app.feign.enabled=false`，控制走本地实现还是远程调用
- 逐步将 AI 服务调用改为通过 `AiServiceClient` 远程调用 `markdown-ai` 服务
- 这对于提升 AI 模块的独立扩缩容能力很重要

---

## 🆕 新增功能建议

### N-1. 阅读历史 / 最近浏览 ✅ 已完成

详见 [阅读历史 API](#17-阅读历史-api) 章节。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/reading-history` | 获取最近浏览历史（分页，含文章标题和作者） |
| DELETE | `/api/reading-history/{id}` | 删除单条浏览记录 |
| DELETE | `/api/reading-history` | 清空所有浏览历史 |

> 复用了已有的 `reading_progress` 表，新增 `ReadingHistoryVO` 响应对象，批量查询文章和用户信息消除 N+1。

---

### N-2. 文章系列/合集 ✅ 已完成

详见 [文章系列/合集 API](#18-文章系列合集-api) 章节。

**新增文件**: `entity/ArticleSeries.java`, `entity/ArticleSeriesItem.java`, `vo/ArticleSeriesVO.java`, `mapper/ArticleSeriesMapper.java`, `mapper/ArticleSeriesItemMapper.java`, `service/ArticleSeriesService.java`, `service/impl/ArticleSeriesServiceImpl.java`, `controller/ArticleSeriesController.java`

**新增数据库表**: `article_series`, `article_series_item`（已加入 `schema.sql`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/series` | 创建系列（可选传入初始文章列表） |
| PUT | `/api/series/{id}` | 更新系列信息 |
| DELETE | `/api/series/{id}` | 删除系列 |
| GET | `/api/series/{id}` | 获取系列详情（含文章列表，按 sortOrder 排序） |
| GET | `/api/series` | 获取我的系列列表（分页） |
| GET | `/api/series/user/{userId}` | 获取某用户的公开系列 |
| POST | `/api/series/{id}/articles` | 向系列添加文章 |
| DELETE | `/api/series/{id}/articles/{articleId}` | 从系列移除文章 |
| PUT | `/api/series/{id}/articles/sort` | 调整系列内文章排序（传入排序后的 ID 数组） |

---

### N-3. 草稿箱管理增强

**功能描述**: 独立管理草稿，支持自动保存、草稿预览、草稿状态流转。

**优先级**: 中 | **工作量**: 中

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/articles/drafts` | 获取所有草稿列表（按更新时间倒序） |
| GET | `/api/articles/drafts/count` | 获取草稿数量 |
| DELETE | `/api/articles/drafts` | 批量删除草稿 |
| POST | `/api/articles/{id}/publish` | 将草稿发布为公开/私有文章 |
| POST | `/api/articles/{id}/auto-save` | 自动保存草稿内容（防抖，间隔 30s） |

**请求示例**:
```json
// POST /api/articles/{id}/publish
{ "status": "PUBLIC" }

// POST /api/articles/{id}/auto-save
{ "title": "编辑中的文章", "content": "当前的 Markdown 内容..." }
```

> 自动保存仅在状态为 DRAFT 时生效。如果 30 秒内内容无变化，跳过保存。

---

### N-4. 批量操作增强

**功能描述**: 支持对多篇文章批量修改标签、分类、状态、导出权限。

**优先级**: 中 | **工作量**: 小

| 方法 | 路径 | 说明 |
|------|------|------|
| PUT | `/api/articles/batch/tags` | 批量为文章添加/移除标签 |
| PUT | `/api/articles/batch/category` | 批量移动文章到分类 |
| PUT | `/api/articles/batch/delete` | 批量软删除文章 |
| PUT | `/api/articles/batch/export-permission` | 批量修改导出权限 |

**请求示例**:
```json
// PUT /api/articles/batch/tags
{
  "articleIds": [1, 2, 3],
  "addTagNames": ["java", "spring"],
  "removeTagNames": ["old-tag"]
}

// PUT /api/articles/batch/delete
{ "articleIds": [1, 2, 3] }
```

---

### N-5. 个人统计看板

**功能描述**: 为每个用户提供文章维度的数据统计，包括阅读趋势、热门标签、内容分布等。

**优先级**: 中 | **工作量**: 中

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/stats/overview` | 个人数据概览（文章数、总阅读量、总点赞数等） |
| GET | `/api/stats/reading-trends` | 阅读趋势（按天/周/月聚合） |
| GET | `/api/stats/tag-distribution` | 个人标签使用分布 |
| GET | `/api/stats/writing-calendar` | 写作日历热力图（一年的发文频率） |
| GET | `/api/admin/stats/platform` | 平台整体统计（管理员） |

**响应示例**:
```json
// GET /api/stats/overview
{
  "code": 200,
  "data": {
    "totalArticles": 42,
    "publicArticles": 30,
    "draftArticles": 8,
    "privateArticles": 4,
    "totalViews": 15000,
    "totalLikes": 320,
    "totalComments": 180,
    "totalFollowers": 56,
    "totalFollowing": 23,
    "mostViewedArticle": {
      "id": 100, "title": "Spring Boot 入门", "viewCount": 3200
    },
    "joinDate": "2025-01-15T10:00:00"
  }
}

// GET /api/stats/writing-calendar?year=2026
{
  "code": 200,
  "data": [
    { "date": "2026-01-15", "count": 2 },
    { "date": "2026-02-01", "count": 1 },
    { "date": "2026-03-10", "count": 3 }
  ]
}
```

---

### N-6. 文章 SEO 元数据

**功能描述**: 为文章支持自定义 URL Slug 和 SEO 元数据（meta description、keywords），便于搜索引擎索引和社交分享。

**优先级**: 中 | **工作量**: 小

**数据库变更**:
```sql
ALTER TABLE article ADD COLUMN `slug` VARCHAR(200) NULL COMMENT '自定义URL标识';
ALTER TABLE article ADD COLUMN `meta_description` VARCHAR(300) NULL COMMENT 'SEO描述';
ALTER TABLE article ADD COLUMN `meta_keywords` VARCHAR(300) NULL COMMENT 'SEO关键词';
CREATE UNIQUE INDEX uk_slug ON article(slug) WHERE slug IS NOT NULL;
```

**API 变更**:
- 在 `ArticleCreateDTO`、`ArticleSaveDTO` 中新增 `slug`、`metaDescription`、`metaKeywords` 字段
- 支持通过 slug 访问文章: `GET /api/articles/slug/{slug}`
- 创建文章时若未提供 slug，自动根据标题生成拼音 slug

---

### N-7. 内容操作审计日志

**功能描述**: 记录文章的关键操作（创建、编辑、删除、发布、回滚），满足合规和排查需求。

**优先级**: 中 | **工作量**: 中

**新增表**:
```sql
CREATE TABLE audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    username VARCHAR(50),
    action VARCHAR(30) NOT NULL COMMENT 'CREATE/UPDATE/DELETE/PUBLISH/ROLLBACK/EXPORT',
    target_type VARCHAR(30) NOT NULL COMMENT 'ARTICLE/CATEGORY/TAG/COMMENT/USER',
    target_id BIGINT,
    target_name VARCHAR(200),
    detail VARCHAR(1000) COMMENT '变更摘要',
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_target (target_type, target_id),
    INDEX idx_create_time (create_time)
);
```

**API 端点**（管理员）:

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/audit-logs` | 分页查询审计日志（支持按用户、操作类型、时间范围筛选） |
| GET | `/api/admin/audit-logs/{id}` | 查看单条日志详情 |
| GET | `/api/users/me/activity` | 当前用户的活动记录 |

---

### N-8. 两步验证 (2FA)

**功能描述**: 支持 TOTP 两步验证，增强账户安全性。

**优先级**: 低 | **工作量**: 大

**数据库变更**:
```sql
ALTER TABLE sys_user ADD COLUMN `totp_secret` VARCHAR(64) NULL COMMENT 'TOTP密钥';
ALTER TABLE sys_user ADD COLUMN `totp_enabled` TINYINT(1) DEFAULT 0 COMMENT '2FA是否启用';
```

**API 端点**:

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/2fa/setup` | 生成 TOTP 密钥和二维码 URI |
| POST | `/api/auth/2fa/enable` | 验证 TOTP 码并启用 2FA |
| POST | `/api/auth/2fa/disable` | 禁用 2FA（需验证当前 TOTP 码） |
| GET | `/api/auth/2fa/status` | 查询 2FA 状态 |

> 登录流程变更：用户名密码验证通过后，若用户启用 2FA，返回 `code=201` 要求输入 TOTP 验证码。二次验证通过后发放 JWT。

---

### N-9. 文章模板

**功能描述**: 用户可以创建和保存文章模板（预设的 Markdown 结构），新建文章时从模板快速开始。

**优先级**: 低 | **工作量**: 小

**新增表**:
```sql
CREATE TABLE article_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(300),
    content LONGTEXT NOT NULL COMMENT '模板Markdown内容',
    category_id BIGINT,
    tag_names VARCHAR(500) COMMENT '逗号分隔的标签名',
    use_count INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

**API 端点**:

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/templates` | 保存当前文章为模板 |
| GET | `/api/templates` | 获取我的模板列表 |
| GET | `/api/templates/{id}` | 获取模板详情 |
| PUT | `/api/templates/{id}` | 更新模板 |
| DELETE | `/api/templates/{id}` | 删除模板 |
| POST | `/api/templates/{id}/use` | 从模板创建新文章 |

---

### N-10. 文章定时发布

**功能描述**: 支持设置文章在未来的某个时间点自动发布。

**优先级**: 低 | **工作量**: 中

**数据库变更**:
```sql
ALTER TABLE article ADD COLUMN `scheduled_publish_time` DATETIME NULL COMMENT '定时发布时间';
```

**API 变更**:
- 创建/更新文章时可选 `scheduledPublishTime` 字段
- 后端定时任务每分钟扫描已到达发布时间的文章，自动将状态从 DRAFT 改为 PUBLIC
- 新增取消定时发布端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/articles/{id}/schedule` | 设置定时发布时间 |
| DELETE | `/api/articles/{id}/schedule` | 取消定时发布 |

---

## 📋 API 接口完整索引

以下为本项目全部 20 个控制器的接口总览（均已详细文档化）：

| 编号 | 功能 | 控制器 | 基础路径 | 接口数 |
|------|------|--------|----------|--------|
| 1 | 认证 | AuthController | `/api/auth` | 7 |
| 2 | 用户管理 | UserController | `/api/users` | 9 |
| 3 | 文章管理（含置顶） | ArticleController | `/api/articles` | 17 |
| 4 | 标签管理 | TagController | `/api/tags` | 8 |
| 5 | 分类管理 | CategoryController | `/api/categories` | 6 |
| 6 | 文章版本控制 | ArticleVersionController | `/api/articles/{id}/versions` | 4 |
| 7 | 点赞 | InteractionController | `/api/articles/{id}/like` | 3 |
| 8 | 收藏 | InteractionController + FavoriteFolderController | `/api/favorites` | 8 |
| 9 | 评论（含我的评论） | InteractionController | `/api/articles/{id}/comments` | 5 |
| 10 | 热门文章 | InteractionController | `/api/articles/hot` | 1 |
| 11 | 图片上传 | ImageController | `/api/images` | 5 |
| 12 | 通知系统 | NotificationController | `/api/notifications` | 7 |
| 13 | 全文搜索 | SearchController | `/api/search` | 9 |
| 14 | 文件导出 | ExportController | `/api/export` | 6 |
| 15 | 数据备份 | BackupController | `/api/backup` | 3 |
| 16 | AI 功能 | DeepSeekController | `/api/deepseek` | 7 |
| 17 | 阅读历史/进度 | ReadingProgressController | `/api/reading-history` | 6 |
| 18 | 文章系列 | ArticleSeriesController | `/api/series` | 9 |
| 19 | 文章导入 | ArticleImportController | `/api/articles/import` | 2 |
| 20 | 计数修复 | AdminController | `/api/admin` | 2 |
| 21 | 用户关注 | UserFollowController | `/api/users/{id}/follow` | 6 |
| 22 | 文章协作 | ArticleCollaboratorController | `/api/articles/{id}/collaborators` | 4 |
| - | 合计 | **20 个控制器** | - | **~120 个接口** |

---

## 📊 优先级排序总览

| 优先级 | 编号 | 名称 | 类型 | 工作量 | 状态 |
|--------|------|------|------|--------|------|
| 🔴 高 | I-1 | 评论审核机制修复 | 改进 | 小 | ✅ 完成 |
| 🔴 高 | I-2 | 忘记密码流程 | 改进 | 小 | ✅ 完成 |
| 🔴 高 | I-3 | Redis 缓存层启用 | 改进 | 中 | ✅ 完成 |
| 🔴 高 | I-5 | 计数字段一致性修复 | 改进 | 小 | ✅ 完成 |
| 🔴 高 | N-1 | 阅读历史/最近浏览 | 新增 | 小 | ✅ 完成 |
| 🔴 高 | N-2 | 文章系列/合集 | 新增 | 中 | ✅ 完成 |
| 🟡 中 | I-4 | API 限流保护 | 改进 | 中 | 待实现 |
| 🟡 中 | I-6 | Feign 微服务拆分 | 改进 | 大 | 待实现 |
| 🟡 中 | N-3 | 草稿箱管理增强 | 新增 | 中 | 待实现 |
| 🟡 中 | N-4 | 批量操作增强 | 新增 | 小 | 待实现 |
| 🟡 中 | N-5 | 个人统计看板 | 新增 | 中 | 待实现 |
| 🟡 中 | N-6 | 文章 SEO 元数据 | 新增 | 小 | 待实现 |
| 🟡 中 | N-7 | 内容操作审计日志 | 新增 | 中 | 待实现 |
| 🟢 低 | N-8 | 两步验证 (2FA) | 新增 | 大 | 待实现 |
| 🟢 低 | N-9 | 文章模板 | 新增 | 小 | 待实现 |
| 🟢 低 | N-10 | 文章定时发布 | 新增 | 中 | 待实现 |
