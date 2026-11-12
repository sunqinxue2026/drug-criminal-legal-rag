# 故障排查指南

## 常见编译错误及解决方案

### 错误 1：`java.lang.ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag`

**原因**：JDK 版本过新，和当前编译链存在兼容问题。

**解决方案**

#### 方案 A：使用 JDK 17

当前项目最稳定的做法是直接使用 JDK 17。

#### 方案 B：在 `.mvn/jvm.config` 中加入兼容参数

```text
-Dmaven.wagon.http.ssl.insecure=true
-Dmaven.wagon.http.ssl.allowall=true
--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED
--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED
--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED
--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED
--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED
--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED
--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED
```

### 错误 2：`Communications link failure`

**原因**：MySQL 未启动，或数据库配置错误。

**解决方案**

1. 启动 MySQL
2. 检查 `application.yml` 中的账号密码
3. 确认数据库 `legal_rag` 已创建
4. 确认已执行根目录 `docs/init.sql`

### 错误 3：`Port 8080 was already in use`

**原因**：8080 端口被占用。

**解决方案**

```powershell
netstat -ano | findstr :8080
taskkill /PID <PID> /F
```

或修改 `application.yml` 中的端口。

### 错误 4：Maven 依赖下载失败

**原因**：网络问题或仓库连接失败。

**解决方案**

1. 配置国内 Maven 镜像
2. 清理本地缓存后重试

```powershell
Remove-Item -Recurse -Force ~/.m2/repository
.\mvnw.cmd clean compile
```

### 错误 5：Lombok 不生效

**原因**：IDEA 未安装 Lombok 插件，或未启用注解处理。

**解决方案**

1. 安装 Lombok 插件
2. 开启 Annotation Processing
3. 重新构建项目

### 错误 6：表不存在

**原因**：数据库未初始化，或脚本路径使用错误。

**解决方案**

```powershell
mysql -u root -p legal_rag < ..\docs\init.sql
```

说明：当前仓库应使用根目录 `docs/init.sql`，而不是不存在的 `src/main/resources/sql/init.sql`。

### 错误 7：`Access denied for user 'root'@'localhost'`

**原因**：数据库用户名或密码错误。

**解决方案**

1. 确认 MySQL 密码正确
2. 修改 `application.yml`
3. 必要时重置 MySQL 密码

### 错误 8：`OutOfMemoryError: Java heap space`

**原因**：JVM 堆内存不足。

**解决方案**

在 `.mvn/jvm.config` 中加入：

```text
-Xms512m
-Xmx1024m
```

或直接用 JVM 参数启动。

## 调试技巧

```powershell
.\mvnw.cmd clean compile -e
.\mvnw.cmd clean compile -X
.\mvnw.cmd clean compile -o
.\mvnw.cmd clean compile -U
```

## 检查清单

- [ ] JDK 17 已安装
- [ ] MySQL 8.0 已启动
- [ ] 数据库 `legal_rag` 已创建
- [ ] 已执行 `..\docs\init.sql`
- [ ] `application.yml` 中数据库密码正确
- [ ] 8080 端口未被占用
- [ ] `web-flask` 和 `web-file` 地址配置正确

## 获取帮助

如果以上方案仍不能解决问题，建议按以下顺序排查：

1. 查看 [README.md](/D:/CODE/RAG/web-springboot/README.md)
2. 查看 [QUICKSTART.md](/D:/CODE/RAG/web-springboot/QUICKSTART.md)
3. 查看 [PROJECT_OVERVIEW.md](/D:/CODE/RAG/web-springboot/PROJECT_OVERVIEW.md)
4. 查看 [API文档.md](/D:/CODE/RAG/docs/API文档.md)
5. 查看 Spring Boot 控制台日志

## 启动成功的标志

访问：

<http://localhost:8080/api/health>

返回类似结果：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "status": "UP",
    "service": "legal-rag-backend"
  }
}
```
