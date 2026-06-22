"""
RAG API 请求/响应 Pydantic 模型
"""

from pydantic import BaseModel
from typing import List, Optional
from enum import Enum


class QuestionScope(str, Enum):
    """问答范围"""
    ALL = "all"           # 跨所有文章（私有 + 全局公共）
    PERSONAL = "personal" # 仅个人知识库（私有索引，默认当用户提到"我的"时自动切换）
    ARTICLE = "article"   # 单篇文章
    CATEGORY = "category" # 按分类
    TAG = "tag"           # 按标签


class RAGQuestionRequest(BaseModel):
    """问答请求"""
    question: str                           # 用户问题
    user_id: Optional[int] = None           # 用户 ID（可选，优先从 X-User-Id 请求头获取）
    article_id: Optional[int] = None        # 指定文章（精读模式）
    session_id: Optional[str] = None        # 会话 ID（多轮对话）
    scope: QuestionScope = QuestionScope.ALL
    highlight: Optional[str] = None         # 用户选中的文本
    max_sources: int = 5                    # 最大来源数


class SourceItem(BaseModel):
    """来源引用项"""
    article_id: int
    article_title: str
    chunk_content: str                      # 匹配的段落内容
    relevance_score: float                  # 相关性分数
    chunk_index: int                        # 段落在文章中的位置


class RAGResponse(BaseModel):
    """问答响应"""
    answer: str                             # 生成的回答
    sources: List[SourceItem]               # 引用来源
    session_id: str                         # 会话 ID
    confidence: float                       # 置信度 0-1
    query_rewritten: Optional[str] = None   # 重写后的查询（如果有）


class ArticleChunk(BaseModel):
    """文章分块"""
    article_id: int
    article_title: str
    chunk_index: int
    content: str
    metadata: dict = {}


class IndexStatus(BaseModel):
    """索引状态"""
    user_id: int
    total_articles: int
    total_chunks: int
    total_vectors: int


class ReindexRequest(BaseModel):
    """全量重建索引请求"""
    user_id: int
    articles: List[dict]  # [{"id": 1, "title": "...", "content": "..."}, ...]


class ArticleSyncRequest(BaseModel):
    """文章同步请求（增量）"""
    user_id: int
    article_id: int
    article_title: str
    content: str


class ArticleRemoveRequest(BaseModel):
    """文章删除请求"""
    user_id: int
    article_id: int


class GlobalSyncRequest(BaseModel):
    """全局公共索引同步请求"""
    article_id: int
    article_title: str
    content: str


class GlobalReindexRequest(BaseModel):
    """全局公共索引全量重建请求"""
    articles: List[dict]  # [{"id": 1, "title": "...", "content": "..."}, ...]
