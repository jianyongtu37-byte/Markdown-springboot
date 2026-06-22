# 📝 Markdown 知识库系统 — 项目简历

---

## 一、项目概述

**Markdown 知识库系统** 是一个基于 **Spring Cloud 微服务架构** 的知识管理平台，包含 **6 个 Java 微服务 + 1 个 Python RAG 服务 + Vue 3 前端**。核心功能包括文章管理、AI 智能摘要、Elasticsearch 全文搜索、RAG 知识问答、多格式导出等。项目采用 **Spring Boot 3.2 + Spring Cloud Alibaba** 技术栈，通过 **Nacos** 实现服务注册发现，**Gateway** 统一路由鉴权，**OpenFeign** 实现服务间调用，**Sentinel** 提供熔断降级。

---

## 二、岗位匹配度速览

| JD 要求 | 项目对应实现 | 匹配程度 |
|---------|-------------|---------|
| **Java 基础**，了解面向对象编程和常用类库 | Java 17，24 个实体类、23 个 Controller，使用泛型 `Result<T>`/`PageResult<T>` 统一封装、Stream API、CompletableFuture 异步编程 | ✅ 完全匹配 |
| **Spring Boot / Spring MVC**，有实际项目经验 | Spring Boot 3.2.5，Spring MVC 6.1，Spring Security 6.x，Spring AOP，Spring Validation，Spring Task @Async/@Scheduled | ✅ 完全匹配 |
| **MySQL**，能编写基本 SQL 查询 | MySQL 8.0+，MyBatis-Plus ORM，LambdaQueryWrapper 类型安全查询，`COUNT + CASE WHEN` 多维统计，原子 UPDATE 并发更新，逻辑外键设计 | ✅ 完全匹配 |
| **Redis / Elasticsearch** 等中间件 | Redis 7.x（邮箱验证码 TTL 缓存、阅读量防刷）；Elasticsearch 9.x（multi_match 全文检索，title^3/content^2/summary^1.5/tags^2 权重调优，fuzziness AUTO） | ✅ 完全匹配 |
| **微服务架构**，Spring Cloud / Nacos / Gateway / Feign | 6 个微服务：Gateway(8080) + User(8081) + Article(8084) + AI(8082) + Export(8083) + RAG(8085)，Nacos 注册发现，Gateway JWT 鉴权 + 用户上下文传播，OpenFeign 服务间调用，Sentinel 熔断降级 | ✅ 完全匹配 |

---

## 三、微服务架构设计（重点）

### 3.1 服务拆分

| 服务 | 端口 | 职责 | 核心技术 |
|------|------|------|---------|
| **markdown-gateway** | 8080 | API 网关：统一入口、JWT 解析、用户上下文传播、路由转发 | Spring Cloud Gateway（响应式）、Nacos、Sentinel |
| **markdown-user** | 8081 | 用户服务：注册、登录、邮箱验证、密码重置 | Spring Security、JWT、Redis、MyBatis-Plus |
| **Markdown**（文章核心） | 8084 | 文章 CRUD、评论、点赞、收藏、搜索、版本控制、通知 | Spring MVC、MyBatis-Plus、Elasticsearch、Redis |
| **markdown-ai** | 8082 | AI 服务：摘要生成、文章润色、标签提取、知识图谱 | Spring WebFlux（响应式）、DeepSeek API |
| **markdown-export** | 8083 | 文件导出：PDF、Word、Markdown ZIP | RabbitMQ（异步任务）、iText、Flexmark、docx4j |
| **python-rag-agent** | 8085 | RAG 知识问答：向量检索 + LLM 生成 | Python FastAPI、FAISS、sentence-transformers |
| **markdown-common** | — | 共享库：BaseEntity、Result\<T\>、UserContextHolder | MyBatis-Plus 注解、Jackson、Lombok |

### 3.2 服务间通信

```
客户端 → Gateway(8080) → [JWT解析 + X-User-* 头注入] → 各微服务
                              │
                    ┌─────────┼─────────┐
                    │         │         │
              OpenFeign   OpenFeign   Feign+FallbackFactory
                    │         │         │
              User Service  AI Service  RAG Agent (Python)
```

- **Gateway → 下游**：`AuthGlobalFilter` 解析 JWT，注入 `X-User-Id`/`X-User-Name`/`X-User-Email`/`X-User-Role` 头，**剥离客户端伪造的 X-User-* 头**
- **Java 服务间**：OpenFeign + Nacos 服务名，`FeignConfig` 自动传播用户上下文头
- **Java → Python**：Feign 调用 RAG Agent，`RAGServiceClientFallbackFactory` 提供降级
- **各服务内部**：`UserContextFilter`（Servlet Filter）读取 X-User-* 头 → `UserContextHolder`（ThreadLocal）

### 3.3 Gateway 鉴权流程

```java
// AuthGlobalFilter 核心逻辑（伪代码）
1. 白名单路径放行（/api/auth/**, /api/rag/**）
2. 提取 Authorization: Bearer <token>
3. JwtUtil 解析 token → userId, username, email, role, authorities
4. 剥离客户端 X-User-* 头（防伪造）
5. 注入标准化 X-User-* 头到下游请求
6. 转发到目标微服务
```

---

## 四、技术栈详解

### 4.1 Java 后端框架

| 技术 | 版本 | 用途 |
|------|------|------|
| **Spring Boot** | 3.2.5 | 应用主框架，IoC 容器，自动配置 |
| **Spring MVC** | 6.1.x | RESTful API 控制器层，统一异常处理 |
| **Spring Security** | 6.x | JWT 认证过滤链、BCrypt 密码加密、方法级权限控制 |
| **Spring AOP** | 6.x | 日志切面、权限校验切面 |
| **Spring Validation** | 6.x | Jakarta Validation 参数校验（@Valid + @NotNull 等） |
| **Spring Task** | 6.x | @Async 异步任务（AI 生成）、@Scheduled 定时备份 |
| **Spring WebFlux** | 6.x | WebClient 异步调用 DeepSeek API（AI 服务采用全响应式） |
| **Spring Cloud Gateway** | — | 响应式 API 网关，JWT 鉴权，路由转发 |
| **Spring Cloud Alibaba Nacos** | 2023.0.1.0 | 服务注册发现、配置中心 |
| **Spring Cloud Alibaba Sentinel** | — | 熔断降级、流量控制 |
| **OpenFeign** | — | 声明式 HTTP 客户端，服务间调用 |
| **Spring Boot Actuator** | 3.2.5 | 健康检查、监控端点 |

### 4.2 数据层

| 技术 | 版本 | 用途 |
|------|------|------|
| **MySQL** | 8.0+ | 主数据库，utf8mb4 字符集 |
| **MyBatis-Plus** | 3.5.5 | ORM 框架，BaseMapper 通用 CRUD，LambdaQueryWrapper 类型安全查询，分页插件，逻辑删除 |
| **Redis** | 7.x | 邮箱验证码（TTL 过期）、阅读量防刷、缓存 |
| **Elasticsearch** | 9.x | 全文搜索引擎，multi_match 多字段加权检索 |
| **Spring Data Elasticsearch** | 5.x | ES 数据访问层，@Field 注解映射 |
| **Spring Data Redis** | 3.x | Redis 数据访问层，RedisTemplate |
| **HikariCP** | 内置 | 高性能数据库连接池 |

### 4.3 安全与认证

| 技术 | 版本 | 用途 |
|------|------|------|
| **JWT (jjwt)** | 0.12.5 | 无状态 Token 认证，Payload 携带 userId/nickname/authorities |
| **BCrypt** | 内置 | 密码单向加密 |
| **UserContextHolder** | 自研 | ThreadLocal 缓存用户信息，Service 层零数据库查询 |

### 4.4 消息队列与异步

| 技术 | 用途 |
|------|------|
| **RabbitMQ** | Export 服务异步任务（PDF/Word 导出） |
| **@Async + CompletableFuture** | AI 摘要异步生成、ES 索引异步同步、阅读量异步更新 |
| **SSE (Server-Sent Events)** | 通知实时推送、RAG 流式回答 |

### 4.5 文档处理

| 技术 | 用途 |
|------|------|
| **Flexmark** | Markdown → HTML 解析 |
| **Flexmark Docx Converter** | Markdown → Word (.docx) |
| **iText html2pdf** | HTML → PDF |
| **docx4j** | Word 文档操作、自定义样式模板 |
| **Apache POI** | Word 文档导入 |
| **Apache PDFBox** | PDF 文档导入 |
| **Jsoup** | HTML 清理（XSS 防护） |

### 4.6 前端（Vue 3）

| 技术 | 用途 |
|------|------|
| **Vue 3.5** | Composition API + `<script setup>` |
| **TypeScript 5.9** | 全量类型安全 |
| **Vite 7.3** | 极速构建 + 开发服务器 |
| **Pinia 3.0** | 状态管理（auth、article、rag store） |
| **Element Plus 2.13** | 企业级 UI 组件库 |
| **Vditor 3.11** | 所见即所得 Markdown 编辑器 |
| **Tailwind CSS 3.4** | 原子化 CSS + 自研 Cursor Design System |
| **AntV G6 5.1** | 知识图谱可视化（力导向布局） |
| **PWA (Workbox)** | 离线缓存、Service Worker |

### 4.7 Python RAG 服务

| 技术 | 用途 |
|------|------|
| **FastAPI** | 异步 Web 框架 |
| **FAISS** | 向量数据库（IndexIDMap + IndexFlatIP），用户级隔离 |
| **sentence-transformers** | Embedding 模型（BAAI/bge-small-zh-v1.5，512 维，中文优化） |
| **DeepSeek API** | LLM 生成（deepseek-v4-pro） |
| **httpx** | 异步 HTTP 客户端 |

### 4.8 部署与运维

| 技术 | 用途 |
|------|------|
| **Docker + Docker Compose** | 容器化部署，6 个服务 + 基础设施一键启动 |
| **Nginx** | 反向代理，统一入口（80/443） |
| **Maven** | 多模块构建（parent POM 统一依赖管理） |

---

## 五、数据库设计（24 张表）

### 5.1 核心业务表

| 表名 | 说明 | 核心字段 | 设计要点 |
|------|------|---------|---------|
| `sys_user` | 用户表 | username, password(BCrypt), email, nickname, role | 逻辑删除 |
| `article` | 文章主表 | title, content(LONGTEXT), status(0草稿/1私密/2公开), view_count, ai_status | 逻辑删除，阅读量原子更新 |
| `category` | 分类表 | name, user_id(0=系统默认), sort_order | 支持全局分类 + 个人分类 |
| `tag` | 标签表 | name(唯一约束) | 全局标签池 |
| `article_tag` | 文章-标签关联 | article_id, tag_id(联合主键) | 多对多关联 |
| `article_version` | 版本历史 | version, title, content, change_note | 每次更新自动快照 |
| `article_comment` | 评论表 | content, parent_id(二级回复), status(审核状态) | 树形结构 |
| `article_like` | 点赞表 | article_id, user_id(唯一约束) | Toggle 式，原子计数 |
| `user_favorite` | 收藏表 | article_id, user_id, folder_name | 多收藏夹分类 |
| `favorite_folder` | 收藏夹 | name, user_id, sort_order | 用户自定义 |
| `notification` | 通知表 | type, title, content, is_read | SSE 实时推送 |
| `image` | 图片表 | storage_path, file_size, mime_type | 自动缩略图 |
| `backup_record` | 备份记录 | backup_type, format, file_path, status | 定时 + 手动 |

### 5.2 扩展功能表

| 表名 | 说明 |
|------|------|
| `article_video` | 视频绑定（YouTube/Bilibili/本地） |
| `article_timestamp` | 视频时间戳目录 |
| `knowledge_node` / `knowledge_edge` | 知识图谱节点与边 |
| `knowledge_graph_generation` | 知识图谱生成记录 |
| `user_follow` | 用户关注关系 |
| `reading_progress` | 阅读进度跟踪 |
| `article_collaborator` | 文章协作者 |
| `article_series` / `article_series_item` | 文章系列（有序集合） |

### 5.3 数据库设计原则

- **逻辑外键**：消除物理外键，采用应用层关联，提升写入性能
- **逻辑删除**：所有主表 `deleted` 字段（0=正常，1=删除），避免数据丢失
- **统一基类**：`BaseEntity`（id, createTime, updateTime, deleted）由 MyBatis-Plus 自动填充
- **原子更新**：阅读量、点赞数、收藏数使用 `UPDATE SET count = count + 1`，避免并发问题

---

## 六、核心功能模块

### 6.1 文章管理

- **Markdown 编辑器**：Vditor 所见即所得，支持数学公式（KaTeX）、代码高亮、TOC 目录
- **三级状态**：草稿(DRAFT) / 仅自己可见(PRIVATE) / 公开可见(PUBLIC)
- **分类 + 标签**：全局分类 + 个人标签（即写即存，自动创建）
- **版本控制**：每次更新自动保存快照，Unified Diff 对比，一键回滚
- **文章系列**：有序集合，适合教程/连载场景
- **协作编辑**：支持添加协作者
- **导入导出**：支持 Word/PDF 导入，PDF/Word/Markdown ZIP 导出

### 6.2 AI 智能模块（技术亮点）

| 功能 | 实现方式 | 技术细节 |
|------|---------|---------|
| **智能摘要** | DeepSeek API + @Async 异步 | WebClient 响应式调用，状态追踪（未开始/生成中/已完成/失败） |
| **文章润色** | DeepSeek API | 4 种风格：正式/随意/学术/商务 |
| **标题生成** | DeepSeek API | 4 种风格：吸引眼球/专业/SEO/简洁 |
| **标签建议** | DeepSeek API | 从内容提取 3-5 个关键标签，JSON Schema 结构化输出 |
| **知识图谱** | DeepSeek API | 6 种实体类型 + 8 种关系类型，AntV G6 力导向可视化 |
| **RAG 知识问答** | FAISS + bge-small-zh + DeepSeek | 用户级向量隔离，增量同步，SSE 流式输出，来源引用 + 置信度评分 |
| **知识缺口分析** | RAG Agent | 分析用户知识库，识别薄弱领域 |
| **学习路径推荐** | RAG Agent | 基于知识库生成结构化阅读顺序 |

### 6.3 全文搜索

- **Elasticsearch 检索**：`multi_match` 跨 title(×3)、content(×2)、summary(×1.5)、tags(×2) 加权检索
- **模糊匹配**：`fuzziness: AUTO`，容忍拼写错误
- **搜索高亮**：命中片段高亮显示
- **异步索引同步**：文章增删改时自动同步 ES 索引
- **索引管理**：管理员可重建索引、查看索引统计

### 6.4 社交互动

- **评论系统**：树形结构（一级 + 子回复）、敏感词过滤、管理员审核
- **点赞系统**：Toggle 式，原子计数更新
- **收藏系统**：多收藏夹分类管理，支持排序
- **通知系统**：SSE 实时推送 + 邮件通知，未读计数
- **热门排行**：按阅读量/点赞数/收藏数排序

### 6.5 导出与备份

- **PDF 导出**：Markdown → HTML → PDF（iText html2pdf）
- **Word 导出**：Markdown → .docx（Flexmark + docx4j），自定义样式模板
- **全站备份**：每天凌晨 2:00 自动备份（@Scheduled），支持手动触发，过期自动清理
- **RabbitMQ 异步**：导出任务通过消息队列异步处理，避免阻塞

---

## 七、性能优化亮点

### 7.1 数据库优化
- **批量查询消除 N+1**：批量查询用户、分类、标签，替代循环单条查询
- **SQL 原子更新**：阅读量/点赞数/收藏数使用 `UPDATE SET count = count + 1`
- **单 SQL 多维统计**：`COUNT + CASE WHEN` 一次查询获取多个统计维度
- **逻辑外键**：消除物理外键依赖，提升写入性能

### 7.2 缓存优化
- **UserContextHolder (ThreadLocal)**：JWT 认证后缓存用户信息，Service 层零数据库查询
- **JWT Payload 扩展**：Token 携带 userId/nickname/authorities，减少数据库查询
- **Redis 缓存**：邮箱验证码 TTL 过期、阅读量防刷

### 7.3 异步处理
- **@Async + CompletableFuture**：AI 摘要、ES 索引同步、阅读量更新均异步执行
- **自定义线程池**：`aiTaskExecutor` 独立线程池，隔离 AI 任务
- **RabbitMQ**：导出任务异步队列处理
- **SSE 流式输出**：RAG 问答逐字渲染，提升用户体验

### 7.4 安全优化
- **Gateway 统一鉴权**：JWT 解析 + X-User-* 头注入，剥离伪造头
- **XSS 防护**：Jsoup HTML 清理
- **SQL 注入防护**：MyBatis-Plus 参数绑定
- **敏感词过滤**：评论内容检测
- **用户数据隔离**：RAG 向量索引按用户隔离

---

## 八、API 接口概览（50+ 接口）

| 模块 | 接口 | 说明 |
|------|------|------|
| **认证** | `POST /api/auth/register` | 用户注册（邮箱验证） |
| | `POST /api/auth/login` | 用户登录（返回 JWT） |
| | `POST /api/auth/forgot-password` | 忘记密码 |
| **文章** | `GET/POST /api/articles` | 文章列表/创建 |
| | `GET/PUT/DELETE /api/articles/{id}` | 文章详情/更新/删除 |
| | `GET /api/articles/my` | 我的文章 |
| | `GET /api/articles/my/stats` | 文章统计 |
| | `PUT /api/articles/batch-status` | 批量更新状态 |
| **搜索** | `GET /api/search` | ES 全文搜索 |
| | `GET /api/search/suggest` | 搜索建议 |
| **互动** | `POST /api/articles/{id}/like` | 点赞/取消 |
| | `POST /api/articles/{id}/favorite` | 收藏/取消 |
| | `POST /api/articles/{id}/comments` | 添加评论 |
| | `GET /api/articles/hot` | 热门排行 |
| **AI** | `POST /api/deepseek/generate-summary` | AI 摘要 |
| | `POST /api/deepseek/polish` | 文章润色 |
| | `POST /api/deepseek/generate-title` | 标题生成 |
| | `POST /api/ai/generate-tags` | 标签建议 |
| | `POST /api/ai/extract-knowledge-graph` | 知识图谱 |
| **RAG** | `POST /api/rag/ask/stream` | RAG 问答（SSE 流式） |
| | `POST /api/rag/analysis/gap` | 知识缺口分析 |
| | `POST /api/rag/analysis/learning-path` | 学习路径推荐 |
| **导出** | `POST /api/export/pdf/{id}` | 导出 PDF |
| | `POST /api/export/word/{id}` | 导出 Word |
| **通知** | `GET /api/notifications/subscribe` | SSE 实时通知 |
| **版本** | `GET /api/articles/{id}/versions` | 版本历史 |
| | `POST /api/articles/{id}/versions/{v}/rollback` | 版本回滚 |

---

## 九、项目亮点总结

1. **微服务架构实践**：6 个微服务 + Python RAG 服务，Spring Cloud Gateway + Nacos + OpenFeign + Sentinel 全链路
2. **Gateway 统一鉴权**：JWT 解析、用户上下文传播（X-User-* 头）、防伪造头剥离，下游服务零鉴权负担
3. **AI 全链路赋能**：DeepSeek 集成摘要/润色/标题/标签/知识图谱 5 大 AI 功能 + RAG 知识问答系统
4. **RAG 智能问答**：FAISS 向量检索 + bge-small-zh Embedding + DeepSeek LLM，用户级数据隔离，增量同步，流式输出
5. **企业级搜索**：Elasticsearch 多字段加权检索（title×3, content×2），异步索引同步
6. **高性能优化**：ThreadLocal 用户缓存、批量查询消除 N+1、SQL 原子更新、@Async 异步处理、RabbitMQ 消息队列
7. **完整互动体系**：评论（树形+审核）、点赞（原子计数）、收藏（多文件夹）、通知（SSE 实时推送）
8. **多格式导出**：PDF/Word/Markdown ZIP，RabbitMQ 异步任务，自定义 Word 样式模板
9. **容器化部署**：Docker Compose 一键部署，Nginx 反向代理，基础设施安全加固
10. **前后端分离**：Vue 3 + TypeScript + Pinia 前端，RESTful API 统一响应格式 `Result<T>`
