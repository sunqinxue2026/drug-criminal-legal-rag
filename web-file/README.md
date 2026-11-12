# 文件存储服务

本目录是项目中的轻量级文件服务，负责接收上传文件、将文件保存到本地 `storage` 目录，并通过 HTTP 提供访问与删除能力。

## 模块职责

- 接收前端或 Spring Boot 转发的文件上传请求
- 按 `bucket` 分类保存文件
- 为已上传文件生成可访问 URL
- 提供文件读取与删除接口

## 技术栈

- Python 3.8+
- Flask 3.0.0
- flask-cors 4.0.0

依赖文件见 [requirements.txt](requirements.txt)。

## 启动方式

```powershell
cd D:\CODE\RAG\web-file
python -m venv venv
.\venv\Scripts\Activate.ps1
pip install -r requirements.txt
python app.py
```

默认监听：

```text
http://localhost:5001
```

默认存储目录：

```text
D:\CODE\RAG\web-file\storage
```

## 主要接口

- `GET /health`：健康检查
- `POST /file/upload`：上传文件
- `GET /file/{bucket}/{object_key}`：访问文件
- `DELETE /file/{bucket}/{object_key}`：删除文件

## 上传说明

上传接口接收：

- 表单字段 `file`：待上传文件
- 可选表单字段 `bucket`：文件分类目录，默认 `default`

成功后返回：

- `bucket`
- `objectKey`
- `url`

其中 `url` 默认形如：

```text
http://localhost:5001/file/{bucket}/{object_key}
```

## 与其他服务的关系

- `web-vue` 通过 Spring Boot 业务后端间接使用本服务
- `web-springboot` 中的文件上传接口会调用本服务
- 图片咨询、文书上传等场景依赖本服务提供可访问的附件 URL

## 当前说明

- 当前实现是本地文件存储方案，不是对象存储云服务。
- 文件会直接写入仓库下的 `storage` 目录，适合开发和演示环境。
- 如果该服务未启动，前端中的图片和文书上传链路将不可用。

