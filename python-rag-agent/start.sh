#!/bin/bash
cd "$(dirname "$0")"

echo "============================================"
echo "  Mdown RAG Agent - 启动脚本"
echo "============================================"
echo ""

# 检测虚拟环境
PYTHON_CMD=""
if [ -f "venv/bin/python" ]; then
    PYTHON_CMD="venv/bin/python"
    echo "[✓] 检测到虚拟环境: venv/"
elif [ -f "source/bin/python" ]; then
    PYTHON_CMD="source/bin/python"
    echo "[✓] 检测到虚拟环境: source/"
else
    echo "[✗] 未找到虚拟环境，请先创建:"
    echo "    python3 -m venv venv"
    echo "    source venv/bin/activate"
    echo "    pip install -r requirements.txt"
    exit 1
fi

# 激活虚拟环境
if [ -f "venv/bin/activate" ]; then
    source venv/bin/activate
elif [ -f "source/bin/activate" ]; then
    source source/bin/activate
fi

# 检查依赖
echo "[·] 检查依赖..."
if ! $PYTHON_CMD -c "import fastapi; import uvicorn; import faiss" 2>/dev/null; then
    echo "[!] 缺少依赖，正在安装..."
    $PYTHON_CMD -m pip install -r requirements.txt
    if [ $? -ne 0 ]; then
        echo "[✗] 依赖安装失败，请手动执行: pip install -r requirements.txt"
        exit 1
    fi
    echo "[✓] 依赖安装完成"
else
    echo "[✓] 依赖检查通过"
fi

# 读取端口配置
RAG_PORT=8085
if [ -f ".env" ]; then
    PORT_LINE=$(grep -i "RAG_PORT" .env 2>/dev/null | head -1)
    if [ -n "$PORT_LINE" ]; then
        RAG_PORT=$(echo "$PORT_LINE" | cut -d'=' -f2 | tr -d ' ')
    fi
fi

echo ""
echo "============================================"
echo "  RAG 服务启动中..."
echo "  端口: $RAG_PORT"
echo "  地址: http://localhost:$RAG_PORT"
echo "  健康检查: http://localhost:$RAG_PORT/health"
echo "============================================"
echo ""

$PYTHON_CMD main.py
