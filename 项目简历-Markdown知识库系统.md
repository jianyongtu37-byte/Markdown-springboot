# 📝 Markdown 知识库系统 — 项目简历

---

## 一、项目概述

**Markdown 知识库系统** 是一个基于 **Spring Boot 3.2** 构建的现代化 Markdown 文章管理平台，集成了 **AI 智能摘要生成、Elasticsearch 全文搜索、JWT 用户认证、多格式导出** 等核心功能。项目采用 **前后端分离架构**，提供完整的 **RESTful API** 接口，支持 **Docker 容器化一键部署**。

---

## 二、技术栈总览

### 🔷 后端核心框架

| 技术 | 版本 | 用途 |
|------|------|------|
| **Spring Boot** | 3.2.5 | 应用主框架，IoC 容器 |
| **Spring MVC** | 6.1.x | RESTful API 控制器层 |
| **Spring Security** | 6.x | 认证授权、CORS 跨域配置 |
| **Spring AOP** | 6.x | 面向切面编程 |
| **Spring Validation** | 6.x | 参数校验（Jakarta Validation） |
| **Spring Task** | 6.x | 异步任务调度（@Async、@Scheduled） |
| **Spring Mail** | 6.x | 邮件发送服务 |
| **Spring WebFlux** | 6.x | WebClient 异步 HTTP 调用（调用 DeepSeek AI API） |
| **Spring Boot Actuator** | 3.2.5 | 健康检查、监控端点 |

### 🔷 数据层

| 技术 | 版本 | 用途 |
|------|------|------|
| **MyBatis-Plus** | 3.5.5 | ORM 框架，代码生成器，分页插件 |
| **MySQL** | 8.0+ | 关系型数据库 |
| **Redis** | 7.x | 缓存、阅读量防刷 |
| **Elasticsearch** | 9.x | 全文搜索引擎 |
| **Spring Data Elasticsearch** | 5.x | ES 数据访问层 |
| **Spring Data Redis** | 3.x | Redis 数据访问层 |
| **HikariCP** | 内置于 Spring Boot | 数据库连接池 |

### 🔷 安全与认证

| 技术 | 版本 | 用途 |
|------|------|------|
| **JWT (jjwt)** | 0.12.5 | 无状态 Token 认证 |
| **BCrypt** | 内置于 Spring Security | 密码加密 |
| **Spring Security** | 6.x | 方法级权限控制（@EnableMethodSecurity） |

### 🔷 AI 与智能

| 技术 | 版本 | 用途 |
|------|------|------|
| **DeepSeek AI API** | — | 智能摘要生成、文章润色 |
| **Spring WebClient** | 6.x | 异步调用 DeepSeek API |
| **Spring @Async** | 6.x | AI 摘要异步生成 |

### 🔷 Markdown 处理与导出

| 技术 | 版本 | 用途 |
|------|------|------|
| **Flexmark** | 0.64.8 | Markdown 解析、HTML 渲染 |
| **Flexmark Docx Converter** | 0.64.8 | Markdown → Word (.docx) 转换 |
| **iText html2pdf** | 4.0.5 | HTML → PDF 转换 |
| **docx4j** | 11.4.9 | Word 文档操作、模板渲染 |
| **Jsoup** | 1.17.2 | HTML 清理与安全处理 |
| **Thymeleaf** | 内置于 Spring Boot | 邮件模板引擎 |

### 🔷 工具与实用库

| 技术 | 版本 | 用途 |
|------|------|------|
| **Lombok** | 最新 | 代码简化（@Data、@Builder 等） |
| **Hutool** | 5.8.25 | Java 工具类库 |
| **Jackson JSR310** | 最新 | Java 8 时间序列化 |
| **Jakarta Validation** | 最新 | Bean 参数校验 |

### 🔷 部署与运维

| 技术 | 版本 | 用途 |
|------|------|------|
| **JDK** | 17 | 运行环境 |
| **Maven** | 3.6+ | 项目构建 |
| **Docker** | 最新 | 容器化部署 |
| **Docker Compose** | 3.8 | 多容器编排 |
| **Nginx** | Alpine | 反向代理 |
| **Adminer** | 最新 | 数据库管理工具 |
| **Redis Commander** | 最新 | Redis 管理工具 |

---

## 三、系统架构图

```
┌─────────────────────────────────────────────────────────────┐
│                        Nginx (反向代理)                       │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                    Spring Boot Application                    │
│  ┌────────────────────────────────────────────────────────┐  │
│  │                    Controller 层                        │  │
│  │  ArticleController  AuthController  SearchController   │  │
│  │  InteractionController  ExportController  ...          │  │
│  └──────────────────────┬─────────────────────────────────┘  │
│                         │                                     │
│  ┌──────────────────────▼─────────────────────────────────┐  │
│  │                    Service 层                           │  │
│  │  ArticleService  InteractionService  SearchService     │  │
│  │  ExportService  AiSummaryService  NotificationService  │  │
│  │  BackupService  VideoParserService  ...                │  │
│  └──────────────────────┬─────────────────────────────────┘  │
│                         │                                     │
│  ┌──────────────────────▼─────────────────────────────────┐  │
│  │                    Mapper 层 (MyBatis-Plus)             │  │
│  │  ArticleMapper  UserMapper  TagMapper  CategoryMapper  │  │
│  │  CommentMapper  LikeMapper  FavoriteMapper  ...        │  │
│  └──────────────────────┬─────────────────────────────────┘  │
│                         │                                     │
│  ┌──────────────────────▼─────────────────────────────────┐  │
│  │                    Security 层                          │  │
│  │  JwtAuthenticationFilter → JwtUtil → SecurityConfig    │  │
│  │  CustomUserDetails → UserContextHolder (ThreadLocal)   │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────┬──────────────────────────────────────┘
                       │
    ┌──────────────────┼──────────────────┐
    │                  │                  │
┌───▼────┐      ┌─────▼─────┐      ┌─────▼─────┐
│ MySQL  │      │   Redis   │      │Elasticsearch│
│  8.0   │      │    7.x    │      │    9.x     │
└────────┘      └───────────┘      └───────────┘
```

---

## 四、核心功能模块

### 1️⃣ 文章管理模块
- **Markdown 文章 CRUD**：创建、编辑、删除、发布文章
- **三级状态体系**：草稿(DRAFT) / 仅自己可见(PRIVATE) / 公开可见(PUBLIC)
- **分类与标签**：全局分类 + 个人标签（即写即存，自动创建）
- **文章版本历史**：每次更新自动保存版本快照，支持回滚
- **阅读量统计**：异步原子更新，防刷机制
- **批量操作**：批量更新文章状态、导出权限设置

### 2️⃣ AI 智能模块
- **DeepSeek AI 摘要**：自动为文章生成智能摘要（异步执行）
- **文章润色**：支持多种风格（学术、商务、创意等）和语气
- **状态追踪**：未生成 → 生成中 → 已生成 / 生成失败

### 3️⃣ 全文搜索模块
- **Elasticsearch 全文检索**：支持标题、内容、标签多维度搜索
- **异步索引同步**：文章创建/更新/删除时自动同步 ES 索引
- **中文分词**：支持中文全文搜索

### 4️⃣ 用户认证模块
- **JWT 无状态认证**：Token 中携带用户 ID、昵称、权限信息
- **UserContextHolder 优化**：ThreadLocal 缓存用户信息，减少数据库查询
- **邮箱验证**：注册邮箱验证，评论通知邮件
- **BCrypt 密码加密**

### 5️⃣ 互动模块
- **点赞系统**：Toggle 式点赞/取消，原子更新计数
- **收藏系统**：多收藏夹分类管理
- **评论系统**：二级回复、敏感词过滤、审核机制
- **通知系统**：SSE 实时推送、站内通知、邮件通知
- **热门排行榜**：按阅读量/点赞数/收藏数排序

### 6️⃣ 导出模块
- **PDF 导出**：Markdown → HTML → PDF（iText html2pdf）
- **Word 导出**：Markdown → .docx（Flexmark + docx4j）
- **Markdown ZIP 导出**：全站文章打包下载
- **自定义 Word 模板**：预定义 Heading 1-4、Code、Quote 样式
- **导出权限控制**：作者可设置是否允许他人导出

### 7️⃣ 备份模块
- **自动定时备份**：每天凌晨 2:00 自动备份所有公开文章
- **手动备份**：支持按用户导出
- **过期清理**：自动清理超过保留天数的备份文件

### 8️⃣ 视频集成模块
- **多平台支持**：YouTube / Bilibili / 本地视频
- **视频元数据解析**：自动获取视频时长、ID 等信息
- **时间戳目录**：从文章内容中提取时间戳，生成视频导航目录

---

## 五、数据库设计（15 张表）

| 表名 | 说明 | 核心字段 |
|------|------|---------|
| `sys_user` | 用户表 | username, password(BCrypt), email, nickname |
| `article` | 文章核心表 | title, content(LONGTEXT), status, view_count, ai_status |
| `category` | 分类表 | name, user_id(0=系统默认), sort_order |
| `tag` | 标签表 | name(唯一) |
| `article_tag` | 文章-标签关联 | article_id, tag_id(联合主键) |
| `article_version` | 文章版本历史 | version, title, content, change_note |
| `article_video` | 文章视频关联 | video_url, video_source, video_id, duration |
| `article_timestamp` | 文章时间戳目录 | label, seconds, excerpt |
| `article_like` | 文章点赞表 | article_id, user_id(唯一约束) |
| `user_favorite` | 用户收藏表 | article_id, user_id, folder_name |
| `article_comment` | 评论表 | content, parent_id(二级回复), status(审核) |
| `favorite_folder` | 收藏夹分类表 | name, user_id, sort_order |
| `image` | 图片资源表 | storage_path, file_size, mime_type |
| `notification` | 通知表 | type, title, content, is_read |
| `backup_record` | 备份记录表 | backup_type, format, file_path, status |

---

## 六、性能优化亮点

### 🚀 数据库优化
- **批量查询消除 N+1**：批量查询用户、分类、标签信息，替代循环查询
- **SQL 原子更新**：阅读量、点赞数、收藏数使用原子 UPDATE，避免并发问题
- **单 SQL 多维统计**：使用 `COUNT + CASE WHEN` 一次查询获取多个统计维度
- **逻辑外键**：消除物理外键依赖，采用逻辑外键模式，提升写入性能

### 🚀 缓存优化
- **UserContextHolder (ThreadLocal)**：JWT 认证后缓存用户信息，Service 层零数据库查询
- **JWT Payload 扩展**：Token 中携带 userId、nickname、authorities，减少数据库查询

### 🚀 异步处理
- **@Async 异步执行**：AI 摘要生成、ES 索引同步、阅读量更新均异步执行
- **自定义线程池**：`aiTaskExecutor` 独立线程池，隔离 AI 任务

### 🚀 安全优化
- **XSS 防护**：Jsoup HTML 清理
- **SQL 注入防护**：MyBatis-Plus 参数绑定
- **敏感词过滤**：评论内容敏感词检测
- **权限校验**：Service 层手动权限检查 + 数据权限拦截器

---

## 七、API 接口概览（30+ 接口）

| 模块 | 接口 | 说明 |
|------|------|------|
| **认证** | `POST /api/auth/register` | 用户注册 |
| | `POST /api/auth/login` | 用户登录 |
| **文章** | `GET/POST /api/articles` | 文章列表/创建 |
| | `GET/PUT/DELETE /api/articles/{id}` | 文章详情/更新/删除 |
| | `POST /api/articles/{id}/view` | 增加阅读量 |
| | `POST /api/articles/{id}/ai-status` | 更新 AI 摘要状态 |
| | `GET /api/articles/my` | 我的文章列表 |
| | `GET /api/articles/my/stats` | 文章统计 |
| | `PUT /api/articles/batch-status` | 批量更新状态 |
| **搜索** | `GET /api/search` | Elasticsearch 全文搜索 |
| **互动** | `POST /api/articles/{id}/like` | 点赞/取消点赞 |
| | `POST /api/articles/{id}/favorite` | 收藏/取消收藏 |
| | `POST /api/articles/{id}/comments` | 添加评论 |
| | `GET /api/articles/hot` | 热门文章排行榜 |
| **导出** | `POST /api/export/pdf/{articleId}` | 导出 PDF |
| | `POST /api/export/word/{articleId}` | 导出 Word |
| | `POST /api/export/markdown-zip` | 导出 Markdown ZIP |
| **AI** | `POST /api/deepseek/summary` | 生成 AI 摘要 |
| | `POST /api/deepseek/polish` | 文章润色 |
| **通知** | `GET /api/notifications/subscribe` | SSE 实时通知订阅 |

---

## 八、部署架构

```
┌─────────────────────────────────────────────────────────────┐
│                    Docker Compose 编排                        │
│                                                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────────┐  ┌────────┐ │
│  │  Nginx   │  │   App    │  │  Elasticsearch│  │Adminer │ │
│  │ (Alpine) │──│ (JDK 17) │──│    (9.x)     │  │(DB管理)│ │
│  └──────────┘  └────┬─────┘  └──────────────┘  └────────┘ │
│                     │                                       │
│           ┌─────────┼─────────┐                             │
│           │         │         │                             │
│     ┌─────▼──┐ ┌────▼───┐ ┌──▼──────────┐                  │
│     │ MySQL  │ │ Redis  │ │ Redis Cmdr  │                  │
│     │  8.0   │ │  7.x   │ │  (管理工具)  │                  │
│     └────────┘ └────────┘ └─────────────┘                  │
└─────────────────────────────────────────────────────────────┘
```

---

## 九、项目亮点总结

1. **全栈 Spring Boot 3.2 生态**：紧跟最新技术栈，使用 Jakarta EE、Spring Security 6.x
2. **AI 赋能**：集成 DeepSeek AI，实现智能摘要生成和文章润色
3. **企业级搜索**：Elasticsearch 9.x 全文搜索，中文分词支持
4. **多格式导出**：PDF / Word / Markdown ZIP 三种导出格式
5. **高性能优化**：批量查询消除 N+1、ThreadLocal 缓存、异步处理、SQL 原子更新
6. **完整互动体系**：点赞、收藏（多文件夹）、评论（二级回复+审核）、通知（SSE+邮件）
7. **容器化部署**：Docker Compose 一键部署，6 个服务协同工作
8. **安全体系完善**：JWT 无状态认证、BCrypt 加密、XSS 防护、SQL 注入防护、敏感词过滤
9. **代码质量高**：清晰的包结构、完善的异常处理、详细的日志记录、丰富的代码注释
10. **视频集成**：支持 YouTube/Bilibili 视频解析和时间戳目录生成
