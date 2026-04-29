# DeepSeek AI 控制器 API 使用示例

## API 基础信息
- 基础路径：`/api/deepseek`
- 请求头：`Content-Type: application/json`
- 认证：部分接口可能需要 JWT 认证（如果集成到安全配置中）

## API 接口列表

### 1. 获取 DeepSeek API 状态
**GET** `/api/deepseek/status`

**响应示例：**
```json
{
  "code": 200,
  "message": "DeepSeek API 状态查询成功",
  "data": {
    "serviceName": "DeepSeek AI Summary Service",
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

### 2. 手动生成文章摘要
**POST** `/api/deepseek/generate-summary`

**请求示例：**
```json
{
  "content": "这是一篇关于人工智能发展的文章。人工智能正在改变世界，机器学习、深度学习等技术正在推动各个领域的创新..."
}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "AI 摘要生成成功",
  "data": {
    "originalLength": 58,
    "summaryLength": 150,
    "summary": "本文讨论了人工智能的发展趋势，重点介绍了机器学习和深度学习技术的应用，以及它们如何推动各行业的创新。",
    "serviceUsed": "DeepSeek AI Summary Service",
    "success": true
  }
}
```

### 3. 测试 API 连接
**POST** `/api/deepseek/test-connection`

**响应示例：**
```json
{
  "code": 200,
  "message": "DeepSeek API 连接正常",
  "data": {
    "connected": true,
    "serviceName": "DeepSeek AI Summary Service",
    "testContent": "这是一个测试内容，用于验证 DeepSeek API 连接状态。",
    "testSummary": "这是一段测试内容的摘要，证明 API 连接正常。",
    "responseTime": "正常"
  }
}
```

### 4. 获取配置信息
**GET** `/api/deepseek/config`

**响应示例：**
```json
{
  "code": 200,
  "message": "DeepSeek 配置信息查询成功",
  "data": {
    "apiUrl": "https://api.deepseek.com/v1",
    "model": "deepseek-chat",
    "timeoutSeconds": 30,
    "maxTokens": 500,
    "serviceName": "DeepSeek AI Summary Service",
    "apiKeyConfigured": true,
    "apiKeyStatus": "已配置",
    "serviceType": "真实 API 服务"
  }
}
```

### 5. AI 聊天接口
**POST** `/api/deepseek/chat`

**请求示例：**
```json
{
  "message": "请介绍一下人工智能的主要应用领域"
}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "聊天响应生成成功",
  "data": {
    "request": "请介绍一下人工智能的主要应用领域",
    "response": "人工智能的主要应用领域包括：1. 自动驾驶技术，2. 智能语音助手，3. 医疗影像诊断，4. 金融风控，5. 智能推荐系统，6. 工业自动化等。",
    "serviceUsed": "DeepSeek AI Summary Service",
    "timestamp": 1743345600000
  }
}
```

### 6. 文本润色接口（增强版）
**POST** `/api/deepseek/polish`

**功能说明：**
全方位文本润色，涵盖四个维度：
1. **基础除错**：纠正语病、错别字、规范标点
2. **表达升级**：消除冗余、丰富词汇、优化句式
3. **风格与语气适配**：根据参数调整风格和语气
4. **逻辑与连贯性增强**：增加过渡衔接，增强逻辑

**请求参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `content` | string | 是 | 需要润色的文本内容 |
| `style` | string | 否 | 润色风格（可选值：`academic`, `formal`, `business`, `casual`, `creative`, `technical`, `marketing`） |
| `tone` | string | 否 | 语气风格（可选值：`friendly`, `professional`, `persuasive`, `neutral`, `enthusiastic`, `authoritative`） |

**请求示例1：学术风格润色**
```json
{
  "content": "这是一段关于人工智能的论文摘要，需要润色使其更加学术化。",
  "style": "academic",
  "tone": "professional"
}
```

**请求示例2：商务风格润色**
```json
{
  "content": "我们的产品具有很多优点，比如性能好、价格便宜。",
  "style": "business",
  "tone": "persuasive"
}
```

**请求示例3：友好风格润色**
```json
{
  "content": "大家好，今天我想分享一些关于健康饮食的小贴士。",
  "style": "casual",
  "tone": "friendly"
}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "文本润色成功",
  "data": {
    "originalLength": 30,
    "polishedLength": 45,
    "original": "这是一段需要润色的文本。它的表达可能不够流畅，需要改进。",
    "polished": "这是一段需要润色优化的文本内容，其表达方式可能不够流畅自然，有待进一步改进和完善。",
    "serviceUsed": "DeepSeek AI Summary Service",
    "success": true,
    "style": "academic",
    "tone": "professional"
  }
}
```

**支持的风格选项：**
- `academic`：学术/正式风格 - 严谨的书面用语，适合论文、公文
- `formal`：正式/官方风格 - 规范严谨，适合正式文档
- `business`：商务/专业风格 - 强调效率和结果，适合邮件、汇报
- `casual`：友好/活泼风格 - 增加亲和力，适合博客、社交媒体
- `creative`：创意/文学风格 - 富有文采和想象力，适合文学创作
- `technical`：技术/专业风格 - 精确专业，适合技术文档
- `marketing`：营销/推广风格 - 富有吸引力，适合产品介绍

**支持的语气选项：**
- `friendly`：友好、亲切 - 像朋友间交流一样自然
- `professional`：专业、客观 - 保持专业距离，客观中立
- `persuasive`：有说服力、打动人心 - 用于说服读者采取行动
- `neutral`：中性、平衡 - 不带明显情感色彩
- `enthusiastic`：热情、积极 - 充满活力和正面能量
- `authoritative`：权威、自信 - 展示专业权威性

**注意：** 如果不提供`style`和`tone`参数，系统将根据内容智能判断最适合的风格和语气。

## 使用 cURL 测试示例

### 测试 API 状态
```bash
curl -X GET "http://localhost:8080/api/deepseek/status"
```

### 生成文章摘要
```bash
curl -X POST "http://localhost:8080/api/deepseek/generate-summary" \
  -H "Content-Type: application/json" \
  -d '{"content": "你的文章内容在这里..."}'
```

### 测试 API 连接
```bash
curl -X POST "http://localhost:8080/api/deepseek/test-connection"
```

## 配置要求

### 必需的环境变量
在 `.env` 文件中配置：
```properties
# DeepSeek API 配置
DEEPSEEK_API_KEY=your_deepseek_api_key_here
DEEPSEEK_API_URL=https://api.deepseek.com/v1
DEEPSEEK_MODEL=deepseek-chat
DEEPSEEK_TIMEOUT_SECONDS=30
DEEPSEEK_MAX_TOKENS=500
```

### 可选配置
如果没有配置 API Key，系统会自动使用模拟摘要服务作为备选方案。

## 错误处理

### 常见错误响应

1. **API Key 未配置**
```json
{
  "code": 500,
  "message": "AI 摘要生成失败: DeepSeek API错误: No API key provided",
  "data": {
    "success": false,
    "error": "DeepSeek API错误: No API key provided",
    "serviceUsed": "DeepSeek AI Summary Service"
  }
}
```

2. **网络连接问题**
```json
{
  "code": 500,
  "message": "DeepSeek API 连接测试失败: Connection timeout",
  "data": {
    "connected": false,
    "serviceName": "DeepSeek AI Summary Service",
    "error": "Connection timeout",
    "suggestion": "请检查 API Key 配置、网络连接或 API 服务状态"
  }
}
```

3. **内容为空**
```json
{
  "code": 400,
  "message": "文章内容不能为空"
}
```

## 集成到现有功能

### 自动摘要生成
当创建文章时，如果 `aiStatus` 为未生成（0），系统会自动调用 DeepSeek AI 服务生成摘要。

### 手动触发摘要生成
可以通过文章控制器的接口手动触发：
```http
POST /api/articles/{id}/ai-status?aiStatus=1
```

## 监控和日志

控制器会记录以下日志：
- API 调用开始和结束时间
- 生成摘要的内容长度和摘要长度
- 错误和异常信息
- 连接测试结果

查看日志：
```bash
# 查看 DeepSeek 相关日志
grep "DeepSeek" logs/application.log