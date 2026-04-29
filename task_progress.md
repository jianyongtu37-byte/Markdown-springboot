# 数据库重复查询优化任务进度

## 已完成优化

- [x] 1. 优化 `InteractionServiceImpl` - 消除 `toggleLike`/`toggleFavorite`/`addComment` 中重复的文章查询
- [x] 2. 优化 `InteractionServiceImpl.addComment` - 消除重复的父评论查询（缓存 parentComment 变量）
- [x] 3. 优化 `ArticleServiceImpl.getTagsByArticleId` - 消除 N+1 标签查询（批量查询替代循环查库）
- [x] 4. 优化 `InteractionServiceImpl.getComments` - 批量查询回复消除 N+1（IN 查询替代循环查库）
- [x] 5. 优化 `ArticleServiceImpl.batchUpdateStatus` - 批量查询文章消除循环查库（selectBatchIds 替代逐个查询）
- [x] 6. 优化 `InteractionServiceImpl` - 缓存文章作者信息避免重复查库（UserContextHolder 优先）
- [x] 7. 优化 `ArticleServiceImpl.getArticleDetail` - 确保一级缓存生效（已有 @Transactional(readOnly = true)）
- [x] 8. 优化 `ArticleServiceImpl.checkArticlePermission` - 减少不必要的数据库查询（已优化 batchUpdateStatus）

## 🔥 新增：根治"重复查询"的终极方案

### 问题根源分析

经过深度代码审查，发现以下核心问题：

1. **`JwtAuthenticationFilter` 第 85 行**：`userMapper.selectById(userId)` — Filter 层查了一次用户，不在事务范围内
2. **`ArticleServiceImpl.getArticleDetail()`** 中查作者信息时，如果当前用户不是作者，会查库
3. **`/api/articles/**` 被配置为 `permitAll()`**，匿名用户也能访问，此时 UserContextHolder 为空
4. **前端可能同时调用 `GET /api/articles/{id}` 和 `GET /api/articles/{id}/detail`**，产生两次独立请求

### 已实施的根治方案

- [x] 9. 在 `UserMapper` 中添加调用栈打印的"抓鬼"方法（注释状态，需要时取消注释即可使用）
- [x] 10. 优化 `ArticleServiceImpl.getArticleDetail()` — 优先从 UserContextHolder 获取作者信息
- [x] 11. 优化 `InteractionServiceImpl.getCurrentUserNickname()` — 添加 null 安全检查和详细注释
- [x] 12. 优化 `InteractionServiceImpl.getCurrentUser()` — 添加 null 安全检查和详细注释
