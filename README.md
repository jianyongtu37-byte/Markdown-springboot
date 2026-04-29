# Markdown 知识库系统

一个基于Spring Boot的现代化Markdown文章管理平台，集成了AI摘要生成、全文搜索、用户认证等功能。

## ✨ 功能特性

- 📝 **文章管理**：支持Markdown格式的文章创建、编辑、删除和发布
- 🏷️ **标签分类**：多级分类和标签系统，方便文章组织
- 🤖 **AI摘要**：集成DeepSeek AI，自动为文章生成智能摘要
- 🔍 **全文搜索**：支持Elasticsearch全文检索
- 🔐 **用户认证**：基于JWT的完整认证授权系统
- 📱 **响应式设计**：支持前后端分离，提供RESTful API
- ⚡ **异步处理**：AI摘要生成等耗时操作异步执行
- 🔄 **实时更新**：WebSocket支持实时通知
- 🛡️ **安全防护**：完善的输入验证和SQL注入防护

## 🚀 快速开始

### 环境要求

- JDK 17+
- MySQL 8.0+
- Redis 6.0+
- Elasticsearch 8.x (可选)
- Maven 3.6+

### 1. 配置环境变量

```bash
# 复制环境变量示例文件
cp .env.example .env

# 编辑 .env 文件，填写实际配置
# 重要：修改数据库密码、JWT密钥和DeepSeek API密钥
```

### 2. 初始化数据库

```sql
-- 执行 schema.sql 创建数据库表结构
mysql -u root -p < schema.sql
```

### 3. 启动应用

```bash
# 使用Maven启动
mvn spring-boot:run

# 或者打包后运行
mvn clean package
java -jar target/Markdown-0.0.1-SNAPSHOT.jar
```

### 4. 访问应用

- 应用地址：http://localhost:8080
- API文档：http://localhost:8080/swagger-ui/index.html

## 📁 项目结构

```
src/
├── main/
│   ├── java/com/nineone/markdown/
│   │   ├── config/          # 配置类
│   │   ├── controller/      # 控制器层
│   │   ├── service/         # 服务层
│   │   ├── entity/          # 实体类
│   │   ├── mapper/          # 数据访问层
│   │   ├── dto/            # 数据传输对象
│   │   ├── vo/             # 视图对象
│   │   └── security/       # 安全配置
│   └── resources/
│       ├── application.properties  # 主配置文件
│       └── schema.sql      # 数据库脚本
```

## 🔧 配置说明

### 核心配置文件

1. **.env** - 环境变量文件（敏感信息）
2. **application.properties** - 应用主配置
3. **SecurityConfig.java** - 安全与跨域配置

### 跨域配置

默认允许的跨域域名：
- http://localhost:3000 (前端开发)
- http://localhost:8080 (后端自身)

如需修改，请编辑 `SecurityConfig.java` 中的 `corsConfigurationSource()` 方法。

## 📚 API 接口

### 认证接口
- `POST /api/auth/register` - 用户注册
- `POST /api/auth/login` - 用户登录
- `POST /api/auth/logout` - 用户登出

### 文章接口（公开访问）
- `GET /api/articles` - 获取文章列表（分页）
- `GET /api/articles/{id}` - 获取文章详情
- `POST /api/articles/{id}/view` - 增加文章阅读量

### 文章接口（需要认证）
- `POST /api/articles` - 创建文章
- `PUT /api/articles/{id}` - 更新文章
- `DELETE /api/articles/{id}` - 删除文章
- `POST /api/articles/{id}/ai-status` - 更新AI摘要状态

## 🐳 Docker 快速部署

### 使用 Docker Compose

```yaml
# docker-compose.yml
version: '3.8'
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: root_password
      MYSQL_DATABASE: markdown_db
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  elasticsearch:
    image: elasticsearch:8.11.0
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
    ports:
      - "9200:9200"

  app:
    build: .
    depends_on:
      - mysql
      - redis
      - elasticsearch
    environment:
      - DB_HOST=mysql
      - REDIS_HOST=redis
      - ES_HOST=elasticsearch
    ports:
      - "8080:8080"

volumes:
  mysql_data:
```

## 🧪 测试

### 运行测试

```bash
# 运行所有测试
mvn test

# 运行特定测试类
mvn test -Dtest=DeepSeekAiSummaryServiceImplTest

# 生成测试覆盖率报告
mvn jacoco:report
```

### 测试覆盖率
- 单元测试：覆盖核心业务逻辑
- 集成测试：验证外部服务集成
- API测试：确保接口正确性

## 🔒 安全说明

### 敏感信息保护
- API密钥、数据库密码等敏感信息通过环境变量管理
- JWT密钥建议定期更换
- 生产环境务必使用强密码

### 输入验证
- 所有用户输入都经过严格验证
- 防止SQL注入、XSS攻击
- 请求频率限制

## 📊 监控与日志

### 日志级别配置
```properties
logging.level.com.nineone.markdown=DEBUG
logging.level.root=INFO
```

### 健康检查
- 应用健康状态：`GET /actuator/health`
- 数据库连接状态：`GET /actuator/health/db`
- Redis连接状态：`GET /actuator/health/redis`

## 🔄 部署指南

### 生产环境部署

1. **环境准备**
   ```bash
   # 设置生产环境变量
   export SPRING_PROFILES_ACTIVE=prod
   export DB_PASSWORD=your_strong_password
   export JWT_SECRET=your_secure_jwt_secret
   ```

2. **数据库优化**
   ```sql
   -- 添加索引优化查询性能
   CREATE INDEX idx_article_status ON article(status);
   CREATE INDEX idx_article_create_time ON article(create_time);
   ```

3. **性能调优**
   ```properties
   # 调整连接池大小
   spring.datasource.hikari.maximum-pool-size=20
   spring.datasource.hikari.minimum-idle=5
   ```

## 🤝 贡献指南

1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 打开 Pull Request

## 📄 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情

## 📞 支持与反馈

- 问题反馈：[GitHub Issues](https://github.com/yourusername/markdown-knowledge-base/issues)
- 功能建议：[GitHub Discussions](https://github.com/yourusername/markdown-knowledge-base/discussions)

## 🙏 致谢

感谢以下开源项目的支持：
- [Spring Boot](https://spring.io/projects/spring-boot)
- [MyBatis-Plus](https://baomidou.com/)
- [DeepSeek AI](https://platform.deepseek.com/)
- [Elasticsearch](https://www.elastic.co/)
- [Redis](https://redis.io/)

---

**注意**：本项目为学习和技术演示用途，生产环境部署前请进行充分的安全评估和性能测试。