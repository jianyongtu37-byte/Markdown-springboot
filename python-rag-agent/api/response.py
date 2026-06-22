"""
统一响应包装 — 与 Java 微服务的 Result<T> 格式一致
前端期望: {"code": 200, "message": "success", "data": ..., "timestamp": ...}
"""

import time
from typing import Any, Optional


def ok(data: Any = None, message: str = "success") -> dict:
    """成功响应"""
    return {
        "code": 200,
        "message": message,
        "data": data,
        "timestamp": int(time.time() * 1000),
    }


def error(code: int = 500, message: str = "操作失败") -> dict:
    """错误响应"""
    return {
        "code": code,
        "message": message,
        "data": None,
        "timestamp": int(time.time() * 1000),
    }
