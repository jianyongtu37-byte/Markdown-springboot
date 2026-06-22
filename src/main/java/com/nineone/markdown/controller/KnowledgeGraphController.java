package com.nineone.markdown.controller;

import com.nineone.common.result.Result;
import com.nineone.markdown.service.KnowledgeGraphService;
import com.nineone.markdown.vo.GlobalKnowledgeGraphVO;
import com.nineone.markdown.vo.KnowledgeGraphVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 知识图谱控制器
 */
@RestController
@RequestMapping("/api/knowledge-graph")
@RequiredArgsConstructor
public class KnowledgeGraphController {

    private final KnowledgeGraphService knowledgeGraphService;

    /**
     * 为文章生成知识图谱
     * POST /api/knowledge-graph/generate/{articleId}
     */
    @PostMapping("/generate/{articleId}")
    public Result<KnowledgeGraphVO> generateGraph(@PathVariable Long articleId) {
        KnowledgeGraphVO graph = knowledgeGraphService.generateGraph(articleId);
        return Result.success("知识图谱生成成功", graph);
    }

    /**
     * 获取文章的知识图谱
     * GET /api/knowledge-graph/{articleId}
     */
    @GetMapping("/{articleId}")
    public Result<KnowledgeGraphVO> getGraph(@PathVariable Long articleId) {
        KnowledgeGraphVO graph = knowledgeGraphService.getGraphByArticleId(articleId);
        if (graph == null) {
            return Result.success("该文章暂无知识图谱", null);
        }
        return Result.success(graph);
    }

    /**
     * 获取全局知识图谱
     * GET /api/knowledge-graph/global
     */
    @GetMapping("/global")
    public Result<GlobalKnowledgeGraphVO> getGlobalGraph() {
        GlobalKnowledgeGraphVO graph = knowledgeGraphService.getGlobalGraph();
        return Result.success(graph);
    }

    /**
     * 重新生成知识图谱
     * POST /api/knowledge-graph/regenerate/{articleId}
     */
    @PostMapping("/regenerate/{articleId}")
    public Result<KnowledgeGraphVO> regenerateGraph(@PathVariable Long articleId) {
        KnowledgeGraphVO graph = knowledgeGraphService.regenerateGraph(articleId);
        return Result.success("知识图谱重新生成成功", graph);
    }

    /**
     * 删除文章的知识图谱
     * DELETE /api/knowledge-graph/{articleId}
     */
    @DeleteMapping("/{articleId}")
    public Result<Void> deleteGraph(@PathVariable Long articleId) {
        knowledgeGraphService.deleteGraph(articleId);
        return Result.success("知识图谱已删除", null);
    }
}
