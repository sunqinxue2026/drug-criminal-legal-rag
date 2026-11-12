# 快速参考指南

## 常用命令

| 操作 | 命令 | 说明 |
| --- | --- | --- |
| 开发启动 | `.\start-dev.bat` | 快速启动后端 |
| 完整启动 | `.\start-v2.bat` | 编译、打包后启动 |
| 修复编译 | `.\fix-compile.bat` | 处理 JDK 兼容与编译问题 |
| 直接运行 | `.\mvnw.cmd spring-boot:run` | Maven Wrapper 启动 |
| 仅编译 | `.\mvnw.cmd clean compile` | 只编译不运行 |
| 运行测试 | `.\mvnw.cmd -q test` | 运行 Java 测试 |
| 打包 | `.\mvnw.cmd clean package -DskipTests` | 打包 jar |

## 数据库初始化

当前仓库使用根目录脚本：

```powershell
mysql -u root -p legal_rag < ..\docs\init.sql
```

不要再使用不存在的 `init-db.bat` 或 `src/main/resources/sql/init.sql` 路径。

## 访问地址

| 项目 | 地址 |
| --- | --- |
| Spring Boot API | <http://localhost:8080/api> |
| Swagger | <http://localhost:8080/api/swagger-ui.html> |
| 健康检查 | <http://localhost:8080/api/health> |
| Flask 服务 | <http://localhost:5000> |
| 文件服务 | <http://localhost:5001> |
| 前端 | <http://localhost:5174> |

## 默认账号

- 用户名：`admin`
- 密码：`admin123`

## 快速验证

### 1. 健康检查

```powershell
curl http://localhost:8080/api/health
```

### 2. 登录测试

```powershell
curl -X POST http://localhost:8080/api/user/login `
  -H "Content-Type: application/json" `
  -d '{"username":"admin","password":"admin123"}'
```

### 3. 分类查询

```powershell
curl http://localhost:8080/api/category/list
```

## 目录内文档导航

| 文档 | 内容 |
| --- | --- |
| `README.md` | 后端整体说明 |
| `QUICKSTART.md` | 快速启动步骤 |
| `QUICK_REFERENCE.md` | 常用命令与地址速查 |
| `TROUBLESHOOTING.md` | 故障排查 |
| `PROJECT_OVERVIEW.md` | 当前后端现状总览 |

## 仓库根目录重要文档

| 文档 | 内容 |
| --- | --- |
| `docs/API文档.md` | 当前接口说明 |
| `docs/数据库设计文档.md` | 数据库与检索设计 |
| `docs/legal_document_schema_v2.md` | 法律文书字段设计 |
| `docs/THESIS_COMPLETION_TRACKER.md` | 论文补强清单 |

## 当前后端能力

- 用户、分类、专题案例、收藏、聊天
- 法律知识导入与索引重建
- 法律文书导入、官方文书导入与索引块管理
- 模型状态与向量状态查看
- 用户行为统计与搜索日志

## 说明

- 本目录不再维护不存在的 `MODULES.md`、`API_TEST.md`、`DEPLOYMENT.md`、`PROJECT_SUMMARY.md`
- 如果文档和实际接口有差异，请以 Swagger 和控制器代码为准
