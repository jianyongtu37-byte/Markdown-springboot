"""
MarkdownChunker 分块器测试
"""

import pytest
from core.chunker import MarkdownChunker


class TestMarkdownChunker:
    """分块器单元测试"""

    def setup_method(self):
        self.chunker = MarkdownChunker(chunk_size=200, overlap=30)

    def test_empty_content(self):
        """空内容返回空列表"""
        assert self.chunker.chunk("") == []
        assert self.chunker.chunk("   ") == []
        assert self.chunker.chunk(None) == []

    def test_single_section(self):
        """单个章节不拆分"""
        content = "# 标题\n\n这是一段简短的内容。"
        chunks = self.chunker.chunk(content)
        assert len(chunks) == 1
        assert chunks[0]["header"] == "标题"
        assert "这是一段简短的内容" in chunks[0]["content"]

    def test_split_by_headers(self):
        """按标题分割多个章节"""
        content = """# 第一章

这是第一章的内容。

# 第二章

这是第二章的内容。

# 第三章

这是第三章的内容。"""
        chunks = self.chunker.chunk(content)
        assert len(chunks) >= 3
        headers = [c["header"] for c in chunks]
        assert "第一章" in headers
        assert "第二章" in headers
        assert "第三章" in headers

    def test_chunk_index_assigned(self):
        """每个 chunk 被分配正确的索引"""
        content = """# A

内容 A

# B

内容 B

# C

内容 C"""
        chunks = self.chunker.chunk(content)
        for i, chunk in enumerate(chunks):
            assert chunk["index"] == i

    def test_metadata_attached(self):
        """元数据被正确附加到每个 chunk"""
        content = "# 标题\n\n一些内容。"
        metadata = {"article_id": 42}
        chunks = self.chunker.chunk(content, metadata=metadata)
        assert len(chunks) >= 1
        for chunk in chunks:
            assert chunk["metadata"]["article_id"] == 42

    def test_large_section_split_by_paragraphs(self):
        """超大章节按段落进一步分割"""
        # 创建一个超过 chunk_size 的章节
        paragraphs = ["段落" + str(i) + "。" * 20 for i in range(10)]
        content = "# 大章节\n\n" + "\n\n".join(paragraphs)
        chunker = MarkdownChunker(chunk_size=100, overlap=0)
        chunks = chunker.chunk(content)
        assert len(chunks) > 1

    def test_overlap_added(self):
        """chunk 之间添加了重叠内容"""
        content = """# 第一部分

这是第一部分的详细内容，包含了很多重要的信息。

# 第二部分

这是第二部分的详细内容，也包含了很多重要的信息。"""
        chunker = MarkdownChunker(chunk_size=50, overlap=20)
        chunks = chunker.chunk(content)
        if len(chunks) > 1:
            # 第二个 chunk 应该包含 "..." 标记表示重叠
            assert "..." in chunks[1]["content"] or len(chunks[1]["content"]) > len("这是第二部分的详细内容")

    def test_no_overlap_when_single_chunk(self):
        """只有一个 chunk 时不添加重叠"""
        content = "# 标题\n\n简短内容。"
        chunks = self.chunker.chunk(content)
        assert len(chunks) == 1
        assert "..." not in chunks[0]["content"]

    def test_sub_headers_preserved(self):
        """子标题（##、###）被正确识别"""
        content = """# 一级标题

## 二级标题 A

内容 A

## 二级标题 B

内容 B"""
        chunks = self.chunker.chunk(content)
        headers = [c["header"] for c in chunks]
        assert "二级标题 A" in headers
        assert "二级标题 B" in headers


class TestPreprocessing:
    """预处理与去噪测试"""

    def setup_method(self):
        self.chunker = MarkdownChunker(chunk_size=200, overlap=0)

    # ── P0: YAML frontmatter ──────────────────────────────────

    def test_strip_frontmatter_basic(self):
        """剥离标准 YAML frontmatter"""
        content = """---
title: 测试文档
date: 2025-01-01
tags: [rag, ai]
---
# 正文标题

这是正文内容。"""
        cleaned = self.chunker._preprocess(content)
        assert "title:" not in cleaned
        assert "tags:" not in cleaned
        assert "# 正文标题" in cleaned
        assert "这是正文内容" in cleaned

    def test_strip_frontmatter_none(self):
        """无 frontmatter 的文档不受影响"""
        content = "# 直接开始的标题\n\n正文内容。"
        cleaned = self.chunker._preprocess(content)
        assert "# 直接开始的标题" in cleaned
        assert "正文内容" in cleaned

    # ── P0: HTML 标签 ─────────────────────────────────────────

    def test_remove_html_tags(self):
        """移除 HTML 标签，保留内部文本"""
        content = '这段<font color="red">非常重要</font>的内容<br/>换行'
        cleaned = self.chunker._preprocess(content)
        assert "<font" not in cleaned
        assert "</font>" not in cleaned
        assert "<br/>" not in cleaned
        assert "非常重要" in cleaned

    # ── P1: 图片语法 ──────────────────────────────────────────

    def test_clean_images_with_alt(self):
        """清洗图片语法，保留 alt 文本"""
        content = "这是文本 ![系统架构图](https://example.com/img.png) 继续文本"
        cleaned = self.chunker._preprocess(content)
        assert "系统架构图" in cleaned
        assert "https://example.com/img.png" not in cleaned
        assert "![" not in cleaned

    def test_clean_images_without_alt(self):
        """无 alt 文本的图片被完全移除"""
        content = "文本 ![](https://example.com/img.png) 继续"
        cleaned = self.chunker._preprocess(content)
        assert "https://example.com/img.png" not in cleaned
        # alt 为空，替换后该位置为空字符串，但周围文本保持
        assert "文本" in cleaned
        assert "继续" in cleaned

    # ── P1: 链接语法 ──────────────────────────────────────────

    def test_clean_links(self):
        """清洗链接语法，保留显示文本"""
        content = "参考 [官方文档](https://docs.example.com/guide) 了解更多"
        cleaned = self.chunker._preprocess(content)
        assert "官方文档" in cleaned
        assert "https://docs.example.com/guide" not in cleaned
        assert "](" not in cleaned

    def test_clean_multiple_links(self):
        """多个链接全部清洗"""
        content = "[链接A](urlA) 和 [链接B](urlB)"
        cleaned = self.chunker._preprocess(content)
        assert "链接A" in cleaned
        assert "链接B" in cleaned
        assert "urlA" not in cleaned
        assert "urlB" not in cleaned

    # ── P1: 水平分割线 ────────────────────────────────────────

    def test_remove_horizontal_rules(self):
        """移除水平分割线"""
        content = """# 章节一

内容一

---

# 章节二

内容二"""
        cleaned = self.chunker._preprocess(content)
        assert "---" not in cleaned

    def test_hr_not_remove_inline_dash(self):
        """不会误删正文中的破折号"""
        content = "这是正文——包含破折号——的内容"
        cleaned = self.chunker._preprocess(content)
        assert "——" in cleaned

    # ── P2: 空白规范化 ────────────────────────────────────────

    def test_normalize_multi_newlines(self):
        """4 个连续空行压缩为 2 个"""
        content = "段落一\n\n\n\n\n段落二"
        cleaned = self.chunker._preprocess(content)
        # 检查最多 2 个连续空行
        assert "\n\n\n" not in cleaned

    # ── 端到端：预处理 + 分块 ─────────────────────────────────

    def test_end_to_end_noisy_document(self):
        """高噪音文档端到端测试"""
        content = """---
title: 测试
---
# 概述 ![图标](icon.png)

这是**重要**内容，详见 [官方文档](https://docs.example.com)。

<div class="note">提示：请备份数据</div>

---

## 详细说明

这里是详细的技术说明。"""
        chunks = self.chunker.chunk(content)
        # 至少产出 2 个 chunk
        assert len(chunks) >= 2
        # 所有 chunk 都不应包含噪音
        for chunk in chunks:
            text = chunk["content"]
            assert "title:" not in text  # frontmatter 已剥离
            assert "<div" not in text    # HTML 已移除
            assert "https://" not in text  # URL 已清洗
            assert "](http" not in text    # 链接语法已清洗
            assert "![" not in text        # 图片语法已清洗
        # 语义内容应保留
        all_text = " ".join(c["content"] for c in chunks)
        assert "概述" in all_text
        assert "图标" in all_text  # 图片 alt 文本保留
        assert "重要" in all_text
        assert "官方文档" in all_text  # 链接显示文本保留
        assert "提示" in all_text and "备份数据" in all_text  # HTML 内文本保留
        assert "详细说明" in all_text


class TestMarkdownChunkerEdgeCases:
    """边界情况测试"""

    def test_only_headers_no_content(self):
        """只有标题没有内容"""
        content = "# 标题1\n\n# 标题2\n\n# 标题3"
        chunker = MarkdownChunker(chunk_size=200, overlap=0)
        chunks = chunker.chunk(content)
        # 应该能处理，不崩溃
        assert isinstance(chunks, list)

    def test_very_long_paragraph(self):
        """单个超长段落"""
        long_text = "这是一段很长的文字。" * 100
        content = f"# 标题\n\n{long_text}"
        chunker = MarkdownChunker(chunk_size=200, overlap=0)
        chunks = chunker.chunk(content)
        assert len(chunks) >= 1

    def test_code_blocks_preserved(self):
        """代码块内容被保留"""
        content = """# 代码示例

```python
def hello():
    print("Hello, World!")
```

这是代码说明。"""
        chunker = MarkdownChunker(chunk_size=200, overlap=0)
        chunks = chunker.chunk(content)
        code_found = any("hello" in c["content"] for c in chunks)
        assert code_found
