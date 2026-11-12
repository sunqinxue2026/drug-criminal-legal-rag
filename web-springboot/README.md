# Spring Boot 业务后端

本目录是项目中的业务后端，负责用户、权限、专题案例、聊天会话，以及法律知识和法律文书的后台管理，并与 Flask 算法服务、文件服务协同完成完整业务链路。

## 技术栈

- Java 17
- Spring Boot 3.1.5
- MyBatis Plus 3.5.7
- MySQL 8.0
- JWT
- BCrypt
- SpringDoc OpenAPI

## 模块职责

- 用户注册、登录与身份校验
- 分类、专题案例、收藏、聊天记录管理
- 法律知识导入、预览、删除、索引重建
- 法律文书导入、官方文书同步、分块索引管理
- 调用 Flask 算法服务完成检索、RAG 问答和索引刷新
- 调用文件服务完成附件上传与访问

## 运行前准备

### 1. 初始化数据库

执行仓库根目录的 [init.sql](../docs/init.sql) 初始化数据库，默认数据库名为：

```sql
legal_rag
```

### 2. 配置数据库与外部服务

主要配置文件：

```text
D:\CODE\RAG\web-springboot\src\main\resources\application.yml
```

重点检查以下配置：

- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`
- `external.algorithm-service.base-url`
- `external.file-service.base-url`
- `jwt.secret`

默认情况下：

- Spring Boot：`http://localhost:8080/api`
- Flask 算法服务：`http://localhost:5000`
- 文件服务：`http://localhost:5001`

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

启动后默认地址：

```text
http://localhost:8080/api
```

接口文档：

```text
http://localhost:8080/api/swagger-ui.html
```

## 默认账号

执行初始化脚本后可使用：

- 管理员：`admin / admin123`
- 普通用户：`user1 / 123456`
- 普通用户：`user2 / 123456`

## 主要接口模块

- `/user`：登录、注册、用户信息、后台用户管理
- `/category`：分类查询与管理
- `/item`：专题案例展示、导入、重建索引、官方案例同步
- `/favorite`：收藏列表与收藏状态
- `/chat`：会话管理、消息历史、多轮咨询
- `/legal-knowledge`：规范知识导入、预览、分页、索引块查看
- `/legal-document`：法律文书导入、官方文书导入、分块索引管理
- `/search`：检索代理接口
- `/file`：文件上传与 URL 转发
- `/user-action`：后台行为统计与搜索日志
- `/llm/models`、`/vector/status`：模型与向量状态
- `/health`：健康检查

## 认证说明

当前后端主要采用：

- 登录后签发 JWT
- 通过自定义鉴权逻辑校验 `Authorization: Bearer <token>`
- 使用 BCrypt 存储用户密码哈希

因此，更准确的描述应是“JWT + 自定义鉴权 + BCrypt”，而不是完整的 Spring Security 认证授权体系。

## 服务依赖关系

要体验完整功能，建议同时启动：

- `web-flask`
- `web-file`
- MySQL

其中：

- 聊天、文书解析、索引重建依赖 `web-flask`
- 图片和文书上传依赖 `web-file`

## 健康检查

```bash
curl http://localhost:8080/api/health
```

## 当前说明

- 当前系统重点服务“涉毒刑事法律风险分析与普法”场景
- 后端已包含法律知识、法律文书、分块索引管理等能力，不再只是传统的用户和案例 CRUD
- 若外部算法服务或文件服务未启动，部分接口会因下游不可用而失败
