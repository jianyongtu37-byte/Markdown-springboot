"""
Markdown 文章分块器
按标题层级分块，保持语义完整性
包含文档预处理与去噪层，在分块前清洗 Markdown 噪音
"""

import re
from typing import List, Tuple
from config import Config


class MarkdownChunker:
    """
    Markdown 感知的分块器
    - 预处理去噪：剥离 YAML frontmatter、HTML 标签、清洗链接/图片语法
    - 优先按 # / ## / ### 标题分割
    - 同一章节内按段落分割
    - 保留 chunk 之间的重叠（overlap）以保持上下文
    """

    # ── 预处理正则（编译一次，复用） ──────────────────────────────

    # YAML frontmatter：文档开头被 --- 包裹的元数据块
    _FRONTMATTER_RE = re.compile(r"^---\s*\n.*?\n---\s*\n", re.DOTALL)

    # HTML 标签（保留标签内的文本）
    _HTML_TAG_RE = re.compile(r"<[^>]+>")

    # Markdown 图片：![alt](url) → 保留 alt 文本
    _IMAGE_RE = re.compile(r"!\[([^\]]*)\]\([^)]+\)")

    # Markdown 链接：[text](url) → 保留 text
    _LINK_RE = re.compile(r"\[([^\]]*)\]\([^)]+\)")

    # 水平分割线（单独成行的 --- / *** / ___）
    _HR_RE = re.compile(r"^[-*_]{3,}\s*$", re.MULTILINE)

    # 连续 3 个以上的空行 → 压缩为 2 个空行
    _MULTI_NEWLINE_RE = re.compile(r"\n{3,}")

    # 行内代码（反引号包裹）的空白字符清理
    _INLINE_CODE_RE = re.compile(r"`([^`]+)`")

    def __init__(self, chunk_size: int = None, overlap: int = None):
        self.chunk_size = chunk_size or Config.CHUNK_SIZE
        self.overlap = overlap or Config.CHUNK_OVERLAP

    # ── 公开接口 ────────────────────────────────────────────────

    def chunk(self, content: str, metadata: dict = None) -> List[dict]:
        """
        将 Markdown 文章分块（含预处理去噪）

        Args:
            content: Markdown 文章原始内容
            metadata: 附加到每个 chunk 的元数据

        Returns:
            [{"content": str, "index": int, "header": str, "metadata": dict}, ...]
        """
        if not content or not content.strip():
            return []

        # 0. 预处理去噪
        cleaned = self._preprocess(content)

        # 1. 按标题分割成 sections
        sections = self._split_by_headers(cleaned)

        # 2. 对过大的 section 进一步按段落分割
        chunks = []
        for section in sections:
            if len(section["content"]) <= self.chunk_size:
                chunks.append(section)
            else:
                sub_chunks = self._split_by_paragraphs(
                    section["content"], section["header"]
                )
                chunks.extend(sub_chunks)

        # 3. 添加索引和元数据
        for i, chunk in enumerate(chunks):
            chunk["index"] = i
            if metadata:
                chunk["metadata"] = {**metadata, **chunk.get("metadata", {})}

        # 4. 添加重叠
        chunks = self._add_overlap(chunks)

        return chunks

    # ── 预处理管道 ──────────────────────────────────────────────

    def _preprocess(self, content: str) -> str:
        """
        文档预处理管道，按优先级依次清洗：

        P0  剥离 YAML frontmatter       — 元数据不参与语义检索
        P0  移除 HTML 标签               — 纯噪音
        P1  清洗图片语法 → 保留 alt 文本  — URL 无意义，alt 可保留
        P1  清洗链接语法 → 保留显示文本   — URL 无意义，文本可保留
        P1  移除水平分割线               — 无语义价值
        P2  压缩连续空行                 — 减少 token 浪费，不影响语义
        """
        # P0: 剥离 YAML frontmatter
        content = self._strip_frontmatter(content)

        # P0: 移除 HTML 标签
        content = self._remove_html_tags(content)

        # P1: 清洗图片语法 ![alt](url) → alt
        content = self._clean_images(content)

        # P1: 清洗链接语法 [text](url) → text
        content = self._clean_links(content)

        # P1: 移除水平分割线
        content = self._remove_horizontal_rules(content)

        # P2: 压缩连续空行（3+ → 2）
        content = self._normalize_whitespace(content)

        return content

    # ── P0 清洗：YAML frontmatter ───────────────────────────────

    def _strip_frontmatter(self, content: str) -> str:
        """剥离文档开头的 YAML frontmatter 块。

        示例：
            ---
            title: 我的文档
            date: 2025-01-01
            ---
            正文开始...
            →
            正文开始...
        """
        return self._FRONTMATTER_RE.sub("", content).lstrip()

    # ── P0 清洗：HTML 标签 ─────────────────────────────────────

    def _remove_html_tags(self, content: str) -> str:
        """移除所有 HTML 标签，保留标签内部的文本内容。

        示例：
            <font color="red">重要</font> → 重要
            <br/> → （移除）
        """
        return self._HTML_TAG_RE.sub("", content)

    # ── P1 清洗：图片语法 ──────────────────────────────────────

    def _clean_images(self, content: str) -> str:
        """清洗 Markdown 图片语法，保留 alt 文本。

        示例：
            ![架构图](https://xxx.com/img.png) → 架构图
            ![](https://xxx.com/img.png)      → （移除，无 alt 文本）
        """
        return self._IMAGE_RE.sub(r"\1", content)

    # ── P1 清洗：链接语法 ──────────────────────────────────────

    def _clean_links(self, content: str) -> str:
        """清洗 Markdown 链接语法，保留显示文本。

        示例：
            [官方文档](https://docs.xxx.com) → 官方文档
            [](https://xxx.com)              → （移除，无显示文本）
        """
        return self._LINK_RE.sub(r"\1", content)

    # ── P1 清洗：水平分割线 ────────────────────────────────────

    def _remove_horizontal_rules(self, content: str) -> str:
        """移除单独成行的水平分割线（--- / *** / ___）。

        注意：不会误删 YAML frontmatter 的 --- 分隔符，
        因为 frontmatter 已在上游被剥离。
        """
        return self._HR_RE.sub("", content)

    # ── P2 清洗：空白规范化 ────────────────────────────────────

    def _normalize_whitespace(self, content: str) -> str:
        """压缩连续空行、清理行尾空白。

        示例：
            "段落一\n\n\n\n段落二" → "段落一\n\n段落二"
        """
        # 压缩连续空行：3+ → 2
        content = self._MULTI_NEWLINE_RE.sub("\n\n", content)
        # 清理每行末尾的空白字符
        content = "\n".join(line.rstrip() for line in content.split("\n"))
        return content

    def _split_by_headers(self, content: str) -> List[dict]:
        """按 Markdown 标题分割"""
        header_pattern = r"^(#{1,4})\s+(.+)$"

        sections = []
        current_header = ""
        current_content = []

        for line in content.split("\n"):
            match = re.match(header_pattern, line, re.MULTILINE)
            if match:
                # 保存上一个 section
                if current_content:
                    text = "\n".join(current_content).strip()
                    if text:
                        sections.append(
                            {"header": current_header, "content": text}
                        )
                current_header = match.group(2)
                current_content = [line]
            else:
                current_content.append(line)

        # 保存最后一个 section
        if current_content:
            text = "\n".join(current_content).strip()
            if text:
                sections.append({"header": current_header, "content": text})

        return sections

    def _split_by_paragraphs(self, content: str, header: str) -> List[dict]:
        """按段落分割过大的 section"""
        paragraphs = content.split("\n\n")
        chunks = []
        current_chunk = ""

        for para in paragraphs:
            if len(current_chunk) + len(para) > self.chunk_size:
                if current_chunk:
                    chunks.append(
                        {
                            "header": header,
                            "content": current_chunk.strip(),
                            "metadata": {"split_reason": "paragraph_overflow"},
                        }
                    )
                current_chunk = para
            else:
                current_chunk += "\n\n" + para if current_chunk else para

        if current_chunk.strip():
            chunks.append(
                {
                    "header": header,
                    "content": current_chunk.strip(),
                    "metadata": {"split_reason": "paragraph_overflow"},
                }
            )

        return chunks

    def _add_overlap(self, chunks: List[dict]) -> List[dict]:
        """在 chunk 之间添加重叠内容"""
        if len(chunks) <= 1 or self.overlap == 0:
            return chunks

        for i in range(1, len(chunks)):
            prev_content = chunks[i - 1]["content"]
            # 取上一个 chunk 末尾的 overlap 字符
            overlap_text = prev_content[-self.overlap :]
            chunks[i]["content"] = overlap_text + " ... " + chunks[i]["content"]

        return chunks
