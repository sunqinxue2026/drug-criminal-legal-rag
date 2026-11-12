# 涉毒刑事法律咨询 RAG 系统

一个面向涉毒刑事法律风险分析与普法场景的专题法律咨询问答系统。项目采用 RAG（Retrieval-Augmented Generation，检索增强生成）架构，把法律知识、法律文书、专题案例和本地语料检索结果交给大语言模型，生成带有风险提示、法律依据、参考材料和行动建议的结构化回答。

> 本项目用于学习、研究和毕业设计实践。系统输出仅供普法和辅助分析参考，不构成正式法律意见，也不应直接作为生产法律服务系统使用。

## 功能概览

- 用户注册、登录和 JWT 登录态管理
- 涉毒刑事专题智能问答与多轮会话
- 法律知识、法律文书和专题案例的导入、预览、管理与索引重建
- 基于 BGE Embedding + FAISS 的本地向量检索
- 结合多源语料的 RAG 召回与结构化答案生成
- 图片、PDF、DOCX、TXT 等材料的文本提取和咨询辅助
- 案例浏览、详情查看和收藏
- 管理员用户、分类、案例、知识、文书、索引和行为记录管理
- 独立文件上传、访问和删除服务
- Swagger/OpenAPI 接口文档

## 系统架构

```text
浏览器
  |
  v
Vue 3 前端（5174）
  |
  v
Spring Boot 业务后端（8080 /api）
  |-- MySQL 8.0：用户、会话、案例、法律知识和文书数据
  |-- Flask RAG 服务（5000）
  |     |-- BGE 中文向量模型
  |     |-- FAISS 本地索引
  |     `-- 云端大语言模型 / 视觉模型 API
  `-- 文件服务（5001）
        `-- 本地附件存储
```

一次咨询的主要流程：

1. 用户在前端输入问题，可同时附带图片、文书或引用案例。
2. Spring Boot 保存会话消息并调用 Flask RAG 服务。
3. Flask 根据专题配置加载或构建 FAISS 索引。
4. 系统从法律知识、法律文书分块、专题案例和本地补充语料中召回相关内容。
5. 图片或附件先经过文本提取、内容识别等处理，再与问题一起进入生成流程。
6. 大语言模型生成结构化回答，后端保存结果并返回前端展示。

## 技术栈

| 模块 | 主要技术 |
| --- | --- |
| 前端 | Vue 3、TypeScript、Vite、Element Plus、Pinia、Vue Router、Axios、ECharts |
| 业务后端 | Java 17、Spring Boot 3.1.5、MyBatis-Plus 3.5.7、MySQL 8.0、JWT、BCrypt、SpringDoc |
| RAG 服务 | Python、Flask 3.1、sentence-transformers、BGE、FAISS、PyTorch、pypdf、python-docx |
| 文件服务 | Python、Flask、flask-cors、本地文件存储 |
| 测试 | Spring Boot Test、Python 测试、Playwright E2E |

## 项目结构

```text
RAG/
├── data/                  # 专题语料、抓取数据、评估数据和索引相关数据
├── docs/                  # 启动指南、数据库脚本、接口和设计文档
├── scripts/               # 语料抓取、清洗、导入、评估和索引重建脚本
├── tests/                 # 根目录脚本测试
├── tools/                 # 辅助工具
├── web-file/              # 文件上传与访问服务
├── web-flask/             # Flask RAG 算法服务
├── web-springboot/        # Spring Boot 业务后端
├── web-vue/               # Vue 前端
├── .gitignore
└── README.md
```

## 环境要求

- Java 17+
- Python 3.10+（建议 3.11 或 3.12；完整 RAG 依赖需使用受支持的 Python 版本）
- Node.js 18+
- npm
- MySQL 8.0+
- Windows 环境下建议使用 PowerShell
- RAG 问答需要能够访问配置的云端模型 API

## 快速开始

完整的 Windows 启动说明见 [`docs/START_GUIDE.md`](docs/START_GUIDE.md)。下面给出最短启动路径。

### 1. 初始化数据库

创建数据库并导入初始表结构和演示账号：

```powershell
cmd /c "mysql -u root -p < docs/init.sql"
```

也可以使用 MySQL 客户端执行 [`docs/init.sql`](docs/init.sql)。脚本默认创建数据库 `legal_rag`。

### 2. 配置 Spring Boot

配置数据库和服务地址。Spring Boot 默认不会自动读取 `.env`，请将以下变量设置到进程环境或 IDE 的运行配置中；也可修改 `application.yml` 中的配置值。主要变量如下：

```dotenv
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/legal_rag?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=你的数据库密码
JWT_SECRET=请替换为长度足够的随机密钥
ALGORITHM_SERVICE_BASE_URL=http://localhost:5000
FILE_SERVICE_BASE_URL=http://localhost:5001
INTERNAL_SERVICE_TOKEN=内部服务共享密钥
```

配置示例位于 [`web-springboot/.env.example`](web-springboot/.env.example)。项目也支持直接通过 `application.yml` 中的 Spring 环境变量占位符配置。

### 3. 启动文件服务

```powershell
cd web-file
python -m venv venv
.\venv\Scripts\Activate.ps1
pip install -r requirements.txt
python app.py
```

默认地址：`http://localhost:5001`

文件服务默认允许 `jpg`、`jpeg`、`png`、`gif`、`pdf`、`doc`、`docx` 和 `txt`，上传文件保存在本地 `storage` 目录。生产或非本机部署时应配置 `INTERNAL_SERVICE_TOKEN`，避免未授权写入。

### 4. 启动 Flask RAG 服务

```powershell
cd web-flask
python -m venv venv
.\venv\Scripts\Activate.ps1
pip install -r requirements.txt
Copy-Item .env.example .env
```

编辑 `.env`，至少填写：

```dotenv
LLM_API_KEY=你的云端模型API密钥
LLM_MODEL=qwen-plus
VISION_MODEL=qwen-vl-plus
BGE_MODEL_NAME=BAAI/bge-small-zh-v1.5
SPRINGBOOT_BASE_URL=http://localhost:8080/api
```

然后启动：

```powershell
python app.py
```

默认地址：`http://localhost:5000`

首次运行 BGE 模型或构建 FAISS 索引时可能需要额外下载模型并占用较多内存。模型、索引目录和超时参数可在 [`web-flask/config.py`](web-flask/config.py) 或 `.env` 中调整。

### 5. 启动 Spring Boot 后端

```powershell
cd web-springboot
.\mvnw.cmd spring-boot:run
```

如果本机已经安装 Maven，也可以执行：

```powershell
mvn spring-boot:run
```

默认地址：`http://localhost:8080/api`

Swagger UI：`http://localhost:8080/api/swagger-ui.html`

### 6. 启动 Vue 前端

```powershell
cd web-vue
npm install
npm run dev
```

默认地址：`http://localhost:5174`

开发环境下，Vite 会把前端 `/api` 请求代理到 `http://localhost:8080`。完整咨询链路需要同时运行 MySQL、Spring Boot、Flask RAG 和文件服务；只查看页面时可以只启动前端。

## 演示账号

执行数据库初始化脚本后，可以使用以下演示账号：

| 角色 | 用户名 | 密码 |
| --- | --- | --- |
| 管理员 | `admin` | `admin123` |
| 普通用户 | `user1` | `123456` |
| 普通用户 | `user2` | `123456` |

这些账号只适合本地演示。部署到其他环境后，请立即修改默认密码并替换 JWT 密钥。

## 服务地址与健康检查

| 服务 | 默认地址 | 检查接口 |
| --- | --- | --- |
| Vue 前端 | `http://localhost:5174` | 浏览器访问首页 |
| Spring Boot | `http://localhost:8080/api` | `GET /api/health` |
| Flask RAG | `http://localhost:5000` | `GET /health` |
| 文件服务 | `http://localhost:5001` | `GET /health` |
| Swagger | `http://localhost:8080/api/swagger-ui.html` | 浏览器访问 |

可以使用以下命令检查服务：

```powershell
curl http://localhost:8080/api/health
curl http://localhost:5000/health
curl http://localhost:5001/health
```

## 主要接口模块

Spring Boot 后端的主要业务接口包括：

- `/user`：登录、注册、用户信息和用户管理
- `/category`：法律领域分类
- `/item`：专题案例展示、导入和索引管理
- `/favorite`：案例收藏
- `/chat`：会话、消息历史和多轮咨询
- `/legal-knowledge`：法律知识导入、预览和分块管理
- `/legal-document`：法律文书导入、解析和分块索引管理
- `/search`：检索代理
- `/file`：文件上传和文件 URL 转发
- `/user-action`：行为记录与搜索日志
- `/llm/models`、`/vector/status`：模型和向量索引状态
- `/health`：后端健康检查

Flask RAG 服务的主要接口包括：

- `GET /health`：服务健康检查
- `POST /api/chat`：RAG 问答
- `POST /api/search`：语义检索
- `/api/vector/*`：向量索引创建、写入、搜索、重建和状态查看
- `/api/document/extract-text`：附件文本提取
- `/api/llm/*`：模型列表、模型测试和模型解析

更完整的接口说明见 [`docs/API文档.md`](docs/API文档.md) 和各服务目录下的 README。

## 数据与索引

- 数据库初始化：[`docs/init.sql`](docs/init.sql)
- 法律知识导入格式：[`docs/legal_knowledge_import_format.md`](docs/legal_knowledge_import_format.md)
- 法律文书字段设计：[`docs/legal_document_schema_v2.md`](docs/legal_document_schema_v2.md)
- 涉毒专题语料计划：[`docs/drug_vertical_corpus_plan.md`](docs/drug_vertical_corpus_plan.md)
- 语料评估说明：[`docs/RAG_EVALUATION.md`](docs/RAG_EVALUATION.md)
- 语料和索引脚本：[`scripts/`](scripts/)

常见脚本包括：

```powershell
# 生成专题语料报告
python scripts/report_drug_vertical_corpus.py

# 构建本地专题补充语料
python scripts/build_local_drug_vertical_supplement.py

# 重建专题向量索引
python scripts/rebuild_drug_vertical_index.py

# 运行 RAG 支持脚本测试
pytest tests
```

具体参数和数据前置条件以脚本源码及 `docs/` 下的说明为准。

## 前端命令

在 `web-vue` 目录执行：

```powershell
npm run dev        # 开发服务器
npm run build      # 生产构建
npm run preview    # 预览构建结果
npm run typecheck  # TypeScript 类型检查
npm run test:e2e   # Playwright 端到端测试
npm run verify     # 类型检查并构建
```

## 后端命令

在 `web-springboot` 目录执行：

```powershell
.\mvnw.cmd spring-boot:run  # 启动服务
.\mvnw.cmd test             # 运行测试
.\mvnw.cmd verify           # 测试、打包和覆盖率报告
```

## 配置与安全说明

- 不要把真实 API Key、数据库密码、JWT 密钥或内部服务令牌提交到 Git。
- `.env`、`.env.local` 和本地虚拟环境只用于开发，不应上传。
- 生产环境必须替换默认的 `JWT_SECRET`、演示账号密码和文件服务令牌。
- 文件服务是本地存储实现，适合开发和演示；生产部署时应评估对象存储、访问控制、备份和病毒扫描方案。
- 当前 RAG 结果依赖语料质量、索引状态、模型配置和下游服务可用性。
- 法律问答结果应由专业人员结合原始法律文本和案件事实复核。

## 文档索引

- [`docs/START_GUIDE.md`](docs/START_GUIDE.md)：Windows 启动指南
- [`docs/API文档.md`](docs/API文档.md)：接口说明
- [`docs/数据库设计文档.md`](docs/数据库设计文档.md)：数据库和检索设计
- [`docs/legal_document_schema_v2.md`](docs/legal_document_schema_v2.md)：法律文书字段设计
- [`docs/legal_rag_optimization_plan.md`](docs/legal_rag_optimization_plan.md)：RAG 优化计划
- [`docs/drug_vertical_corpus_plan.md`](docs/drug_vertical_corpus_plan.md)：涉毒专题语料计划
- [`docs/legal_knowledge_import_format.md`](docs/legal_knowledge_import_format.md)：法律知识导入格式

## 许可证

当前项目用于学习、研究和毕业设计实践。仓库未单独声明开源许可证；如果计划公开发布或允许他人再分发，请补充明确的 LICENSE 文件和第三方数据授权说明。
