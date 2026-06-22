"""
共享测试 fixtures
"""

import os
import sys
import pytest
import asyncio
import tempfile
import shutil

# 将项目根目录加入 Python 路径
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


@pytest.fixture(scope="session")
def event_loop():
    """创建全局事件循环"""
    loop = asyncio.new_event_loop()
    yield loop
    loop.close()


@pytest.fixture
def tmp_faiss_path(tmp_path):
    """提供临时 FAISS 索引目录"""
    faiss_dir = str(tmp_path / "faiss_indexes")
    os.makedirs(faiss_dir, exist_ok=True)
    return faiss_dir


@pytest.fixture
def sample_markdown():
    """示例 Markdown 文章"""
    return """# Spring Boot 入门

## 什么是 Spring Boot

Spring Boot 是一个基于 Spring 框架的快速开发工具。
它简化了 Spring 应用的初始搭建和开发过程。

### 核心特性

- 自动配置
- 起步依赖
- 内嵌服务器
- Actuator 监控

## 快速开始

### 创建项目

使用 Spring Initializr 创建项目，选择需要的依赖。

### 编写代码

```java
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

## 配置文件

Spring Boot 支持 application.properties 和 application.yml 两种格式。

### 常用配置

```yaml
server:
  port: 8080
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/test
```

## 数据访问

### JPA 集成

Spring Boot 对 JPA 提供了开箱即用的支持。

### MyBatis 集成

MyBatis 是另一个流行的 ORM 框架，Spring Boot 同样提供了良好的支持。

## 总结

Spring Boot 大大简化了 Java Web 应用的开发流程。
"""


@pytest.fixture
def sample_chunks():
    """示例分块数据"""
    return [
        {"content": "Spring Boot 是一个快速开发框架", "index": 0, "header": "概述"},
        {"content": "它提供了自动配置功能", "index": 1, "header": "特性"},
        {"content": "使用 starter 依赖简化配置", "index": 2, "header": "依赖管理"},
    ]


@pytest.fixture
def sample_messages():
    """示例对话历史"""
    return [
        {"role": "user", "content": "什么是微服务架构？"},
        {"role": "assistant", "content": "微服务架构是一种将应用拆分为小型服务的架构风格。"},
        {"role": "user", "content": "它有什么优缺点？"},
        {"role": "assistant", "content": "优点包括独立部署、技术栈灵活；缺点包括分布式复杂性增加。"},
    ]
