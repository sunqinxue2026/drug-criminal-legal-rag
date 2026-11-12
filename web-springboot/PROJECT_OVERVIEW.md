# Spring Boot 后端项目总览

## 项目定位

`web-springboot` 是整套涉毒刑事法律咨询系统的业务后端，负责：

- 用户、分类、专题案例、收藏、会话等基础业务
- 法律知识与法律文书的后台管理
- 与 Flask 算法服务协同完成 RAG 检索与回答生成
- 与文件服务协同完成附件上传与访问

它不是独立的“单后端演示仓库”，而是整个多服务系统中的业务中心。

## 当前模块

当前已落地的主要接口模块如下：

| 模块 | 路径前缀 | 说明 |
| --- | --- | --- |
| 用户 | `/user` | 登录、注册、用户信息、后台用户管理 |
| 分类 | `/category` | 分类查询与管理 |
| 专题案例 | `/item` | 前台展示、后台导入、官方案例同步、重建索引 |
| 收藏 | `/favorite` | 收藏列表与收藏状态 |
| 聊天 | `/chat` | 会话列表、消息历史、多轮问答 |
| 法律知识 | `/legal-knowledge` | JSON 导入、记录预览、重建索引、索引块查看 |
| 法律文书 | `/legal-document` | 单文件/批量导入、官方文书导入、重建索引 |
| 搜索 | `/search` | 检索代理接口 |
| 文件 | `/file` | 文件服务转发 |
| 行为日志 | `/user-action` | 后台统计与搜索日志 |
| 模型状态 | `/llm/models`、`/vector/status` | Flask 模型与向量状态查看 |
| 健康检查 | `/health` | 服务健康检查 |

## 当前目录内文档

当前目录中实际存在的 Markdown 文档只有以下几份：

| 文件 | 用途 |
| --- | --- |
| `README.md` | 后端整体说明 |
| `QUICKSTART.md` | 快速启动说明 |
| `QUICK_REFERENCE.md` | 常用命令与地址速查 |
| `TROUBLESHOOTING.md` | 故障排查 |
| `PROJECT_OVERVIEW.md` | 本文件，后端现状总览 |

论文相关、数据结构相关文档主要位于仓库根目录的 `docs/` 中，而不是本目录。

## 运行依赖

要完整体验当前后端能力，通常需要同时准备：

- MySQL
- `web-flask`
- `web-file`

其中：

- 聊天问答、文书解析、索引重建依赖 `web-flask`
- 图片和文书上传依赖 `web-file`

## 启动方式

### 使用 Maven Wrapper

```powershell
cd D:\CODE\RAG\web-springboot
mvnw.cmd spring-boot:run
```

### 或使用本机 Maven

```powershell
cd D:\CODE\RAG\web-springboot
mvn spring-boot:run
```

默认地址：

```text
http://localhost:8080/api
```

Swagger：

```text
http://localhost:8080/api/swagger-ui.html
```

## 认证方式

当前实现采用：

- JWT 签发登录态
- 自定义请求鉴权服务与拦截器做用户/管理员访问校验
- BCrypt 存储密码哈希

因此，更准确的描述是“JWT + 自定义鉴权 + BCrypt”，而不是完整的 Spring Security 授权体系。

## 与当前项目状态对齐的结论

- 前端已经存在且可用，`web-vue` 不是“待开发”
- Flask 算法服务已经存在且承担核心 RAG 能力，`web-flask` 不是占位目录
- 后端已经扩展到法律知识、法律文书和索引管理，不再只是用户与案例 CRUD
- 本目录旧版“文档清单”和“开发进度”如果引用了不存在文件或“前端待开始”等表述，均不应再使用

## 建议搭配阅读

- [README.md](/D:/CODE/RAG/web-springboot/README.md)
- [QUICKSTART.md](/D:/CODE/RAG/web-springboot/QUICKSTART.md)
- [QUICK_REFERENCE.md](/D:/CODE/RAG/web-springboot/QUICK_REFERENCE.md)
- [API文档.md](/D:/CODE/RAG/docs/API文档.md)
- [数据库设计文档.md](/D:/CODE/RAG/docs/数据库设计文档.md)
- [legal_document_schema_v2.md](/D:/CODE/RAG/docs/legal_document_schema_v2.md)

## 更新时间

本文档已按当前仓库状态更新，基准时间为 `2026-03-28`。
