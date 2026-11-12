# 🚀 项目启动完整指南（Windows版）

## 第一步：确认环境准备

### 1.1 检查软件安装

打开 PowerShell，依次输入以下命令检查：

```powershell
# 检查 Java
java -version
# 应该显示：java version "17.x.x" 或更高

# 检查 Python
python --version
# 应该显示：Python 3.8.x 或更高

# 检查 Node.js
node -v
# 应该显示：v18.x.x 或更高

# 检查 npm
npm -v
# 应该显示版本号
```

如果任何命令报错，请先安装对应软件。

### 1.2 确认 MySQL 运行

1. 打开 Navicat Premium 17
2. 双击你的 MySQL 连接
3. 确认能够成功连接
4. 确认 `legal_rag` 数据库已存在

---

## 第二步：配置数据库连接

1. 打开文件：`D:\CODE\RAG\web-springboot\src\main\resources\application.yml`

2. 找到第 8-10 行，修改为你的 MySQL 配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/legal_rag?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root          # 改为你的 MySQL 用户名
    password: your_password # 改为你的 MySQL 密码
```

3. 保存文件（Ctrl+S）

---

## 第三步：启动服务（手动方式）

### 为什么使用手动方式？
- ✅ 可以清楚看到每个服务的启动状态
- ✅ 便于发现和解决问题
- ✅ 了解每个服务的作用

### 3.1 打开 4 个终端

在 VS Code 中：
1. 按 `Ctrl + Shift + `` （反引号）打开终端
2. 点击终端右上角的 `+` 号，创建新终端
3. 重复 3 次，总共 4 个终端

或者直接打开 4 个 PowerShell 窗口。

---

### 3.2 终端 1：启动文件服务

**复制以下命令，逐行执行：**

```powershell
# 进入目录
cd D:\CODE\RAG\web-file

# 创建虚拟环境
python -m venv venv

# 激活虚拟环境
.\venv\Scripts\Activate.ps1

# 安装依赖
pip install -r requirements.txt

# 启动服务
python app.py
```

**成功标志：**
```
文件存储服务启动成功！
服务地址: http://localhost:5001
 * Running on http://127.0.0.1:5001
```

✅ 看到这个输出后，**保持此终端运行**，切换到终端 2。

---

### 3.3 终端 2：启动算法服务

**复制以下命令，逐行执行：**

```powershell
# 进入目录
cd D:\CODE\RAG\web-flask

# 创建虚拟环境
python -m venv venv

# 激活虚拟环境
.\venv\Scripts\Activate.ps1

# 安装基础依赖（大型库会在后台安装）
pip install -r requirements.txt

# 启动服务
python app.py
```

**成功标志：**
```
法律咨询智能问答系统 - 算法服务启动成功！
服务地址: http://localhost:5000
 * Running on http://127.0.0.1:5000
```

✅ 看到这个输出后，**保持此终端运行**，切换到终端 3。

---

### 3.4 终端 3：启动后端服务

**复制以下命令，逐行执行：**

```powershell
# 进入目录
cd D:\CODE\RAG\web-springboot

# 启动服务（首次会下载 Maven 依赖，需要 5-10 分钟）
.\mvnw.cmd spring-boot:run
```

**首次启动说明：**
- 会自动下载 Maven wrapper
- 会下载项目依赖（约 200MB）
- 需要等待 5-10 分钟
- 请耐心等待，不要关闭窗口

**成功标志：**
```
========================================
法律咨询智能问答系统启动成功！
API文档地址: http://localhost:8080/api/swagger-ui.html
========================================
```

✅ 看到这个输出后，**保持此终端运行**，切换到终端 4。

---

### 3.5 终端 4：启动前端服务

**复制以下命令，逐行执行：**

```powershell
# 进入目录
cd D:\CODE\RAG\web-vue

# 安装依赖（首次需要 3-5 分钟）
npm install

# 启动服务
npm run dev
```

**首次启动说明：**
- 会下载 node_modules（约 300MB）
- 需要等待 3-5 分钟

**成功标志：**
```
VITE v6.1.0  ready in xxx ms

➜  Local:   http://localhost:5174/
➜  Network: use --host to expose
```

✅ 看到这个输出后，所有服务启动完成！

---

## 第四步：访问系统

### 4.1 打开浏览器

推荐使用 Chrome、Edge 或 Firefox。

访问：**http://localhost:5174**

### 4.2 登录系统

**管理员账号：**
- 用户名：`admin`
- 密码：`admin123`

**普通用户账号：**
- 用户名：`user1`
- 密码：`123456`

### 4.3 验证所有服务

在浏览器中依次访问以下地址，确认都能正常访问：

1. ✅ 前端：http://localhost:5174
2. ✅ 后端健康检查：http://localhost:8080/api/health
3. ✅ 算法服务：http://localhost:5000/health
4. ✅ 文件服务：http://localhost:5001/health
5. ✅ API 文档：http://localhost:8080/api/swagger-ui.html

---

## 🐛 常见问题解决

### 问题 1：PowerShell 脚本执行策略

**错误信息：**
```
无法加载文件 xxx.ps1，因为在此系统上禁止运行脚本
```

**解决方法：**
```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

然后重新执行激活命令。

---

### 问题 2：Python 虚拟环境激活失败

**如果 `.\venv\Scripts\Activate.ps1` 失败，使用以下替代方法：**

```powershell
# 不激活虚拟环境，直接使用虚拟环境中的 Python
venv\Scripts\python.exe -m pip install -r requirements.txt
venv\Scripts\python.exe app.py
```

---

### 问题 3：Maven wrapper 下载失败

**错误信息：**
```
Error downloading maven-wrapper.jar
```

**解决方法 A：使用本地 Maven（如果已安装）**
```powershell
mvn spring-boot:run
```

**解决方法 B：使用 IDE 运行**
1. 在 VS Code 中安装 Java 扩展包
2. 打开文件：`src/main/java/com/gjq/LegalRagApplication.java`
3. 点击文件中的 `Run` 按钮

**解决方法 C：手动下载 Maven wrapper**
访问：https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar

下载后放到：`D:\CODE\RAG\web-springboot\.mvn\wrapper\maven-wrapper.jar`

---

### 问题 4：npm install 失败

**错误信息：**
```
npm error code ENOENT
```

**解决方法：使用淘宝镜像**
```powershell
npm install --registry=https://registry.npmmirror.com
```

---

### 问题 5：端口被占用

**错误信息：**
```
Port 8080 is already in use
```

**解决方法：**
```powershell
# 查看占用端口的进程
netstat -ano | findstr :8080

# 结束进程（替换 <PID> 为实际进程 ID）
taskkill /PID <PID> /F
```

---

### 问题 6：MySQL 连接失败

**错误信息：**
```
Access denied for user 'root'@'localhost'
```

**解决方法：**
1. 检查 `application.yml` 中的用户名密码是否正确
2. 在 Navicat 中测试连接是否成功
3. 确保 MySQL 服务正在运行

---

## 📝 启动检查清单

启动前请确认：

- [ ] MySQL 服务正在运行
- [ ] 已在 Navicat 中导入 `legal_rag` 数据库
- [ ] 已修改 `application.yml` 中的数据库密码
- [ ] Java、Python、Node.js 都已正确安装

启动后请确认：

- [ ] 文件服务启动成功（Port 5001）
- [ ] 算法服务启动成功（Port 5000）
- [ ] 后端服务启动成功（Port 8080）
- [ ] 前端服务启动成功（Port 5174）
- [ ] 浏览器可以访问 http://localhost:5174
- [ ] 可以使用 admin/admin123 登录

---

## 🎯 启动时间参考

| 服务 | 首次启动 | 后续启动 |
|------|---------|---------|
| 文件服务 | 30秒 | 5秒 |
| 算法服务 | 2-3分钟 | 10秒 |
| 后端服务 | 5-10分钟 | 30秒 |
| 前端服务 | 3-5分钟 | 10秒 |

---

## 💡 温馨提示

1. **首次启动需要耐心等待**
   - 后端需要下载 Maven 依赖（约 200MB）
   - 前端需要下载 npm 包（约 300MB）
   - 总共需要 10-15 分钟

2. **保持所有终端窗口打开**
   - 关闭终端会停止对应服务
   - 建议使用 VS Code 的多终端功能

3. **停止服务**
   - 在每个终端按 `Ctrl + C`
   - 或直接关闭终端窗口

4. **后续启动会快很多**
   - 依赖已下载，不需要重新下载
   - 通常 1-2 分钟即可全部启动

---

## 📞 需要帮助？

如果遇到问题：
1. 查看上面的"常见问题解决"部分
2. 检查终端的错误信息
3. 确认所有前置条件都满足
4. 将错误信息发给我，我会帮你解决

---

**现在开始按照步骤启动项目吧！** 🚀

祝你使用愉快！
