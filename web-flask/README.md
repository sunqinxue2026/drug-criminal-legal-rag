# Flask RAG 算法服务

本目录是项目中的 Flask 算法服务，负责向量检索、RAG 问答、图片分析、文书文本提取以及索引管理，不直接承担用户、权限和业务数据管理。

## 模块职责

- 接收 Spring Boot 转发的咨询请求
- 基于 BGE 生成文本向量
- 基于 FAISS 维护本地检索索引
- 从 Spring Boot 拉取法律知识分块、法律文书分块和专题案例数据
- 调用云端大模型生成结构化回答
- 提取图片、PDF、DOCX、DOC、TXT、HTML、JSON 等材料中的可用文本

## 运行环境

- Python 3.8+
- 建议 Windows 环境下运行
- 需要联网访问云端模型 API

## 主要依赖

- Flask 3.1.0
- flask-cors 5.0.0
- sentence-transformers 5.3.0
- faiss-cpu 1.11.0.post1
- pypdf 5.4.0
- python-docx 1.2.0

依赖文件见 [requirements.txt](requirements.txt)。

## 启动方式

```powershell
cd D:\CODE\RAG\web-flask
python -m venv venv
.\venv\Scripts\Activate.ps1
pip install -r requirements.txt
python app.py
```

默认监听：

```text
http://localhost:5000
```

## 核心接口

### 健康检查

- `GET /health`
- `GET /ping`

### 问答与检索

- `POST /api/chat`：RAG 问答
- `POST /api/search`：语义检索

### 模型与状态

- `GET /api/llm/test`
- `GET /api/llm/models`
- `POST /api/llm/resolve-model`
- `GET /api/vector/status`

### 向量索引

- `GET /api/vector/indexes`
- `POST /api/vector/create`
- `POST /api/vector/index`
- `POST /api/vector/index/batch`
- `POST /api/vector/search`
- `POST /api/vector/rebuild`
- `GET /api/vector/info/{index_name}`
- `DELETE /api/vector/index/{index_name}`

### 文档处理

- `GET /api/document/test`
- `POST /api/document/extract-text`

## 配置项

主要配置文件：

```text
D:\CODE\RAG\web-flask\config.py
```

常用配置项：

- `LLM_API_KEY`：云端大模型 API Key
- `LLM_MODEL`：默认文本模型，默认 `qwen-turbo`
- `VISION_MODEL`：默认视觉模型，默认 `qwen-vl-plus`
- `BGE_MODEL_NAME`：默认向量模型，默认 `BAAI/bge-small-zh-v1.5`
- `INDEX_DIR`：本地 FAISS 索引目录
- `SPRINGBOOT_BASE_URL`：Spring Boot 后端地址，默认 `http://localhost:8080/api`

## 服务依赖关系

本服务本身可以单独启动，但完整功能依赖以下外部条件：

- Spring Boot 服务已启动，便于读取法律知识、文书分块和专题案例数据
- 已配置可用的云端模型 API Key
- 已准备好专题语料或已在业务后端完成数据导入

如果缺少 Spring Boot 数据源，部分专题索引无法完整构建；如果缺少云端模型配置，问答能力将不可用或受限。

## 当前说明

- 当前实现重点服务于“涉毒刑事法律风险分析与普法”垂直场景。
- 本服务已经集成 `faiss-cpu`，不是“待安装 FAISS”的占位版本。
- `.doc` 解析在 Windows 下依赖本机 Microsoft Word COM 能力；如环境不满足，建议优先上传 `docx`、`pdf`、`txt`。

