"""
DeepSeek API 封装
支持同步调用和流式调用
"""

import httpx
from typing import List, Dict, AsyncGenerator
import json
from config import Config


class DeepSeekLLM:
    """DeepSeek API 客户端"""

    def __init__(
        self,
        api_key: str = None,
        api_url: str = None,
        model: str = None,
    ):
        self.api_key = api_key or Config.DEEPSEEK_API_KEY
        self.api_url = api_url or Config.DEEPSEEK_API_URL
        self.model = model or Config.DEEPSEEK_MODEL

    async def chat(
        self,
        messages: List[Dict],
        temperature: float = 0.7,
        max_tokens: int = 2000,
    ) -> str:
        """
        同步调用 DeepSeek API

        Args:
            messages: 对话消息列表 [{"role": "system/user", "content": "..."}]
            temperature: 温度参数
            max_tokens: 最大生成 token 数

        Returns:
            生成的文本内容
        """
        async with httpx.AsyncClient(timeout=60) as client:
            response = await client.post(
                self.api_url,
                headers={
                    "Authorization": f"Bearer {self.api_key}",
                    "Content-Type": "application/json",
                },
                json={
                    "model": self.model,
                    "messages": messages,
                    "temperature": temperature,
                    "max_tokens": max_tokens,
                },
            )
            response.raise_for_status()
            data = response.json()
            return data["choices"][0]["message"]["content"]

    async def chat_stream(
        self,
        messages: List[Dict],
        temperature: float = 0.7,
        max_tokens: int = 2000,
    ) -> AsyncGenerator[str, None]:
        """
        流式调用 DeepSeek API，逐 chunk yield

        Args:
            messages: 对话消息列表
            temperature: 温度参数
            max_tokens: 最大生成 token 数

        Yields:
            生成的文本片段
        """
        async with httpx.AsyncClient(timeout=120) as client:
            async with client.stream(
                "POST",
                self.api_url,
                headers={
                    "Authorization": f"Bearer {self.api_key}",
                    "Content-Type": "application/json",
                },
                json={
                    "model": self.model,
                    "messages": messages,
                    "temperature": temperature,
                    "max_tokens": max_tokens,
                    "stream": True,
                },
            ) as response:
                response.raise_for_status()
                async for line in response.aiter_lines():
                    if line.startswith("data: "):
                        data_str = line[6:]
                        if data_str.strip() == "[DONE]":
                            break
                        try:
                            data = json.loads(data_str)
                            delta = data["choices"][0].get("delta", {})
                            content = delta.get("content", "")
                            if content:
                                yield content
                        except json.JSONDecodeError:
                            continue
