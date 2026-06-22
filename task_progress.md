# Markdown 知识库系统 - 微服务架构改造进度

## 已完成

### 1. 父工程 (Markdown-Cloud)
- [x] 创建 `pom.xml` - 统一管理 Spring Boot 3.2.5、Spring Cloud 2023.0.1、Spring Cloud Alibaba 2023.0.1.0 版本
- [x] 统一管理所有公共依赖版本

### 2. 公共模块 (markdown-common)
- [x] `Result.java` - 统一响应结果封装
- [x] `PageResult.java` - 分页结果封装
- [x] `BaseEntity.java` - 基础实体类
- [x] `UserContextHolder.java` - 用户上下文 ThreadLocal

### 3. 网关服务 (markdown-gateway)
- [x] `pom.xml` - Spring Cloud Gateway + Nacos + Sentinel
- [x] `GatewayApplication.java` - 启动类
- [x] `application.yml` - 网关路由配置（路由到 user、ai、export、app 服务）
- [x] `CorsConfig.java` - CORS 跨域配置
- [x] `AuthGlobalFilter.java` - JWT 认证全局过滤器（解析 Token 并传递用户信息到下游）
- [x] `Dockerfile` - 服务容器化

### 4. 用户服务 (markdown-user)
- [x] `pom.xml` - Spring Boot Web + Security + MyBatis-Plus + Nacos + Sentinel
- [x] `UserApplication.java` - 启动类
- [x] `application.yml` - 服务配置
- [x] `SecurityConfig.java` - 安全配置（BCrypt + 无状态 Session）
- [x] `JwtUtil.java` - JWT 工具类
- [x] `MyMetaObjectHandler.java` - MyBatis-Plus 自动填充
- [x] `User.java` - 用户实体
- [x] `UserMapper.java` - MyBatis-Plus Mapper
- [x] `UserMapper.xml` - 自定义 SQL（按用户名/邮箱查询）
- [x] `UserService.java` / `UserServiceImpl.java` - 用户服务（注册/登录）
- [x] `AuthController.java` - 认证控制器
- [x] `LoginRequest.java` / `RegisterRequest.java` - 请求 DTO
- [x] `Dockerfile` - 服务容器化

### 5. AI 智能服务 (markdown-ai)
- [x] `pom.xml` - Spring Boot WebFlux + Nacos + Sentinel
- [x] `AiApplication.java` - 启动类
- [x] `application.yml` - 服务配置
- [x] `DeepSeekController.java` - AI 控制器（摘要生成 + 文章润色）
- [x] `DeepSeekService.java` / `DeepSeekServiceImpl.java` - DeepSeek API 调用实现
- [x] `Dockerfile` - 服务容器化

### 6. 导出服务 (markdown-export)
- [x] `pom.xml` - Spring Boot Web + Nacos + Sentinel + RabbitMQ + Flexmark + iText
- [x] `ExportApplication.java` - 启动类
- [x] `application.yml` - 服务配置
- [x] `ExportController.java` - 导出控制器（PDF/Word/Markdown ZIP）
- [x] `ExportService.java` / `ExportServiceImpl.java` - 导出服务实现（PDF/Word/Markdown ZIP）
- [x] `ExportRequest.java` - 请求 DTO
- [x] `Dockerfile` - 服务容器化

### 7. 原有 Markdown 项目改造
- [x] `pom.xml` - 添加 Nacos、Feign、Sentinel、markdown-common 依赖
- [x] `MarkdownApplication.java` - 添加 @EnableDiscoveryClient、@EnableFeignClients
- [x] `application.properties` - 添加 Nacos、Sentinel 配置
- [x] `UserContextFilter.java` - 用户上下文过滤器（解析网关传递的用户信息）
- [x] `FeignConfig.java` - Feign 请求拦截器（传递用户信息到下游服务）
- [x] `UserServiceClient.java` - 用户服务 Feign 客户端
- [x] `AiServiceClient.java` - AI 服务 Feign 客户端
- [x] `ExportServiceClient.java` - 导出服务 Feign 客户端
- [x] `Dockerfile` - 服务容器化

### 8. Docker 部署
- [x] `docker-compose.yml` - 添加 Nacos、Sentinel、RabbitMQ 基础设施
- [x] `docker-compose.yml` - 添加 user-service、ai-service、export-service、gateway-service、article-service
- [x] 所有服务 Dockerfile 配置

## 待完成

### 9. 用户服务完善
- [x] 用户信息查询接口（`GET /api/users/{userId}`）
- [x] 用户管理（管理员功能：用户列表、状态管理、信息修改）
- [x] 邮箱验证码功能（Redis 存储 + Spring Mail 发送）
- [x] `UserController.java` - 用户管理控制器
- [x] `EmailCodeService.java` - 邮箱验证码服务
- [x] `UserContextFilter.java` - 用户服务中的用户上下文过滤器
- [x] 安全配置更新 - 允许用户接口访问

### 10. 一致性修复
- [x] 网关 `AuthGlobalFilter` 请求头标准化（`X-User-Id`, `X-User-Name`, `X-User-Email`, `X-User-Role`, `X-User-Authorities`）
- [x] `UserContextHolder` 公共类增强（添加 `UserInfo`、`Email`、`Role`、`Nickname`、`Authorities` 支持）
- [x] Markdown 模块 `UserContextFilter` 适配标准化请求头
- [x] `FeignConfig` 请求拦截器更新（自动传递完整的用户上下文请求头）
- [x] Feign 客户端路径修正（`AiServiceClient`、`ExportServiceClient` 与控制器路径对齐）
- [x] Markdown 模块服务名修正（`spring.application.name=markdown-app` 与网关路由匹配）

### 11. 测试与验证
- [ ] 本地启动验证各服务注册到 Nacos
- [ ] 验证网关路由功能
- [ ] 验证 Feign 服务间调用
- [ ] 验证 JWT 认证流程

### 12. 一致性修复（第二轮）
- [x] 统一 UserContextHolder — local holder 回退到 common holder，确保网关模式和本地模式数据一致
- [x] JwtAuthenticationFilter 同时填充 common UserContextHolder
- [x] UserContextFilter（Markdown + markdown-user）增加 X-User-Nickname 头处理
- [x] 网关 AuthGlobalFilter 从 JWT 提取并转发 nickname
- [x] markdown-user JwtUtil 补充 nickname 和 authorities 字段到 JWT
- [x] 删除 markdown-user 中未使用的重复 JwtUtil（security/JwtUtil）
- [x] 修复 ExportServiceClient Feign 返回值类型（Result<byte[]> → byte[]）
- [x] 修复 Dockerfile 端口（EXPOSE 8084 → 8080）
- [x] 修复网关路由路径（/api/user/** → /api/users/**）
- [x] 网关白名单补充 send-code、verify-code、check-email
- [x] 为 markdown-ai 添加 UserContextFilter（WebFilter，适配 WebFlux）
- [x] 为 markdown-export 添加 UserContextFilter

### 13. 新功能 — 文章置顶
- [x] Article 实体、VO、DetailVO、DetailDTO 添加 isPinned + pinnedTime 字段
- [x] ArticleMapper.selectArticleDetailById SQL 增加 is_pinned, pinned_time
- [x] ArticleService 添加 pinArticle / unpinArticle 方法
- [x] 置顶上限 3 篇，权限校验
- [x] 所有文章列表排序：置顶优先 → 置顶时间 → 创建时间
- [x] ArticleController PUT /{id}/pin 和 /{id}/unpin

### 14. 新功能 — 用户关注系统
- [x] UserFollow 实体 + user_follow 表
- [x] UserFollowMapper（countByFollowerAndFollowee, countFollowers, countFollowing, selectFolloweeIds）
- [x] UserFollowService / UserFollowServiceImpl（follow/unfollow/isFollowing/getFollowers/getFollowing/getFollowingArticles）
- [x] UserFollowController（6 个端点）
- [x] FOLLOW 通知类型（关注时推送）
- [x] UserVO 添加 followerCount / followingCount

### 15. 新功能 — 公开用户主页
- [x] UserProfileVO（id, username, nickname, articleCount, totalLikes, followerCount, followingCount, createTime）
- [x] ArticleMapper.selectProfileStatsByUserId（文章数 + 获赞总数）
- [x] UserService.getUserProfile 实现
- [x] UserController GET /{id}/profile（无需认证）
- [x] SecurityConfig 放行 /api/users/*/profile

### 16. 新功能 — 阅读进度追踪
- [x] ReadingProgress 实体 + reading_progress 表
- [x] ReadingProgressMapper / Service / Controller
- [x] POST /api/reading-progress（保存/更新进度）
- [x] GET /api/reading-progress/{articleId}（查询单篇进度）
- [x] GET /api/reading-progress（列出全部进度）

### 17. 新功能 — 文章协作
- [x] ArticleCollaborator 实体 + article_collaborator 表
- [x] ArticleCollaboratorMapper（hasEditPermission, isCollaborator）
- [x] ArticleCollaboratorService / Impl（addCollaborator, removeCollaborator, getCollaborators, getSharedArticles）
- [x] ArticleCollaboratorController（4 个端点 + GET /api/articles/shared）
- [x] ArticleServiceImpl.checkArticlePermission 允许 EDIT 协作者修改
- [x] ArticleServiceImpl.checkArticleAccessPermission 允许协作者查看私密文章
- [x] ArticleServiceImpl.addPermissionFilter 包含协作者文章

### 18. 新功能 — 文章导入
- [x] ArticleImportService / Impl（Markdown 文件上传 + URL HTML 抓取）
- [x] ArticleImportController（POST /api/articles/import/file + /url）
- [x] URL 导入使用 Jsoup 抓取网页标题和内容
- [x] application.properties 上传限制 5MB → 10MB