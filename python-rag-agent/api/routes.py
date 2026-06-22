"""
FastAPI 路由定义
RAG 知识问答 API — 支持多轮对话、选段提问、索引管理
"""

import json
import uuid
from fastapi import APIRouter, HTTPException, Header, Depends
from fastapi.responses import StreamingResponse
from typing import Optional

from api.models import (
    RAGQuestionRequest,
    RAGResponse,
    SourceItem,
    IndexStatus,
    ReindexRequest,
    ArticleSyncRequest,
    ArticleRemoveRequest,
    GlobalSyncRequest,
    GlobalReindexRequest,
)
from api.response import ok, error
from agents.cross_article import CrossArticleAgent
from agents.conversational import ConversationalAgent
from agents.gap_analysis import GapAnalysisAgent
from agents.learning_path import LearningPathAgent
from core.vectorstore import VectorStore, get_global_vector_store
from core.chunker import MarkdownChunker
from core.llm import DeepSeekLLM
from core.mysql_session import MySQLSessionManager as SessionManager

router = APIRouter(prefix="/api/rag", tags=["RAG"])

# 全局实例（懒加载）
_llm: DeepSeekLLM = None
_chunker: MarkdownChunker = None
_session_manager: SessionManager = None


def get_llm() -> DeepSeekLLM:
    global _llm
    if _llm is None:
        _llm = DeepSeekLLM()
    return _llm


def get_chunker() -> MarkdownChunker:
    global _chunker
    if _chunker is None:
        _chunker = MarkdownChunker()
    return _chunker


def get_session_manager() -> SessionManager:
    global _session_manager
    if _session_manager is None:
        _session_manager = SessionManager()
    return _session_manager


def get_vector_store(user_id: int) -> VectorStore:
    return VectorStore(user_id)


# ==================== 依赖：从 Gateway 传递的请求头获取用户 ID ====================


async def get_user_id_from_header(
    x_user_id: Optional[str] = Header(None, alias="X-User-Id"),
) -> int:
    """从 Gateway 注入的 X-User-Id 请求头解析用户 ID"""
    if not x_user_id:
        raise HTTPException(status_code=401, detail="缺少用户身份信息（X-User-Id）")
    try:
        return int(x_user_id)
    except ValueError:
        raise HTTPException(status_code=400, detail="X-User-Id 格式无效")


# ==================== 问答接口 ====================


@router.post("/ask")
async def ask_knowledge_base(
    request: RAGQuestionRequest,
    user_id: int = Depends(get_user_id_from_header),
):
    """跨文章知识问答（非流式，支持多轮对话）"""
    llm = get_llm()
    vs = get_vector_store(user_id)
    gs = get_global_vector_store()
    sm = get_session_manager()
    agent = ConversationalAgent(vs, llm, sm, gs)

    result = await agent.ask(
        question=request.question,
        user_id=user_id,
        session_id=request.session_id,
        scope=request.scope.value if request.scope else "all",
        article_id=request.article_id,
        max_sources=request.max_sources,
        highlight=request.highlight,
    )
    return ok(result)


@router.post("/ask/stream")
async def ask_knowledge_base_stream(
    request: RAGQuestionRequest,
    user_id: int = Depends(get_user_id_from_header),
):
    """跨文章知识问答（流式 SSE，支持多轮对话）"""
    llm = get_llm()
    vs = get_vector_store(user_id)
    gs = get_global_vector_store()
    sm = get_session_manager()
    agent = ConversationalAgent(vs, llm, sm, gs)

    async def event_generator():
        try:
            async for event in agent.ask_stream(
                question=request.question,
                user_id=user_id,
                session_id=request.session_id,
                scope=request.scope.value if request.scope else "all",
                article_id=request.article_id,
                max_sources=request.max_sources,
                highlight=request.highlight,
            ):
                yield f"data: {json.dumps(event, ensure_ascii=False)}\n\n"
        except Exception as e:
            yield f"data: {json.dumps({'type': 'error', 'data': str(e)}, ensure_ascii=False)}\n\n"

    return StreamingResponse(event_generator(), media_type="text/event-stream")


@router.post("/article/{article_id}/ask")
async def ask_about_article(
    article_id: int,
    request: RAGQuestionRequest,
    user_id: int = Depends(get_user_id_from_header),
):
    """文章精读问答（非流式，支持多轮对话）"""
    llm = get_llm()
    vs = get_vector_store(user_id)
    sm = get_session_manager()
    # 精读模式不需要全局索引，只在用户索引中检索
    agent = ConversationalAgent(vs, llm, sm)

    result = await agent.ask(
        question=request.question,
        user_id=user_id,
        session_id=request.session_id,
        scope="article",
        article_id=article_id,
        max_sources=request.max_sources,
        highlight=request.highlight,
    )
    return ok(result)


@router.post("/article/{article_id}/ask/stream")
async def ask_about_article_stream(
    article_id: int,
    request: RAGQuestionRequest,
    user_id: int = Depends(get_user_id_from_header),
):
    """文章精读问答（流式 SSE，支持多轮对话）"""
    llm = get_llm()
    vs = get_vector_store(user_id)
    sm = get_session_manager()
    agent = ConversationalAgent(vs, llm, sm)

    async def event_generator():
        try:
            async for event in agent.ask_stream(
                question=request.question,
                user_id=user_id,
                session_id=request.session_id,
                scope="article",
                article_id=article_id,
                max_sources=request.max_sources,
                highlight=request.highlight,
            ):
                yield f"data: {json.dumps(event, ensure_ascii=False)}\n\n"
        except Exception as e:
            yield f"data: {json.dumps({'type': 'error', 'data': str(e)}, ensure_ascii=False)}\n\n"

    return StreamingResponse(event_generator(), media_type="text/event-stream")


# ==================== 会话管理接口 ====================


@router.get("/sessions")
async def list_sessions(user_id: int = Depends(get_user_id_from_header)):
    """列出用户的所有活跃会话"""
    sm = get_session_manager()
    sessions = sm.list_sessions(user_id)
    return ok({"sessions": sessions})


@router.get("/sessions/{session_id}/history")
async def get_session_history(
    session_id: str,
    user_id: int = Depends(get_user_id_from_header),
):
    """获取会话的对话历史"""
    sm = get_session_manager()
    history = sm.get_history(user_id, session_id)
    return ok({"session_id": session_id, "history": history})


@router.delete("/sessions/{session_id}")
async def clear_session(
    session_id: str,
    user_id: int = Depends(get_user_id_from_header),
):
    """清除会话"""
    sm = get_session_manager()
    cleared = sm.clear_session(user_id, session_id)
    return ok({"status": "cleared" if cleared else "not_found", "session_id": session_id})


# ==================== 用户索引管理接口 ====================


@router.post("/reindex")
async def rebuild_index(
    request: ReindexRequest,
    user_id: int = Depends(get_user_id_from_header),
):
    """全量重建用户索引"""
    vs = get_vector_store(user_id)
    chunker = get_chunker()

    # 使用 defer_save 延迟写盘，clear + 批量 add 替代逐条 remove + add
    async with vs.defer_save():
        await vs.clear()
        for article in request.articles:
            chunks = chunker.chunk(
                article["content"],
                metadata={"article_id": article["id"]},
            )
            await vs.add_chunks(chunks, article["id"], article["title"])

    status = vs.get_status()
    return ok({"status": "reindex_completed", **status})


@router.post("/article/sync")
async def sync_article(
    request: ArticleSyncRequest,
    user_id: int = Depends(get_user_id_from_header),
):
    """增量同步单篇文章到用户索引"""
    vs = get_vector_store(user_id)
    chunker = get_chunker()

    chunks = chunker.chunk(
        request.content,
        metadata={"article_id": request.article_id},
    )
    await vs.update_article(request.article_id, request.article_title, chunks)

    return ok({
        "status": "synced",
        "article_id": request.article_id,
        "chunks_count": len(chunks),
    })


@router.delete("/article/{article_id}")
async def remove_article(
    article_id: int,
    user_id: int = Depends(get_user_id_from_header),
):
    """删除文章的索引数据"""
    vs = get_vector_store(user_id)
    await vs.remove_article(article_id)
    return ok({"status": "removed", "article_id": article_id})


@router.get("/status")
async def get_index_status(user_id: int = Depends(get_user_id_from_header)):
    """获取用户索引状态（含全局公共索引）"""
    vs = get_vector_store(user_id)
    gs = get_global_vector_store()
    user_status = vs.get_status()
    global_status = gs.get_status()
    return ok({
        "user_index": user_status,
        "global_index": global_status,
    })


# ==================== 全局公共索引管理接口 ====================


@router.post("/global/sync")
async def sync_global_article(
    request: GlobalSyncRequest,
    user_id: int = Depends(get_user_id_from_header),
):
    """同步单篇公开文章到全局公共索引（Java 后端调用）"""
    gs = get_global_vector_store()
    chunker = get_chunker()

    chunks = chunker.chunk(
        request.content,
        metadata={"article_id": request.article_id},
    )
    await gs.update_article(request.article_id, request.article_title, chunks)

    return ok({
        "status": "synced",
        "article_id": request.article_id,
        "chunks_count": len(chunks),
    })


@router.delete("/global/article/{article_id}")
async def remove_global_article(
    article_id: int,
    user_id: int = Depends(get_user_id_from_header),
):
    """从全局公共索引移除文章"""
    gs = get_global_vector_store()
    await gs.remove_article(article_id)
    return ok({"status": "removed", "article_id": article_id})


@router.post("/global/reindex")
async def rebuild_global_index(
    request: GlobalReindexRequest,
    user_id: int = Depends(get_user_id_from_header),
):
    """全量重建全局公共索引"""
    gs = get_global_vector_store()
    chunker = get_chunker()

    # 使用 defer_save 延迟写盘，clear + 批量 add 替代逐条 remove + add
    async with gs.defer_save():
        await gs.clear()
        for article in request.articles:
            chunks = chunker.chunk(
                article["content"],
                metadata={"article_id": article["id"]},
            )
            await gs.add_chunks(chunks, article["id"], article["title"])

    status = gs.get_status()
    return ok({"status": "global_reindex_completed", **status})


# ==================== 智能分析接口 ====================


@router.post("/analysis/gap")
async def analyze_knowledge_gap(
    user_id: int = Depends(get_user_id_from_header),
):
    """分析知识库的知识缺口"""
    llm = get_llm()
    vs = get_vector_store(user_id)
    gs = get_global_vector_store()
    agent = GapAnalysisAgent(vs, llm)
    result = await agent.analyze(user_id)
    return ok(result)


@router.post("/analysis/learning-path")
async def recommend_learning_path(
    topic: str = "",
    user_id: int = Depends(get_user_id_from_header),
):
    """推荐学习路径"""
    llm = get_llm()
    vs = get_vector_store(user_id)
    agent = LearningPathAgent(vs, llm)
    result = await agent.recommend(user_id, topic=topic)
    return ok(result)


# ==================== 索引对账接口 ====================


@router.get("/article-ids")
async def get_user_article_ids(user_id: int = Depends(get_user_id_from_header)):
    """获取用户私有索引中的所有文章 ID"""
    vs = get_vector_store(user_id)
    article_ids = vs.get_article_ids()
    return ok({"user_id": user_id, "article_ids": article_ids, "count": len(article_ids)})


@router.get("/global/article-ids")
async def get_global_article_ids(user_id: int = Depends(get_user_id_from_header)):
    """获取全局公共索引中的所有文章 ID"""
    gs = get_global_vector_store()
    article_ids = gs.get_article_ids()
    return ok({"article_ids": article_ids, "count": len(article_ids)})


@router.get("/users")
async def list_index_users(user_id: int = Depends(get_user_id_from_header)):
    """获取所有拥有 FAISS 索引的用户 ID"""
    user_ids = VectorStore.get_all_user_ids()
    return ok({"user_ids": user_ids, "count": len(user_ids)})


@router.delete("/user/{target_user_id}")
async def delete_user_index(
    target_user_id: int,
    user_id: int = Depends(get_user_id_from_header),
):
    """删除指定用户的整个 FAISS 索引"""
    deleted = VectorStore.delete_user_index(target_user_id)
    return ok({"user_id": target_user_id, "deleted": deleted})


# ============================================================
# Rewrite Pipeline — Cache & Evaluation Management
# ============================================================

@router.get("/rewrite-cache/stats")
async def get_rewrite_cache_stats(
    user_id: int = Header(None, alias="X-User-Id"),
):
    """Get rewrite cache hit/miss statistics."""
    agent = _get_conversational_agent(user_id)
    if agent and agent.rewrite_pipeline:
        stats = agent.rewrite_pipeline.stats
        return ok(stats)
    return ok({"message": "Rewrite pipeline not available"})


@router.delete("/rewrite-cache")
async def clear_rewrite_cache(
    user_id: int = Header(None, alias="X-User-Id"),
    session_id: Optional[str] = None,
):
    """Clear rewrite cache for a user or session."""
    agent = _get_conversational_agent(user_id)
    if agent and agent.rewrite_pipeline:
        await agent.rewrite_pipeline._cache.invalidate_session(
            user_id or 0, session_id or ""
        )
        return ok({"message": "Rewrite cache cleared"})
    return ok({"message": "Rewrite pipeline not available"})


@router.get("/eval/metrics")
async def get_rewrite_metrics(
    user_id: int = Header(None, alias="X-User-Id"),
):
    """Get rewrite quality metrics (latency, fidelity, distribution)."""
    agent = _get_conversational_agent(user_id)
    if agent and agent.rewrite_pipeline:
        return ok(agent.rewrite_pipeline.stats)
    return ok({"message": "Rewrite pipeline not available"})


@router.get("/eval/samples")
async def get_rewrite_samples(
    limit: int = 20,
    user_id: int = Header(None, alias="X-User-Id"),
):
    """Get recent rewrite evaluation samples for manual review."""
    agent = _get_conversational_agent(user_id)
    if agent and agent.rewrite_pipeline:
        samples = agent.rewrite_pipeline.get_eval_samples(limit)
        return ok({
            "samples": samples,
            "count": len(samples),
        })
    return ok({"message": "Rewrite pipeline not available"})


@router.get("/domain-dict")
async def get_domain_dictionary():
    """Get the current domain entity dictionary statistics."""
    from core.rewrite.domain_dict import DomainDict
    dd = DomainDict()
    return ok({
        "total_entries": len(dd),
        "entries_loaded": not dd.is_empty(),
    })


@router.post("/domain-dict/reload")
async def reload_domain_dictionary():
    """Hot-reload the domain dictionary from file."""
    from core.rewrite.domain_dict import DomainDict
    dd = DomainDict()
    dd.reload()
    return ok({
        "message": "Domain dictionary reloaded",
        "total_entries": len(dd),
    })


def _get_conversational_agent(user_id: int) -> Optional[ConversationalAgent]:
    """Get a ConversationalAgent instance for the given user."""
    try:
        llm = get_llm()
        vs = get_vector_store(user_id)
        global_vs = get_global_vector_store()
        sm = get_session_manager()
        return ConversationalAgent(vs, llm, sm, global_vs)
    except Exception:
        return None
