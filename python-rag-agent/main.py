"""
RAG Knowledge QA Agent — FastAPI 应用入口
基于 FAISS + bge-small-zh-v1.5 + DeepSeek 的知识库问答服务
"""

import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from api.routes import router
from config import Config

app = FastAPI(
    title="Mdown RAG Agent",
    description="基于 RAG 技术的 Markdown 知识库智能问答服务",
    version="1.0.0",
)

# CORS 配置 — 允许 Java 主服务和前端调用
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 注册 API 路由
app.include_router(router)


@app.get("/health")
async def health_check():
    """健康检查端点"""
    return {"status": "ok", "service": "rag-agent"}


if __name__ == "__main__":
    uvicorn.run(
        "main:app",
        host=Config.HOST,
        port=Config.PORT,
        reload=True,
    )
