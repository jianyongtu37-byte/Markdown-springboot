"""
RAG Agent 配置管理
从环境变量读取配置，支持 .env 文件
"""

import os
from dotenv import load_dotenv

load_dotenv()


class Config:
    # DeepSeek API
    DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "")
    DEEPSEEK_API_URL = os.getenv(
        "DEEPSEEK_API_URL", "https://api.deepseek.com/v1/chat/completions"
    )
    DEEPSEEK_MODEL = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")

    # Embedding 模型 — bge-small-zh-v1.5，中文语义检索效果优秀
    EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "BAAI/bge-small-zh-v1.5")
    EMBEDDING_DIMENSION = int(os.getenv("EMBEDDING_DIMENSION", "512"))

    # FAISS 索引存储路径
    FAISS_INDEX_PATH = os.getenv("FAISS_INDEX_PATH", "data/faiss_indexes")

    # 服务配置
    HOST = os.getenv("RAG_HOST", "0.0.0.0")
    PORT = int(os.getenv("RAG_PORT", "8084"))

    # 分块参数
    CHUNK_SIZE = int(os.getenv("CHUNK_SIZE", "500"))
    CHUNK_OVERLAP = int(os.getenv("CHUNK_OVERLAP", "50"))

    # 检索参数
    DEFAULT_TOP_K = int(os.getenv("DEFAULT_TOP_K", "5"))
    MAX_TOP_K = int(os.getenv("MAX_TOP_K", "20"))
    # 相关性阈值：低于此分数的检索结果视为不相关，将回退到通用知识回答
    # FAISS IndexFlatIP 对 L2 归一化向量等价于余弦相似度，范围 [-1, 1]
    RELEVANCE_THRESHOLD = float(os.getenv("RELEVANCE_THRESHOLD", "0.4"))

    # Java 主服务地址（用于拉取文章数据）
    JAVA_SERVICE_URL = os.getenv("JAVA_SERVICE_URL", "http://localhost:8080")

    # MySQL 配置（RAG 会话存储）
    MYSQL_HOST = os.getenv("MYSQL_HOST", "localhost")
    MYSQL_PORT = int(os.getenv("MYSQL_PORT", "3306"))
    MYSQL_USER = os.getenv("MYSQL_USER", "root")
    MYSQL_PASSWORD = os.getenv("MYSQL_PASSWORD", "root")
    MYSQL_DATABASE = os.getenv("MYSQL_DATABASE", "markdown_db")

    # ============================================================
    # Query Rewrite Configuration
    # ============================================================

    # Layer thresholds: confidence above which to stop at a layer
    REWRITE_L1_CONFIDENCE_THRESHOLD = float(os.getenv("REWRITE_L1_CONFIDENCE_THRESHOLD", "0.9"))
    REWRITE_L2_CONFIDENCE_THRESHOLD = float(os.getenv("REWRITE_L2_CONFIDENCE_THRESHOLD", "0.8"))

    # Coreference: number of conversation rounds to include in LLM rewrite
    REWRITE_MAX_HISTORY_ROUNDS = int(os.getenv("REWRITE_MAX_HISTORY_ROUNDS", "5"))

    # HyDE (Hypothetical Document Embeddings)
    REWRITE_HYDE_MAX_TOKENS = int(os.getenv("REWRITE_HYDE_MAX_TOKENS", "200"))
    REWRITE_HYDE_ENABLED = os.getenv("REWRITE_HYDE_ENABLED", "true").lower() == "true"

    # Decomposition (sub-query splitting)
    REWRITE_MAX_SUB_QUERIES = int(os.getenv("REWRITE_MAX_SUB_QUERIES", "4"))
    REWRITE_DECOMPOSITION_ENABLED = os.getenv("REWRITE_DECOMPOSITION_ENABLED", "true").lower() == "true"

    # Step-back abstraction (retry fallback)
    REWRITE_ABSTRACTION_RETRY = os.getenv("REWRITE_ABSTRACTION_RETRY", "true").lower() == "true"

    # Multi-perspective rewriting
    REWRITE_MAX_VARIANTS = int(os.getenv("REWRITE_MAX_VARIANTS", "3"))
    REWRITE_MULTI_PERSPECTIVE_ENABLED = os.getenv("REWRITE_MULTI_PERSPECTIVE_ENABLED", "true").lower() == "true"

    # Cache TTLs (seconds)
    REWRITE_CACHE_TTL_L1 = int(os.getenv("REWRITE_CACHE_TTL_L1", "1800"))    # 30 min
    REWRITE_CACHE_TTL_L2 = int(os.getenv("REWRITE_CACHE_TTL_L2", "600"))     # 10 min
    REWRITE_CACHE_TTL_L3 = int(os.getenv("REWRITE_CACHE_TTL_L3", "300"))     # 5 min
    REWRITE_CACHE_TTL_HOT = int(os.getenv("REWRITE_CACHE_TTL_HOT", "3600"))  # 1 hour
    REWRITE_EMBEDDING_CACHE_TTL = int(os.getenv("REWRITE_EMBEDDING_CACHE_TTL", "3600"))

    # Redis for rewrite cache (uses same Redis host as session, separate DB)
    REWRITE_CACHE_REDIS_DB = int(os.getenv("REWRITE_CACHE_REDIS_DB", "3"))

    # Evaluation
    REWRITE_EVAL_ENABLED = os.getenv("REWRITE_EVAL_ENABLED", "true").lower() == "true"
    REWRITE_EVAL_SAMPLE_RATE = float(os.getenv("REWRITE_EVAL_SAMPLE_RATE", "0.1"))  # 10% sampling
    REWRITE_EVAL_MAX_SAMPLES = int(os.getenv("REWRITE_EVAL_MAX_SAMPLES", "10000"))

    # Domain dictionary path
    REWRITE_DOMAIN_DICT_PATH = os.getenv(
        "REWRITE_DOMAIN_DICT_PATH",
        os.path.join(os.path.dirname(__file__), "data", "domain_dict.json"),
    )
