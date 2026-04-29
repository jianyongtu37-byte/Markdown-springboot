# 高优先级功能 API 文档

> 本文档面向前端开发，涵盖文章版本控制、收藏/点赞/评论、图片上传管理、通知系统四个高优先级功能的全部 API 接口。

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

1. [文章版本控制 API](#1-文章版本控制-api)
2. [点赞 API](#2-点赞-api)
3. [收藏 API](#3-收藏-api)
4. [评论 API](#4-评论-api)
5. [热门文章 API](#5-热门文章-api)
6. [图片上传管理 API](#6-图片上传管理-api)
7. [通知系统 API](#7-通知系统-api)

---

## 1. 文章版本控制 API

**基础路径**: `/api/articles/{articleId}/versions`

### 1.1 获取版本列表

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

### 1.2 获取版本详情

获取指定版本的完整内容。

```
GET /api/articles/{articleId}/versions/{versionId}
```

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| articleId | Long | 文章 ID |
| versionId | Long | 版本 ID |

### 1.3 回滚到指定版本

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

### 1.4 比较版本差异

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

## 2. 点赞 API

### 2.1 点赞/取消点赞

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

### 2.2 查询点赞状态

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

### 2.3 获取点赞数

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

## 3. 收藏 API

### 3.1 收藏夹分类管理

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

### 3.2 收藏/取消收藏

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

### 3.2 查询收藏状态

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

### 3.3 获取我的收藏列表

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

### 3.4 获取收藏夹名称列表（旧接口）

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

## 4. 评论 API

### 4.1 添加评论

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

### 4.2 获取评论列表

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

### 4.3 删除评论

```
DELETE /api/comments/{commentId}
```

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| commentId | Long | 评论 ID |

### 4.4 审核评论（管理员）

```
PUT /api/comments/{commentId}/review?status=1
```

**请求参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| status | Integer | 是 | 0-待审核, 1-已通过, 2-已拒绝 |

### 4.5 获取待审核评论列表（管理员）

```
GET /api/comments/pending
```

---

## 5. 热门文章 API

### 5.1 获取热门文章排行榜

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

## 6. 图片上传管理 API

**基础路径**: `/api/images`

### 6.1 上传图片

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

### 6.2 获取我的图片列表

```
GET /api/images/my
```

**响应**: ImageVO 列表

### 6.3 获取文章的图片列表

```
GET /api/images/article/{articleId}
```

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| articleId | Long | 文章 ID |

### 6.4 删除图片

```
DELETE /api/images/{imageId}
```

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| imageId | Long | 图片 ID |

### 6.5 获取图片文件（公开访问）

直接访问图片文件，可用于 `<img>` 标签的 `src`。

```
GET /api/images/{imageId}/file
```

> 返回图片二进制流，Content-Type 为图片的 MIME 类型。

---

## 7. 通知系统 API

**基础路径**: `/api/notifications`

### 7.1 获取未读通知列表

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

### 7.2 获取所有通知列表

```
GET /api/notifications
```

### 7.3 获取未读通知数量

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

### 7.4 标记通知为已读

```
PUT /api/notifications/{notificationId}/read
```

### 7.5 全部标记为已读

```
PUT /api/notifications/read-all
```

### 7.6 删除通知

```
DELETE /api/notifications/{notificationId}
```

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
