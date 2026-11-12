# 涉毒刑事法律咨询系统 API 文档

## 基础信息

- 基础 URL：`http://localhost:8080/api`
- 认证方式：`Authorization: Bearer <token>`
- 返回格式：统一使用 `Result<T>` 包装
- 权威入口：运行后优先以 Swagger 为准 `http://localhost:8080/api/swagger-ui.html`

## 通用响应格式

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

## 模块总览

| 模块 | 路径前缀 | 说明 |
| --- | --- | --- |
| 用户 | `/user` | 登录、注册、用户管理、个人信息 |
| 分类 | `/category` | 专题分类查询与管理 |
| 专题案例 | `/item` | 前台案例展示、后台导入与重建索引 |
| 收藏 | `/favorite` | 用户收藏列表、状态、增删 |
| 聊天 | `/chat` | 会话、消息、多轮咨询 |
| 法律知识 | `/legal-knowledge` | 规范知识导入、预览、分页、索引块查看 |
| 法律文书 | `/legal-document` | 文书导入、官方文书同步、分块索引管理 |
| 搜索 | `/search` | 检索代理接口 |
| 文件 | `/file` | 文件上传与 URL 转发 |
| 系统状态 | `/health`、`/llm/models`、`/vector/status` | 健康检查、模型状态、向量状态 |
| 行为日志 | `/user-action` | 后台行为统计与搜索日志 |

## 权限说明

- 无需登录：`/user/login`、`/user/register`、`/category/list`、`/item/page`、`/search`、`/health`
- 登录后按用户身份访问：`/user/info/{id}`、`/favorite/*`、`/chat/*`
- 管理员访问：`/user/list`、`/category` 写接口、`/item` 写接口、`/legal-knowledge/*`、`/legal-document/*`、`/user-action/*`

## 1. 用户模块

### 1.1 登录

**接口**：`POST /user/login`

**请求体**

```json
{
  "username": "admin",
  "password": "admin123"
}
```

**响应示例**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "user": {
      "id": 1,
      "username": "admin",
      "realName": "管理员",
      "role": "admin"
    }
  }
}
```

### 1.2 注册

**接口**：`POST /user/register`

```json
{
  "username": "user3",
  "password": "123456",
  "realName": "测试用户",
  "phone": "13800138000",
  "email": "user3@example.com"
}
```

### 1.3 获取个人信息

**接口**：`GET /user/info/{id}`

说明：只允许本人或管理员访问。

### 1.4 管理员用户接口

- `GET /user/list`
- `POST /user`
- `PUT /user/{id}`
- `PUT /user/{id}/status`
- `DELETE /user/{id}`
- `PUT /user/update`

## 2. 分类与专题案例

### 2.1 分类列表

**接口**：`GET /category/list`

### 2.2 专题案例分页

**接口**：`GET /item/page`

**查询参数**

- `pageNum`：默认 `1`
- `pageSize`：默认 `10`
- `categoryId`：可选
- `keyword`：可选
- `tags`：可选

**响应示例**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [
      {
        "id": 5,
        "title": "家属收到涉毒拘留通知后的应对案例",
        "summary": "围绕拘留通知、律师会见和取保候审条件的专题案例。",
        "categoryId": 2,
        "viewCount": 36,
        "favoriteCount": 8
      }
    ],
    "total": 5,
    "size": 10,
    "current": 1,
    "pages": 1
  }
}
```

### 2.3 专题案例详情

**接口**：`GET /item/{id}`

可选参数：`userId`

说明：带 `userId` 时会校验当前登录用户是否有权查看该用户上下文关联的数据。

### 2.4 专题案例后台接口

- `POST /item`
- `PUT /item`
- `DELETE /item/{id}`
- `POST /item/preview/file`
- `POST /item/import/file`
- `POST /item/import/files`
- `POST /item/reindex`
- `POST /item/sync/official`

## 3. 收藏模块

- `GET /favorite/list?userId=1`
- `GET /favorite/status?userId=1&itemId=5`
- `POST /favorite?userId=1&itemId=5`
- `DELETE /favorite/{itemId}?userId=1`

## 4. 聊天模块

### 4.1 获取会话列表

**接口**：`GET /chat/sessions/{userId}`

### 4.2 创建会话

**接口**：`POST /chat/session`

**参数**

- `userId`
- `title`

### 4.3 获取会话消息

**接口**：`GET /chat/messages/{sessionId}`

### 4.4 发送消息

**接口**：`POST /chat/send`

**请求体示例**

```json
{
  "sessionId": 12,
  "message": "家属刚收到涉毒拘留通知，现在第一步应该做什么？",
  "model": "qwen-turbo",
  "smartConsulting": true,
  "profile": "drug_crime",
  "history": [
    {
      "role": "user",
      "content": "警方说涉嫌容留他人吸毒。",
      "createTime": "2026-03-28 20:00:00"
    }
  ],
  "attachedCases": [
    {
      "id": 5,
      "title": "家属收到涉毒拘留通知后的应对案例",
      "summary": "涉及拘留通知、会见与法律援助。"
    }
  ],
  "attachedDocuments": [
    {
      "name": "拘留通知书.jpg",
      "url": "http://localhost:5001/file/default/notice.jpg",
      "preview": "通知书照片",
      "extractedText": "拘留通知书……",
      "charCount": 128,
      "statusLabel": "已解析"
    }
  ]
}
```

**响应重点字段**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 88,
    "sessionId": 12,
    "role": "assistant",
    "content": "初步结论：...\n法律依据：...\n风险提示：...\n行动建议：...",
    "structuredAnswer": {
      "conclusion": "……",
      "basis": "……",
      "risk": "……",
      "advice": "……"
    },
    "references": [
      {
        "title": "中华人民共和国刑事诉讼法（节录）",
        "source_name": "中国政府网",
        "source_url": "https://www.gov.cn/...",
        "issuer": "全国人大常委会",
        "effective_date": "2023-01-01",
        "current_status": "现行有效"
      }
    ],
    "disclaimer": "本回答仅用于普法与学习参考。",
    "scopeStatement": "当前系统聚焦涉毒刑事风险分析与普法。",
    "privacyNotice": "请勿上传与问题无关的个人敏感信息。"
  }
}
```

### 4.5 其他聊天接口

- `GET /chat/stats`：管理员统计
- `DELETE /chat/session/{id}`：删除会话

## 5. 法律知识管理

以下接口均需管理员权限。

### 5.1 分页查询

**接口**：`GET /legal-knowledge/page`

**查询参数**

- `pageNum`
- `pageSize`
- `categoryId`
- `keyword`
- `knowledgeType`
- `businessDomain`

### 5.2 详情、删除与索引块

- `GET /legal-knowledge/{id}`
- `DELETE /legal-knowledge/{id}?rebuildIndex=true`
- `GET /legal-knowledge/index/chunks?pageNum=1&pageSize=200`

### 5.3 导入与预览

- `POST /legal-knowledge/import/file`
- `POST /legal-knowledge/preview/record`
- `POST /legal-knowledge/import/record`
- `POST /legal-knowledge/reindex`

**文件导入说明**

- 上传字段：`file`
- 可选参数：`categoryId`、`rebuildIndex`
- 推荐 JSON 结构见 [legal_knowledge_import_format.md](./legal_knowledge_import_format.md)

## 6. 法律文书管理

以下接口均需管理员权限。

### 6.1 分页查询

**接口**：`GET /legal-document/page`

**查询参数**

- `pageNum`
- `pageSize`
- `categoryId`
- `keyword`
- `documentType`
- `caseType`
- `procedureDomain`

### 6.2 详情、删除与索引块

- `GET /legal-document/{id}`
- `DELETE /legal-document/{id}?rebuildIndex=true`
- `GET /legal-document/index/chunks?pageNum=1&pageSize=200`

### 6.3 导入、预览与重建

- `POST /legal-document/preview/file`
- `POST /legal-document/import/file`
- `POST /legal-document/import/files`
- `POST /legal-document/import/official`
- `POST /legal-document/reindex`

## 7. 文件、搜索与状态接口

### 7.1 文件

- `POST /file/upload`
- `GET /file/url?filename=xxx.pdf`

### 7.2 搜索

**接口**：`GET /search`

**参数**

- `query`
- `topK`，默认 `5`

说明：该接口由 Spring Boot 转发至 Flask 算法服务，返回检索结果。

### 7.3 系统状态

- `GET /health`
- `GET /llm/models`
- `GET /vector/status`

## 8. 用户行为日志

以下统计接口需管理员权限：

- `GET /user-action/stats`
- `GET /user-action/list`

记录搜索日志：

- `POST /user-action/search-log`

## 错误码说明

| 错误码 | 说明 |
| --- | --- |
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未登录或 Token 无效 |
| 403 | 已登录但无权限 |
| 404 | 资源不存在 |
| 500 | 服务内部错误 |

## 说明

- 本文档以当前控制器与 DTO/VO 为准，适合作为开发说明和论文附录摘要。
- 若出现字段级差异，请以运行时 Swagger 文档和实际返回 JSON 为最终依据。
