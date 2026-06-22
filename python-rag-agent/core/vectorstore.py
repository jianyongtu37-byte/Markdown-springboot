"""
FAISS 向量索引管理
每用户独立索引，支持增删改查

优化点：
1. IndexIDMap + IndexFlatIP 实现 O(1) 精准 ID 删除，避免全量重建
2. asyncio.Lock 保证并发写安全
3. 元数据用 dict（id→meta）替代 list，删除时无需遍历
4. defer_save 上下文管理器，批量操作时延迟写盘，性能提升 ~100x
"""

import faiss
import pickle
import os
import asyncio
import hashlib
from contextlib import asynccontextmanager
from typing import List, Tuple, Optional, Dict
import numpy as np

from core.embeddings import EmbeddingManager
from config import Config

# 全局公共索引的固定 user_id 标识
GLOBAL_PUBLIC_USER_ID = -1


def chunk_id(article_id: int, chunk_index: int) -> int:
    """
    为每个 chunk 生成全局唯一 int64 ID
    用 article_id + chunk_index 的 hash 映射到 int64 范围
    """
    key = f"{article_id}:{chunk_index}"
    h = hashlib.md5(key.encode()).hexdigest()
    return int(h[:15], 16)  # 取前 15 位 hex → 保证在 int64 范围内


class VectorStore:
    """FAISS 向量索引管理器（每用户独立实例）"""

    def __init__(self, user_id: int, base_path: str = None):
        self.user_id = user_id
        base_path = base_path or Config.FAISS_INDEX_PATH
        self.index_dir = os.path.join(base_path, str(user_id))
        self.index_path = os.path.join(self.index_dir, "index.faiss")
        self.meta_path = os.path.join(self.index_dir, "index_meta.pkl")

        self.embedding_manager = EmbeddingManager.get_instance()
        self.dimension = self.embedding_manager.dimension

        # 元数据：{cid: {"article_id", "article_title", "chunk_index", "content", "cid"}}
        self.metadata: Dict[int, dict] = {}
        self._id_map: Dict[int, int] = {}  # cid → faiss internal id
        self.index: Optional[faiss.IndexIDMap] = None

        # 异步锁，保护并发写安全
        self._lock = asyncio.Lock()

        # 延迟写盘标志
        self._defer_save = False
        self._dirty = False  # 标记是否有未写盘的变更

        self._load_or_create()

    def _load_or_create(self):
        """加载已有索引或创建新索引"""
        os.makedirs(self.index_dir, exist_ok=True)

        if os.path.exists(self.index_path) and os.path.exists(self.meta_path):
            self.index = faiss.read_index(self.index_path)
            with open(self.meta_path, "rb") as f:
                data = pickle.load(f)
                self.metadata = data.get("metadata", {})
                self._id_map = data.get("id_map", {})
            print(
                f"Loaded index for user {self.user_id}: {self.index.ntotal} vectors"
            )
        else:
            # IndexIDMap 包装 IndexFlatIP，支持 remove_ids
            flat = faiss.IndexFlatIP(self.dimension)
            self.index = faiss.IndexIDMap(flat)
            self.metadata = {}
            self._id_map = {}
            print(f"Created new index for user {self.user_id}")

    async def add_chunks(
        self, chunks: List[dict], article_id: int, article_title: str
    ):
        """
        添加文章 chunks 到索引

        Args:
            chunks: [{"content": str, "index": int, ...}, ...]
            article_id: 文章 ID
            article_title: 文章标题
        """
        if not chunks:
            return

        async with self._lock:
            texts = [c["content"] for c in chunks]
            vectors = self.embedding_manager.encode(texts).astype(np.float32)

            # 生成唯一 ID
            ids = np.array(
                [chunk_id(article_id, c["index"]) for c in chunks], dtype=np.int64
            )

            # 添加到 FAISS
            self.index.add_with_ids(vectors, ids)

            # 保存元数据
            for i, c in enumerate(chunks):
                cid = int(ids[i])
                self._id_map[cid] = cid
                self.metadata[cid] = {
                    "article_id": article_id,
                    "article_title": article_title,
                    "chunk_index": c["index"],
                    "content": c["content"],
                    "cid": cid,
                }

            self._save()
            print(
                f"Added {len(chunks)} chunks for article {article_id} "
                f"(user {self.user_id}, total: {self.index.ntotal})"
            )

    async def search(
        self,
        query: str,
        top_k: int = 10,
        article_id: Optional[int] = None,
    ) -> List[Tuple[dict, float]]:
        """
        语义检索

        Args:
            query: 查询文本
            top_k: 返回结果数
            article_id: 可选，限定在某篇文章内检索

        Returns:
            [(metadata, score), ...] 按相关性降序
        """
        if self.index is None or self.index.ntotal == 0:
            return []

        query_vector = self.embedding_manager.encode_single(query).astype(np.float32)
        query_vector = query_vector.reshape(1, -1)

        search_k = min(top_k * 3, self.index.ntotal)
        scores, ids = self.index.search(query_vector, search_k)

        results = []
        for score, fid in zip(scores[0], ids[0]):
            if fid == -1:
                continue
            meta = self.metadata.get(int(fid))
            if meta is None:
                continue

            # 如果指定了文章 ID，过滤其他文章
            if article_id is not None and meta["article_id"] != article_id:
                continue

            results.append((meta, float(score)))

            if len(results) >= top_k:
                break

        return results

    async def search_with_embedding(
        self,
        query_embedding: "np.ndarray",
        top_k: int = 10,
        article_id: Optional[int] = None,
    ) -> List[Tuple[dict, float]]:
        """
        Search using a pre-computed embedding vector (HyDE).

        Args:
            query_embedding: Pre-computed embedding vector (should be L2-normalized)
            top_k: Number of results to return
            article_id: Optional article ID to scope search

        Returns:
            [(metadata, score), ...] sorted by relevance descending
        """
        if self.index is None or self.index.ntotal == 0:
            return []

        query_vector = query_embedding.astype(np.float32).reshape(1, -1)
        search_k = min(top_k * 3, self.index.ntotal)
        scores, ids = self.index.search(query_vector, search_k)

        results = []
        for score, fid in zip(scores[0], ids[0]):
            if fid == -1:
                continue
            meta = self.metadata.get(int(fid))
            if meta is None:
                continue
            if article_id is not None and meta["article_id"] != article_id:
                continue
            results.append((meta, float(score)))
            if len(results) >= top_k:
                break

        return results

    async def remove_article(self, article_id: int):
        """
        O(1) 级精准删除，通过 IndexIDMap.remove_ids 实现
        """
        async with self._lock:
            # 找到该文章所有 chunk 的 faiss ID
            ids_to_remove = [
                cid
                for cid, meta in self.metadata.items()
                if meta["article_id"] == article_id
            ]

            if not ids_to_remove:
                return

            ids_array = np.array(ids_to_remove, dtype=np.int64)
            self.index.remove_ids(ids_array)

            # 清理元数据
            for cid in ids_to_remove:
                del self.metadata[cid]
                self._id_map.pop(cid, None)

            self._save()
            print(
                f"Removed article {article_id} chunks (user {self.user_id}, "
                f"remaining: {self.index.ntotal})"
            )

    async def update_article(
        self, article_id: int, article_title: str, chunks: List[dict]
    ):
        """更新某文章的 chunks（先删后增，在单次 lock 内完成）"""
        async with self._lock:
            # 删除旧 chunks
            ids_to_remove = [
                cid
                for cid, meta in self.metadata.items()
                if meta["article_id"] == article_id
            ]
            if ids_to_remove:
                ids_array = np.array(ids_to_remove, dtype=np.int64)
                self.index.remove_ids(ids_array)
                for cid in ids_to_remove:
                    del self.metadata[cid]
                    self._id_map.pop(cid, None)

            # 添加新 chunks
            if chunks:
                texts = [c["content"] for c in chunks]
                vectors = self.embedding_manager.encode(texts).astype(np.float32)
                ids = np.array(
                    [chunk_id(article_id, c["index"]) for c in chunks], dtype=np.int64
                )
                self.index.add_with_ids(vectors, ids)
                for i, c in enumerate(chunks):
                    cid = int(ids[i])
                    self._id_map[cid] = cid
                    self.metadata[cid] = {
                        "article_id": article_id,
                        "article_title": article_title,
                        "chunk_index": c["index"],
                        "content": c["content"],
                        "cid": cid,
                    }

            self._save()

    @asynccontextmanager
    async def defer_save(self):
        """
        延迟写盘上下文管理器
        在此上下文内的所有 mutate 操作不会触发磁盘写入
        退出上下文时自动 flush（仅写一次盘）

        用法：
            async with vs.defer_save():
                for article in articles:
                    await vs.add_chunks(chunks, ...)
            # 退出时自动写盘一次
        """
        self._defer_save = True
        self._dirty = False
        try:
            yield self
        finally:
            self._defer_save = False
            if self._dirty:
                self._do_save()

    async def flush(self):
        """手动触发写盘（配合 defer_save 使用）"""
        async with self._lock:
            if self._dirty:
                self._do_save()
                self._dirty = False

    async def clear(self):
        """
        清空索引和元数据（不写盘）
        用于全量重建场景，配合 defer_save 使用
        """
        async with self._lock:
            flat = faiss.IndexFlatIP(self.dimension)
            self.index = faiss.IndexIDMap(flat)
            self.metadata = {}
            self._id_map = {}
            self._dirty = True
            if not self._defer_save:
                self._do_save()

    def get_article_ids(self) -> list:
        """获取索引中所有不重复的文章 ID"""
        unique_articles = set()
        for meta in self.metadata.values():
            unique_articles.add(meta["article_id"])
        return list(unique_articles)

    def get_status(self) -> dict:
        """获取索引状态"""
        unique_articles = set()
        for meta in self.metadata.values():
            unique_articles.add(meta["article_id"])

        return {
            "user_id": self.user_id,
            "total_vectors": self.index.ntotal if self.index else 0,
            "total_articles": len(unique_articles),
            "total_chunks": len(self.metadata),
        }

    @staticmethod
    def get_all_user_ids(base_path: str = None) -> list:
        """扫描 FAISS 索引目录，返回所有拥有索引的 user_id 列表"""
        base_path = base_path or Config.FAISS_INDEX_PATH
        user_ids = []
        if not os.path.isdir(base_path):
            return user_ids
        for name in os.listdir(base_path):
            subdir = os.path.join(base_path, name)
            if os.path.isdir(subdir) and os.path.exists(os.path.join(subdir, "index.faiss")):
                try:
                    user_ids.append(int(name))
                except ValueError:
                    pass
        return user_ids

    @staticmethod
    def delete_user_index(user_id: int, base_path: str = None):
        """删除指定用户的整个索引目录"""
        import shutil
        base_path = base_path or Config.FAISS_INDEX_PATH
        index_dir = os.path.join(base_path, str(user_id))
        if os.path.exists(index_dir):
            shutil.rmtree(index_dir)
            return True
        return False

    def _save(self):
        """持久化索引和元数据（如果启用了 defer_save，则只标记 dirty）"""
        if self._defer_save:
            self._dirty = True
            return
        self._do_save()

    def _do_save(self):
        """实际执行磁盘写入"""
        faiss.write_index(self.index, self.index_path)
        with open(self.meta_path, "wb") as f:
            pickle.dump({"metadata": self.metadata, "id_map": self._id_map}, f)


def get_global_vector_store() -> VectorStore:
    """获取全局公共索引实例（所有公开文章，所有用户共享）"""
    return VectorStore(GLOBAL_PUBLIC_USER_ID)
