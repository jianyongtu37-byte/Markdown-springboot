package com.nineone.markdown.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nineone.common.context.UserContextHolder;
import com.nineone.common.result.Result;
import com.nineone.markdown.client.AiServiceClient;
import com.nineone.markdown.entity.Article;
import com.nineone.markdown.entity.KnowledgeEdge;
import com.nineone.markdown.entity.KnowledgeGraphGeneration;
import com.nineone.markdown.entity.KnowledgeNode;
import com.nineone.markdown.exception.BizException;
import com.nineone.markdown.mapper.*;
import com.nineone.markdown.service.ArticleService;
import com.nineone.markdown.service.KnowledgeGraphService;
import com.nineone.markdown.vo.GlobalKnowledgeGraphVO;
import com.nineone.markdown.vo.KnowledgeGraphVO;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识图谱服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeGraphServiceImpl implements KnowledgeGraphService {

    private final KnowledgeNodeMapper nodeMapper;
    private final KnowledgeEdgeMapper edgeMapper;
    private final KnowledgeGraphGenerationMapper generationMapper;
    private final ArticleService articleService;
    private final AiServiceClient aiServiceClient;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Parser FLEXMARK_PARSER = Parser.builder().build();
    private static final HtmlRenderer FLEXMARK_HTML_RENDERER = HtmlRenderer.builder().build();
    private static final int MAX_CONTENT_LENGTH = 8000;

    @Override
    @Transactional
    public KnowledgeGraphVO generateGraph(Long articleId) {
        // 1. 检查是否已有成功的图谱
        KnowledgeGraphGeneration existing = generationMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeGraphGeneration>()
                        .eq(KnowledgeGraphGeneration::getArticleId, articleId)
                        .eq(KnowledgeGraphGeneration::getStatus, 2)
        );
        if (existing != null) {
            return getGraphByArticleId(articleId);
        }

        // 2. 获取文章并校验归属权限（只有作者本人才能生成）
        Article article = articleService.getById(articleId);
        if (article == null) {
            throw new BizException("文章不存在");
        }
        Long currentUserId = UserContextHolder.getUserId();
        if (currentUserId == null || !article.getUserId().equals(currentUserId)) {
            throw new BizException("无权操作他人文章的知识图谱");
        }

        // 3. 记录生成状态为 generating
        saveGenerationStatus(articleId, 1, null);

        try {
            // 4. 提取纯文本（去除 Markdown 语法）
            String plainText = extractPlainText(article.getContent());

            // 5. 截断过长文本
            if (plainText.length() > MAX_CONTENT_LENGTH) {
                plainText = plainText.substring(0, MAX_CONTENT_LENGTH);
            }

            // 6. 调用 AI 服务抽取知识图谱
            Map<String, String> request = Map.of("content", plainText);
            Result<String> result = aiServiceClient.extractKnowledgeGraph(request);

            if (result == null || result.getCode() != 200 || result.getData() == null) {
                throw new BizException("AI 抽取失败");
            }

            // 7. 解析 JSON 结果
            Map<String, Object> graphResult = parseResult(result.getData());

            // 8. 保存节点和边
            int nodeCount = saveNodes(articleId, graphResult);
            int edgeCount = saveEdges(articleId, graphResult);

            // 9. 更新生成状态为 success
            saveGenerationStatus(articleId, 2, null);

            // 10. 更新生成状态中的节点和边数量
            updateGenerationCounts(articleId, nodeCount, edgeCount);

            // 11. 返回 VO
            return getGraphByArticleId(articleId);

        } catch (Exception e) {
            log.error("知识图谱生成失败, articleId={}", articleId, e);
            saveGenerationStatus(articleId, 3, e.getMessage());
            throw new BizException("知识图谱生成失败: " + e.getMessage());
        }
    }

    @Override
    public KnowledgeGraphVO getGraphByArticleId(Long articleId) {
        // 查询生成状态
        KnowledgeGraphGeneration generation = generationMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeGraphGeneration>()
                        .eq(KnowledgeGraphGeneration::getArticleId, articleId)
        );

        if (generation == null) {
            return null;
        }

        // 查询文章标题
        Article article = articleService.getById(articleId);
        String articleTitle = article != null ? article.getTitle() : "";

        // 查询节点
        List<KnowledgeNode> nodes = nodeMapper.selectList(
                new LambdaQueryWrapper<KnowledgeNode>()
                        .eq(KnowledgeNode::getArticleId, articleId)
        );

        // 查询边
        List<KnowledgeEdge> edges = edgeMapper.selectList(
                new LambdaQueryWrapper<KnowledgeEdge>()
                        .eq(KnowledgeEdge::getArticleId, articleId)
        );

        // 构建节点名称映射
        Map<Long, String> nodeNameMap = nodes.stream()
                .collect(Collectors.toMap(KnowledgeNode::getId, KnowledgeNode::getName));

        // 转换为 VO
        List<KnowledgeGraphVO.NodeVO> nodeVOs = nodes.stream()
                .map(n -> KnowledgeGraphVO.NodeVO.builder()
                        .id(n.getId())
                        .name(n.getName())
                        .type(n.getType())
                        .description(n.getDescription())
                        .build())
                .collect(Collectors.toList());

        List<KnowledgeGraphVO.EdgeVO> edgeVOs = edges.stream()
                .map(e -> KnowledgeGraphVO.EdgeVO.builder()
                        .id(e.getId())
                        .sourceNodeId(e.getSourceNodeId())
                        .targetNodeId(e.getTargetNodeId())
                        .sourceName(nodeNameMap.getOrDefault(e.getSourceNodeId(), ""))
                        .targetName(nodeNameMap.getOrDefault(e.getTargetNodeId(), ""))
                        .relation(e.getRelation())
                        .weight(e.getWeight())
                        .description(e.getDescription())
                        .build())
                .collect(Collectors.toList());

        return KnowledgeGraphVO.builder()
                .articleId(articleId)
                .articleTitle(articleTitle)
                .status(generation.getStatus())
                .nodeCount(generation.getNodeCount())
                .edgeCount(generation.getEdgeCount())
                .nodes(nodeVOs)
                .edges(edgeVOs)
                .build();
    }

    @Override
    public GlobalKnowledgeGraphVO getGlobalGraph() {
        // 查询所有成功的图谱
        List<KnowledgeGraphGeneration> generations = generationMapper.selectList(
                new LambdaQueryWrapper<KnowledgeGraphGeneration>()
                        .eq(KnowledgeGraphGeneration::getStatus, 2)
        );

        if (generations.isEmpty()) {
            return GlobalKnowledgeGraphVO.builder()
                    .totalNodes(0)
                    .totalEdges(0)
                    .totalArticles(0)
                    .nodes(Collections.emptyList())
                    .edges(Collections.emptyList())
                    .build();
        }

        List<Long> articleIds = generations.stream()
                .map(KnowledgeGraphGeneration::getArticleId)
                .collect(Collectors.toList());

        // 查询所有节点
        List<KnowledgeNode> allNodes = nodeMapper.selectList(
                new LambdaQueryWrapper<KnowledgeNode>()
                        .in(KnowledgeNode::getArticleId, articleIds)
        );

        // 查询所有边
        List<KnowledgeEdge> allEdges = edgeMapper.selectList(
                new LambdaQueryWrapper<KnowledgeEdge>()
                        .in(KnowledgeEdge::getArticleId, articleIds)
        );

        // 去重节点（同名实体合并）
        Map<String, KnowledgeNode> uniqueNodes = new LinkedHashMap<>();
        for (KnowledgeNode node : allNodes) {
            uniqueNodes.putIfAbsent(node.getName(), node);
        }

        // 构建节点名称到ID的映射
        Map<String, Long> nodeNameToId = new HashMap<>();
        List<KnowledgeGraphVO.NodeVO> nodeVOs = new ArrayList<>();
        long virtualId = 1;
        for (KnowledgeNode node : uniqueNodes.values()) {
            nodeNameToId.put(node.getName(), virtualId);
            nodeVOs.add(KnowledgeGraphVO.NodeVO.builder()
                    .id(virtualId++)
                    .name(node.getName())
                    .type(node.getType())
                    .description(node.getDescription())
                    .build());
        }

        // 转换边，使用去重后的节点ID
        Set<String> edgeKeys = new HashSet<>();
        List<KnowledgeGraphVO.EdgeVO> edgeVOs = new ArrayList<>();
        Map<Long, String> allNodeNameMap = allNodes.stream()
                .collect(Collectors.toMap(KnowledgeNode::getId, KnowledgeNode::getName));

        for (KnowledgeEdge edge : allEdges) {
            String sourceName = allNodeNameMap.getOrDefault(edge.getSourceNodeId(), "");
            String targetName = allNodeNameMap.getOrDefault(edge.getTargetNodeId(), "");
            Long newSourceId = nodeNameToId.get(sourceName);
            Long newTargetId = nodeNameToId.get(targetName);

            if (newSourceId == null || newTargetId == null) continue;

            String edgeKey = sourceName + "->" + targetName + ":" + edge.getRelation();
            if (edgeKeys.contains(edgeKey)) continue;
            edgeKeys.add(edgeKey);

            edgeVOs.add(KnowledgeGraphVO.EdgeVO.builder()
                    .id(edge.getId())
                    .sourceNodeId(newSourceId)
                    .targetNodeId(newTargetId)
                    .sourceName(sourceName)
                    .targetName(targetName)
                    .relation(edge.getRelation())
                    .weight(edge.getWeight())
                    .description(edge.getDescription())
                    .build());
        }

        return GlobalKnowledgeGraphVO.builder()
                .totalNodes(nodeVOs.size())
                .totalEdges(edgeVOs.size())
                .totalArticles(articleIds.size())
                .nodes(nodeVOs)
                .edges(edgeVOs)
                .build();
    }

    @Override
    @Transactional
    public void deleteGraph(Long articleId) {
        // 校验归属权限（只有作者本人才能删除）
        Article article = articleService.getById(articleId);
        if (article == null) {
            throw new BizException("文章不存在");
        }
        Long currentUserId = UserContextHolder.getUserId();
        if (currentUserId == null || !article.getUserId().equals(currentUserId)) {
            throw new BizException("无权操作他人文章的知识图谱");
        }

        nodeMapper.delete(
                new LambdaQueryWrapper<KnowledgeNode>()
                        .eq(KnowledgeNode::getArticleId, articleId)
        );
        edgeMapper.delete(
                new LambdaQueryWrapper<KnowledgeEdge>()
                        .eq(KnowledgeEdge::getArticleId, articleId)
        );
        generationMapper.delete(
                new LambdaQueryWrapper<KnowledgeGraphGeneration>()
                        .eq(KnowledgeGraphGeneration::getArticleId, articleId)
        );
    }

    @Override
    @Transactional
    public KnowledgeGraphVO regenerateGraph(Long articleId) {
        deleteGraph(articleId);
        return generateGraph(articleId);
    }

    // ==================== 私有方法 ====================

    /**
     * 使用 flexmark-java 将 Markdown 转为纯文本
     */
    private String extractPlainText(String markdownContent) {
        if (markdownContent == null || markdownContent.isEmpty()) {
            return "";
        }
        try {
            // 解析为 AST，然后渲染为 HTML，再去除 HTML 标签
            com.vladsch.flexmark.util.ast.Node document = FLEXMARK_PARSER.parse(markdownContent);
            String html = FLEXMARK_HTML_RENDERER.render(document);
            // 去除 HTML 标签，保留文本内容
            String plainText = html.replaceAll("<[^>]+>", "");
            // 去除多余空白
            plainText = plainText.replaceAll("&nbsp;", " ")
                    .replaceAll("&lt;", "<")
                    .replaceAll("&gt;", ">")
                    .replaceAll("&amp;", "&")
                    .replaceAll("&quot;", "\"")
                    .replaceAll("&#39;", "'");
            // 合并多个空行为单个换行
            plainText = plainText.replaceAll("\\n{3,}", "\n\n");
            return plainText.trim();
        } catch (Exception e) {
            log.warn("Markdown 解析失败，使用原始文本", e);
            return markdownContent;
        }
    }

    /**
     * 解析 AI 返回的 JSON 结果
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseResult(String jsonStr) {
        try {
            return OBJECT_MAPPER.readValue(jsonStr, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("解析知识图谱 JSON 失败: {}", jsonStr, e);
            throw new BizException("AI 返回的数据格式错误");
        }
    }

    /**
     * 保存节点，返回保存的节点数量
     */
    @SuppressWarnings("unchecked")
    private int saveNodes(Long articleId, Map<String, Object> graphResult) {
        // 先删除旧节点
        nodeMapper.delete(
                new LambdaQueryWrapper<KnowledgeNode>()
                        .eq(KnowledgeNode::getArticleId, articleId)
        );

        List<Map<String, Object>> nodesData = (List<Map<String, Object>>) graphResult.get("nodes");
        if (nodesData == null || nodesData.isEmpty()) {
            return 0;
        }

        List<KnowledgeNode> nodes = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();

        for (Map<String, Object> nodeData : nodesData) {
            String name = (String) nodeData.get("name");
            if (name == null || name.trim().isEmpty() || seenNames.contains(name)) {
                continue;
            }
            seenNames.add(name);

            KnowledgeNode node = KnowledgeNode.builder()
                    .articleId(articleId)
                    .name(name)
                    .type(getOrDefault(nodeData, "type", "concept"))
                    .description(getOrDefault(nodeData, "description", ""))
                    .build();
            nodes.add(node);
        }

        // 批量插入
        for (KnowledgeNode node : nodes) {
            nodeMapper.insert(node);
        }

        return nodes.size();
    }

    /**
     * 保存边，返回保存的边数量
     */
    @SuppressWarnings("unchecked")
    private int saveEdges(Long articleId, Map<String, Object> graphResult) {
        // 先删除旧边
        edgeMapper.delete(
                new LambdaQueryWrapper<KnowledgeEdge>()
                        .eq(KnowledgeEdge::getArticleId, articleId)
        );

        List<Map<String, String>> edgesData = (List<Map<String, String>>) graphResult.get("edges");
        if (edgesData == null || edgesData.isEmpty()) {
            return 0;
        }

        // 查询当前文章的所有节点，构建名称到ID的映射
        List<KnowledgeNode> nodes = nodeMapper.selectList(
                new LambdaQueryWrapper<KnowledgeNode>()
                        .eq(KnowledgeNode::getArticleId, articleId)
        );
        Map<String, Long> nodeNameToId = nodes.stream()
                .collect(Collectors.toMap(KnowledgeNode::getName, KnowledgeNode::getId));

        List<KnowledgeEdge> edges = new ArrayList<>();
        Set<String> seenEdges = new HashSet<>();

        for (Map<String, String> edgeData : edgesData) {
            String source = edgeData.get("source");
            String target = edgeData.get("target");
            String relation = edgeData.get("relation");

            if (source == null || target == null || relation == null) continue;

            Long sourceId = nodeNameToId.get(source);
            Long targetId = nodeNameToId.get(target);
            if (sourceId == null || targetId == null) continue;

            String edgeKey = articleId + ":" + sourceId + ":" + targetId + ":" + relation;
            if (seenEdges.contains(edgeKey)) continue;
            seenEdges.add(edgeKey);

            KnowledgeEdge edge = KnowledgeEdge.builder()
                    .articleId(articleId)
                    .sourceNodeId(sourceId)
                    .targetNodeId(targetId)
                    .relation(relation)
                    .weight(1.0)
                    .description(edgeData.getOrDefault("description", ""))
                    .build();
            edges.add(edge);
        }

        // 批量插入
        for (KnowledgeEdge edge : edges) {
            edgeMapper.insert(edge);
        }

        return edges.size();
    }

    /**
     * 保存或更新生成状态
     */
    private void saveGenerationStatus(Long articleId, Integer status, String errorMessage) {
        KnowledgeGraphGeneration existing = generationMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeGraphGeneration>()
                        .eq(KnowledgeGraphGeneration::getArticleId, articleId)
        );

        if (existing != null) {
            existing.setStatus(status);
            existing.setErrorMessage(errorMessage);
            if (status == 2) {
                existing.setGenerateTime(LocalDateTime.now());
            }
            generationMapper.updateById(existing);
        } else {
            KnowledgeGraphGeneration generation = KnowledgeGraphGeneration.builder()
                    .articleId(articleId)
                    .status(status)
                    .nodeCount(0)
                    .edgeCount(0)
                    .errorMessage(errorMessage)
                    .generateTime(status == 2 ? LocalDateTime.now() : null)
                    .build();
            generationMapper.insert(generation);
        }
    }

    /**
     * 更新生成状态中的节点和边数量
     */
    private void updateGenerationCounts(Long articleId, int nodeCount, int edgeCount) {
        KnowledgeGraphGeneration generation = generationMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeGraphGeneration>()
                        .eq(KnowledgeGraphGeneration::getArticleId, articleId)
        );
        if (generation != null) {
            generation.setNodeCount(nodeCount);
            generation.setEdgeCount(edgeCount);
            generationMapper.updateById(generation);
        }
    }

    /**
     * 安全地从 Map 中获取值，带默认值
     */
    private String getOrDefault(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }
}
