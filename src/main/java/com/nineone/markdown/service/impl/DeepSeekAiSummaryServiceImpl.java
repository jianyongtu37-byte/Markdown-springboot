package com.nineone.markdown.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.nineone.markdown.service.AiSummaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * DeepSeek AI摘要生成服务实现
 * 使用DeepSeek API进行真正的AI摘要生成
 */
@Service
@Primary
@Slf4j
public class DeepSeekAiSummaryServiceImpl implements AiSummaryService {

    @Value("${ai.deepseek.api-key:}")
    private String apiKey;

    @Value("${ai.deepseek.api-url:https://api.deepseek.com/v1}")
    private String apiUrl;

    @Value("${ai.deepseek.model:deepseek-chat}")
    private String model;

    @Value("${ai.deepseek.timeout-seconds:60}") // 润色长文本可能需要更长时间，建议调高超时时间至60秒或以上
    private int timeoutSeconds;

    @Value("${ai.deepseek.max-tokens:500}")
    private int maxTokens;

    // --- 新增：针对润色功能的独立输入/输出上限配置 ---
    @Value("${ai.deepseek.polish-max-tokens:4000}")
    private int polishMaxTokens;

    @Value("${ai.deepseek.polish-max-input-length:8000}")
    private int polishMaxInputLength;
    // ------------------------------------------------

    private WebClient webClient;

    private WebClient getWebClient() {
        if (webClient == null) {
            webClient = WebClient.builder()
                    .baseUrl(apiUrl + "/chat/completions")
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();
        }
        return webClient;
    }

    @Override
    public String generateSummary(String content) {
        if (StrUtil.isBlank(content)) {
            log.warn("文章内容为空，无法生成摘要");
            return "这篇文章暂无摘要内容。";
        }

        if (StrUtil.isBlank(apiKey)) {
            log.error("DeepSeek API密钥未配置，无法生成摘要");
            throw new RuntimeException("AI服务暂不可用，请检查API密钥配置后重试");
        }

        try {
            log.info("调用DeepSeek API生成文章摘要，内容长度: {}", content.length());

            // 构造提示词
            String prompt = buildPrompt(content);

            // 构造请求体
            String requestBody = buildRequestBody(prompt, maxTokens);

            // 调用API
            String response = getWebClient().post()
                    .uri("")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            // 解析响应
            String summary = parseResponse(response);
            log.info("DeepSeek摘要生成成功，摘要长度: {}", summary.length());

            return summary;

        } catch (Exception e) {
            log.error("DeepSeek API调用失败: {}", e.getMessage());
            
            // 检查是否是超时异常
            if (e instanceof java.util.concurrent.TimeoutException || 
                e.getCause() instanceof java.util.concurrent.TimeoutException ||
                e.getMessage().contains("Timeout") || 
                e.getMessage().contains("超时")) {
                throw new RuntimeException("AI服务繁忙，请5分钟后再次尝试");
            }
            
            // 其他异常
            throw new RuntimeException("AI摘要生成服务暂时不可用，请稍后重试");
        }
    }

    @Override
    public String generateTitle(String content) {
        if (StrUtil.isBlank(content)) {
            log.warn("文章内容为空，无法生成标题");
            return "未命名文章";
        }

        if (StrUtil.isBlank(apiKey)) {
            log.error("DeepSeek API密钥未配置，无法生成标题");
            throw new RuntimeException("AI服务暂不可用，请检查API密钥配置后重试");
        }

        try {
            log.info("调用DeepSeek API生成文章标题，内容长度: {}", content.length());

            // 构造标题生成提示词
            String prompt = buildTitlePrompt(content);

            // 构造请求体 (标题所需 token 较少，直接硬编码或传入较小值)
            String requestBody = buildRequestBody(prompt, 50);

            // 调用API
            String response = getWebClient().post()
                    .uri("")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            // 解析响应
            String title = parseTitleResponse(response);
            log.info("DeepSeek标题生成成功，标题: {}", title);

            return title;

        } catch (Exception e) {
            log.error("DeepSeek API调用失败: {}", e.getMessage());
            
            // 检查是否是超时异常
            if (e instanceof java.util.concurrent.TimeoutException || 
                e.getCause() instanceof java.util.concurrent.TimeoutException ||
                e.getMessage().contains("Timeout") || 
                e.getMessage().contains("超时")) {
                throw new RuntimeException("AI服务繁忙，请5分钟后再次尝试");
            }
            
            // 其他异常
            throw new RuntimeException("AI标题生成服务暂时不可用，请稍后重试");
        }
    }

    @Override
    public String polishText(String content, String style, String tone) {
        if (StrUtil.isBlank(content)) {
            log.warn("文本内容为空，无法润色");
            return content;
        }

        // 限制输入长度，避免超出 API 限制或导致巨额费用
        String contentToPolish = content;
        if (content.length() > polishMaxInputLength) {
            log.warn("润色文本过长 ({}字)，截取前 {} 个字符", content.length(), polishMaxInputLength);
            contentToPolish = content.substring(0, polishMaxInputLength) + "\n\n...（因超出字数限制，后续内容已截断）";
        }

        if (StrUtil.isBlank(apiKey)) {
            log.error("DeepSeek API密钥未配置，无法进行文本润色");
            throw new RuntimeException("AI服务暂不可用，请检查API密钥配置");
        }

        try {
            log.info("调用DeepSeek API进行文本润色，截取后内容长度: {}, 风格: {}, 语气: {}",
                    contentToPolish.length(), style, tone);

            // 构造润色提示词
            String prompt = buildPolishPrompt(contentToPolish, style, tone);

            // 构造请求体 (使用提高了的 polishMaxTokens)
            String requestBody = buildRequestBody(prompt, polishMaxTokens);

            // 调用API
            String response = getWebClient().post()
                    .uri("")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            // 解析响应
            String polishedText = parsePolishResponse(response);
            log.info("DeepSeek文本润色成功，润色后长度: {}", polishedText.length());

            return polishedText;

        } catch (Exception e) {
            log.error("DeepSeek API调用失败: {}", e.getMessage());
            
            // 检查是否是超时异常
            if (e instanceof java.util.concurrent.TimeoutException || 
                e.getCause() instanceof java.util.concurrent.TimeoutException ||
                e.getMessage().contains("Timeout") || 
                e.getMessage().contains("超时")) {
                throw new RuntimeException("AI服务繁忙，请5分钟后再次尝试");
            }
            
            // 其他异常
            throw new RuntimeException("AI润色服务暂时不可用，请稍后重试");
        }
    }

    @Override
    public String getServiceName() {
        return "DeepSeek AI Summary Service";
    }

    @Async("aiTaskExecutor")
    public CompletableFuture<String> generateSummaryAsync(String content) {
        String summary = generateSummary(content);
        return CompletableFuture.completedFuture(summary);
    }

    /**
     * 构造提示词
     */
    private String buildPrompt(String content) {
        return String.format(
                "请为以下文章生成一个简洁、准确的摘要，摘要长度不超过%d个字符。要求：\n" +
                        "1. 提取文章的核心观点和关键信息\n" +
                        "2. 保持客观中立，不添加个人观点\n" +
                        "3. 语言流畅，逻辑清晰\n" +
                        "4. 如果原文是技术文章，请突出技术要点\n\n" +
                        "文章内容：\n%s",
                maxTokens, content
        );
    }

    /**
     * 通用的构造请求体方法（支持动态传入 max_tokens）
     */
    private String buildRequestBody(String prompt, int tokensLimit) {
        JSONObject requestBody = JSONUtil.createObj();
        requestBody.set("model", model);
        requestBody.set("max_tokens", tokensLimit);
        requestBody.set("temperature", 0.7);

        JSONObject message = JSONUtil.createObj();
        message.set("role", "user");
        message.set("content", prompt);

        requestBody.set("messages", JSONUtil.createArray().put(message));

        return requestBody.toString();
    }

    /**
     * 解析响应
     */
    private String parseResponse(String response) {
        try {
            JSONObject jsonResponse = JSONUtil.parseObj(response);

            if (jsonResponse.containsKey("error")) {
                log.error("DeepSeek API返回错误: {}", jsonResponse.getStr("error"));
                throw new RuntimeException("DeepSeek API错误: " + jsonResponse.getStr("error"));
            }

            String content = jsonResponse.getByPath("choices[0].message.content", String.class);
            if (StrUtil.isNotBlank(content)) {
                return content.trim()
                        .replaceAll("^【摘要】|^摘要：|^摘要：", "")
                        .replaceAll("^\\s*", "")
                        .replaceAll("\\s*$", "");
            }

            throw new RuntimeException("无法从DeepSeek响应中提取摘要内容");

        } catch (Exception e) {
            log.error("解析DeepSeek响应失败", e);
            throw new RuntimeException("解析DeepSeek响应失败", e);
        }
    }

    /**
     * 构造标题生成提示词
     */
    private String buildTitlePrompt(String content) {
        String contentForTitle = content;
        if (content.length() > 1000) {
            contentForTitle = content.substring(0, 1000) + "...（内容已截断）";
        }

        return String.format(
                "请为以下文章生成一个简洁、吸引人的标题。要求：\n" +
                        "1. 标题长度不超过20个汉字或40个英文字符\n" +
                        "2. 准确反映文章的核心内容\n" +
                        "3. 语言简洁有力，有吸引力\n" +
                        "4. 如果是技术文章，请突出技术关键词\n" +
                        "5. 直接返回标题，不要添加任何解释、引号或其他标记\n\n" +
                        "文章内容：\n%s",
                contentForTitle
        );
    }

    /**
     * 解析标题响应
     */
    private String parseTitleResponse(String response) {
        try {
            JSONObject jsonResponse = JSONUtil.parseObj(response);

            if (jsonResponse.containsKey("error")) {
                throw new RuntimeException("DeepSeek API错误: " + jsonResponse.getStr("error"));
            }

            String title = jsonResponse.getByPath("choices[0].message.content", String.class);
            if (StrUtil.isNotBlank(title)) {
                title = title.trim()
                        .replaceAll("^[\"']|[\"']$", "")
                        .replaceAll("^标题：|^标题:|^【标题】|^标题\\s*", "")
                        .replaceAll("\\s+", " ")
                        .trim();

                if (title.length() > 40) {
                    title = title.substring(0, 40) + "...";
                }
                return title;
            }

            throw new RuntimeException("无法从DeepSeek响应中提取标题内容");

        } catch (Exception e) {
            log.error("解析DeepSeek标题响应失败", e);
            throw new RuntimeException("解析DeepSeek标题响应失败", e);
        }
    }

    /**
     * 构造润色提示词
     * 增强版：提供更专业的文本润色，涵盖四个维度
     * 1. 基础除错（文字的"质检员"）- 纠正语病、错别字、规范标点
     * 2. 表达升级（文字的"化妆师"）- 消除冗余、丰富词汇、优化句式
     * 3. 风格与语气适配（文字的"调音台"）- 根据style和tone参数调整
     * 4. 逻辑与连贯性增强（文字的"桥梁工"）- 增加过渡衔接，增强逻辑
     */
    private String buildPolishPrompt(String content, String style, String tone) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一位专业的文字编辑，请对以下文本进行全方位润色改进。\n\n");
        prompt.append("### 核心要求：\n");
        prompt.append("1. **绝对忠于原意**：不篡改核心事实、数据、观点\n");
        prompt.append("2. **原意保真**：不擅自添加作者没有表达过的立场\n");
        prompt.append("3. **优化表达**：在保持原意基础上提升文本质量\n\n");
        
        prompt.append("### 润色维度（请全面优化）：\n");
        prompt.append("1. **基础除错**：纠正语病、错别字、主谓宾搭配不当；规范中英文标点，修正标点缺失或误用\n");
        prompt.append("2. **表达升级**：消除冗余啰嗦的表达；将干瘪、重复的词汇替换为更高级、准确的同义词；将晦涩难懂的超长句拆分为清晰的短句，或将松散的短句组合成有逻辑的复合句\n");
        prompt.append("3. **逻辑与连贯性**：在段落或句子之间补充合适的关联词（如'然而'、'因此'、'进一步来说'），让上下文逻辑滑动更平滑\n");
        
        // 处理风格参数
        if (StrUtil.isNotBlank(style)) {
            prompt.append("4. **风格适配**：");
            switch (style.toLowerCase()) {
                case "academic":
                    prompt.append("使用学术/正式风格 - 剔除主观情绪词，使用客观、严谨的书面用语，适合论文、公文\n");
                    break;
                case "formal":
                    prompt.append("使用正式/官方风格 - 规范严谨，适合正式文档、官方通知\n");
                    break;
                case "business":
                    prompt.append("使用商务/专业风格 - 强调效率和结果，语气自信、专业，适合邮件、汇报、商业企划\n");
                    break;
                case "casual":
                    prompt.append("使用友好/活泼风格 - 增加亲和力，拉近与读者的距离，适合博客、社交媒体、社群互动\n");
                    break;
                case "creative":
                    prompt.append("使用创意/文学风格 - 富有文采和想象力，适合文学创作、广告文案\n");
                    break;
                case "technical":
                    prompt.append("使用技术/专业风格 - 精确专业，适合技术文档、API说明、开发指南\n");
                    break;
                case "marketing":
                    prompt.append("使用营销/推广风格 - 富有吸引力，适合产品介绍、营销文案\n");
                    break;
                default:
                    prompt.append("使用").append(style).append("风格\n");
                    break;
            }
        } else {
            prompt.append("4. **风格适配**：根据内容智能判断最适合的风格（自动选择学术、商务或友好风格）\n");
        }
        
        // 处理语气参数
        if (StrUtil.isNotBlank(tone)) {
            prompt.append("5. **语气调整**：");
            switch (tone.toLowerCase()) {
                case "friendly":
                    prompt.append("友好、亲切 - 像朋友间交流一样自然\n");
                    break;
                case "professional":
                    prompt.append("专业、客观 - 保持专业距离，客观中立\n");
                    break;
                case "persuasive":
                    prompt.append("有说服力、打动人心 - 用于说服读者采取行动\n");
                    break;
                case "neutral":
                    prompt.append("中性、平衡 - 不带明显情感色彩\n");
                    break;
                case "enthusiastic":
                    prompt.append("热情、积极 - 充满活力和正面能量\n");
                    break;
                case "authoritative":
                    prompt.append("权威、自信 - 展示专业权威性\n");
                    break;
                default:
                    prompt.append(tone).append("语气\n");
                    break;
            }
        } else {
            prompt.append("5. **语气调整**：根据内容和风格自动匹配最合适的语气\n");
        }
        
        prompt.append("\n### 文本内容：\n");
        prompt.append(content);
        prompt.append("\n\n### 输出要求：\n");
        prompt.append("1. 直接返回润色后的完整文本\n");
        prompt.append("2. 不要添加任何解释、评论或标记（如【润色后】）\n");
        prompt.append("3. 保持原文的段落结构\n");
        prompt.append("4. 如果原文有特殊格式（如列表、标题），请尽量保留\n");

        return prompt.toString();
    }

    /**
     * 解析润色响应
     */
    private String parsePolishResponse(String response) {
        try {
            JSONObject jsonResponse = JSONUtil.parseObj(response);

            if (jsonResponse.containsKey("error")) {
                throw new RuntimeException("DeepSeek API错误: " + jsonResponse.getStr("error"));
            }

            String polishedText = jsonResponse.getByPath("choices[0].message.content", String.class);
            if (StrUtil.isNotBlank(polishedText)) {
                return polishedText.trim()
                        .replaceAll("^【润色后】|^润色后：|^润色结果：", "")
                        .replaceAll("^\\s*", "")
                        .replaceAll("\\s*$", "");
            }

            throw new RuntimeException("无法从DeepSeek响应中提取润色内容");

        } catch (Exception e) {
            log.error("解析DeepSeek润色响应失败", e);
            throw new RuntimeException("解析DeepSeek润色响应失败", e);
        }
    }

}