"""
向量模型管理
使用 BAAI/bge-small-zh-v1.5 — 中文检索效果远优于 MiniLM，体积 ~90MB
"""

import os
# 确保 HuggingFace 环境变量在导入 sentence_transformers 之前设置
from dotenv import load_dotenv
load_dotenv()
# 模型已缓存时强制离线，避免启动时连接 HuggingFace 超时
if not os.environ.get("HF_HUB_OFFLINE"):
    os.environ["HF_HUB_OFFLINE"] = "1"

from sentence_transformers import SentenceTransformer
import numpy as np
from typing import List
from config import Config


class EmbeddingManager:
    """Embedding 模型单例管理器"""

    _instance = None
    _model = None

    def __init__(self):
        if EmbeddingManager._model is None:
            model_name = Config.EMBEDDING_MODEL
            print(f"Loading embedding model: {model_name}...")
            EmbeddingManager._model = SentenceTransformer(model_name)
            print("Embedding model loaded.")

    @classmethod
    def get_instance(cls) -> "EmbeddingManager":
        """获取单例实例"""
        if cls._instance is None:
            cls._instance = cls()
        return cls._instance

    def encode(self, texts: List[str], batch_size: int = 32) -> np.ndarray:
        """
        将文本列表编码为向量

        Args:
            texts: 文本列表
            batch_size: 批处理大小

        Returns:
            numpy 数组，shape=(len(texts), dimension)
        """
        return self._model.encode(
            texts,
            batch_size=batch_size,
            show_progress_bar=False,
            normalize_embeddings=True,  # L2 归一化，配合内积相似度
        )

    def encode_single(self, text: str) -> np.ndarray:
        """编码单条文本"""
        return self.encode([text])[0]

    @property
    def dimension(self) -> int:
        """向量维度"""
        return Config.EMBEDDING_DIMENSION
