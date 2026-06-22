@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo ============================================
echo   Mdown RAG Agent - 启动脚本
echo ============================================
echo.

REM 检测虚拟环境
set "PYTHON_CMD="
if exist "venv\Scripts\python.exe" (
    set "PYTHON_CMD=venv\Scripts\python.exe"
    echo [✓] 检测到虚拟环境: venv\
) else if exist "source\Scripts\python.exe" (
    set "PYTHON_CMD=source\Scripts\python.exe"
    echo [✓] 检测到虚拟环境: source\
) else (
    echo [✗] 未找到虚拟环境，请先创建:
    echo     python -m venv venv
    echo     venv\Scripts\activate
    echo     pip install -r requirements.txt
    pause
    exit /b 1
)

REM 激活虚拟环境
if exist "venv\Scripts\activate.bat" (
    call venv\Scripts\activate.bat
) else if exist "source\Scripts\activate.bat" (
    call source\Scripts\activate.bat
)

REM 检查依赖
echo [·] 检查依赖...
%PYTHON_CMD% -c "import fastapi; import uvicorn; import faiss" >nul 2>&1
if errorlevel 1 (
    echo [!] 缺少依赖，正在安装...
    %PYTHON_CMD% -m pip install -r requirements.txt
    if errorlevel 1 (
        echo [✗] 依赖安装失败，请手动执行: pip install -r requirements.txt
        pause
        exit /b 1
    )
    echo [✓] 依赖安装完成
) else (
    echo [✓] 依赖检查通过
)

REM 读取端口配置
set "RAG_PORT=8085"
for /f "tokens=1,* delims==" %%a in ('findstr /i "RAG_PORT" .env 2^>nul') do (
    set "RAG_PORT=%%b"
)

echo.
echo ============================================
echo   RAG 服务启动中...
echo   端口: %RAG_PORT%
echo   地址: http://localhost:%RAG_PORT%
echo   健康检查: http://localhost:%RAG_PORT%/health
echo ============================================
echo.

%PYTHON_CMD% main.py
pause
